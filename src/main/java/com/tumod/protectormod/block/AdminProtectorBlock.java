package com.tumod.protectormod.block;

import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class AdminProtectorBlock extends Block implements EntityBlock {
    // Definimos la propiedad HALF (igual que en las puertas o el núcleo normal)
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public AdminProtectorBlock(Properties properties) {
        super(properties);
        // Estado por defecto: parte inferior
        this.registerDefaultState(this.stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            // Hitbox de la cabeza (bloque superior)
            return Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);
        }
        // Hitbox de la base y piernas (bloque inferior)
        return Shapes.or(
                Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D),
                Block.box(2.0D, 4.0D, 2.0D, 14.0D, 16.0D, 14.0D)
        );
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // Solo creamos la BlockEntity en la parte inferior para evitar duplicidad de datos
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return new ProtectionCoreBlockEntity(ModBlockEntities.ADMIN_PROTECTOR_BE.get(), pos, state);
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && placer instanceof Player player) {
            if (!player.hasPermissions(2)) {
                level.destroyBlock(pos, false);
                player.displayClientMessage(Component.literal("§c¡Solo los administradores pueden colocar este bloque!"), true);
                return;
            }

            // COLOCAR LA PARTE SUPERIOR (Crucial para que se vea la estatua completa)
            level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);

            if (level.getBlockEntity(pos) instanceof ProtectionCoreBlockEntity core) {
                core.setOwner(player.getUUID());
                core.markDirtyAndUpdate();
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            // Redirección: Si tocan la cabeza (UPPER), buscamos la BE en los pies (LOWER)
            BlockPos targetPos = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
            BlockEntity be = level.getBlockEntity(targetPos);

            if (be instanceof ProtectionCoreBlockEntity core) {
                if (player.hasPermissions(2) || player.getUUID().equals(core.getOwnerUUID())) {
                    player.openMenu(core, targetPos);
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    // Al romper uno, se rompe el otro
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            DoubleBlockHalf half = state.getValue(HALF);
            BlockPos otherPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState otherState = level.getBlockState(otherPos);

            // Si el otro bloque es parte de esta misma estatua, lo eliminamos
            if (otherState.is(this) && otherState.getValue(HALF) != half) {
                level.setBlock(otherPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, otherPos, Block.getId(otherState));
            }
        }

        // IMPORTANTE: Llamar al super y retornar el resultado
        return super.playerWillDestroy(level, pos, state, player);
    }
}
