package com.tumod.protectormod.network;

import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.menu.ProtectionCoreMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.world.entity.player.Player;

import java.util.List;

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
    public static void handleCreateClan(CreateClanPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player owner = context.player();
            Level level = owner.level();

            if (level.getBlockEntity(payload.pos()) instanceof ProtectionCoreBlockEntity core) {
                // 1. El Owner pone el mensaje en el chat (como pediste)
                Component chatMsg = Component.literal("§6[Clan] " + owner.getName().getString() + " ha fundado el clan: §b" + payload.clanName());
                level.getServer().getPlayerList().broadcastSystemMessage(chatMsg, false);

                // 2. Procesar a los miembros
                List<String> members = core.getTrustedNames();
                for (String memberName : members) {
                    // Dar todos los permisos
                    core.updatePermission(memberName, "build", true);
                    core.updatePermission(memberName, "interact", true);
                    core.updatePermission(memberName, "chests", true);

                    // 3. Enviar mensaje encima de la Hotbar a los miembros
                    ServerPlayer sMember = level.getServer().getPlayerList().getPlayerByName(memberName);
                    if (sMember != null) {
                        sMember.displayClientMessage(
                                Component.literal("§a¡Felicidades por unirte al clan §6" + payload.clanName() + "§a!"),
                                true // 'true' hace que aparezca sobre la hotbar
                        );
                    }
                }
                core.setChanged();
                level.sendBlockUpdated(payload.pos(), core.getBlockState(), core.getBlockState(), 3);
            }
        });
    }

    public static void handleAdminUpdate(UpdateAdminCorePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            // Verificamos si el jugador es OP antes de aplicar cambios
            if (player.hasPermissions(2)) {
                Level level = player.level();
                if (level.getBlockEntity(payload.pos()) instanceof ProtectionCoreBlockEntity core) {
                    core.setAdminRadius(payload.radius());
                    core.setPvpEnabled(payload.pvp());
                    core.setExplosionsDisabled(payload.explosions());

                    player.displayClientMessage(Component.literal("§aConfiguración de Admin Core actualizada."), true);
                }
            }
        });
    }

}
