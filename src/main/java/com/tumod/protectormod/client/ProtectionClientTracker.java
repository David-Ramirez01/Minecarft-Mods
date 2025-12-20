package com.tumod.protectormod.client;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.*;

@EventBusSubscriber(modid = ProtectorMod.MOD_ID, value = Dist.CLIENT)
public class ProtectionClientTracker {

    private static final Set<ProtectionCoreBlockEntity> insideCores = new HashSet<>();
    private static final List<ProtectionAreaEffect> activeEffects = new ArrayList<>();

    @SubscribeEvent
    public static void onClientTick(PlayerTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        // Solo ejecutar para el jugador local y en el lado cliente
        if (event.getEntity() != mc.player || mc.level == null) return;

        Player player = mc.player;
        BlockPos pos = player.blockPosition();
        ClientLevel world = mc.level;

        Set<ProtectionCoreBlockEntity> coresHere = new HashSet<>();

        // Usamos la lista estática que definimos en la BlockEntity
        for (ProtectionCoreBlockEntity core : ProtectionCoreBlockEntity.CORES) {
            // Verificamos si estamos en el mismo mundo y dentro del radio
            if (core.getLevel() == world && core.isInside(pos) && !core.isTrusted(player)) {
                coresHere.add(core);
            }
        }

        // Detectar entrada
        for (ProtectionCoreBlockEntity core : coresHere) {
            if (!insideCores.contains(core)) {
                insideCores.add(core);
                String ownerName = getOwnerName(world, core);
                player.displayClientMessage(
                        Component.literal("Entraste a la zona protegida de " + ownerName).withStyle(ChatFormatting.RED),
                        true
                );
                showArea(core);
            }
        }

        // Detectar salida
        insideCores.removeIf(core -> {
            if (!coresHere.contains(core)) {
                String ownerName = getOwnerName(world, core);
                player.displayClientMessage(
                        Component.literal("Saliste de la zona protegida de " + ownerName).withStyle(ChatFormatting.GREEN),
                        true
                );
                return true;
            }
            return false;
        });

        // Actualizar efectos visuales (partículas)
        activeEffects.removeIf(effect -> !effect.tick(world));
    }

    public static void showArea(ProtectionCoreBlockEntity core) {
        if (core == null) return;
        activeEffects.add(new ProtectionAreaEffect(core.getBlockPos(), core.getRadius(), 100));
    }

    private static String getOwnerName(ClientLevel world, ProtectionCoreBlockEntity core) {
        if (core.getOwnerUUID() != null) {
            Player owner = world.getPlayerByUUID(core.getOwnerUUID());
            if (owner != null) {
                return owner.getName().getString();
            }
        }
        return "un extraño";
    }
}





