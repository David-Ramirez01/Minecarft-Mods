package com.tumod.protectormod.protection;

import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.util.ProtectionDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ProtectionUtils {

    /**
     * Busca si existe un núcleo de protección que cubra la posición dada.
     * Migrado para usar ProtectionDataManager (Evita lag por iteración masiva).
     */
    public static ProtectionCoreBlockEntity getCoreAt(LevelAccessor accessor, BlockPos pos) {
        // El Manager solo funciona en el lado del servidor (ServerLevel)
        if (!(accessor instanceof ServerLevel sLevel)) {
            return null;
        }

        // 1. Buscamos en el Manager si hay alguna protección registrada en esta coordenada
        ProtectionDataManager manager = ProtectionDataManager.get(sLevel);
        ProtectionDataManager.CoreEntry entry = manager.getCoreAt(pos);

        if (entry != null) {
            // 2. Si hay una entrada, obtenemos la BlockEntity física para verificar permisos/flags
            BlockEntity be = sLevel.getBlockEntity(entry.pos());

            if (be instanceof ProtectionCoreBlockEntity core) {
                // 3. Verificamos que el área realmente lo cubra (doble check de radio)
                if (!core.isRemoved() && core.isInside(pos)) {
                    return core;
                }
            }
        }

        return null;
    }
}


