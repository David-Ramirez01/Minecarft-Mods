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
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            if (player.containerMenu instanceof ProtectionCoreMenu menu) {
                ProtectionCoreBlockEntity core = menu.getCore();

                if (core != null && !core.isRemoved()) {
                    // SEGURIDAD: Solo el dueño o un OP pueden mejorar el núcleo
                    boolean isOwner = player.getUUID().equals(core.getOwnerUUID());
                    boolean isOP = player.hasPermissions(2);

                    if (isOwner || isOP) {
                        // Pasamos el serverPlayer para que el core pueda enviar mensajes de error
                        core.upgrade(serverPlayer);
                    } else {
                        serverPlayer.displayClientMessage(Component.literal("§c[!] No eres el dueño de este núcleo."), true);
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
            if (!(context.player() instanceof ServerPlayer owner)) return;

            ServerLevel serverLevel = owner.serverLevel();
            String name = payload.clanName().trim(); // Limpiamos espacios

            if (serverLevel.getBlockEntity(payload.pos()) instanceof ProtectionCoreBlockEntity core) {
                ClanSavedData data = ClanSavedData.get(serverLevel);

                // 1. Validar si el nombre es demasiado corto o largo
                if (name.length() < 3 || name.length() > 16) {
                    owner.displayClientMessage(Component.literal("§c[!] El nombre debe tener entre 3 y 16 caracteres."), true);
                    return;
                }

                // 2. Validar si el jugador ya es líder (Usando el método que añadimos a ClanSavedData)
                if (data.getClanByLeader(owner.getUUID()) != null) {
                    owner.displayClientMessage(Component.literal("§c[!] Ya eres líder de un clan. Disuélvelo primero."), true);
                    return;
                }

                // 3. Validar si el nombre ya existe
                if (data.getClan(name) != null) {
                    owner.displayClientMessage(Component.literal("§c[!] El nombre '" + name + "' ya está en uso."), true);
                    return;
                }

                // 4. Intentar creación definitiva
                boolean creado = data.tryCreateClan(name, owner.getUUID(), owner.getName().getString(), payload.pos());

                if (creado) {
                    core.setClanName(name);
                    core.setOwner(owner.getUUID());

                    // Notificación Global
                    Component chatMsg = Component.literal("§6§l[Clan] §f" + owner.getName().getString() + " ha fundado: §b" + name);
                    serverLevel.getServer().getPlayerList().broadcastSystemMessage(chatMsg, false);

                    // Efecto de sonido de éxito para el fundador
                    owner.playNotifySound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);

                    core.markDirtyAndUpdate();
                }
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
