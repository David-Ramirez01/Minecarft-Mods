package com.tumod.protectormod.registry;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.block.AdminProtectorBlock;
import com.tumod.protectormod.block.ProtectionCoreBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, ProtectorMod.MOD_ID);

    // Bloque Protection Core
    public static final DeferredHolder<Block, ProtectionCoreBlock> PROTECTION_CORE = BLOCKS.register("protection_core",
            () -> new ProtectionCoreBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.0f, 1200.0f)
                            .noOcclusion() // <--- CORRECTO
                            .lightLevel(state -> 10)
                            .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
            ));

    // Bloque Admin Protector - CORREGIDO
    public static final DeferredHolder<Block, AdminProtectorBlock> ADMIN_PROTECTOR = BLOCKS.register("admin_protector",
            () -> new AdminProtectorBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.GOLD)
                            .strength(-1.0f, 3600000.0f) // Indestructible
                            .noOcclusion() // <--- AÑADIDO: Vital para la estatua de oro
                            .lightLevel(state -> 15)
            ));
}