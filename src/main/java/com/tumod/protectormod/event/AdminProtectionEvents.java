package com.tumod.protectormod.event;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

@EventBusSubscriber(modid = ProtectorMod.MOD_ID)
public class AdminProtectionEvents {

    private static ProtectionCoreBlockEntity findCoreAt(LevelAccessor level, BlockPos pos) {
        for (ProtectionCoreBlockEntity core : ProtectionCoreBlockEntity.getLoadedCores()) {
            if (core.getLevel() == level && core.isInside(pos)) {
                return core;
            }
        }
        return null;
    }

    // 1. EXPLOSIONES
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        event.getAffectedBlocks().removeIf(pos -> {
            ProtectionCoreBlockEntity core = findCoreAt(level, pos);
            return core != null && !core.getFlag("explosions");
        });
    }

    // 2. DAÑO (PVP y Fuego)
    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        Level level = event.getEntity().level();
        ProtectionCoreBlockEntity core = findCoreAt(level, event.getEntity().blockPosition());

        if (core != null) {
            // Flag: fire-damage
            if (event.getSource().is(DamageTypes.IN_FIRE) || event.getSource().is(DamageTypes.LAVA) || event.getSource().is(DamageTypes.ON_FIRE)) {
                if (!core.getFlag("fire-damage")) {
                    event.setCanceled(true);
                    event.getEntity().setRemainingFireTicks(0);
                }
            }
            // Flag: pvp
            if (event.getSource().getEntity() instanceof Player && !core.getFlag("pvp")) {
                event.setCanceled(true);
            }
        }
    }

    // 3. FUEGO (Propagación)
    // Nota: Si BurnEvent no existe en tu MDK, usa NeighborNotifyEvent o el chequeo de EntityPlaceEvent
    @SubscribeEvent
    public static void onFireSpread(BlockEvent.EntityPlaceEvent event) {
        if (event.getState().is(Blocks.FIRE) || event.getState().is(Blocks.SOUL_FIRE)) {
            ProtectionCoreBlockEntity core = findCoreAt(event.getLevel(), event.getPos());
            if (core != null && !core.getFlag("fire-spread")) {
                event.setCanceled(true);
            }
        }
    }

    // 4. CONSTRUCCIÓN Y DESTRUCCIÓN (Corregido para 1.21.1)
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (isActionRestricted(player, event.getLevel(), event.getPos(), "build")) {
                event.setCanceled(true);
                player.displayClientMessage(Component.literal("§cZona protegida: No puedes construir."), true);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (isActionRestricted(player, event.getLevel(), event.getPos(), "break")) {
            event.setCanceled(true);
            player.displayClientMessage(Component.literal("§cZona protegida: No puedes romper."), true);
        }
    }

    private static boolean isActionRestricted(Player player, LevelAccessor level, BlockPos pos, String flag) {
        if (player.hasPermissions(2)) return false;

        ProtectionCoreBlockEntity core = findCoreAt(level, pos);
        if (core == null) return false;

        if (player.getUUID().equals(core.getOwnerUUID()) || core.hasPermission(player, "build")) {
            return false;
        }

        return !core.getFlag(flag);
    }
}