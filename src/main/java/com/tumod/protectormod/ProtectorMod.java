package com.tumod.protectormod;

import com.tumod.protectormod.network.*;
import com.tumod.protectormod.registry.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
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

        // Registro de red
        bus.addListener(this::registerNetworking);
    }

    private void registerNetworking(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(MOD_ID).versioned("1.0.0");

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

        // Servidor -> Cliente (Usamos un ClientPayloadHandler para las partículas y sincronización)
        registrar.playToClient(
                ShowAreaClientPayload.TYPE,
                ShowAreaClientPayload.CODEC,
                ClientPayloadHandler::handleShowArea
        );
        registrar.playToClient(
                SyncCoreLevelPayload.TYPE,
                SyncCoreLevelPayload.CODEC,
                ClientPayloadHandler::handleSyncCore // Esta es la forma más limpia en 1.21.1
        );
    }
}
