package com.tumod.protectormod.registry;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.item.ProtectionUpgradeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ProtectorMod.MOD_ID);

    // Item del Core normal
    public static final DeferredItem<BlockItem> PROTECTION_CORE_ITEM = ITEMS.register(
            "protection_core",
            () -> new BlockItem(ModBlocks.PROTECTION_CORE.get(), new Item.Properties()
                    .stacksTo(1)
                    .fireResistant()
            )
    );

    // Item del Admin Protector
    public static final DeferredItem<BlockItem> ADMIN_PROTECTOR_ITEM = ITEMS.register(
            "admin_protector",
            () -> new BlockItem(ModBlocks.ADMIN_PROTECTOR.get(), new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC))
    );

    // Item de Mejora
    public static final DeferredItem<ProtectionUpgradeItem> PROTECTION_UPGRADE = ITEMS.register(
            "protection_upgrade",
            () -> new ProtectionUpgradeItem(new Item.Properties()
                    .stacksTo(16)
                    .rarity(Rarity.RARE)
            )
    );
}

