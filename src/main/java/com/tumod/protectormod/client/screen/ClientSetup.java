package com.tumod.protectormod.client.screen;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.menu.ProtectionCoreMenu;
import com.tumod.protectormod.registry.ModMenus;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
        // Especificamos explícitamente los tipos <ProtectionCoreMenu, AbstractContainerScreen<ProtectionCoreMenu>>
        event.<ProtectionCoreMenu, AbstractContainerScreen<ProtectionCoreMenu>>register(
                ModMenus.PROTECTION_CORE_MENU.get(),
                (menu, inventory, title) -> {
                    var core = menu.getCore();

                    // Verificamos si es el bloque de Admin
                    if (core.getBlockState().is(com.tumod.protectormod.registry.ModBlocks.ADMIN_PROTECTOR.get())) {
                        return new AdminCoreScreen(menu, inventory, title);
                    }

                    // Si no, devolvemos la pantalla normal
                    return new ProtectionCoreScreen(menu, inventory, title);
                }
        );
    }
}
