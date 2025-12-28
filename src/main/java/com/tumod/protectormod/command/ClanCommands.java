package com.tumod.protectormod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.util.ClanSavedData;
import com.tumod.protectormod.util.InviteManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
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
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("cantidad", IntegerArgumentType.integer(1, 100))
                                .executes(c -> {
                                    int nuevaCant = IntegerArgumentType.getInteger(c, "cantidad");
                                    ClanSavedData data = ClanSavedData.get(c.getSource().getLevel());
                                    data.serverMaxCores = nuevaCant;
                                    data.setDirty();
                                    c.getSource().sendSuccess(() -> Component.literal("§aLímite global de cores actualizado a: §f" + nuevaCant), true);
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
            ClanSavedData data = ClanSavedData.get(player.serverLevel());
            ClanSavedData.ClanInstance clan = data.getClanByLeader(invite.requesterUUID());

            if (clan != null) {
                // VALIDACIÓN DE LÍMITE
                if (clan.members.size() >= clan.maxMembers) {
                    player.sendSystemMessage(Component.literal("§c[!] El clan está lleno. Límite: " + clan.maxMembers));
                    return 0;
                }

                // UNIÓN OFICIAL AL CLAN
                clan.members.add(player.getUUID());
                data.setDirty();

                // PERMISOS EN EL CORE
                if (player.level().getBlockEntity(invite.corePos()) instanceof ProtectionCoreBlockEntity core) {
                    core.updatePermission(player.getName().getString(), "build", true);
                    core.markDirtyAndUpdate();
                }

                player.sendSystemMessage(Component.literal("§a✔ Te has unido al clan: §b" + clan.name));
                InviteManager.removeInvite(player.getUUID());
                return 1;
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
        var cores = ProtectionCoreBlockEntity.getLoadedCores();
        if (cores.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§cNo hay núcleos activos."), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("§6§lAUDITORÍA DE NÚCLEOS:"), false);
        for (ProtectionCoreBlockEntity core : cores) {
            source.sendSuccess(() -> Component.literal("§e- §fPos: §b" + core.getBlockPos().toShortString() + " §8| §e" + core.getClanName()), false);
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
