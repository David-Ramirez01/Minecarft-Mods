package com.tumod.protectormod.event;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.block.AdminProtectorBlock;
import com.tumod.protectormod.block.ProtectionCoreBlock;
import com.tumod.protectormod.blockentity.AdminProtectorBlockEntity;
import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

@EventBusSubscriber(modid = ProtectorMod.MOD_ID)
public class AdminProtectionEvents {

    // 1. EXPLOSIONES
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide) return;

        event.getAffectedBlocks().removeIf(pos -> isAdminArea(level, pos));
        event.getAffectedEntities().removeIf(entity -> isAdminArea(level, entity.blockPosition()));
    }

    // 2. DAÑO (PVP y MOBS)
    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        Level level = victim.level();

        if (isAdminArea(level, victim.blockPosition())) {
            // Bloquea el daño si proviene de otra entidad (Jugador o Mob)
            if (event.getSource().getEntity() != null) {
                event.setCanceled(true);
            }
        }
    }


    // 3. ROMPER BLOQUES (ADMIN Y NORMAL)
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        Level level = (Level) event.getLevel();
        Player player = event.getPlayer();
        BlockPos pos = event.getPos();

        Block block = event.getState().getBlock();
        if (block instanceof ProtectionCoreBlock || block instanceof AdminProtectorBlock) {
            if (level.getBlockEntity(pos) instanceof ProtectionCoreBlockEntity core) {
                boolean isOp = player.hasPermissions(2);
                boolean isOwner = player.getUUID().equals(core.getOwnerUUID());

                if (!isOp && !isOwner) {
                    event.setCanceled(true);
                    player.displayClientMessage(Component.literal("§cNo tienes permiso para retirar este protector."), true);
                    return;
                }
            }
        }

        for (ProtectionCoreBlockEntity core : ProtectionCoreBlockEntity.getLoadedCores()) {
            if (core.getLevel() == level && core.isInside(pos)) {
                if (core instanceof AdminProtectorBlockEntity) {
                    if (!player.hasPermissions(2)) {
                        event.setCanceled(true);
                        player.displayClientMessage(Component.literal("§4Zona Administrativa: No puedes modificar bloques aquí."), true);
                        return;
                    }
                } else if (!core.isTrusted(player)) {
                    event.setCanceled(true);
                    player.displayClientMessage(Component.literal("§cEste terreno está protegido."), true);
                    return;
                }
            }
        }
    }

    // --- UTILIDAD UNIFICADA ---
    private static boolean isAdminArea(Level level, BlockPos pos) {
        for (ProtectionCoreBlockEntity core : ProtectionCoreBlockEntity.getLoadedCores()) {
            if (core instanceof AdminProtectorBlockEntity && core.getLevel() == level && core.isInside(pos)) {
                return true;
            }
        }
        return false;
    }
}
