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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;


public class ProtectionCoreBlock extends Block implements EntityBlock {
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;


    public ProtectionCoreBlock(Properties properties) {
        super(properties);
        // Definimos el estado por defecto como la parte inferior
        this.registerDefaultState(this.stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF); // Registramos la propiedad HALF
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

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide || !(placer instanceof Player player)) return;

        // 1. VALIDACIÓN DE ALTURA
        if (!level.getBlockState(pos.above()).isAir() && !level.getBlockState(pos.above()).canBeReplaced()) {
            cancelarColocacion(level, pos, player, stack, "§c[!] No hay espacio suficiente (necesitas 2 bloques de alto).");
            return;
        }

        ServerLevel sLevel = (ServerLevel) level;
        com.tumod.protectormod.util.ClanSavedData data = com.tumod.protectormod.util.ClanSavedData.get(sLevel);

        // 2. COMPROBAR SUPERPOSICIÓN
        int radioNuevo = 16;
        for (var existingCore : com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity.getLoadedCores()) {
            if (existingCore.getBlockPos().equals(pos)) continue;
            if (existingCore.areaOverlaps(pos, radioNuevo)) {
                if (!existingCore.getOwnerUUID().equals(player.getUUID()) && !player.hasPermissions(2)) {
                    cancelarColocacion(level, pos, player, stack, "§c[!] Esta zona ya está protegida por otro núcleo.");
                    return;
                }
            }
        }

        // 3. COMPROBAR LÍMITE (Solo para cores normales)
        if (!this.asBlock().equals(com.tumod.protectormod.registry.ModBlocks.ADMIN_PROTECTOR.get())) {
            int currentCores = data.getPlayerCoreCount(player.getUUID());
            if (!player.hasPermissions(2) && currentCores >= data.serverMaxCores) {
                cancelarColocacion(level, pos, player, stack, "§c[!] Límite alcanzado: §e" + data.serverMaxCores + " núcleos.");
                return;
            }
        }

        // 4. CONFIGURACIÓN DEL BLOQUE (ÉXITO)
        if (level.getBlockEntity(pos) instanceof com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity core) {
            // --- CAMBIO CLAVE AQUÍ ---
            // Pasamos UUID y el Nombre Real del jugador para evitar el "Lider "
            core.setOwner(player.getUUID(), player.getName().getString());

            String clanName = "Base_" + player.getName().getString() + "_" + pos.getX() + "_" + pos.getZ();
            data.tryCreateClan(clanName, player.getUUID(), player.getName().getString(), pos);

            core.markDirtyAndUpdate();
        }

        // Colocamos la parte superior heredando las propiedades del estado actual
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);

        super.setPlacedBy(level, pos, state, placer, stack);
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
        if (!state.is(newState.getBlock())) {
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative()) {
            // Redirigir a la posición de la BlockEntity si se rompe la parte superior
            BlockPos targetPos = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
            BlockEntity be = level.getBlockEntity(targetPos);

            if (be instanceof ProtectionCoreBlockEntity core) {
                if (player.getUUID().equals(core.getOwnerUUID()) || player.hasPermissions(2)) {
                    // Solo soltar el item si es la parte inferior la que se rompe
                    if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
                        popResource(level, pos, new ItemStack(this));
                    }
                } else {
                    player.displayClientMessage(Component.literal("§c¡No puedes destruir un núcleo que no te pertenece!"), true);
                    return state; // Cancela visualmente la destrucción
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}


