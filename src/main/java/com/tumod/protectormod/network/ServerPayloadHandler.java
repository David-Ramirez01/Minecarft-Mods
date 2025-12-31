package com.tumod.protectormod.network;

import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.menu.ProtectionCoreMenu;
import com.tumod.protectormod.util.InviteManager;
import com.tumod.protectormod.util.ProtectionDataManager;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
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
                if (player.getUUID().equals(core.getOwnerUUID()) || player.hasPermissions(2)) {

                    // 1. CASO ELIMINAR
                    if (payload.permissionType().equals("remove")) {
                        // Debes asegurarte de que este método en el Core acepte String o buscar el UUID
                        core.removePlayerPermissions(payload.playerName());
                        player.displayClientMessage(Component.literal("§c[!] §fJugador §b" + payload.playerName() + "§f eliminado."), true);
                        core.markDirtyAndUpdate();
                        return;
                    }

                    // 2. CASO JUGADOR EXISTENTE
                    // Cambiado para usar el nuevo mapa de permisos del Core
                    if (core.getPlayersWithAnyPermission().contains(payload.playerName())) {
                        // FIX: Asegúrate de que el Core tenga este método exacto o pásale el UUID si lo tienes
                        core.updatePermission(payload.playerName(), payload.permissionType(), payload.value());
                        player.displayClientMessage(Component.literal("§7[§6Protector§7] §fPermisos de §b" + payload.playerName() + "§f actualizados."), true);
                        core.markDirtyAndUpdate();
                        return;
                    }



                    // --- VALIDACIÓN DE LÍMITE ---
                    if (core.getPlayersWithAnyPermission().size() >= 8) {
                        player.displayClientMessage(Component.literal("§c[!] El núcleo ya alcanzó el límite de 8 invitados."), true);
                        return;
                    }

                    // 3. CASO JUGADOR NUEVO (Invitación)
                    ServerPlayer target = level.getServer().getPlayerList().getPlayerByName(payload.playerName());
                    if (target == null) {
                        player.displayClientMessage(Component.literal("§c[!] El jugador no está en línea."), true);
                        return;
                    }

                    InviteManager.addInvite(target.getUUID(), payload.pos(), player.getUUID());

                    Component msg = Component.literal("\n§6§l[Protector] §fInvitación de §b" + player.getName().getString() + "\n")
                            .append(Component.literal("§a§l[ACEPTAR] ")
                                    .withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/protector accept"))
                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Unirse al núcleo")))))
                            .append(Component.literal("  ") ) // Espacio
                            .append(Component.literal("§c§l[RECHAZAR]")
                                    .withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/protector deny"))
                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Ignorar invitación")))));

                    target.sendSystemMessage(msg);
                    // Sonido de notificación para el invitado
                    target.playNotifySound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(),
                            net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
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
                    // FIX: Ahora pasamos ambos datos para que el Core guarde nombre y UUID del dueño
                    core.setOwner(owner.getUUID(), owner.getName().getString());

                    serverLevel.getServer().getPlayerList().broadcastSystemMessage(
                            Component.literal("§6§l[Clan] §f" + owner.getName().getString() + " ha fundado: §b" + name),
                            false
                    );
                    core.markDirtyAndUpdate();
                }
            }
        });
    }

    public static void handleAdminUpdate(UpdateAdminCorePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            Level level = player.level();

            if (player.hasPermissions(2) && level.getBlockEntity(payload.pos()) instanceof ProtectionCoreBlockEntity core) {
                core.setAdminRadius(payload.radius());

                // Sincronizamos TODAS las flags relacionadas con construcción e interacción
                boolean canBuild = payload.canBuild();
                core.setFlag("break", canBuild);
                core.setFlag("build", canBuild);
                core.setFlag("interact", canBuild); // Si permites construir, sueles permitir interactuar
                core.setFlag("chests", canBuild);   // Para el Admin Core, esto suele ir ligado al build general
                core.setFlag("pvp", payload.pvp());
                core.setFlag("explosions", payload.explosions());

                // Actualizar Manager Global
                ProtectionDataManager.get(level).addOrUpdateCore(payload.pos(), core.getOwnerUUID(), payload.radius());

                ProtectionDataManager data = ProtectionDataManager.get(level);
                data.addOrUpdateCore(payload.pos(), core.getOwnerUUID(), payload.radius());

                if (level instanceof ServerLevel sLevel) {
                    sLevel.getChunkSource().blockChanged(payload.pos());
                    data.syncToAll(sLevel);
                }
                core.setChanged();
                level.sendBlockUpdated(payload.pos(), core.getBlockState(), core.getBlockState(), 3);

                player.displayClientMessage(Component.literal("§d[Admin]§a Flags de construcción y cofres actualizadas."), true);
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

                // 1. Verificación de Seguridad y Niveles
                // Asegúrate de que core.canPlayerEditFlag compruebe si el nivel del core permite esa flag
                if (core.canPlayerEditFlag(player, flagId)) {

                    boolean newValue = !core.getFlag(flagId);
                    core.setFlag(flagId, newValue);

                    // IMPORTANTE: Sincronización
                    core.setChanged();
                    level.sendBlockUpdated(payload.pos(), core.getBlockState(), core.getBlockState(), 3);

                    // Feedback visual y sonoro
                    String status = newValue ? "§aHABILITADO" : "§cDESHABILITADO";
                    player.displayClientMessage(Component.literal("§6[Core] §f" + flagId + " §7➜ " + status), true);

                    // Si el jugador es ServerPlayer, podemos enviar un sonido de "click"
                    if (player instanceof net.minecraft.server.level.ServerPlayer sPlayer) {
                        sPlayer.playNotifySound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(),
                                net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
                    }
                } else {
                    player.displayClientMessage(Component.literal("§c[!] Nivel insuficiente o sin permisos para: " + flagId), true);
                }
            }
        });
    }
}
