package com.tumod.protectormod.event;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.util.ClanSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.tumod.protectormod.command.ClanCommands;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = ProtectorMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class ProtectionEvent {

    private static final Map<UUID, Boolean> PLAYER_INSIDE_CACHE = new HashMap<>();

    // --- 1. GESTIÓN DE BLOQUES ---

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        System.out.println("DEBUG: Intento de romper bloque en " + event.getPos());
        if (isActionRestricted(event.getPlayer(), event.getPos(), "break", true)) {
            event.setCanceled(true);
            event.getPlayer().displayClientMessage(Component.literal("§c[!] No puedes romper bloques aquí."), true);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (isActionRestricted(player, event.getPos(), "build", false)) {
                event.setCanceled(true);
                player.displayClientMessage(Component.literal("§c[!] No tienes permiso para construir aquí."), true);
            }
        }
    }

    @SubscribeEvent
    public static void onBucketUse(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (event.getItemStack().getItem() instanceof net.minecraft.world.item.BucketItem) {
            BlockPos targetPos = event.getPos().relative(event.getFace());
            if (isActionRestricted(player, targetPos, "build", false)) {
                event.setCanceled(true);
                player.displayClientMessage(Component.literal("§c[!] No puedes usar cubos aquí."), true);
            }
        }
    }

    // --- 2. EXPLOSIONES (FIX ADMIN CORE) ---

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        Level level = (Level) event.getLevel();
        // removeIf para filtrar bloques protegidos
        event.getAffectedBlocks().removeIf(pos -> {
            ProtectionCoreBlockEntity core = findCoreAt(level, pos);
            // Si la flag 'explosions' es false, el bloque NO se rompe
            return core != null && !core.getFlag("explosions");
        });
    }

    // --- 3. DAÑO E INTERACCIONES ---

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        ProtectionCoreBlockEntity core = findCoreAt(event.getEntity().level(), event.getEntity().blockPosition());
        if (core == null) return;

        if (event.getSource().getEntity() instanceof Player && event.getEntity() instanceof Player) {
            if (!core.getFlag("pvp")) event.setCanceled(true);
        }
        if (event.getSource().is(DamageTypes.FALL) && !core.getFlag("fall-damage")) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onInteractBlock(PlayerInteractEvent.RightClickBlock event) {
        BlockPos pos = event.getPos();
        Player player = event.getEntity();
        ProtectionCoreBlockEntity core = findCoreAt(player.level(), pos);

        if (core == null || canBypass(player, core)) return;

        BlockEntity be = player.level().getBlockEntity(pos);
        boolean isContainer = be != null && !(be instanceof ProtectionCoreBlockEntity);
        String flagNeeded = isContainer ? "chests" : "interact";

        if (!core.hasPermission(player, flagNeeded) && !core.getFlag(flagNeeded)) {
            event.setCanceled(true);
            player.displayClientMessage(Component.literal("§c[!] Esta interacción está bloqueada."), true);
        }
    }

    // --- 4. TICKS Y MENSAJES ---

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        ServerLevel level = (ServerLevel) player.level();

        // 1. LÓGICA DEL VISUALIZADOR (Cada 10 ticks para mayor fluidez visual)
        if (player.tickCount % 10 == 0) {
            if (ClanCommands.VISUALIZER_ENABLED.contains(player.getUUID())) {
                renderAreaParticles(level, player);
            }
        }

        // 2. LÓGICA DE PROTECCIÓN Y MENSAJES (Cada 20 ticks / 1 segundo)
        if (player.tickCount % 20 == 0) {
            ProtectionCoreBlockEntity core = findCoreAt(level, player.blockPosition());

            // --- LLAMADA AL MENSAJE ---
            updateEntryMessage(player, core);

            // Lógica de flags pasivas
            if (core != null) {
                // Flag de Hambre (Si es false, el jugador no tiene hambre)
                if (!core.getFlag("hunger")) {
                    player.getFoodData().setFoodLevel(20);
                }

                // Flag de Entrada (Expulsión)
                if (!core.getFlag("entry") && !canBypass(player, core)) {
                    ejectPlayer(player, core);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        ClanSavedData data = ClanSavedData.get(player.serverLevel());

        // Buscamos el clan (ya sea líder o miembro)
        ClanSavedData.ClanInstance clan = data.getClanByMember(player.getUUID());

        if (clan != null && !clan.name.isEmpty()) {
            // Formato limpio: [NombreClan] Jugador: Mensaje
            Component clanPrefix = Component.literal("§8[§6" + clan.name + "§8] ");

            // Aplicamos el prefijo al mensaje
            event.setMessage(Component.empty()
                    .append(clanPrefix)
                    .append(player.getDisplayName())
                    .append(": ")
                    .append(event.getMessage()));
        }
    }

    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ClanSavedData data = ClanSavedData.get(player.serverLevel());
            ClanSavedData.ClanInstance clan = data.getClanByMember(player.getUUID());

            if (clan != null) {
                // Solo añadimos el nombre del clan como prefijo visual
                Component displayName = Component.literal("§8[§6" + clan.name + "§8] ")
                        .append(event.getDisplayname());
                event.setDisplayname(displayName);
            }
        }
    }

    private static void updateEntryMessage(Player player, @Nullable ProtectionCoreBlockEntity core) {
        UUID uuid = player.getUUID();
        boolean wasInside = PLAYER_INSIDE_CACHE.getOrDefault(uuid, false);
        boolean isInside = core != null;

        if (!wasInside && isInside) {
            // 1. Verificar si el jugador quiere ver el mensaje (NBT del jugador)
            boolean wantsPresentation = !player.getPersistentData().contains("ProtectorPresentation") ||
                    player.getPersistentData().getBoolean("ProtectorPresentation");

            if (wantsPresentation) {
                // 2. Obtener el nombre a mostrar
                String displayName = core.getOwnerName();

                // Si es un Admin Core y el nombre es el genérico, poner "Administración"
                if (core.isAdmin() && (displayName == null || displayName.equals("Protector"))) {
                    displayName = "Administración";
                }

                // 3. Solo enviamos el mensaje si el jugador NO es el dueño
                // (Para no spamear al dueño cada vez que entra a su casa)
                if (!player.getUUID().equals(core.getOwnerUUID())) {
                    player.displayClientMessage(
                            Component.literal("§e§l[!] §fEntrando a la zona de: §b" + displayName),
                            true
                    );
                }
            }
        }
        else if (wasInside && !isInside) {
            // Mensaje al salir
            player.displayClientMessage(Component.literal("§aHas salido de la zona protegida"), true);
        }

        // Actualizar el cache
        PLAYER_INSIDE_CACHE.put(uuid, isInside);
    }

    // --- 5. LÓGICA DE BÚSQUEDA (SIN DUPLICADOS) ---

    private static boolean isActionRestricted(Player player, BlockPos pos, String flagKey, boolean isBreak) {
        // 1. Bypass para Operadores
        if (player.hasPermissions(2)) return false;

        ProtectionCoreBlockEntity core = findCoreAt(player.level(), pos);
        if (core == null) return false;

        // 2. Si es el DUEÑO, permitir siempre
        if (player.getUUID().equals(core.getOwnerUUID())) return false;

        // 3. Si es un INVITADO con permiso de 'build', permitir siempre
        // (Asegúrate de que hasPermission use el nombre o UUID correctamente)
        if (core.hasPermission(player, "build")) return false;

        // 4. VERIFICACIÓN DE LA FLAG PÚBLICA
        // Si la flag es TRUE -> Significa que la acción es PÚBLICA (No restringir)
        // Si la flag es FALSE -> Significa que está PROTEGIDO (Restringir)
        boolean estaPermitidoPublicamente = core.getFlag(isBreak ? "break" : "build");

        // LOG DE DEBUG (Solo para nosotros en consola)
        System.out.println("DEBUG: Jugador " + player.getName().getString() +
                " intentó " + (isBreak ? "romper" : "poner") +
                " | Flag Pública: " + estaPermitidoPublicamente);

        // Retornamos el opuesto: si está permitido, la restricción es FALSE.
        return !estaPermitidoPublicamente;
    }
    private static ProtectionCoreBlockEntity findCoreAt(Level level, BlockPos pos) {
        // 1. Buscar en radio (Cubre Admin y Normales)
        for (ProtectionCoreBlockEntity core : ProtectionCoreBlockEntity.getLoadedCores()) {
            if (core.getLevel() == level && core.isInside(pos)) return core;
        }
        // 2. Buscar por bloque físico o parte superior
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ProtectionCoreBlockEntity core) return core;

        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(com.tumod.protectormod.block.ProtectionCoreBlock.HALF) &&
                state.getValue(com.tumod.protectormod.block.ProtectionCoreBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER) {
            return findCoreAt(level, pos.below());
        }
        return null;
    }

    @SubscribeEvent
    public static void onFireSpread(BlockEvent.PortalSpawnEvent event) {
        // Nota: Algunas versiones usan BlockEvent.NeighborNotifyEvent o eventos de tick de bloque
    }

    @SubscribeEvent
    public static void onFireTick(BlockEvent.FarmlandTrampleEvent event) { /* ... */ }

    // RECOMENDADO: Usa este evento para detectar fuego intentando colocarse
    @SubscribeEvent
    public static void onFirePlace(BlockEvent.EntityPlaceEvent event) {
        // Verificamos si lo que se intenta colocar es fuego
        if (event.getState().is(Blocks.FIRE) || event.getState().is(Blocks.SOUL_FIRE)) {

            // Convertimos LevelAccessor a Level de forma segura
            if (event.getLevel() instanceof Level world) {
                ProtectionCoreBlockEntity core = findCoreAt(world, event.getPos());

                // Si hay protección y la flag de propagación/fuego está desactivada (false)
                if (core != null && !core.getFlag("fire-spread")) {
                    event.setCanceled(true);
                }
            }
        }
    }

    private static boolean canBypass(Player player, ProtectionCoreBlockEntity core) {
        return player.getUUID().equals(core.getOwnerUUID()) || player.hasPermissions(2);
    }

    private static boolean estaVisualizadorActivo(Player player) {
        return com.tumod.protectormod.command.ClanCommands.VISUALIZER_ENABLED.contains(player.getUUID());
    }

    private static void ejectPlayer(Player player, ProtectionCoreBlockEntity core) {
        Vec3 coreCenter = Vec3.atCenterOf(core.getBlockPos());
        double radius = core.getRadius() + 1.5;
        Vec3 exitPoint = coreCenter.add(player.position().subtract(coreCenter).normalize().scale(radius));
        player.teleportTo(exitPoint.x, player.getY(), exitPoint.z);
        player.displayClientMessage(Component.literal("§c§l[!] §cEntrada restringida."), true);
    }

    private static void renderAreaParticles(ServerLevel level, Player player) {
        for (ProtectionCoreBlockEntity core : ProtectionCoreBlockEntity.getLoadedCores()) {
            if (core.getBlockPos().closerThan(player.blockPosition(), 32)) {
                BlockPos center = core.getBlockPos();
                int r = core.getRadius();
                var pType = core.isAdmin() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.END_ROD;
                for (int i = -r; i <= r; i += 2) {
                    spawnEdgeParticle(level, pType, center.getX() + i + 0.5, player.getY() + 0.1, center.getZ() - r);
                    spawnEdgeParticle(level, pType, center.getX() + i + 0.5, player.getY() + 0.1, center.getZ() + r + 1);
                    spawnEdgeParticle(level, pType, center.getX() - r, player.getY() + 0.1, center.getZ() + i + 0.5);
                    spawnEdgeParticle(level, pType, center.getX() + r + 1, player.getY() + 0.1, center.getZ() + i + 0.5);
                }
            }
        }
    }

    private static void spawnEdgeParticle(ServerLevel level, net.minecraft.core.particles.ParticleOptions type, double x, double y, double z) {
        level.sendParticles(type, x, y, z, 1, 0, 0.05, 0, 0.01);
    }
}