package com.tumod.protectormod.block;

import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.registry.ModBlockEntities;
import com.tumod.protectormod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.phys.shapes.Shapes;
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
                .setValue(LEVEL, 1)); // Añade el nivel inicial aquí
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        // Es vital añadir FACING, HALF y LEVEL
        builder.add(FACING, HALF, LEVEL);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        // Indica que el bloque debe usar el modelo JSON de resources
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            // Bloque superior: La caja va de 0 a 16 (que visualmente son los píxeles 16-32)
            return Block.box(2.0D, 0.0D, 2.0D, 14.0D, 16.0D, 14.0D);
        }
        // Bloque inferior: La base de la estatua
        return Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getShape(state, level, pos, context);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        // IMPORTANTE: Solo la parte INFERIOR tiene la BlockEntity con los datos
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return null;
        }

        if (state.is(ModBlocks.ADMIN_PROTECTOR.get())) {
            return new ProtectionCoreBlockEntity(ModBlockEntities.ADMIN_PROTECTOR_BE.get(), pos, state);
        }
        return new ProtectionCoreBlockEntity(ModBlockEntities.PROTECTION_CORE_BE.get(), pos, state);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();

        if (pos.getY() < level.getMaxBuildHeight() - 1 && level.getBlockState(pos.above()).canBeReplaced(context)) {
            return this.defaultBlockState()
                    .setValue(FACING, context.getHorizontalDirection().getOpposite())
                    .setValue(HALF, DoubleBlockHalf.LOWER)
                    .setValue(LEVEL, 1); // Aseguramos que empiece en nivel 1
        }
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide || !(placer instanceof Player player)) return;

        // 1. VALIDACIÓN DE ALTURA (Mantenemos tu lógica)
        if (!level.getBlockState(pos.above()).isAir() && !level.getBlockState(pos.above()).canBeReplaced()) {
            cancelarColocacion(level, pos, player, stack, "§c[!] No hay espacio suficiente.");
            return;
        }

        boolean isAdminCore = this.asBlock().equals(com.tumod.protectormod.registry.ModBlocks.ADMIN_PROTECTOR.get());

        // 2. COMPROBAR SUPERPOSICIÓN TOTAL
        // Solo validamos si NO es admin core y NO es un OP con permisos
        if (!isAdminCore && !player.hasPermissions(2)) {
            int miRadioInicial = 16; // Radio que tendrá este bloque nivel 1

            for (var existingCore : com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity.getLoadedCores()) {
                if (existingCore.getBlockPos().equals(pos)) continue;

                // Calculamos distancia real entre los dos bloques
                double distancia = Math.sqrt(pos.distSqr(existingCore.getBlockPos()));
                int radioEnemigo = existingCore.getRange();

                // REGLA: Distancia debe ser > (Mi Radio + Radio Enemigo)
                if (distancia < (miRadioInicial + radioEnemigo)) {
                    cancelarColocacion(level, pos, player, stack, "§c[!] El área de este núcleo choca con otra zona protegida.");
                    return;
                }
            }
        }

        // 3. COMPROBAR LÍMITE (Tu lógica actual...)
        ServerLevel sLevel = (ServerLevel) level;
        com.tumod.protectormod.util.ClanSavedData data = com.tumod.protectormod.util.ClanSavedData.get(sLevel);
        if (!isAdminCore) {
            int currentCores = data.getPlayerCoreCount(player.getUUID());
            if (!player.hasPermissions(2) && currentCores >= data.serverMaxCores) {
                cancelarColocacion(level, pos, player, stack, "§c[!] Límite de núcleos alcanzado.");
                return;
            }
        }

        // 4. CONFIGURACIÓN FINAL
        if (level.getBlockEntity(pos) instanceof com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity core) {
            core.setOwner(player.getUUID(), player.getName().getString());
            core.markDirtyAndUpdate();
        }

        Direction currentFacing = state.getValue(FACING);
        BlockState upperState = state.setValue(HALF, DoubleBlockHalf.UPPER)
                .setValue(FACING, currentFacing)
                .setValue(LEVEL, 1); // La parte de arriba también necesita un nivel definido
        level.setBlock(pos.above(), upperState, 3);
        super.setPlacedBy(level, pos, state, placer, stack);
    }

    @Override
    public boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        super.triggerEvent(state, level, pos, id, param);
        BlockEntity blockentity = level.getBlockEntity(pos);
        return blockentity == null ? false : blockentity.triggerEvent(id, param);
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
            ItemStack itemADevolver = stack.copy();
            itemADevolver.setCount(1);
            if (!player.getInventory().add(itemADevolver)) {
                player.drop(itemADevolver, false);
            }
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return true; // Permite que la luz pase a través del modelo
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F; // Evita sombras raras dentro de la estatua
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        // 1. REDIRECCIÓN: Si el clic es en la parte superior, operamos sobre la posición inferior
        BlockPos targetPos = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;

        // 2. Feedback visual en el cliente (animación de mano)
        if (level.isClientSide) return InteractionResult.SUCCESS;

        // 3. Obtener la BlockEntity desde la posición real (targetPos)
        BlockEntity be = level.getBlockEntity(targetPos);
        if (!(be instanceof ProtectionCoreBlockEntity core)) return InteractionResult.PASS;

        // 4. Lógica para el ADMIN PROTECTOR (Estatua de Oro)
        // Importante: Usar targetPos en openMenu para que el contenedor sepa de dónde sacar los datos
        if (state.is(ModBlocks.ADMIN_PROTECTOR.get())) {
            if (player.hasPermissions(2)) {
                core.markDirtyAndUpdate();
                player.openMenu(core, targetPos);
                return InteractionResult.SUCCESS;
            }
            player.displayClientMessage(Component.literal("§c[!] Solo los administradores del servidor pueden configurar esta estatua."), true);
            return InteractionResult.CONSUME;
        }

        // 5. Lógica para el NÚCLEO NORMAL
        boolean isOwner = player.getUUID().equals(core.getOwnerUUID());
        boolean isOP = player.hasPermissions(2);
        boolean isTrusted = core.isTrusted(player);

        if (isOwner || isOP || isTrusted) {
            core.markDirtyAndUpdate();
            player.openMenu(core, targetPos);
            return InteractionResult.SUCCESS;
        }

        // 6. Denegación
        player.displayClientMessage(Component.literal("§c[!] No tienes permisos de acceso a este núcleo de protección."), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        // REGLA DE ORO: Si el bloque base es el mismo, NO hacer nada (esto permite el cambio de textura/nivel)
        if (state.is(newState.getBlock())) {
            super.onRemove(state, level, pos, newState, isMoving);
            return;
        }

        if (!level.isClientSide) {
            // 1. Lógica de limpieza de datos (Solo en el servidor)
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity core) {
                var data = com.tumod.protectormod.util.ClanSavedData.get((net.minecraft.server.level.ServerLevel) level);
                if (core.getOwnerUUID() != null) {
                    data.deleteClan(core.getOwnerUUID());

                    // Notificar al dueño
                    net.minecraft.server.level.ServerPlayer player = level.getServer().getPlayerList().getPlayer(core.getOwnerUUID());
                    if (player != null) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal("§eNúcleo destruido. Cupo liberado."), true);
                    }
                }
            }

            // 2. Manejar la eliminación de la otra mitad del bloque doble
            DoubleBlockHalf half = state.getValue(HALF);
            BlockPos otherPos = (half == DoubleBlockHalf.LOWER) ? pos.above() : pos.below();

            // Evitamos bucle infinito verificando que el bloque de la otra mitad sea el mismo
            if (level.getBlockState(otherPos).is(this)) {
                level.removeBlock(otherPos, false);
            }
        }

        // 3. Llamar al super para eliminar la BlockEntity física solo cuando el bloque cambia
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            // 1. Localizar la base (LOWER) donde reside la BlockEntity con los datos
            BlockPos basePos = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
            BlockEntity be = level.getBlockEntity(basePos);

            if (be instanceof ProtectionCoreBlockEntity core) {
                // 2. Comprobar si es el dueño o un Administrador
                boolean isOwner = player.getUUID().equals(core.getOwnerUUID());
                boolean isOP = player.hasPermissions(2);

                if (!isOwner && !isOP) {
                    // Bloquear destrucción: notificamos y forzamos actualización visual
                    player.displayClientMessage(Component.literal("§c[!] No puedes destruir un núcleo que no te pertenece."), true);

                    // Sincronizamos con el cliente para que el bloque "reaparezca" si intentó romperlo
                    level.sendBlockUpdated(pos, state, state, 3);
                    return state;
                }
            }

            // 3. Si el permiso es válido, procedemos a destruir la otra mitad (Lógica original)
            DoubleBlockHalf half = state.getValue(HALF);
            BlockPos otherPos = half == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            BlockState otherState = level.getBlockState(otherPos);

            if (otherState.is(this) && otherState.getValue(HALF) != half) {
                // Usamos 35 para evitar actualizaciones de red innecesarias y soltar partículas
                level.setBlock(otherPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, otherPos, Block.getId(otherState));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}

