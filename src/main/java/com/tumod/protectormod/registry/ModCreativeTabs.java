package com.tumod.protectormod.registry;

import com.tumod.protectormod.ProtectorMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ProtectorMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> PROTECTOR_TAB =
            TABS.register("protector_tab", () ->
                    CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.protector_tab"))
                            .icon(() -> new ItemStack(ModItems.PROTECTION_CORE_ITEM.get()))
                            .displayItems((params, output) -> {
                                // 1. Primero el bloque de Admin (El más importante)
                                output.accept(ModItems.ADMIN_PROTECTOR_ITEM.get());

                                // 2. Luego el Core normal
                                output.accept(ModItems.PROTECTION_CORE_ITEM.get());

                                // 3. Por último el ítem de mejora
                                output.accept(ModItems.PROTECTION_UPGRADE.get());
                            })
                            .build()
            );
}

