package com.tumod.protectormod.event;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.util.ProtectionDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.tumod.protectormod.command.ClanCommands;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
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

    private static final Map<UUID, BlockPos> PLAYER_CORE_CACHE = new HashMap<>();

    // --- 1. EL CORTAFUEGOS (INTERACCIONES) ---
    // Usamos HIGHEST para interceptar la acción antes que cualquier otra lógica de construcción.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteractBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) return;

        BlockPos pos = event.getPos();
        Player player = event.getEntity();
        ServerLevel sLevel = (ServerLevel) event.getLevel();
        ItemStack itemInHand = event.getItemStack();

        ProtectionCoreBlockEntity core = findCoreAt(sLevel, pos);

        // 1. Validaciones básicas de omisión
        if (core == null || core.isTrusted(player) || pos.equals(core.getBlockPos())) return;

        // 2. Identificar el tipo de bloque
        BlockEntity be = sLevel.getBlockEntity(pos);
        boolean isContainer = be instanceof net.minecraft.world.Container;

        // 3. LÓGICA DE INDEPENDENCIA TOTAL
        // Si es un cofre y la flag 'chests' está ON (Verde)
        if (isContainer && core.getFlag("chests")) {
            allowInteractionButDenyPlacement(event);
            return;
        }

        // Si es un botón/puerta y la flag 'interact' está ON (Verde)
        if (!isContainer && core.getFlag("interact")) {
            allowInteractionButDenyPlacement(event);
            return;
        }

        // 4. SI LLEGAMOS AQUÍ, es porque la flag específica está OFF o no es una interacción válida.
        // Si la flag 'build' está desactivada, cancelamos el evento completamente.
        if (!core.getFlag("build")) {
            event.setCanceled(true);
            String owner = core.isAdmin() ? "la Administración" : core.getOwnerName();
            player.displayClientMessage(Component.literal("§c[!] Zona protegida por " + owner), true);
        }
    }

    /**
     * Este método es la clave: Permite que el bloque se abra/use,
     * pero prohíbe que el ítem de la mano intente colocarse como bloque.
     */
    private static void allowInteractionButDenyPlacement(PlayerInteractEvent.RightClickBlock event) {
        // Obligamos a Minecraft a usar el BLOQUE
        event.setUseBlock(net.neoforged.neoforge.common.util.TriState.TRUE);
        // PROHIBIMOS usar el ÍTEM (esto evita que salte el mensaje de 'No puedes construir')
        event.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);
    }

    // --- 2. GESTIÓN DE BLOQUES (SOLO PARA ROMPER Y PONER REALMENTE) ---

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (isBuildRestricted(event.getPlayer(), event.getLevel(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (isBuildRestricted(player, event.getLevel(), event.getPos())) {
                event.setCanceled(true);
            }
        }
    }

    private static boolean isBuildRestricted(Player player, LevelAccessor level, BlockPos pos) {
        if (level.isClientSide()) return false;
        ProtectionCoreBlockEntity core = findCoreAt((ServerLevel) level, pos);
        if (core == null || core.isTrusted(player)) return false;

        if (!core.getFlag("build")) {
            String owner = core.isAdmin() ? "la Administración" : core.getOwnerName();
            player.displayClientMessage(Component.literal("§c[!] No puedes construir en la zona de " + owner), true);
            return true;
        }
        return false;
    }

    // --- 3. OTROS EVENTOS (EXPLOSIONES, DAÑO, MOBS) ---

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel sLevel)) return;
        event.getAffectedBlocks().removeIf(pos -> {
            ProtectionCoreBlockEntity core = findCoreAt(sLevel, pos);
            return core != null && !core.getFlag("explosions");
        });
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel sLevel)) return;
        ProtectionCoreBlockEntity core = findCoreAt(sLevel, event.getEntity().blockPosition());
        if (core == null) return;

        // PvP, Caída y Fuego
        if (event.getSource().getEntity() instanceof Player && event.getEntity() instanceof Player && !core.getFlag("pvp"))
            event.setCanceled(true);
        if (event.getSource().is(DamageTypes.FALL) && !core.getFlag("fall-damage")) event.setCanceled(true);
        if ((event.getSource().is(DamageTypes.IN_FIRE) || event.getSource().is(DamageTypes.LAVA)) && !core.getFlag("fire-damage")) {
            event.setCanceled(true);
            event.getEntity().setRemainingFireTicks(0);
        }
    }

    // --- 4. UTILIDADES DE BÚSQUEDA ---

    public static ProtectionCoreBlockEntity findCoreAt(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel sLevel)) return null;
        ProtectionDataManager.CoreEntry entry = ProtectionDataManager.get(sLevel).getCoreAt(pos);
        if (entry != null && sLevel.getBlockEntity(entry.pos()) instanceof ProtectionCoreBlockEntity core) {
            return core;
        }
        return null;
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || player.tickCount % 20 != 0) return;

        ServerLevel level = (ServerLevel) player.level();
        ProtectionCoreBlockEntity core = findCoreAt(level, player.blockPosition());

        updateEntryMessage(player, core);

        if (core != null) {
            if (!core.getFlag("hunger")) player.getFoodData().setFoodLevel(20);
            if (!core.getFlag("entry") && !canBypass(player, core)) ejectPlayer(player, core);
        }

        // Lógica del visualizador
        if (ClanCommands.VISUALIZER_ENABLED.contains(player.getUUID())) {
            renderAreaParticles(level, player);
        }
    }


    // --- 4. TICKS Y MENSAJES ---



    // --- UTILIDADES ---

    private static void updateEntryMessage(Player player, @Nullable ProtectionCoreBlockEntity core) {
        UUID uuid = player.getUUID();
        BlockPos lastCorePos = PLAYER_CORE_CACHE.get(uuid); // Ahora devuelve BlockPos
        BlockPos currentCorePos = (core != null) ? core.getBlockPos() : null;

        // Comparamos posiciones de bloques (IDs únicos)
        if (!java.util.Objects.equals(lastCorePos, currentCorePos)) {
            if (currentCorePos != null) {
                // ENTRANDO
                String displayName = core.isAdmin() ? "§d§lAdministración" : "§b" + core.getOwnerName();

                // Solo enviamos el mensaje si el jugador no es el dueño
                if (!player.getUUID().equals(core.getOwnerUUID())) {
                    player.displayClientMessage(Component.literal("§e§l[!] §fEntrando a la zona de: " + displayName), true);
                }
            } else if (lastCorePos != null) {
                // SALIENDO
                player.displayClientMessage(Component.literal("§cHas salido de la zona protegida"), true);
            }

            // Guardamos la nueva posición en la caché
            PLAYER_CORE_CACHE.put(uuid, currentCorePos);
        }
    }

    private static void ejectPlayer(Player player, ProtectionCoreBlockEntity core) {
        Vec3 coreCenter = Vec3.atCenterOf(core.getBlockPos());
        double radius = core.getRadius() + 1.5;
        Vec3 exitPoint = coreCenter.add(player.position().subtract(coreCenter).normalize().scale(radius));
        player.teleportTo(exitPoint.x, player.getY(), exitPoint.z);
        player.displayClientMessage(Component.literal("§c§l[!] Entrada restringida."), true);
    }

    private static boolean canBypass(Player player, ProtectionCoreBlockEntity core) {
        return player.getUUID().equals(core.getOwnerUUID()) || player.hasPermissions(2);
    }

    private static void renderAreaParticles(ServerLevel level, Player player) {
        ServerPlayer sPlayer = (ServerPlayer) player;
        ProtectionDataManager data = ProtectionDataManager.get(level);

        for (Map.Entry<BlockPos, ProtectionDataManager.CoreEntry> entry : data.getAllCores().entrySet()) {
            BlockPos center = entry.getKey();
            if (!center.closerThan(player.blockPosition(), 64)) continue;

            int r = entry.getValue().radius();
            var pType = ParticleTypes.END_ROD;
            double y = player.getY() + 0.5;

            for (int i = -r; i <= r; i += 2) {
                sendParticle(sPlayer, pType, center.getX() + i + 0.5, y, center.getZ() - r + 0.5);
                sendParticle(sPlayer, pType, center.getX() + i + 0.5, y, center.getZ() + r + 0.5);
                sendParticle(sPlayer, pType, center.getX() - r + 0.5, y, center.getZ() + i + 0.5);
                sendParticle(sPlayer, pType, center.getX() + r + 0.5, y, center.getZ() + i + 0.5);
            }
        }
    }

    @SubscribeEvent
    public static void onFireSpread(BlockEvent.EntityPlaceEvent event) {
        if (event.getState().is(Blocks.FIRE)) {
            BlockPos pos = event.getPos();
            Level level = (Level) event.getLevel();

            // Usamos tu método centralizado findCoreAt
            ProtectionCoreBlockEntity core = findCoreAt(level, pos);
            if (core != null && !core.getFlag("fire-spread")) {
                event.setCanceled(true);
            }
        }
    }


    @SubscribeEvent
    public static void onLighterUse(PlayerInteractEvent.RightClickBlock event) {
        ItemStack item = event.getItemStack();
        // Verificamos si es un mechero o carga ígnea
        if (item.is(Items.FLINT_AND_STEEL) || item.is(Items.FIRE_CHARGE)) {
            BlockPos targetPos = event.getPos().relative(event.getFace());
            Player player = event.getEntity();

            ProtectionCoreBlockEntity core = findCoreAt(player.level(), targetPos);
            if (core != null) {
                if (!core.getFlag("lighter") && !core.isTrusted(player)) {
                    event.setCanceled(true);
                    player.displayClientMessage(Component.literal("§c[!] El uso de fuego está desactivado aquí."), true);
                }
            }
        }
    }

    private static void sendParticle(ServerPlayer player, net.minecraft.core.particles.ParticleOptions type, double x, double y, double z) {
        player.connection.send(new ClientboundLevelParticlesPacket(type, false, x, y, z, 0, 0, 0, 0, 1));
    }
}