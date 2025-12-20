package com.tumod.protectormod.network;

import com.tumod.protectormod.menu.ProtectionCoreMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.world.entity.player.Player;

public class ServerPayloadHandler {

    // Se ejecuta cuando el jugador pulsa "Mejorar"
    public static void handleUpgrade(UpgradeCorePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            // Verificamos que el jugador tenga el menú del Core abierto
            if (player.containerMenu instanceof ProtectionCoreMenu menu) {
                menu.handleUpgradeRequest();
            }
        });
    }

    // Se ejecuta cuando el jugador pulsa "Ver Área"
    public static void handleShowArea(ShowAreaPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // El servidor responde enviando un paquete de vuelta al cliente
            // para que este dibuje las partículas
            context.reply(new ShowAreaClientPayload(payload.pos(), payload.radius()));
        });
    }
}
