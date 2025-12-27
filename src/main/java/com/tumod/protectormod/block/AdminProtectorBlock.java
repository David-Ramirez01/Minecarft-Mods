package com.tumod.protectormod.block;

import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock; // <--- IMPORTANTE
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

// AÑADIR "implements EntityBlock"
public class AdminProtectorBlock extends Block implements EntityBlock {

    public AdminProtectorBlock(Properties properties) {
        super(properties);
    }

    // ESTE MÉTODO ES OBLIGATORIO PARA QUE EXISTA LA BLOCK ENTITY
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // Ahora esto NO dará error porque el constructor con 3 argumentos existe
        return new ProtectionCoreBlockEntity(ModBlockEntities.ADMIN_PROTECTOR_BE.get(), pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ProtectionCoreBlockEntity core) {
                // Verificación de seguridad
                if (player.hasPermissions(2) || player.getUUID().equals(core.getOwnerUUID())) {
                    player.openMenu(core, pos);
                }
            } else {
                // Si llegas aquí, es que la BE no se creó correctamente
                player.displayClientMessage(Component.literal("§cError: No se encontró la entidad del bloque."), true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && placer instanceof Player player) {
            if (!player.hasPermissions(2)) {
                level.destroyBlock(pos, false);
                player.displayClientMessage(Component.literal("§c¡Solo los administradores pueden colocar este bloque!"), true);
            } else {
                // Opcional: Asignar al admin como dueño automáticamente
                if (level.getBlockEntity(pos) instanceof ProtectionCoreBlockEntity core) {
                    core.setOwner(player.getUUID());
                }
            }
        }
    }
}
