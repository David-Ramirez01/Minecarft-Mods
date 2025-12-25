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
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ClanCommands {
    public static int maxCoresPerPlayer = 3;
    public static final Set<UUID> VISUALIZER_ENABLED = new HashSet<>();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // --- COMANDO /protector ---
        dispatcher.register(Commands.literal("protector")
                .then(Commands.literal("help").executes(context -> {
                    CommandSourceStack source = context.getSource();
                    boolean isAdmin = source.hasPermission(2);

                    source.sendSuccess(() -> Component.literal("§8§m================================="), false);
                    source.sendSuccess(() -> Component.literal("§6§lSISTEMA DE PROTECCIÓN - AYUDA"), false);
                    source.sendSuccess(() -> Component.literal(" "), false);

                    // --- COMANDOS PARA TODOS ---
                    source.sendSuccess(() -> Component.literal("§b/protector accept §7- Aceptar invitación."), false);
                    source.sendSuccess(() -> Component.literal("§b/protector deny §7- Rechazar invitación."), false);

                    // --- COMANDOS SOLO PARA ADMINS ---
                    if (isAdmin) {
                        source.sendSuccess(() -> Component.literal(" "), false);
                        source.sendSuccess(() -> Component.literal("§d§lMODO ADMINISTRADOR:"), false);
                        source.sendSuccess(() -> Component.literal("§b/protector visualizer §7- Ver todas las áreas."), false);
                        source.sendSuccess(() -> Component.literal("§b/protector list §7- Listar núcleos activos."), false);
                        source.sendSuccess(() -> Component.literal("§b/protector limit <n> §7- Cambiar límite global."), false);
                    }

                    source.sendSuccess(() -> Component.literal("§8§m================================="), false);
                    return 1;
                }))
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
                                    c.getSource().sendSuccess(() -> Component.literal("§aLímite global actualizado a: §f" + nuevaCant), true);
                                    return 1;
                                }))
                )
                .then(Commands.literal("list")
                        .requires(s -> s.hasPermission(2))
                        .executes(context -> {
                            ProtectionCoreBlockEntity.getLoadedCores().removeIf(core ->
                                    core.isRemoved() || core.getLevel() == null);

                            var cores = ProtectionCoreBlockEntity.getLoadedCores();
                            if (cores.isEmpty()) {
                                context.getSource().sendSuccess(() -> Component.literal("§cNo hay núcleos activos."), false);
                                return 0;
                            }

                            context.getSource().sendSuccess(() -> Component.literal("§6§lAUDITORÍA DE NÚCLEOS:"), false);

                            for (ProtectionCoreBlockEntity core : cores) {
                                // Definimos variables como 'final' para que la lambda las acepte
                                final BlockPos p = core.getBlockPos();
                                final String clan = core.getClanName().isEmpty() ? "Sin Clan" : core.getClanName();
                                final UUID ownerId = core.getOwnerUUID();

                                // Obtenemos el nombre del dueño fuera de la lambda
                                String tempName = "Desconocido";
                                var profile = context.getSource().getServer().getProfileCache().get(ownerId);
                                if (profile.isPresent()) {
                                    tempName = profile.get().getName();
                                }
                                final String ownerName = tempName;

                                // Ahora enviamos el mensaje usando las variables 'final'
                                context.getSource().sendSuccess(() -> Component.literal(
                                        "§e- §fPos: §b" + p.getX() + " " + p.getY() + " " + p.getZ() +
                                                " §8| §e" + clan +
                                                " §8| §7Dueño: §f" + ownerName), false);
                            }
                            return cores.size();
                        })
                )
                .then(Commands.literal("accept")
                        .executes(context -> executeAccept(context.getSource())))
                .then(Commands.literal("deny")
                        .executes(context -> executeDeny(context.getSource())))
        );

        // --- COMANDO /clan ---
        dispatcher.register(Commands.literal("clan")
                // Ayuda dinámica si solo escriben /clan
                .executes(context -> showClanHelp(context.getSource()))

                // Subcomando help explícito
                .then(Commands.literal("help").executes(context -> showClanHelp(context.getSource())))

                .then(Commands.literal("delete").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    ClanSavedData data = ClanSavedData.get(player.serverLevel());
                    String clanName = data.getClanOfPlayer(player.getUUID());

                    if (clanName.isEmpty()) {
                        context.getSource().sendFailure(Component.literal("§cNo eres líder de ningún clan."));
                        return 0;
                    }

                    ProtectionCoreBlockEntity.getLoadedCores().stream()
                            .filter(core -> player.getUUID().equals(core.getOwnerUUID()))
                            .forEach(ProtectionCoreBlockEntity::resetToDefault);

                    data.deleteClan(player.getUUID());
                    context.getSource().sendSuccess(() -> Component.literal("§eClan disuelto."), true);
                    return 1;
                }))
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

                                            ProtectionCoreBlockEntity.getLoadedCores().stream()
                                                    .filter(c -> clan.leaderUUID.equals(c.getOwnerUUID()))
                                                    .forEach(ProtectionCoreBlockEntity::resetToDefault);

                                            data.deleteClan(clan.leaderUUID);
                                            context.getSource().sendSuccess(() -> Component.literal("§6[Admin] §eEl clan §b" + target + " §eha sido borrado."), true);
                                            return 1;
                                        }))))
        );
    }

    private static int showClanInfo(CommandSourceStack source, @Nullable String targetClan) {
        ClanSavedData data = ClanSavedData.get(source.getLevel());
        ClanSavedData.ClanInstance clan;
        if (targetClan == null) {
            if (source.getEntity() instanceof Player player) {
                clan = data.getClan(data.getClanOfPlayer(player.getUUID()));
            } else return 0;
        } else {
            clan = data.getClan(targetClan);
        }

        if (clan == null) return 0;

        source.sendSuccess(() -> Component.literal("§6§lInfo: §b" + clan.name), false);
        source.sendSuccess(() -> Component.literal("§eLíder: §f" + clan.leaderName), false);
        return 1;
    }

    // Lógica que deberías poner en tu gestor de comandos
    public static int executeAccept(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        InviteManager.PendingInvite invite = InviteManager.getInvite(player.getUUID());

        if (invite != null) {
            if (player.level().getBlockEntity(invite.corePos()) instanceof ProtectionCoreBlockEntity core) {
                // AHORA SÍ: Lo añadimos oficialmente con permiso básico (interactuar)
                core.updatePermission(player.getName().getString(), "interact", true);
                core.markDirtyAndUpdate();

                player.sendSystemMessage(Component.literal("§a✔ Has aceptado la invitación."));

                // Avisar al dueño si sigue conectado
                ServerPlayer owner = player.server.getPlayerList().getPlayer(invite.requesterUUID());
                if (owner != null) {
                    owner.sendSystemMessage(Component.literal("§b" + player.getName().getString() + " §ahas aceptado tu invitación."));
                }

                InviteManager.removeInvite(player.getUUID());
            }
        } else {
            player.sendSystemMessage(Component.literal("§c[!] No tienes invitaciones pendientes o ya expiraron."));
        }
        return 1;
    }

    public static int executeDeny(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        InviteManager.PendingInvite invite = InviteManager.getInvite(player.getUUID());

        if (invite != null) {
            InviteManager.removeInvite(player.getUUID());
            player.sendSystemMessage(Component.literal("§eHas rechazado la invitación."));

            // Opcional: Avisar al dueño que su invitación fue rechazada
            ServerPlayer owner = player.server.getPlayerList().getPlayer(invite.requesterUUID());
            if (owner != null) {
                owner.sendSystemMessage(Component.literal("§c" + player.getName().getString() + " §7ha rechazado tu invitación."));
            }
        } else {
            player.sendSystemMessage(Component.literal("§c[!] No hay ninguna invitación activa para rechazar."));
        }
        return 1;
    }

    private static int showClanHelp(CommandSourceStack source) {
        boolean isAdmin = source.hasPermission(2);

        source.sendSuccess(() -> Component.literal("§8§m================================="), false);
        source.sendSuccess(() -> Component.literal("§6§lSISTEMA DE CLANES - AYUDA"), false);
        source.sendSuccess(() -> Component.literal(" "), false);

        // Comandos de usuario
        source.sendSuccess(() -> Component.literal("§b/clan info §7- Ver información de tu clan."), false);
        source.sendSuccess(() -> Component.literal("§b/clan delete §7- Disuelve tu clan (Si eres líder)."), false);

        // Comandos de Admin
        if (isAdmin) {
            source.sendSuccess(() -> Component.literal(" "), false);
            source.sendSuccess(() -> Component.literal("§d§lCOMANDOS DE ADMIN:"), false);
            source.sendSuccess(() -> Component.literal("§b/clan info <nombre> §7- Ver info de cualquier clan."), false);
            source.sendSuccess(() -> Component.literal("§b/clan admin delete <nombre> §7- Borrar un clan ajeno."), false);
        }

        source.sendSuccess(() -> Component.literal("§8§m================================="), false);
        return 1;
    }
}
