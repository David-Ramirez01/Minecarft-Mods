package com.tumod.protectormod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tumod.protectormod.blockentity.AdminProtectorBlockEntity;
import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.util.ClanSavedData;
import com.tumod.protectormod.util.InviteManager;
import com.tumod.protectormod.util.ProtectionDataManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ClanCommands {
    public static int maxCoresPerPlayer = 3;
    // Usamos ConcurrentHashMap para evitar errores de acceso simultáneo
    public static final Set<UUID> VISUALIZER_ENABLED = ConcurrentHashMap.newKeySet();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // --- COMANDO /protector ---
        dispatcher.register(Commands.literal("protector")
                .then(Commands.literal("presentation")
                        .then(Commands.argument("state", StringArgumentType.word())
                                .executes(context -> {
                                    Player player = context.getSource().getPlayerOrException();
                                    String state = StringArgumentType.getString(context, "state");
                                    boolean isOn = state.equalsIgnoreCase("on");
                                    player.getPersistentData().putBoolean("ProtectorPresentation", isOn);
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            isOn ? "§aPresentación de zonas activada." : "§cPresentación de zonas desactivada."), true);
                                    return 1;
                                })
                        )
                )
                // Dentro de tu registro en ClanCommands.java
                .then(Commands.literal("admin")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.literal("trust")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> {
                                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                            ServerPlayer admin = context.getSource().getPlayerOrException();
                                            BlockHitResult hit = (BlockHitResult) admin.pick(5.0D, 0.0F, false);
                                            BlockPos pos = hit.getBlockPos();

                                            if (admin.level().getBlockEntity(pos) instanceof AdminProtectorBlockEntity adminCore) {
                                                adminCore.updatePermission(target.getUUID(), target.getName().getString(), "build", true);
                                                adminCore.markDirtyAndUpdate(); // <--- IMPORTANTE para persistencia
                                                admin.sendSystemMessage(Component.literal("§d[Admin] §b" + target.getName().getString() + " §afue añadido."));
                                                return 1;
                                            }
                                            admin.sendSystemMessage(Component.literal("§c[!] Debes mirar un núcleo de admin."));
                                            return 0;
                                        })
                                )
                        )
                        .then(Commands.literal("untrust") // <--- NUEVO: Para quitar permisos
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> {
                                            ServerPlayer target = EntityArgument.getPlayer(context, "player");
                                            ServerPlayer admin = context.getSource().getPlayerOrException();
                                            BlockHitResult hit = (BlockHitResult) admin.pick(5.0D, 0.0F, false);
                                            BlockPos pos = hit.getBlockPos();

                                            if (admin.level().getBlockEntity(pos) instanceof AdminProtectorBlockEntity adminCore) {
                                                adminCore.removePlayerPermissions(target.getName().getString());
                                                adminCore.markDirtyAndUpdate();
                                                admin.sendSystemMessage(Component.literal("§d[Admin] §b" + target.getName().getString() + " §cremovido."));
                                                return 1;
                                            }
                                            return 0;
                                        })
                                )
                        )
                )
                .then(Commands.literal("help").executes(context -> showProtectorHelp(context.getSource())))
                .then(Commands.literal("visualizer")
                        .requires(s -> s.hasPermission(2))
                        .executes(context -> {
                            Player player = context.getSource().getPlayerOrException();
                            if (VISUALIZER_ENABLED.contains(player.getUUID())) {
                                VISUALIZER_ENABLED.remove(player.getUUID());
                                context.getSource().sendSuccess(() -> Component.literal("§cVisualizador desactivado."), true);
                            } else {
                                VISUALIZER_ENABLED.add(player.getUUID());
                                context.getSource().sendSuccess(() -> Component.literal("§aVisualizador activado."), true);
                            }
                            return 1;
                        })
                )
                .then(Commands.literal("limit")
                        .requires(s -> s.hasPermission(2)) // Solo Admins pueden ejecutarlo
                        .then(Commands.argument("cantidad", IntegerArgumentType.integer(1, 100))
                                .executes(c -> {
                                    int nuevaCant = IntegerArgumentType.getInteger(c, "cantidad");
                                    ServerLevel level = c.getSource().getLevel();

                                    // 1. Guardar el dato en el Manager
                                    ProtectionDataManager manager = ProtectionDataManager.get(level);
                                    manager.setGlobalLimit(nuevaCant);

                                    // 2. Mensaje para quien ejecutó el comando
                                    c.getSource().sendSuccess(() -> Component.literal("§6[Protector] §aLímite global actualizado a: §f" + nuevaCant), true);

                                    // 3. Notificar a otros OPs conectados (Broadcast silencioso)
                                    Component notification = Component.literal("§7[Staff] " + c.getSource().getTextName() + " cambió el límite de núcleos a: " + nuevaCant);
                                    level.getServer().getPlayerList().getPlayers().forEach(player -> {
                                        if (player.hasPermissions(2) && player != c.getSource().getEntity()) {
                                            player.displayClientMessage(notification, false);
                                        }
                                    });

                                    return 1;
                                }))
                )
                .then(Commands.literal("list")
                        .requires(s -> s.hasPermission(2))
                        .executes(context -> executeList(context.getSource())))
                .then(Commands.literal("accept").executes(context -> executeAccept(context.getSource())))
                .then(Commands.literal("deny").executes(context -> executeDeny(context.getSource())))
        );

        // --- COMANDO /clan ---
        dispatcher.register(Commands.literal("clan")
                .executes(context -> showClanHelp(context.getSource()))
                .then(Commands.literal("help").executes(context -> showClanHelp(context.getSource())))
                .then(Commands.literal("limit")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("nombreClan", StringArgumentType.string())
                                .then(Commands.argument("nuevoLimite", IntegerArgumentType.integer(1, 100))
                                        .executes(context -> {
                                            String clanName = StringArgumentType.getString(context, "nombreClan");
                                            int limite = IntegerArgumentType.getInteger(context, "nuevoLimite");
                                            ClanSavedData data = ClanSavedData.get(context.getSource().getLevel());
                                            ClanSavedData.ClanInstance clan = data.getClan(clanName);
                                            if (clan == null) {
                                                context.getSource().sendFailure(Component.literal("§cEl clan '" + clanName + "' no existe."));
                                                return 0;
                                            }
                                            clan.maxMembers = limite;
                                            data.setDirty();
                                            context.getSource().sendSuccess(() -> Component.literal("§aLímite del clan §b" + clanName + " §aactualizado a §f" + limite), true);
                                            return 1;
                                        })
                                )
                        )
                )
                .then(Commands.literal("delete").executes(context -> executeDelete(context.getSource())))
                .then(Commands.literal("info")
                        .executes(context -> showClanInfo(context.getSource(), null))
                        .then(Commands.argument("nombreClan", StringArgumentType.greedyString())
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> showClanInfo(context.getSource(), StringArgumentType.getString(context, "nombreClan")))))
                .then(Commands.literal("admin")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("nombreClan", StringArgumentType.greedyString())
                                        .executes(context -> {
                                            String target = StringArgumentType.getString(context, "nombreClan");
                                            ClanSavedData data = ClanSavedData.get(context.getSource().getLevel());
                                            ClanSavedData.ClanInstance clan = data.getClan(target);
                                            if (clan == null) {
                                                context.getSource().sendFailure(Component.literal("§cEl clan '" + target + "' no existe."));
                                                return 0;
                                            }
                                            data.deleteClan(clan.leaderUUID);
                                            context.getSource().sendSuccess(() -> Component.literal("§6[Admin] §eEl clan §b" + target + " §eha sido borrado."), true);
                                            return 1;
                                        }))))
        );
    }

    private static int showClanInfo(CommandSourceStack source, @Nullable String targetClanName) {
        ClanSavedData data = ClanSavedData.get(source.getLevel());
        ClanSavedData.ClanInstance clan;

        if (targetClanName == null) {
            if (source.getEntity() instanceof Player player) {
                clan = data.getClanByMember(player.getUUID()); // Busca por líder o miembro
                if (clan == null) {
                    source.sendFailure(Component.literal("§cNo tienes clan."));
                    return 0;
                }
            } else return 0;
        } else {
            clan = data.getClan(targetClanName);
        }

        if (clan == null) {
            source.sendFailure(Component.literal("§cClan no encontrado."));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§8§m================================="), false);
        source.sendSuccess(() -> Component.literal("§6§lINFO: §b" + clan.name), false);
        source.sendSuccess(() -> Component.literal("§eLíder: §f" + clan.leaderName), false);
        source.sendSuccess(() -> Component.literal("§eMiembros: §a" + clan.members.size() + "/" + clan.maxMembers), false);
        source.sendSuccess(() -> Component.literal("§eUbicación Core: §7(" + clan.corePos.getX() + ", " + clan.corePos.getY() + ", " + clan.corePos.getZ() + ")"), false);
        source.sendSuccess(() -> Component.literal("§8§m================================="), false);
        return 1;
    }

    private static int executeDelete(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ClanSavedData data = ClanSavedData.get(player.serverLevel());

        // Verificamos si es líder
        ClanSavedData.ClanInstance clan = data.getClanByLeader(player.getUUID());

        if (clan == null) {
            // Si es miembro pero no líder
            if (data.getClanByMember(player.getUUID()) != null) {
                source.sendFailure(Component.literal("§cSolo el líder puede disolver el clan."));
            } else {
                source.sendFailure(Component.literal("§cAun no tienes un clan que disolver."));
            }
            return 0;
        }

        data.deleteClan(player.getUUID());
        source.sendSuccess(() -> Component.literal("§eClan §b" + clan.name + " §edisuelto correctamente."), true);
        return 1;
    }

    public static int executeAccept(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        InviteManager.PendingInvite invite = InviteManager.getInvite(player.getUUID());

        if (invite != null) {
            ServerLevel level = player.serverLevel();

            // 1. Intentar unión al Clan (Si el dueño tiene uno)
            ClanSavedData data = ClanSavedData.get(level);
            ClanSavedData.ClanInstance clan = data.getClanByLeader(invite.requesterUUID());
            if (clan != null) {
                if (clan.members.size() < clan.maxMembers) {
                    clan.members.add(player.getUUID());
                    data.setDirty();
                    player.sendSystemMessage(Component.literal("§a✔ Te has unido al clan: §b" + clan.name));
                }
            }

            // 2. VITAL: Añadir permisos directamente al Bloque (Core)
            if (level.getBlockEntity(invite.corePos()) instanceof ProtectionCoreBlockEntity core) {
                // Le damos permiso de construcción y cofres por defecto al aceptar
                core.updatePermission(player.getUUID(), player.getName().getString(), "build", true);
                core.updatePermission(player.getUUID(), player.getName().getString(), "chests", true);
                core.markDirtyAndUpdate();

                player.sendSystemMessage(Component.literal("§a✔ Ahora tienes acceso al núcleo de §b" + core.getOwnerName()));
                InviteManager.removeInvite(player.getUUID());
                return 1;
            } else {
                player.sendSystemMessage(Component.literal("§c[!] El núcleo ya no existe o no se pudo encontrar."));
                InviteManager.removeInvite(player.getUUID());
                return 0;
            }
        }

        player.sendSystemMessage(Component.literal("§c[!] No tienes invitaciones pendientes."));
        return 0;
    }

    // --- MÉTODOS DE AYUDA Y LISTA ---
    private static int showProtectorHelp(CommandSourceStack source) {
        boolean isAdmin = source.hasPermission(2);

        source.sendSuccess(() -> Component.literal("§8§m================================="), false);
        source.sendSuccess(() -> Component.literal("§6§lSISTEMA DE PROTECCIÓN - AYUDA"), false);
        source.sendSuccess(() -> Component.literal(" "), false);

        // Comandos para TODOS
        source.sendSuccess(() -> Component.literal("§b/protector help §7- Muestra este menú."), false);
        source.sendSuccess(() -> Component.literal("§b/protector presentation <on/off> §7- Mensajes de entrada."), false);
        source.sendSuccess(() -> Component.literal("§b/protector accept §7- Aceptar invitación."), false);
        source.sendSuccess(() -> Component.literal("§b/protector deny §7- Rechazar invitación."), false);

        // Comandos solo para ADMINS
        if (isAdmin) {
            source.sendSuccess(() -> Component.literal(" "), false);
            source.sendSuccess(() -> Component.literal("§d§lMODO ADMINISTRADOR:"), false);
            source.sendSuccess(() -> Component.literal("§b/protector debug §7- Info técnica del core."), false);
            source.sendSuccess(() -> Component.literal("§b/protector visualizer §7- Bordes de partículas."), false);
            source.sendSuccess(() -> Component.literal("§b/protector admin trust/untrust §7- Permitir / Remover de un admin core"), false);
            source.sendSuccess(() -> Component.literal("§b/protector list §7- Lista todos los núcleos."), false);
            source.sendSuccess(() -> Component.literal("§b/protector limit <n> §7- Límite global de cores."), false);
        }

        source.sendSuccess(() -> Component.literal("§8§m================================="), false);
        return 1;
    }

    private static int showClanHelp(CommandSourceStack source) {
        boolean isAdmin = source.hasPermission(2);

        source.sendSuccess(() -> Component.literal("§8§m================================="), false);
        source.sendSuccess(() -> Component.literal("§6§lSISTEMA DE CLANES - AYUDA"), false);
        source.sendSuccess(() -> Component.literal(" "), false);

        // Comandos para TODOS
        source.sendSuccess(() -> Component.literal("§b/clan info §7- Ver info de tu clan y miembros."), false);
        source.sendSuccess(() -> Component.literal("§b/clan delete §7- Disuelve tu clan (Solo líder)."), false);

        // Comandos solo para ADMINS
        if (isAdmin) {
            source.sendSuccess(() -> Component.literal(" "), false);
            source.sendSuccess(() -> Component.literal("§d§lMODO ADMINISTRADOR:"), false);
            source.sendSuccess(() -> Component.literal("§b/clan info <nombre> §7- Ver info de cualquier clan."), false);
            source.sendSuccess(() -> Component.literal("§b/clan limit <clan> <n> §7- Cambiar límite de miembros."), false);
            source.sendSuccess(() -> Component.literal("§b/clan admin delete <nombre> §7- Borrar un clan ajeno."), false);
        }

        source.sendSuccess(() -> Component.literal("§8§m================================="), false);
        return 1;
    }

    private static int executeList(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        ProtectionDataManager manager = ProtectionDataManager.get(level);
        ClanSavedData clanData = ClanSavedData.get(level); // Obtenemos datos de clanes
        var cores = manager.getAllCores();

        if (cores.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§cNo hay núcleos activos."), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§6§lAUDITORÍA DE NÚCLEOS:"), false);

        for (var entry : cores.entrySet()) {
            BlockPos pos = entry.getKey();
            UUID ownerUUID = entry.getValue().owner();

            // 1. Obtener el nombre del jugador (si está online o desde el cache del servidor)
            String ownerName = "Desconocido";
            if (ownerUUID != null) {
                var player = level.getServer().getPlayerList().getPlayer(ownerUUID);
                if (player != null) {
                    ownerName = player.getName().getString();
                } else {
                    // Si el jugador está offline, intentamos buscar el nombre en la BE o el cache
                    // Por ahora, si no lo encuentra, mostramos "Offline/UUID" o "Sistema"
                    ownerName = ownerUUID.toString().substring(0, 8) + "...";
                }
            } else {
                ownerName = "§dADMIN";
            }

            // 2. Obtener el nombre del clan
            String clanSuffix = "";
            var clan = clanData.getClanByMember(ownerUUID);
            if (clan != null) {
                clanSuffix = " §8[§b" + clan.name + "§8]";
            }

            String finalName = ownerName;
            String finalClan = clanSuffix;
            source.sendSuccess(() -> Component.literal("§e- §fPos: §b" + pos.toShortString() +
                    " §8| §eOwner: §7" + finalName + finalClan), false);
        }
        return cores.size();
    }

    public static int executeDeny(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (InviteManager.getInvite(player.getUUID()) != null) {
            InviteManager.removeInvite(player.getUUID());
            player.sendSystemMessage(Component.literal("§eInvitación rechazada."));
            return 1;
        }
        return 0;
    }
}
