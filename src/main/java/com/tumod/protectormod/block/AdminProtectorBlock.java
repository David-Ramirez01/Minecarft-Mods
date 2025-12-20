package com.tumod.protectormod.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block; // IMPORT CORRECTO
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

public class AdminProtectorBlock extends Block {

    public AdminProtectorBlock() {
        super(Block.Properties.of()
                .mapColor(MapColor.COLOR_RED)
                .strength(-1.0F, 3600000.0F) // Indestructible como la Bedrock
                .noLootTable());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && placer instanceof Player player) {
            if (!player.hasPermissions(2)) {
                level.destroyBlock(pos, false);
                player.displayClientMessage(Component.literal("§c¡Solo los administradores pueden colocar este bloque!"), true);
            }
        }
    }
}
