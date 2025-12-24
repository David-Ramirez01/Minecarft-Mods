package com.tumod.protectormod.registry;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.menu.ProtectionCoreMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ProtectorMod.MOD_ID);

    // Menú para el Core Normal
    public static final DeferredHolder<MenuType<?>, MenuType<ProtectionCoreMenu>> PROTECTION_CORE_MENU =
            MENUS.register("protection_core_menu",
                    () -> IMenuTypeExtension.create(ProtectionCoreMenu::new));

    // NUEVO: Menú para el Admin Core
    public static final DeferredHolder<MenuType<?>, MenuType<ProtectionCoreMenu>> ADMIN_CORE_MENU =
            MENUS.register("admin_core_menu",
                    () -> IMenuTypeExtension.create(ProtectionCoreMenu::createAdminMenu));
}

