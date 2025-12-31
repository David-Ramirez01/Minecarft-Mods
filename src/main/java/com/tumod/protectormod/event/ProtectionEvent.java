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

    // --- 1. GESTIÓN DE BLOQUES ---

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        // Cambiamos a "build" para que una sola flag controle todo lo referente a obras
        if (isActionRestricted(event.getPlayer(), event.getLevel(), event.getPos(), "build")) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player) {
            // Usamos "build" consistentemente
            if (isActionRestricted(player, event.getLevel(), event.getPos(), "build")) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onMobSpawn(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        // Solo nos interesan los mobs hostiles (monstruos) y que sea en el servidor
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof net.minecraft.world.entity.monster.Monster mob)) return;

        BlockPos pos = mob.blockPosition();
        ServerLevel sLevel = (ServerLevel) event.getLevel();

        ProtectionCoreBlockEntity core = findCoreAt(sLevel, pos);
        if (core != null) {
            // Buscamos la flag "mob-spawn". Si es FALSE (Roja), cancelamos el spawn.
            if (!core.getFlag("mob-spawn")) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onBucketUse(PlayerInteractEvent.RightClickBlock event) {
        BlockPos targetPos = event.getPos().relative(event.getFace());
        if (isActionRestricted(event.getEntity(), event.getLevel(), targetPos, "build")) {
            event.setCanceled(true);
        }
    }

    // --- 2. EXPLOSIONES ---

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel sLevel)) return;

        event.getAffectedBlocks().removeIf(pos -> {
            ProtectionCoreBlockEntity core = findCoreAt(sLevel, pos);
            return core != null && !core.getFlag("explosions");
        });
    }

    // --- 3. DAÑO E INTERACCIONES ---

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel sLevel)) return;

        ProtectionCoreBlockEntity core = findCoreAt(sLevel, event.getEntity().blockPosition());
        if (core == null) return;

        // 1. Lógica de Fuego (del Admin Core)
        if (event.getSource().is(DamageTypes.IN_FIRE) || event.getSource().is(DamageTypes.LAVA) || event.getSource().is(DamageTypes.ON_FIRE)) {
            if (!core.getFlag("fire-damage")) {
                event.setCanceled(true);
                event.getEntity().setRemainingFireTicks(0); // Apaga al jugador
                return;
            }
        }

        // 2. Lógica de PvP
        if (event.getSource().getEntity() instanceof Player && event.getEntity() instanceof Player) {
            if (!core.getFlag("pvp")) {
                event.setCanceled(true);
            }
        }

        // 3. Lógica de Caída
        if (event.getSource().is(DamageTypes.FALL) && !core.getFlag("fall-damage")) {
            event.setCanceled(true);
        }
    }


    @SubscribeEvent
    public static void onInteractBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) return;

        BlockPos pos = event.getPos();
        Player player = event.getEntity();
        ServerLevel sLevel = (ServerLevel) event.getLevel();

        ProtectionCoreBlockEntity core = findCoreAt(sLevel, pos);
        // Si no hay core, o es el bloque físico del core, o el jugador es de confianza, permitimos.
        if (core == null || pos.equals(core.getBlockPos()) || core.isTrusted(player)) return;

        BlockEntity be = sLevel.getBlockEntity(pos);

        // 🔹 Lógica de Flags Independiente
        // Si el bloque tiene inventario, usamos "chests". Si no, es una interacción (puertas/palancas).
        String flagNeeded = (be instanceof net.minecraft.world.Container) ? "chests" : "interact";

        // Verificamos ÚNICAMENTE la flag necesaria.
        if (!core.getFlag(flagNeeded)) {
            event.setCanceled(true);
            String msg = flagNeeded.equals("chests") ? "§c[!] Los cofres están protegidos." : "§c[!] La interacción está desactivada.";
            player.displayClientMessage(Component.literal(msg), true);
        }
    }


    // --- 4. TICKS Y MENSAJES ---

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

    // --- 5. LÓGICA DE BÚSQUEDA CENTRALIZADA ---

    private static boolean isActionRestricted(Player player, LevelAccessor level, BlockPos pos, String action) {
        if (level.isClientSide()) return false;
        ServerLevel sLevel = (ServerLevel) level;

        ProtectionCoreBlockEntity core = findCoreAt(sLevel, pos);
        if (core == null) return false;

        // 1. JERARQUÍA 1: Si es Trusted (Dueño, OP, Clan, Invitado), omitimos las flags.
        if (core.isTrusted(player)) return false;

        // 2. JERARQUÍA 2: Si NO es trusted, manda la Flag.
        // Si la flag es TRUE (ON), significa que el acceso es PÚBLICO.
        boolean isPublic = core.getFlag(action);

        if (isPublic) {
            return false; // La flag está en ON, cualquier usuario puede actuar.
        } else {
            // La flag está en OFF y el jugador no es de confianza: BLOQUEAR.
            String ownerName = core.isAdmin() ? "Administración" : core.getOwnerName();
            player.displayClientMessage(Component.literal("§c[!] Acción bloqueada por la protección de " + ownerName), true);
            return true;
        }
    }

    // Lógica para detección CUADRADA y VERTICAL COMPLETA
    public static ProtectionCoreBlockEntity findCoreAt(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel sLevel)) return null;

        ProtectionDataManager manager = ProtectionDataManager.get(sLevel);
        ProtectionDataManager.CoreEntry entry = manager.getCoreAt(pos);

        if (entry != null) {
            // Obtenemos la entidad de bloque real usando la posición guardada en el entry
            if (sLevel.getBlockEntity(entry.pos()) instanceof ProtectionCoreBlockEntity core) {
                return core;
            }
        }
        return null;
    }

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