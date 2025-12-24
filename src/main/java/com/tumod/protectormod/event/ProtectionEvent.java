package com.tumod.protectormod.event;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = ProtectorMod.MOD_ID)
public class ProtectionEvent {

    private static final Map<UUID, Boolean> PLAYER_INSIDE_CACHE = new HashMap<>();

    // --- PROTECCIÓN DE BLOQUES ---

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (shouldCancelAction(event.getPlayer(), event.getPos())) {
            event.setCanceled(true);
            event.getPlayer().displayClientMessage(Component.literal("§cNo tienes permiso de construcción aquí."), true);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (shouldCancelAction(player, event.getPos())) {
                event.setCanceled(true);
                player.displayClientMessage(Component.literal("§cNo tienes permiso de construcción aquí."), true);
            }
        }
    }

    // Método auxiliar simplificado que devuelve un boolean (evita problemas de ICancelableEvent)
    private static boolean shouldCancelAction(Player player, BlockPos pos) {
        if (player.hasPermissions(2)) return false;
        for (ProtectionCoreBlockEntity core : ProtectionCoreBlockEntity.CORES) {
            if (core.isInside(pos) && !core.hasPermission(player, "build")) {
                return true;
            }
        }
        return false;
    }

    // --- INTERACCIONES ---
    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.hasPermissions(2)) return;

        for (ProtectionCoreBlockEntity core : ProtectionCoreBlockEntity.CORES) {
            if (core.isInside(event.getPos())) {
                boolean canInteract = core.hasPermission(player, "interact") || core.hasPermission(player, "chests");
                if (!canInteract) {
                    event.setCanceled(true);
                    player.displayClientMessage(Component.literal("§cInteracción bloqueada."), true);
                    return;
                }
            }
        }
    }

    // --- HAMBRE ---
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        // Verificamos si la entidad que está tickeando es un jugador
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {

            // Ejecutamos cada 1 segundo (20 ticks) para optimizar
            if (player.tickCount % 20 == 0) {
                for (ProtectionCoreBlockEntity core : ProtectionCoreBlockEntity.CORES) {
                    // Si está dentro y la flag de hambre está apagada (false)
                    if (core.isInside(player.blockPosition()) && !core.getFlag("hunger")) {
                        player.getFoodData().setFoodLevel(20);
                    }
                }
            }
        }
    }

    // --- DAÑO ---
    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        BlockPos pos = event.getEntity().blockPosition();

        for (ProtectionCoreBlockEntity core : ProtectionCoreBlockEntity.CORES) {
            if (core.isInside(pos)) {
                // PvP
                if (event.getSource().getEntity() instanceof Player && event.getEntity() instanceof Player) {
                    if (!core.getFlag("pvp")) event.setCanceled(true);
                }
                // Caída
                if (event.getSource().is(net.minecraft.world.damagesource.DamageTypes.FALL)) {
                    if (!core.getFlag("fall-damage")) event.setCanceled(true);
                }
            }
        }
    }

    // --- EXPLOSIONES Y MOBS ---
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        BlockPos pos = BlockPos.containing(event.getExplosion().center());
        for (ProtectionCoreBlockEntity core : ProtectionCoreBlockEntity.CORES) {
            if (core.isInside(pos) && !core.getFlag("explosions")) {
                event.getAffectedBlocks().clear();
            }
        }
    }

    @SubscribeEvent
    public static void onMobSpawn(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof Monster) {
            for (ProtectionCoreBlockEntity core : ProtectionCoreBlockEntity.CORES) {
                if (core.isInside(event.getEntity().blockPosition()) && !core.getFlag("mob-spawn")) {
                    event.setCanceled(true);
                }
            }
        }
    }

    // --- TICK DE JUGADOR ---
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || player.tickCount % 10 != 0) return;

        BlockPos pos = player.blockPosition();
        boolean isInsideAny = false;

        for (ProtectionCoreBlockEntity core : ProtectionCoreBlockEntity.CORES) {
            if (core.isInside(pos)) {
                isInsideAny = true;
                if (!core.getFlag("entry") && !core.hasPermission(player, "interact") && !player.hasPermissions(2)) {
                    BlockPos exit = core.getBlockPos().relative(player.getDirection().getOpposite(), core.getRadius() + 2);
                    player.teleportTo(exit.getX(), player.getY(), exit.getZ());
                    player.displayClientMessage(Component.literal("§cEntrada restringida."), true);
                }
                break;
            }
        }
        updateEntryMessage(player, isInsideAny);
    }

    private static void updateEntryMessage(Player player, boolean isInside) {
        boolean wasInside = PLAYER_INSIDE_CACHE.getOrDefault(player.getUUID(), false);
        if (!wasInside && isInside) player.displayClientMessage(Component.literal("§eEntraste a zona protegida"), true);
        if (wasInside && !isInside) player.displayClientMessage(Component.literal("§aSaliste de zona protegida"), true);
        PLAYER_INSIDE_CACHE.put(player.getUUID(), isInside);
    }
}