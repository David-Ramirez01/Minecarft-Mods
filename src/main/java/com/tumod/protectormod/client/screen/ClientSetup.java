package com.tumod.protectormod.client.screen;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.registry.ModBlocks;
import com.tumod.protectormod.registry.ModMenus;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(
        modid = ProtectorMod.MOD_ID,
        bus = EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public class ClientSetup {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.PROTECTION_CORE_MENU.get(), ProtectionCoreScreen::new);
        event.register(ModMenus.ADMIN_CORE_MENU.get(), AdminCoreScreen::new);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Esto permite que las texturas PNG del Escudo de Oro se vean transparentes
            // CUTOUT es ideal para modelos con bordes definidos (sin degradados de transparencia)
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.PROTECTION_CORE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.ADMIN_PROTECTOR.get(), RenderType.cutout());
        });
    }
}
