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
            // Radio de administrador (128 bloques totales de cobertura)
            return 64;
        }

        @Override
        public boolean isTrusted(Player player) {
            // Solo OPs pueden abrir el menú de este bloque
            return player.hasPermissions(2);
        }

        // Vuelve a incluir esto para asegurar que el Admin Core entre en la lista de escaneo
        @Override
        public void onLoad() {
            super.onLoad();
            if (this.level != null && !this.level.isClientSide) {
                CORES.add(this);
            }
        }
}
