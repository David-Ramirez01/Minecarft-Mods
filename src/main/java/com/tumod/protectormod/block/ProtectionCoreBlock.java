package com.tumod.protectormod.block;

import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.registry.ModBlockEntities;
import com.tumod.protectormod.registry.ModBlocks;
import com.tumod.protectormod.util.ProtectionDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class ProtectionCoreBlock extends Block implements EntityBlock {
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 1, 5);

    public ProtectionCoreBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(FACING, Direction.NORTH)
                .setValue(LEVEL, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, LEVEL);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);
        }
        return Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) return null;

        if (state.is(ModBlocks.ADMIN_PROTECTOR.get())) {
            return new com.tumod.protectormod.blockentity.AdminProtectorBlockEntity(pos, state);
        }
        return new ProtectionCoreBlockEntity(ModBlockEntities.PROTECTION_CORE_BE.get(), pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide || !(placer instanceof Player player)) return;
        ServerLevel sLevel = (ServerLevel) level;

        // 1. Validar espacio físico superior
        if (!level.getBlockState(pos.above()).isAir() && !level.getBlockState(pos.above()).canBeReplaced()) {
            cancelarColocacion(level, pos, player, stack, "§c[!] No hay espacio suficiente.");
            return;
        }

        boolean isAdminCore = state.is(ModBlocks.ADMIN_PROTECTOR.get());
        ProtectionDataManager manager = ProtectionDataManager.get(sLevel);

        // 2. Validar límite de núcleos
        if (!isAdminCore && !player.hasPermissions(2)) {
            long playerCoreCount = manager.getAllCores().values().stream()
                    .filter(entry -> entry.owner().equals(player.getUUID()))
                    .count();

            if (playerCoreCount >= manager.getGlobalLimit()) {
                cancelarColocacion(level, pos, player, stack, "§c[!] Límite alcanzado: " + manager.getGlobalLimit());
                return;
            }
        }

        // 3. Validar solapamiento
        if (!isAdminCore && !player.hasPermissions(2)) {
            int miRadioInicial = 10;
            boolean overlaps = manager.getAllCores().entrySet().stream()
                    .anyMatch(entry -> !entry.getKey().equals(pos) &&
                            Math.sqrt(pos.distSqr(entry.getKey())) < (miRadioInicial + entry.getValue().radius()));

            if (overlaps) {
                cancelarColocacion(level, pos, player, stack, "§c[!] El área choca con otra zona protegida.");
                return;
            }
        }

        // --- A PARTIR DE AQUÍ TODO ESTÁ VALIDADO ---

        // 4. Colocar la mitad superior PRIMERO
        BlockState upperState = state.setValue(HALF, DoubleBlockHalf.UPPER).setValue(FACING, state.getValue(FACING));
        level.setBlock(pos.above(), upperState, 3);

        // 5. Configurar BlockEntity y REGISTRAR (Al final)
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ProtectionCoreBlockEntity core) {
            core.setOwner(player.getUUID(), player.getName().getString());

            // REGISTRO DEFINITIVO EN EL MANAGER
            manager.addCore(pos, player.getUUID(), core.getRadius());
            manager.syncToAll(sLevel);
        }

        super.setPlacedBy(level, pos, state, placer, stack);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        // Si el bloque sigue siendo el mismo (ej: solo cambió el nivel o la rotación), NO hacer nada
        if (state.is(newState.getBlock())) {
            super.onRemove(state, level, pos, newState, isMoving);
            return;
        }

        if (!level.isClientSide && level instanceof ServerLevel sLevel) {
            BlockPos basePos = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;

            // Solo procesamos la lógica de datos si estamos eliminando la BASE
            if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
                BlockEntity be = level.getBlockEntity(basePos);
                if (be instanceof ProtectionCoreBlockEntity core) {
                    ProtectionDataManager.get(sLevel).removeCore(basePos);
                    ProtectionDataManager.get(sLevel).syncToAll(sLevel);

                    var clanData = com.tumod.protectormod.util.ClanSavedData.get(sLevel);
                    if (core.getOwnerUUID() != null) {
                        clanData.deleteClan(core.getOwnerUUID());
                    }
                }
            }

            // Eliminar la otra mitad sin disparar drops infinitos
            DoubleBlockHalf half = state.getValue(HALF);
            BlockPos otherPos = (half == DoubleBlockHalf.LOWER) ? pos.above() : pos.below();
            BlockState otherState = level.getBlockState(otherPos);
            if (otherState.is(this) && otherState.getValue(HALF) != half) {
                // Usamos flags específicos para evitar recursión
                level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(null, 2001, otherPos, Block.getId(otherState));
            }
        }

        // Importante: Eliminar el BlockEntity solo si no es un cambio de estado interno
        if (state.hasBlockEntity() && (!state.is(newState.getBlock()) || !newState.hasBlockEntity())) {
            level.removeBlockEntity(pos);
        }

        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockPos targetPos = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(targetPos);
        if (!(be instanceof ProtectionCoreBlockEntity core)) return InteractionResult.PASS;

        if (state.is(ModBlocks.ADMIN_PROTECTOR.get())) {
            if (player.hasPermissions(2)) {
                player.openMenu(core, targetPos);
                return InteractionResult.SUCCESS;
            }
            player.displayClientMessage(Component.literal("§c[!] Solo administradores pueden configurar esto."), true);
            return InteractionResult.CONSUME;
        }

        if (player.getUUID().equals(core.getOwnerUUID()) || player.hasPermissions(2) || core.isTrusted(player)) {
            player.openMenu(core, targetPos);
            return InteractionResult.SUCCESS;
        }

        player.displayClientMessage(Component.literal("§c[!] No tienes permisos de acceso."), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        // Esto hace que el frente del bloque (FACING) sea la dirección opuesta a la que mira el jugador
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(LEVEL, 1);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction.getAxis() == Direction.Axis.Y && half == DoubleBlockHalf.LOWER == (direction == Direction.UP)) {
            return neighborState.is(this) && neighborState.getValue(HALF) != half ? state : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    private void cancelarColocacion(Level level, BlockPos pos, Player player, ItemStack stack, String mensaje) {
        player.displayClientMessage(Component.literal(mensaje), true);
        if (!player.getAbilities().instabuild) {
            ItemStack item = stack.copy();
            item.setCount(1);
            if (!player.getInventory().add(item)) player.drop(item, false);
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            if (player.isCreative()) {
                preventCreativeDropFromBottomPart(level, pos, state, player);
            }
        }
        super.playerWillDestroy(level, pos, state, player);
        return state;
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);

        // FORZAR DROP: Si se rompe en supervivencia y es la parte inferior
        if (!level.isClientSide && !player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            popResource(level, pos, new ItemStack(this.asItem()));
        }
    }

    // Método auxiliar para manejar la destrucción de la parte doble
    protected static void preventCreativeDropFromBottomPart(Level level, BlockPos pos, BlockState state, Player player) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (half == DoubleBlockHalf.UPPER) {
            BlockPos blockpos = pos.below();
            BlockState blockstate = level.getBlockState(blockpos);
            if (blockstate.is(state.getBlock()) && blockstate.getValue(HALF) == DoubleBlockHalf.LOWER) {
                level.setBlock(blockpos, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, blockpos, Block.getId(blockstate));
            }
        }
    }
}

