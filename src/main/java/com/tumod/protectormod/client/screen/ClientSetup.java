package com.tumod.protectormod.client.screen;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.registry.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(
        modid = ProtectorMod.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public class ClientSetup {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        // Pantalla Normal
        event.register(ModMenus.PROTECTION_CORE_MENU.get(), ProtectionCoreScreen::new);

        // Pantalla Admin - Ahora sí se abrirá siempre que el menú sea ADMIN_CORE_MENU
        event.register(ModMenus.ADMIN_CORE_MENU.get(), AdminCoreScreen::new);
    }
}
