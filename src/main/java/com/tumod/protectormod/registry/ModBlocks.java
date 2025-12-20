package com.tumod.protectormod.registry;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.block.AdminProtectorBlock;
import com.tumod.protectormod.block.ProtectionCoreBlock;
import com.tumod.protectormod.blockentity.AdminProtectorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.minecraft.core.registries.Registries;

import static com.tumod.protectormod.registry.ModBlockEntities.BLOCK_ENTITIES;

public class ModBlocks {

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
    public static final DeferredHolder<Block, AdminProtectorBlock>
            ADMIN_PROTECTOR = BLOCKS.register("admin_protector", AdminProtectorBlock::new);
}
