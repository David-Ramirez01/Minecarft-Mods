package com.tumod.protectormod.block;

import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.registry.ModBlockEntities;
import com.tumod.protectormod.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class ProtectionCoreBlock extends Block implements EntityBlock {

    public ProtectionCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.is(ModBlocks.ADMIN_PROTECTOR.get())) {
            return new ProtectionCoreBlockEntity(ModBlockEntities.ADMIN_PROTECTOR_BE.get(), pos, state);
        }
        return new ProtectionCoreBlockEntity(ModBlockEntities.PROTECTION_CORE_BE.get(), pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide || !(placer instanceof Player player)) return;

        ServerLevel sLevel = (ServerLevel) level;
        com.tumod.protectormod.util.ClanSavedData data = com.tumod.protectormod.util.ClanSavedData.get(sLevel);

        // --- 1. COMPROBAR SUPERPOSICIÓN (Zonas de otros) ---
        int radioNuevo = 16; // Radio base nivel 1
        for (var existingCore : com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity.getLoadedCores()) {
            if (existingCore.getBlockPos().equals(pos)) continue;

            if (existingCore.areaOverlaps(pos, radioNuevo)) {
                if (!existingCore.getOwnerUUID().equals(player.getUUID()) && !player.hasPermissions(2)) {
                    cancelarColocacion(level, pos, player, stack, "§c[!] Esta zona ya está protegida por otro núcleo.");
                    return;
                }
            }
        }

        // --- 2. COMPROBAR LÍMITE DE CORES ---
        int currentCores = data.getPlayerCoreCount(player.getUUID());
        if (!player.hasPermissions(2) && currentCores >= data.serverMaxCores) {
            cancelarColocacion(level, pos, player, stack, "§c[!] Límite alcanzado: §e" + data.serverMaxCores);
            return;
        }

        // --- 3. COLOCACIÓN EXITOSA ---
        if (level.getBlockEntity(pos) instanceof com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity core) {
            core.setOwner(player.getUUID());
            String clanName = "Base_" + player.getName().getString() + "_" + pos.getX() + "_" + pos.getZ();

            // Registrar en la data SOLO si pasó todas las pruebas
            data.tryCreateClan(clanName, player.getUUID(), player.getName().getString(), pos);
            core.markDirtyAndUpdate();
        }
    }

    // Método auxiliar para devolver el ítem y limpiar
    private void cancelarColocacion(Level level, BlockPos pos, Player player, ItemStack stack, String mensaje) {
        player.displayClientMessage(Component.literal(mensaje), true);

        // 1. Devolver el bloque al inventario del jugador
        if (!player.getAbilities().instabuild) {
            ItemStack itemADevolver = stack.copy();
            itemADevolver.setCount(1);
            if (!player.getInventory().add(itemADevolver)) {
                player.drop(itemADevolver, false);
            }
        }

        // 2. Eliminar el bloque del mundo SIN soltar el ítem (evita duplicación)
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ProtectionCoreBlockEntity core)) return InteractionResult.PASS;

        // CASO A: Admin Protector
        if (state.is(ModBlocks.ADMIN_PROTECTOR.get())) {
            if (player.hasPermissions(2)) {
                player.openMenu(core, pos);
                return InteractionResult.SUCCESS;
            }
            player.displayClientMessage(Component.literal("§cAcceso denegado: Solo administradores."), true);
            return InteractionResult.CONSUME;
        }

        // CASO B: Núcleo Normal
        // Solo el dueño o alguien con permiso de construcción (Trusted) puede abrir el menú
        if (player.getUUID().equals(core.getOwnerUUID()) || player.hasPermissions(2) || core.isTrusted(player)) {
            player.openMenu(core, pos);
            return InteractionResult.SUCCESS;
        }

        player.displayClientMessage(Component.literal("§cNo eres dueño ni invitado de este núcleo."), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        // Importante: Si el bloque cambia (no es solo una actualización de estado), limpiar inventarios si es necesario
        if (!state.is(newState.getBlock())) {
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !player.isCreative()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ProtectionCoreBlockEntity core) {
                // Verificar permisos de destrucción
                if (player.getUUID().equals(core.getOwnerUUID()) || player.hasPermissions(2)) {
                    ItemStack drop = new ItemStack(this);

                    // Nota: Asegúrate de tener el método saveToItem implementado en tu BE
                    // si quieres que el núcleo mantenga el nivel/flags al romperse.
                    // core.saveToItem(drop, level.registryAccess());

                    popResource(level, pos, drop);
                } else {
                    // Si no tiene permiso, podrías cancelar o enviar mensaje
                    player.displayClientMessage(Component.literal("§c¡No puedes destruir un núcleo que no te pertenece!"), true);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }


}