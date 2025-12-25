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
        // En lugar de 'return 64;', usamos la variable que se guarda en el NBT
        return this.adminRadius;
    }

    @Override
    public boolean isTrusted(Player player) {
        // Los admins siempre están en confianza en un núcleo de admin
        return player.hasPermissions(2);
    }

    @Override
    public boolean isAdmin() {
        return true;
    }
}
