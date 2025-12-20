package com.tumod.protectormod.registry;

import com.tumod.protectormod.ProtectorMod;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.tumod.protectormod.item.ProtectionUpgradeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

public class ModItems {

    // Asegúrate de que diga DeferredRegister.Items
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ProtectorMod.MOD_ID);

    // Si registerBlockItem te da error, usa esta sintaxis manual:
    public static final DeferredItem<BlockItem> PROTECTION_CORE_ITEM = ITEMS.register(
            "protection_core",
            () -> new BlockItem(ModBlocks.PROTECTION_CORE.get(), new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<ProtectionUpgradeItem> PROTECTION_UPGRADE = ITEMS.register(
            "protection_upgrade",
            () -> new ProtectionUpgradeItem(new Item.Properties()
                    .stacksTo(16)      // Solo 16 por stack para que sea valioso
                    .rarity(Rarity.RARE) // Color azul en el nombre
            )
    );
}

