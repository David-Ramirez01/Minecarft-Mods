package com.tumod.protectormod.protection;

import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

public class ProtectionUtils {

    /**
     * Busca si existe un núcleo de protección que cubra la posición dada.
     */
    public static ProtectionCoreBlockEntity getCoreAt(LevelAccessor accessor, BlockPos pos) {
        // En 1.21.1, LevelAccessor es la forma segura de manejar mundos en eventos
        if (!(accessor instanceof Level level)) {
            return null;
        }

        // Iteramos sobre la lista estática global de núcleos cargados
        for (ProtectionCoreBlockEntity core : ProtectionCoreBlockEntity.CORES) {

            // 1. Verificar que el core sigue siendo válido y está en el mismo nivel
            if (core.isRemoved() || core.getLevel() != level) {
                continue;
            }

            // 2. Usar la lógica de bounds del Core para verificar si la posición está dentro
            // Esto asegura que si cambias la forma del área en el Core, se aplique aquí también
            if (core.isInside(pos)) {
                return core;
            }
        }
        return null;
    }
}


