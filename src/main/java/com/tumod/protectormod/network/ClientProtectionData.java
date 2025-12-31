package com.tumod.protectormod.network;

import com.tumod.protectormod.util.ProtectionDataManager.CoreEntry;
import net.minecraft.core.BlockPos;
import java.util.HashMap;
import java.util.Map;

public class ClientProtectionData {
    // Aquí el cliente guarda la copia local de lo que envió el servidor
    private static Map<BlockPos, CoreEntry> clientCores = new HashMap<>();

    public static void update(Map<BlockPos, CoreEntry> data) {
        clientCores = data;
    }

    public static Map<BlockPos, CoreEntry> getCores() {
        return clientCores;
    }
}