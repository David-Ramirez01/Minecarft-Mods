package com.tumod.protectormod.block;

import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class AdminProtectorBlock extends Block implements EntityBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    // 1. AÑADIMOS LA PROPIEDAD FACING
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public AdminProtectorBlock(Properties properties) {
        super(properties);
        // 2. REGISTRAMOS EL ESTADO INICIAL CON ROTACIÓN
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(FACING, Direction.NORTH));
    }

    // 3. REGISTRAMOS LAS PROPIEDADES EN EL BLOQUE
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF, FACING);
    }

    // 4. DETERMINAMOS LA ROTACIÓN AL COLOCARLO
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();

        // Verificamos si hay espacio arriba y si el jugador es OP
        if (pos.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(pos.above()).canBeReplaced(context)) {
            return this.defaultBlockState()
                    .setValue(FACING, context.getHorizontalDirection().getOpposite()) // Mira al admin
                    .setValue(HALF, DoubleBlockHalf.LOWER);
        }
        return null;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);
        }
        return Shapes.or(
                Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D),
                Block.box(2.0D, 4.0D, 2.0D, 14.0D, 16.0D, 14.0D)
        );
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
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

            // 5. OBTENEMOS EL FACING CALCULADO Y LO APLICAMOS A LA PARTE SUPERIOR
            Direction currentFacing = state.getValue(FACING);
            level.setBlock(pos.above(), state
                    .setValue(HALF, DoubleBlockHalf.UPPER)
                    .setValue(FACING, currentFacing), 3);

            if (level.getBlockEntity(pos) instanceof ProtectionCoreBlockEntity core) {
                core.setOwner(player.getUUID(), player.getName().getString());
                core.markDirtyAndUpdate();
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            BlockPos targetPos = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
            BlockEntity be = level.getBlockEntity(targetPos);

            if (be instanceof ProtectionCoreBlockEntity core) {
                // Solo OPs pueden configurar el Admin Protector
                if (player.hasPermissions(2)) {
                    player.openMenu(core, targetPos);
                } else {
                    player.displayClientMessage(Component.literal("§c[!] No tienes permisos de administrador."), true);
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            // 1. Localizar la base (LOWER) donde están los datos del dueño
            BlockPos basePos = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
            BlockEntity be = level.getBlockEntity(basePos);

            if (be instanceof com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity core) {
                // 2. Comprobar: ¿Es el dueño? O ¿Es OP (Nivel 2)?
                boolean isOwner = player.getUUID().equals(core.getOwnerUUID());
                boolean isOP = player.hasPermissions(2);

                if (!isOwner && !isOP) {
                    // Si no es ninguno, cancelamos la destrucción
                    player.displayClientMessage(Component.literal("§c[!] No tienes permiso para destruir este núcleo."), true);

                    // Forzamos la actualización del bloque para que no desaparezca en el cliente
                    level.sendBlockUpdated(pos, state, state, 3);
                    return state;
                }
            }

            // 3. Si tiene permiso (Dueño u OP), destruimos la otra mitad
            DoubleBlockHalf half = state.getValue(HALF);
            BlockPos otherPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState otherState = level.getBlockState(otherPos);

            if (otherState.is(this) && otherState.getValue(HALF) != half) {
                level.setBlock(otherPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 35);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
