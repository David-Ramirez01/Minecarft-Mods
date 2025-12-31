package com.tumod.protectormod;

import com.tumod.protectormod.network.*;
import com.tumod.protectormod.registry.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import com.tumod.protectormod.registry.*;

@Mod(ProtectorMod.MOD_ID)
public class ProtectorMod {
    public static final String MOD_ID = "protectormod";

    public ProtectorMod(IEventBus bus) {
        // Registros de contenido
        ModBlocks.BLOCKS.register(bus);
        ModItems.ITEMS.register(bus);
        ModBlockEntities.BLOCK_ENTITIES.register(bus);
        ModMenus.MENUS.register(bus);
        ModCreativeTabs.TABS.register(bus);
        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);

        // Registro de red
        bus.addListener(this::registerNetworking);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        // Si tu clase se llama ClanCommands:
        com.tumod.protectormod.command.ClanCommands.register(event.getDispatcher());
    }

    private void registerNetworking(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(MOD_ID);

        // Cliente -> Servidor (Usamos ServerPayloadHandler)
        registrar.playToServer(
                UpgradeCorePayload.TYPE,
                UpgradeCorePayload.CODEC,
                ServerPayloadHandler::handleUpgrade
        );
        registrar.playToServer(
                ShowAreaPayload.TYPE,
                ShowAreaPayload.CODEC,
                ServerPayloadHandler::handleShowArea
        );

        registrar.playToClient(
                ShowAreaClientPayload.TYPE,
                ShowAreaClientPayload.CODEC,
                ClientPayloadHandler::handleShowArea
        );
        registrar.playToClient(
                SyncCoreLevelPayload.TYPE,
                SyncCoreLevelPayload.CODEC,
                ClientPayloadHandler::handleSyncCore
        );
        registrar.playToServer(
                ChangePermissionPayload.TYPE,
                ChangePermissionPayload.CODEC,
                ServerPayloadHandler::handleChangePermission
        );
        registrar.playToServer(
                UpdateAdminCorePayload.TYPE,
                UpdateAdminCorePayload.CODEC,
                ServerPayloadHandler::handleAdminUpdate
        );
        registrar.playToServer(
                CreateClanPayload.TYPE,
                CreateClanPayload.CODEC,
                ServerPayloadHandler::handleCreateClan
        );
        registrar.playToServer(
                UpdateFlagPayload.TYPE,
                UpdateFlagPayload.CODEC,
                ServerPayloadHandler::handleUpdateFlag
        );
        registrar.playToClient(
                SyncProtectionPayload.TYPE,
                SyncProtectionPayload.STREAM_CODEC, // <--- Cambiado de .CODEC a .STREAM_CODEC
                (payload, context) -> {
                    context.enqueueWork(() -> {
                        // Actualizamos la instancia del cliente con los nuevos datos
                        // Usamos el método 'get' que modificamos antes para el cliente
                        var data = com.tumod.protectormod.util.ProtectionDataManager.get(context.player().level());
                        data.getAllCores().clear();
                        data.getAllCores().putAll(payload.allCores());

                        // Opcional: Log para verificar en consola que llegan los datos
                        // ProtectorMod.LOGGER.info("Sincronizados {} núcleos de protección", payload.allCores().size());
                    });
                }
        );

    }
}
