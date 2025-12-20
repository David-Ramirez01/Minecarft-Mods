package com.tumod.protectormod.event;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.block.ProtectionCoreBlock;
import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity.CORES;

@EventBusSubscriber(modid = ProtectorMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class ProtectionEvent {

    private static final Map<UUID, Boolean> PLAYER_INSIDE = new HashMap<>();

    // ✅ NUEVO: Limpiar mapa cuando el jugador se va
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PLAYER_INSIDE.remove(event.getEntity().getUUID());
    }

    // ✅ NUEVO: Protección contra interacciones (Cofres, Puertas, Hornos)
    @SubscribeEvent
    public static void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) return;

        BlockPos pos = event.getPos();
        Player player = event.getEntity();

        for (ProtectionCoreBlockEntity core : CORES) {
            if (core.getLevel() == event.getLevel() && core.isInside(pos)) {
                if (!core.isTrusted(player)) {
                    event.setCanceled(true);
                    player.displayClientMessage(Component.literal("No puedes interactuar aquí").withStyle(ChatFormatting.RED), true);
                    return;
                }
            }
        }
    }

    // ================== ROMPER BLOQUES ==================
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;
        Level level = (Level) event.getLevel();
        Player player = event.getPlayer();
        BlockPos pos = event.getPos();

        // 1. Si es un núcleo, verificar dueño
        if (event.getState().getBlock() instanceof ProtectionCoreBlock) {
            if (level.getBlockEntity(pos) instanceof ProtectionCoreBlockEntity core) {
                if (!player.isCreative() && !player.getUUID().equals(core.getOwnerUUID())) {
                    event.setCanceled(true);
                    player.displayClientMessage(Component.literal("Solo el dueño puede retirar este núcleo").withStyle(ChatFormatting.RED), true);
                    return;
                }
            }
        }

        // 2. Protección general
        for (ProtectionCoreBlockEntity core : CORES) {
            if (core.getLevel() == level && core.isInside(pos)) {
                if (!core.isTrusted(player)) {
                    event.setCanceled(true);
                    player.displayClientMessage(Component.literal("Este terreno está protegido").withStyle(ChatFormatting.RED), true);
                    return;
                }
            }
        }
    }

    // ================== COLOCAR BLOQUES ==================
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getLevel().isClientSide()) return;
        Level level = (Level) event.getLevel();
        BlockPos pos = event.getPos();

        // Limite de 3 núcleos
        if (event.getPlacedBlock().getBlock() instanceof ProtectionCoreBlock) {
            long count = CORES.stream()
                    .filter(c -> c.getLevel() == level && player.getUUID().equals(c.getOwnerUUID()))
                    .count();

            if (count >= 3) {
                event.setCanceled(true);
                player.displayClientMessage(Component.literal("Has alcanzado el límite de 3 núcleos").withStyle(ChatFormatting.YELLOW), true);
                return;
            }
        }

        for (ProtectionCoreBlockEntity core : CORES) {
            if (core.getLevel() == level && core.isInside(pos)) {
                if (!core.isTrusted(player)) {
                    event.setCanceled(true);
                    player.displayClientMessage(Component.literal("No tienes permiso para construir aquí").withStyle(ChatFormatting.RED), true);
                    return;
                }
            }
        }
    }

    // ================== DETECTAR ENTRADA/SALIDA ==================
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        // Optimización: solo procesar cada medio segundo
        if (player.level().isClientSide || player.level().getGameTime() % 10 != 0) return;

        BlockPos pos = player.blockPosition();
        boolean isInsideAny = false;

        for (ProtectionCoreBlockEntity core : CORES) {
            if (core.getLevel() == player.level() && core.isInside(pos) && !core.isTrusted(player)) {
                isInsideAny = true;
                break;
            }
        }

        UUID uuid = player.getUUID();
        boolean wasInside = PLAYER_INSIDE.getOrDefault(uuid, false);

        if (!wasInside && isInsideAny) {
            player.displayClientMessage(Component.literal("¡Entraste a zona protegida!").withStyle(ChatFormatting.RED), true);
        } else if (wasInside && !isInsideAny) {
            player.displayClientMessage(Component.literal("Saliste de zona protegida").withStyle(ChatFormatting.GREEN), true);
        }

        PLAYER_INSIDE.put(uuid, isInsideAny);
    }
}
