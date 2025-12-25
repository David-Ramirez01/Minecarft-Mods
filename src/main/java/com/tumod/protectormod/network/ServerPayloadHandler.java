package com.tumod.protectormod.network;

import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.menu.ProtectionCoreMenu;
import com.tumod.protectormod.util.InviteManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.world.entity.player.Player;
import com.tumod.protectormod.util.ClanSavedData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import java.util.UUID;

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
            Level level = player.level();

            if (level.getBlockEntity(payload.pos()) instanceof ProtectionCoreBlockEntity core) {
                // SEGURIDAD: Solo el dueño o Admin
                if (player.getUUID().equals(core.getOwnerUUID()) || player.hasPermissions(2)) {

                    // 1. CASO ELIMINAR: Borrado instantáneo
                    if (payload.permissionType().equals("remove")) {
                        core.removePlayerPermissions(payload.playerName());
                        player.displayClientMessage(Component.literal("§c[!] §fJugador §b" + payload.playerName() + "§f eliminado."), true);
                        core.markDirtyAndUpdate();
                        return;
                    }

                    // 2. CASO JUGADOR EXISTENTE: Modificar sin invitar
                    if (core.getPlayersWithAnyPermission().contains(payload.playerName())) {
                        core.updatePermission(payload.playerName(), payload.permissionType(), payload.value());
                        player.displayClientMessage(Component.literal("§7[§6Protector§7] §fPermisos de §b" + payload.playerName() + "§f actualizados."), true);
                        core.markDirtyAndUpdate();
                        return;
                    }

                    // --- VALIDACIÓN DE LÍMITE DE INVITADOS (NUEVO) ---
                    // Si el jugador no existe en la lista y ya hay 8, bloqueamos la invitación.
                    if (core.getPlayersWithAnyPermission().size() >= 8) {
                        player.displayClientMessage(Component.literal("§c[!] El núcleo ya alcanzó el límite máximo de 8 invitados."), true);
                        return;
                    }

                    // 3. CASO JUGADOR NUEVO: Lógica de invitación
                    ServerPlayer target = level.getServer().getPlayerList().getPlayerByName(payload.playerName());

                    if (target == null) {
                        player.displayClientMessage(Component.literal("§c[!] El jugador no está en línea para recibir la invitación."), true);
                        return;
                    }

                    if (target == player) {
                        player.displayClientMessage(Component.literal("§c[!] No puedes invitarte a ti mismo."), true);
                        return;
                    }

                    // Registrar la invitación pendiente
                    InviteManager.addInvite(target.getUUID(), payload.pos(), player.getUUID());

                    player.displayClientMessage(Component.literal("§eInvitación enviada a §b" + payload.playerName() + "§e..."), true);

                    // Mensaje interactivo
                    target.sendSystemMessage(Component.literal("\n§6§l[Protector] §f" + player.getName().getString() + " te ha invitado a su zona.")
                            .append("\n§7Tienes 1 minuto para responder:")
                            .append("\n\n")
                            .append(Component.literal("§a§l[ACEPTAR] ")
                                    .withStyle(s -> s.withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/protector accept"))))
                            .append("   ")
                            .append(Component.literal("§c§l[RECHAZAR]")
                                    .withStyle(s -> s.withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/protector deny"))))
                            .append("\n"));
                }
            }
        });
    }


    public static void handleCreateClan(CreateClanPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            // Casteamos el player a ServerPlayer para tener acceso a métodos de servidor
            if (!(context.player() instanceof net.minecraft.server.level.ServerPlayer owner)) return;

            // Obtenemos el ServerLevel directamente
            ServerLevel serverLevel = owner.serverLevel();

            // Buscamos el BlockEntity usando el level obtenido
            if (serverLevel.getBlockEntity(payload.pos()) instanceof ProtectionCoreBlockEntity core) {

                // Accedemos a ClanSavedData (Asegúrate de que la clase ClanSavedData exista en .util)
                ClanSavedData data = ClanSavedData.get(serverLevel);

                // Intentar crear el clan en la base de datos
                boolean creado = data.tryCreateClan(
                        payload.clanName(),
                        owner.getUUID(),
                        owner.getName().getString(),
                        payload.pos()
                );

                if (!creado) {
                    owner.displayClientMessage(net.minecraft.network.chat.Component.literal("§c[!] Ya eres líder o el nombre ya existe."), true);
                    return;
                }

                // Guardar en el bloque físico
                core.setClanName(payload.clanName());
                core.setOwner(owner.getUUID());

                // Notificación Global: Usamos serverLevel.getServer()
                net.minecraft.network.chat.Component chatMsg = net.minecraft.network.chat.Component.literal("§6[Clan] " + owner.getName().getString() + " ha fundado: §b" + payload.clanName());
                serverLevel.getServer().getPlayerList().broadcastSystemMessage(chatMsg, false);

                // Sincronización
                core.markDirtyAndUpdate();
            }
        });
    }

    public static void handleAdminUpdate(UpdateAdminCorePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();

            // Solo nivel 2 (OP) puede usar este paquete masivo
            if (player.hasPermissions(2)) {
                Level level = player.level();
                if (level.getBlockEntity(payload.pos()) instanceof ProtectionCoreBlockEntity core) {

                    core.setAdminRadius(payload.radius());

                    // Actualizamos las flags principales usando la lógica de flags
                    core.setFlag("pvp", payload.pvp());
                    core.setFlag("explosions", payload.explosions());

                    core.markDirtyAndUpdate();
                    player.displayClientMessage(Component.literal("§d[Admin]§a Área administrativa sincronizada."), true);
                }
            }
        });
    }

    public static void handleOpenFlags(OpenFlagsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            Level level = player.level();

            if (level.getBlockEntity(payload.pos()) instanceof ProtectionCoreBlockEntity core) {
                // Solo el dueño o un Admin pueden ver las Flags
                if (player.getUUID().equals(core.getOwnerUUID()) || player.hasPermissions(2)) {
                    // Aquí abres el nuevo menú de Flags
                     player.openMenu(core, payload.pos());
                }
            }
        });
    }

    public static void handleUpdateFlag(UpdateFlagPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            Level level = player.level();

            if (level.getBlockEntity(payload.pos()) instanceof ProtectionCoreBlockEntity core) {
                String flagId = payload.flag();

                // SEGURIDAD NIVELADA:
                // canPlayerEditFlag verifica:
                // - Si es Admin (OP): Puede todo.
                // - Si es Dueño: Solo puede editar las BASIC_FLAGS.
                if (core.canPlayerEditFlag(player, flagId)) {

                    boolean currentValue = core.getFlag(flagId);
                    core.setFlag(flagId, !currentValue);

                    core.markDirtyAndUpdate();

                    player.displayClientMessage(Component.literal("§6[Core] §f" + flagId + " §7➜ " +
                            (!currentValue ? "§aHABILITADO" : "§cDESHABILITADO")), true);
                } else {
                    // Si un usuario normal intenta cambiar una flag administrativa
                    player.displayClientMessage(Component.literal("§c[!] No tienes permiso para editar la configuración de: " + flagId), true);
                }
            }
        });
    }
}
