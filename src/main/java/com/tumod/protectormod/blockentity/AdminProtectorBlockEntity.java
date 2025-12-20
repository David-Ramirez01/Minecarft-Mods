package com.tumod.protectormod.blockentity;

import com.tumod.protectormod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

public class AdminProtectorBlockEntity extends ProtectionCoreBlockEntity {

    public AdminProtectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ADMIN_PROTECTOR_BE.get(), pos, state);
    }

    @Override
    public int getRadius() {
        // Radio 64 = Área total de 128x128 (64 hacia cada lado + bloque central)
        return 64;
    }

    @Override
    public boolean isTrusted(Player player) {
        // En el bloque de admin, nadie es "Trusted" excepto los OPs
        return player.hasPermissions(2);
    }
}
