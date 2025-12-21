package com.tumod.protectormod.network;

import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.menu.ProtectionCoreMenu;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.world.entity.player.Player;

public class ServerPayloadHandler {

    // Se ejecuta cuando el jugador pulsa "Mejorar"
// Dentro de ServerPayloadHandler.java

    public static void handleUpgrade(final UpgradeCorePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();

            if (player.containerMenu instanceof ProtectionCoreMenu menu) {
                ProtectionCoreBlockEntity core = menu.getCore();

                // SEGURIDAD: Solo el dueño del núcleo puede realizar mejoras
                if (core != null && !core.isRemoved() && player.getUUID().equals(core.getOwnerUUID())) {
                    if (core.canUpgrade()) {
                        core.upgrade();
                    }
                }
            }
        });
    }

    // Se ejecuta cuando el jugador pulsa "Ver Área"
    public static void handleShowArea(ShowAreaPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            context.reply(new ShowAreaClientPayload(payload.pos(), payload.radius()));
        });
    }
    public static void handleChangePermission(ChangePermissionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();

            if (player.level().getBlockEntity(payload.pos()) instanceof ProtectionCoreBlockEntity core) {
                // SEGURIDAD: Solo el dueño o Admin
                if (player.getUUID().equals(core.getOwnerUUID()) || player.hasPermissions(2)) {

                    if (payload.permissionType().equals("remove")) {
                        core.removePlayerPermissions(payload.playerName());
                        player.displayClientMessage(Component.literal("§c[!] §fJugador §b" +
                                payload.playerName() + "§f eliminado de la lista."), true);
                    } else {
                        core.updatePermission(payload.playerName(), payload.permissionType(), payload.value());
                        player.displayClientMessage(Component.literal("§7[§6Protector§7] §fPermisos de §b" +
                                payload.playerName() + "§f actualizados."), true);
                    }
                    core.setChanged();
                    player.level().sendBlockUpdated(payload.pos(), core.getBlockState(), core.getBlockState(), 3);
                }
            }
        });
    }
}
