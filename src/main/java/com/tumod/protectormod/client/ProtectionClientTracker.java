package com.tumod.protectormod.client;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.util.ProtectionDataManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.*;

@EventBusSubscriber(modid = ProtectorMod.MOD_ID, value = Dist.CLIENT)
public class ProtectionClientTracker {

    // Guardamos BlockPos para evitar referencias directas a BlockEntities que pueden ser descargadas
    private static final Set<BlockPos> currentlyInside = new HashSet<>();
    private static final List<ProtectionAreaEffect> activeEffects = new ArrayList<>();

    @SubscribeEvent
    public static void onClientTick(PlayerTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getEntity() != mc.player || mc.level == null) return;

        Player player = mc.player;
        BlockPos playerPos = player.blockPosition();
        Level level = mc.level;

        // Solo procesar cada 5 ticks para ahorrar CPU
        if (level.getGameTime() % 5 != 0) return;

        Set<BlockPos> coresFound = new HashSet<>();

        // 1. Consultamos al DataManager (que tiene la lista sincronizada de núcleos)
        ProtectionDataManager.get(level).getAllCores().forEach((pos, entry) -> {
            double distSq = pos.distSqr(playerPos);
            if (distSq <= (double) (entry.radius() * entry.radius())) {
                // Verificamos si es un núcleo real en el mundo para comprobar permisos
                if (level.getBlockEntity(pos) instanceof ProtectionCoreBlockEntity core) {
                    if (!core.isTrusted(player)) {
                        coresFound.add(pos);
                    }
                }
            }
        });

        // 4. Actualizar efectos visuales
        activeEffects.removeIf(effect -> !effect.tick((net.minecraft.client.multiplayer.ClientLevel) level));
    }

    public static void showArea(ProtectionCoreBlockEntity core) {
        if (core == null) return;
        activeEffects.add(new ProtectionAreaEffect(core.getBlockPos(), core.getRadius(), 100));
    }
}





