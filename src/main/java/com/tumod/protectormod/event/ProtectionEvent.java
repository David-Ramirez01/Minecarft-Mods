package com.tumod.protectormod.event;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PLAYER_INSIDE.remove(event.getEntity().getUUID());
    }

    // ✅ INTERACCIONES (Cofres, Puertas, Hornos)
    @SubscribeEvent
    public static void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) return;
        if (event.getEntity().hasPermissions(2)) return;

        BlockPos pos = event.getPos();
        Player player = event.getEntity();

        for (ProtectionCoreBlockEntity core : CORES) {
            if (core.getLevel() == event.getLevel() && core.isInside(pos)) {
                // Verificamos el permiso específico de interacción o cofres
                boolean canInteract = core.hasPermission(player, "interact") || core.hasPermission(player, "chests");
                if (!canInteract) {
                    event.setCanceled(true);
                    player.displayClientMessage(Component.literal("No tienes permiso para interactuar aquí").withStyle(ChatFormatting.RED), true);
                    return;
                }
            }
        }
    }

// ✅ ROMPER BLOQUES Y PROTECCIÓN DEL NÚCLEO
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;

        Player player = event.getPlayer();
        BlockPos pos = event.getPos();
        Level level = (Level) event.getLevel();

        // A. Protección especial para el objeto Núcleo (BlockEntity)
        var blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof ProtectionCoreBlockEntity coreBE) {
            // Si es OP (permiso 2+), puede romperlo siempre
            if (player.hasPermissions(2)) return;

            // Si NO es OP y NO es el dueño, cancelamos
            if (coreBE.getOwnerUUID() != null && !coreBE.getOwnerUUID().equals(player.getUUID())) {
                event.setCanceled(true);
                player.displayClientMessage(Component.literal("§cSolo el dueño o un administrador pueden retirar este núcleo."), true);
                return;
            }
        }

        // B. Protección de bloques normales dentro del área
        // Si el jugador es OP, saltamos la validación del área
        if (player.hasPermissions(2)) return;

        for (ProtectionCoreBlockEntity core : CORES) {
            if (core.getLevel() == level && core.isInside(pos)) {
                // Si no tiene permiso de construcción, no puede romper bloques
                if (!core.hasPermission(player, "build")) {
                    event.setCanceled(true);
                    player.displayClientMessage(Component.literal("No tienes permiso para romper bloques aquí").withStyle(ChatFormatting.RED), true);
                    return;
                }
            }
        }
    }

    // ✅ DETECTAR ENTRADA/SALIDA (Para alertas visuales)
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || player.level().getGameTime() % 10 != 0) return;

        BlockPos pos = player.blockPosition();
        boolean isInsideAny = false;

        for (ProtectionCoreBlockEntity core : CORES) {
            // Un jugador es "extraño" si está dentro y no tiene permiso de construcción
            if (core.getLevel() == player.level() && core.isInside(pos) && !core.hasPermission(player, "build")) {
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
