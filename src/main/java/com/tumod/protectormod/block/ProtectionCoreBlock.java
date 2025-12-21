package com.tumod.protectormod.block;

import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.registry.ModBlockEntities;
import com.tumod.protectormod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class ProtectionCoreBlock extends Block implements EntityBlock {

    public ProtectionCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // Validación para instanciar el BE correcto según el bloque
        if (state.is(ModBlocks.ADMIN_PROTECTOR.get())) {
            return new ProtectionCoreBlockEntity(ModBlockEntities.ADMIN_PROTECTOR_BE.get(), pos, state);
        }
        return new ProtectionCoreBlockEntity(ModBlockEntities.PROTECTION_CORE_BE.get(), pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && placer instanceof Player player) {

            // 1. RESTRICCIÓN: No colocar núcleos sobre otras áreas (Excluyendo Admins)
            if (!player.hasPermissions(2)) {
                for (ProtectionCoreBlockEntity otherCore : ProtectionCoreBlockEntity.CORES) {
                    if (otherCore.getBlockPos().equals(pos)) continue;

                    // Si la posición de este nuevo bloque ya está dentro de otro radio
                    if (otherCore.isInside(pos)) {
                        player.displayClientMessage(Component.literal("§c[!] No puedes colocar un núcleo dentro de otra área protegida."), true);
                        level.destroyBlock(pos, true); // Rompe el bloque y devuelve el ítem
                        return;
                    }

                    // Si el área inicial (radio 8) chocaría con un área existente
                    if (otherCore.areaOverlaps(pos, 8)) {
                        player.displayClientMessage(Component.literal("§c[!] El área de este núcleo se superpone con otra."), true);
                        level.destroyBlock(pos, true);
                        return;
                    }
                }
            }

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ProtectionCoreBlockEntity core) {
                core.setOwner(player.getUUID());
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ProtectionCoreBlockEntity core) {
            // El dueño o un admin siempre pueden entrar
            if (!core.isTrusted(player)) {
                player.displayClientMessage(Component.literal("§cNo tienes permiso para usar este core"), true);
                return InteractionResult.CONSUME;
            }

            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(core, pos);
            }
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // Lógica de destrucción (manteniendo lo que ya tenías)
        if (!level.isClientSide && !player.isCreative()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ProtectionCoreBlockEntity core) {
                // Solo permitimos el drop si el jugador es dueño o admin (seguridad extra)
                if (player.getUUID().equals(core.getOwnerUUID()) || player.hasPermissions(2)) {
                    ItemStack drop = new ItemStack(this);
                    core.saveToItem(drop, level.registryAccess());
                    popResource(level, pos, drop);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}