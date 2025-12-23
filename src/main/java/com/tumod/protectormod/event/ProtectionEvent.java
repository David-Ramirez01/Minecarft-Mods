package com.tumod.protectormod.event;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity.CORES;

@EventBusSubscriber(modid = ProtectorMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class ProtectionEvent {

    private static final Map<UUID, Boolean> PLAYER_INSIDE = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PLAYER_INSIDE.remove(event.getEntity().getUUID());
    }

    public class ProtectorCommands {
        public static int maxCoresPerPlayer = 3;

        public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
            dispatcher.register(Commands.literal("protector")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("limit")
                            .then(Commands.argument("cantidad", IntegerArgumentType.integer(1, 100))
                                    .executes(context -> {
                                        int cantidad = IntegerArgumentType.getInteger(context, "cantidad");
                                        maxCoresPerPlayer = cantidad;
                                        context.getSource().sendSuccess(() ->
                                                Component.literal("§aLímite de núcleos actualizado a: " + cantidad), true);
                                        return 1;
                                    })
                            )
                    )
                    // NUEVO COMANDO: /protector list
                    .then(Commands.literal("list")
                            .executes(context -> {
                                if (ProtectionCoreBlockEntity.CORES.isEmpty()) {
                                    context.getSource().sendSuccess(() -> Component.literal("§cNo hay núcleos activos."), false);
                                    return 0;
                                }

                                context.getSource().sendSuccess(() -> Component.literal("§6--- Lista de Núcleos Activos ---"), false);

                                for (ProtectionCoreBlockEntity core : ProtectionCoreBlockEntity.CORES) {
                                    BlockPos pos = core.getBlockPos();
                                    String owner = core.getOwnerUUID() != null ? core.getOwnerUUID().toString().substring(0, 8) : "Desconocido";

                                    // Creamos un mensaje con clic para TP
                                    Component message = Component.literal("§e- Core §7[§b" + pos.getX() + " " + pos.getY() + " " + pos.getZ() + "§7] §fOwner ID: " + owner)
                                            .withStyle(style -> style
                                                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp " + pos.getX() + " " + pos.getY() + " " + pos.getZ()))
                                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click para copiar comando de TP"))));

                                    context.getSource().sendSuccess(() -> message, false);
                                }
                                return 1;
                            })
                    )
            );
        }
    }

    @SubscribeEvent
    public static void onPvP(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Player victim && event.getSource().getEntity() instanceof Player attacker) {
            for (ProtectionCoreBlockEntity core : CORES) {
                if (core.getLevel() == victim.level() && core.isInside(victim.blockPosition())) {
                    // Si el PvP está desactivado en este núcleo
                    if (!core.isPvpEnabled()) {
                        event.setCanceled(true);
                        attacker.displayClientMessage(Component.literal("§cEl PvP está desactivado aquí."), true);
                        return;
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        BlockPos pos = BlockPos.containing(event.getExplosion().center());
        for (ProtectionCoreBlockEntity core : CORES) {
            if (core.areExplosionsDisabled() && core.isInside(pos)) {
                event.getAffectedBlocks().clear();
                return;
            }
        }
    }

    // ✅ INTERACCIONES (Cofres, Puertas, Hornos)
    @SubscribeEvent
    public static void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) return;
        if (event.getEntity().hasPermissions(2)) return;

        BlockPos pos = event.getPos();
        Player player = event.getEntity();

        for (ProtectionCoreBlockEntity core : CORES) {
            if (core.getLevel() == event.getLevel() && core.isInside(pos)) {
                // Si el dueño u OP no están interactuando, revisamos permisos
                boolean canInteract = core.hasPermission(player, "interact") || core.hasPermission(player, "chests");
                if (!canInteract) {
                    event.setCanceled(true);
                    player.displayClientMessage(Component.literal("No tienes permiso para interactuar aquí").withStyle(ChatFormatting.RED), true);
                    return;
                }
            }
        }
    }

    // ✅ ROMPER BLOQUES (Usa el radio dinámico de getRadius())
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;

        Player player = event.getPlayer();
        BlockPos pos = event.getPos();
        Level level = (Level) event.getLevel();

        if (player.hasPermissions(2)) return;

        for (ProtectionCoreBlockEntity core : CORES) {
            if (core.getLevel() == level && core.isInside(pos)) {
                if (!core.hasPermission(player, "build")) {
                    event.setCanceled(true);
                    player.displayClientMessage(Component.literal("No tienes permiso para romper bloques aquí").withStyle(ChatFormatting.RED), true);
                    return;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Player player)) return;

        if (event.getPlacedBlock().is(ModBlocks.PROTECTION_CORE.get())) {
            // 1. Verificar Límite
            long currentCores = CORES.stream()
                    .filter(c -> player.getUUID().equals(c.getOwnerUUID()))
                    .count();

            if (currentCores >= ProtectorCommands.maxCoresPerPlayer && !player.hasPermissions(2)) {
                event.setCanceled(true);
                player.displayClientMessage(Component.literal("§cHas alcanzado tu límite de núcleos."), true);
                return;
            }

            // 2. Verificar Superposición de ÁREAS (no solo del bloque)
            int radioNuevo = 8; // Radio base del nivel 1
            for (ProtectionCoreBlockEntity existingCore : CORES) {
                // Usamos el método areaOverlaps que definimos antes en el BlockEntity
                if (existingCore.areaOverlaps(event.getPos(), radioNuevo)) {
                    if (!player.hasPermissions(2)) {
                        event.setCanceled(true);
                        player.displayClientMessage(Component.literal("§cLas zonas de protección no pueden solaparse."), true);
                        return;
                    }
                }
            }
        }
    }

    // ✅ ALERTAS DE ENTRADA/SALIDA
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || player.level().getGameTime() % 10 != 0) return;

        BlockPos pos = player.blockPosition();
        boolean isInsideAny = false;

        for (ProtectionCoreBlockEntity core : CORES) {
            if (core.getLevel() == player.level() && core.isInside(pos)) {
                isInsideAny = true;
                break;
            }
        }

        UUID uuid = player.getUUID();
        boolean wasInside = PLAYER_INSIDE.getOrDefault(uuid, false);

        if (!wasInside && isInsideAny) {
            player.displayClientMessage(Component.literal("¡Entraste a zona protegida!").withStyle(ChatFormatting.RED), true);
        } else if (wasInside && !isInsideAny) {
            player.displayClientMessage(Component.literal("Saliste de zona protegida").withStyle(ChatFormatting.GREEN), true);
        }

        PLAYER_INSIDE.put(uuid, isInsideAny);
    }
}