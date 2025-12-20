package com.tumod.protectormod.registry;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {

    // 1. Usamos Registries.BLOCK_ENTITY_TYPE (estándar de Minecraft/NeoForge)
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ProtectorMod.MOD_ID);

    // 2. Cambiamos RegistryObject por DeferredHolder
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ProtectionCoreBlockEntity>> PROTECTION_CORE_BE =
            BLOCK_ENTITIES.register(
                    "protection_core_be",
                    () -> BlockEntityType.Builder.of(
                            ProtectionCoreBlockEntity::new,
                            ModBlocks.PROTECTION_CORE.get()
                    ).build(null)
            );
}


