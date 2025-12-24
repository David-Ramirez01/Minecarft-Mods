package com.tumod.protectormod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.util.ClanSavedData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class ClanCommands {
    public static int maxCoresPerPlayer = 3;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("protector")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("limit")
                        .then(Commands.argument("cantidad", IntegerArgumentType.integer(1, 100))
                                .executes(c -> {
                                    maxCoresPerPlayer = IntegerArgumentType.getInteger(c, "cantidad");
                                    c.getSource().sendSuccess(() -> Component.literal("§aLímite: " + maxCoresPerPlayer), true);
                                    return 1;
                                })))
        );

// Dentro del método ejecuta el comando delete
        dispatcher.register(Commands.literal("clan")
                .then(Commands.literal("delete").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    ClanSavedData data = ClanSavedData.get(player.serverLevel());

                    // Comprobamos si es DUEÑO (su UUID debe estar mapeado al nombre del clan como líder)
                    String clanName = data.getClanOfPlayer(player.getUUID());

                    // Suponiendo que en ClanSavedData tienes un método para verificar si es el líder
                    if (clanName.isEmpty() || !data.isLeader(clanName, player.getUUID())) {
                        context.getSource().sendFailure(Component.literal("§cSolo el líder puede disolver el clan."));
                        return 0;
                    }

                    // 1. Resetear físicamente los núcleos del dueño
                    ProtectionCoreBlockEntity.CORES.stream()
                            .filter(core -> player.getUUID().equals(core.getOwnerUUID()))
                            .forEach(core -> {
                                core.getPermissionsMap().clear(); // Borra invitados
                                core.initializeDefaultFlags();    // Resetea las 20 flags a OFF
                                core.setClanName("");             // Quita el nombre del clan del bloque
                                core.markDirtyAndUpdate();        // Sincroniza con los clientes
                            });

                    // 2. Notificar a todos los jugadores online si pertenecían a ese clan
                    for (ServerPlayer onlinePlayer : player.server.getPlayerList().getPlayers()) {
                        if (data.getClanOfPlayer(onlinePlayer.getUUID()).equals(clanName)) {
                            onlinePlayer.sendSystemMessage(Component.literal("§6[Clan] §eEl clan §f" + clanName + " §eha sido disuelto."));
                        }
                    }

                    // 3. Eliminar de la base de datos (NBT)
                    data.deleteClan(player.getUUID());
                    context.getSource().sendSuccess(() -> Component.literal("§eHas disuelto tu clan satisfactoriamente."), true);

                    return 1;
                }))
        );
    }
}
