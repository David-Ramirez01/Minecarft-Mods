package com.tumod.protectormod.registry;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.block.ProtectionCoreBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.Registries;

public class ModBlocks {

    // CORRECCIÓN 1: Solo se necesita <Block>
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, ProtectorMod.MOD_ID);

    // Bloque Protection Core
    public static final DeferredHolder<Block, ProtectionCoreBlock> PROTECTION_CORE = BLOCKS.register("protection_core",
            () -> new ProtectionCoreBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.0f, 1200.0f)
                            .lightLevel(state -> 10)
            ));
}
