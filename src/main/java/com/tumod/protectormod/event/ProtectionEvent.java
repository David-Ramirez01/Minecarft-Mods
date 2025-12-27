package com.tumod.protectormod.event;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = ProtectorMod.MOD_ID)
public class ProtectionEvent {

    private static final Map<UUID, Boolean> PLAYER_INSIDE_CACHE = new HashMap<>();

    // --- 1. GESTIÓN DE BLOQUES Y FUEGO ---

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (isActionRestricted(player, event.getPos(), "break", true)) {
            event.setCanceled(true);
            player.displayClientMessage(Component.literal("§c[!] No puedes romper bloques aquí."), true);
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

    // FLAG: fire-spread (Previene que el fuego se propague a bloques vecinos)
    @SubscribeEvent
    public static void onFireSpread(BlockEvent.NeighborNotifyEvent event) {
        ProtectionCoreBlockEntity core = findCoreAt((Level) event.getLevel(), event.getPos());
        if (core != null && !core.getFlag("fire-spread")) {
            // Si el bloque vecino es fuego, cancelamos la notificación para que no se extienda
            event.setCanceled(true);
        }
    }

    // FLAG: lighter (Previene encender fuego con mechero)
    @SubscribeEvent
    public static void onLighterUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().getItem() instanceof net.minecraft.world.item.FlintAndSteelItem) {
            ProtectionCoreBlockEntity core = findCoreAt(event.getLevel(), event.getPos());
            if (core != null && !canBypass(event.getEntity(), core) && !core.getFlag("lighter")) {
                event.setCanceled(true);
                event.getEntity().displayClientMessage(Component.literal("§c[!] El uso de mecheros está prohibido aquí."), true);
            }
        }
    }

    // --- 2. DAÑO Y EXPLOSIONES ---

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        ProtectionCoreBlockEntity core = findCoreAt(event.getEntity().level(), event.getEntity().blockPosition());
        if (core == null) return;

        // PvP
        if (event.getSource().getEntity() instanceof Player && event.getEntity() instanceof Player) {
            if (!core.getFlag("pvp")) event.setCanceled(true);
        }

        // Daño por Fuego (FLAG: fire-damage)
        if (event.getSource().is(DamageTypes.IN_FIRE) || event.getSource().is(DamageTypes.ON_FIRE) || event.getSource().is(DamageTypes.LAVA)) {
            if (!core.getFlag("fire-damage")) event.setCanceled(true);
        }

        // Caída (FLAG: fall-damage)
        if (event.getSource().is(DamageTypes.FALL)) {
            if (!core.getFlag("fall-damage")) event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        BlockPos pos = BlockPos.containing(event.getExplosion().center());
        ProtectionCoreBlockEntity core = findCoreAt(event.getLevel(), pos);
        if (core != null && !core.getFlag("explosions")) {
            event.getAffectedBlocks().clear();
        }
    }

    // --- 3. INTERACCIONES ---

    @SubscribeEvent
    public static void onInteractBlock(PlayerInteractEvent.RightClickBlock event) {
        BlockPos pos = event.getPos();
        Player player = event.getEntity();
        Level level = player.level();

        ProtectionCoreBlockEntity core = findCoreAt(level, pos);
        if (core == null || canBypass(player, core)) return;

        boolean isContainer = level.getBlockEntity(pos) != null &&
                !(level.getBlockEntity(pos) instanceof ProtectionCoreBlockEntity);

        String flagNeeded = isContainer ? "chests" : "interact";

        if (!core.hasPermission(player, flagNeeded) && !core.getFlag(flagNeeded)) {
            event.setCanceled(true);
            if (isContainer) player.displayClientMessage(Component.literal("§c[!] Los contenedores están protegidos."), true);
        }
    }

    // --- 4. TICKS Y AUXILIARES ---

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || player.tickCount % 5 != 0) return;

        ProtectionCoreBlockEntity core = findCoreAt(player.level(), player.blockPosition());
        updateEntryMessage(player, core != null);

        if (core != null) {
            if (!core.getFlag("hunger")) player.getFoodData().setFoodLevel(20);
            if (!core.getFlag("entry") && !core.hasPermission(player, "entry") && !player.hasPermissions(2)) {
                ejectPlayer(player, core);
            }
        }
    }

    private static boolean isActionRestricted(Player player, BlockPos pos, String permission, boolean isBreak) {
        if (player.hasPermissions(2)) return false;
        ProtectionCoreBlockEntity core = findCoreAt(player.level(), pos);
        if (core == null) return false;
        if (canBypass(player, core) || core.hasPermission(player, "build")) return false;

        String flagToCheck = isBreak ? "break" : "build";
        return !core.getFlag(flagToCheck);
    }

    private static boolean canBypass(Player player, ProtectionCoreBlockEntity core) {
        return player.getUUID().equals(core.getOwnerUUID()) || player.hasPermissions(2);
    }

    private static ProtectionCoreBlockEntity findCoreAt(Level level, BlockPos pos) {
        for (ProtectionCoreBlockEntity core : ProtectionCoreBlockEntity.getLoadedCores()) {
            if (core.getLevel() == level && core.isInside(pos)) return core;
        }
        return null;
    }

    private static void ejectPlayer(Player player, ProtectionCoreBlockEntity core) {
        Vec3 coreCenter = Vec3.atCenterOf(core.getBlockPos());
        Vec3 exitDir = player.position().subtract(coreCenter).normalize();
        double radius = core.getRadius() + 1.5;
        Vec3 exitPoint = coreCenter.add(exitDir.scale(radius));
        player.teleportTo(exitPoint.x, player.getY(), exitPoint.z);
        player.displayClientMessage(Component.literal("§c§l[!] §cEntrada restringida."), true);
    }

    private static void updateEntryMessage(Player player, boolean isInside) {
        UUID uuid = player.getUUID();
        boolean wasInside = PLAYER_INSIDE_CACHE.getOrDefault(uuid, false);
        if (!wasInside && isInside) player.displayClientMessage(Component.literal("§eEntraste a zona protegida"), true);
        if (wasInside && !isInside) player.displayClientMessage(Component.literal("§aSaliste de zona protegida"), true);
        PLAYER_INSIDE_CACHE.put(uuid, isInside);
    }


    private static void renderAreaParticles(ServerLevel level, Player player) {
        for (ProtectionCoreBlockEntity core : ProtectionCoreBlockEntity.getLoadedCores()) {
            if (core.getBlockPos().closerThan(player.blockPosition(), 64)) {
                BlockPos center = core.getBlockPos();
                int r = core.getRadius();
                int pY = (int) player.getY();

                // Definimos las 4 esquinas del área
                int[][] corners = {
                        {center.getX() - r, center.getZ() - r},
                        {center.getX() + r + 1, center.getZ() - r},
                        {center.getX() - r, center.getZ() + r + 1},
                        {center.getX() + r + 1, center.getZ() + r + 1}
                };

                // Dibujamos columnas verticales en las esquinas (alrededor del jugador)
                for (int[] corner : corners) {
                    for (int yOff = -10; yOff <= 10; yOff++) {
                        level.sendParticles(
                                core.isAdmin() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.END_ROD,
                                corner[0] + 0.5,
                                pY + yOff,
                                corner[1] + 0.5,
                                1, 0, 0, 0, 0
                        );
                    }
                }

                // Dibujamos el borde horizontal a la altura de los pies del jugador
                for (int i = -r; i <= r; i++) {
                    var p = core.isAdmin() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.END_ROD;
                    level.sendParticles(p, center.getX() + i + 0.5, pY, center.getZ() - r, 1, 0, 0, 0, 0);
                    level.sendParticles(p, center.getX() + i + 0.5, pY, center.getZ() + r + 1, 1, 0, 0, 0, 0);
                    level.sendParticles(p, center.getX() - r, pY, center.getZ() + i + 0.5, 1, 0, 0, 0, 0);
                    level.sendParticles(p, center.getX() + r + 1, pY, center.getZ() + i + 0.5, 1, 0, 0, 0, 0);
                }
            }
        }
    }
}