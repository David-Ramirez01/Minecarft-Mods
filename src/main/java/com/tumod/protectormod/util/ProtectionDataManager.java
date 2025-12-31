package com.tumod.protectormod.util;

import com.tumod.protectormod.network.SyncProtectionPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ProtectionDataManager extends SavedData {
    // Mapa principal: Posición del bloque físico -> Datos del núcleo
    private final Map<BlockPos, CoreEntry> allCores = new HashMap<>();
    private int globalLimit = 1;

    // DEFINICIÓN ÚNICA DEL RECORD: Incluye la posición para que Utils pueda leer entry.pos()
    public record CoreEntry(BlockPos pos, UUID owner, int radius) {}

    public void addCore(BlockPos pos, UUID owner, int radius) {
        allCores.put(pos, new CoreEntry(pos, owner, radius));
        this.setDirty();
    }

    // Este es el método que le faltaba al ServerPayloadHandler
    public void addOrUpdateCore(BlockPos pos, UUID owner, int radius) {
        // Reutilizamos la lógica: si ya existe la posición, el HashMap la reemplaza (actualiza)
        allCores.put(pos, new CoreEntry(pos, owner, radius));
        this.setDirty();
    }

    public void removeCore(BlockPos pos) {
        if (allCores.remove(pos) != null) {
            this.setDirty();
        }
    }

    public Map<BlockPos, CoreEntry> getAllCores() {
        return allCores;
    }

    /**
     * Este es el método que busca si una posición cualquiera está dentro de alguna protección.
     */
    public CoreEntry getCoreAt(BlockPos targetPos) {
        for (CoreEntry entry : allCores.values()) {
            BlockPos corePos = entry.pos();
            int radius = entry.radius();

            // LÓGICA CUADRADA: Comprobamos si targetPos está dentro del rango X y Z
            boolean withinX = Math.abs(targetPos.getX() - corePos.getX()) <= radius;
            boolean withinZ = Math.abs(targetPos.getZ() - corePos.getZ()) <= radius;

            // PROTECCIÓN VERTICAL COMPLETA:
            // Simplemente no comprobamos la Y. Si X y Z coinciden, toda la columna está protegida.
            if (withinX && withinZ) {
                return entry;
            }
        }
        return null;
    }

    // --- PERSISTENCIA ---

    public static ProtectionDataManager load(CompoundTag tag, HolderLookup.Provider registries) {
        ProtectionDataManager data = new ProtectionDataManager();

        // 1. Cargar el límite global (Fuera del bucle de los núcleos)
        if (tag.contains("globalLimit")) {
            data.globalLimit = tag.getInt("globalLimit");
        }

        // 2. Cargar los núcleos
        ListTag list = tag.getList("Cores", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            BlockPos pos = BlockPos.of(entryTag.getLong("pos"));
            UUID owner = entryTag.getUUID("owner");
            int radius = entryTag.getInt("radius");
            data.allCores.put(pos, new CoreEntry(pos, owner, radius));
        }
        return data;
    }

    public int getGlobalLimit() { return globalLimit; }
    public void setGlobalLimit(int limit) {
        this.globalLimit = limit;
        this.setDirty(); // Importante: marca que hay que guardar el archivo
    }


    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        allCores.forEach((pos, entry) -> {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putLong("pos", pos.asLong());
            entryTag.putUUID("owner", entry.owner());
            entryTag.putInt("radius", entry.radius());
            list.add(entryTag);
        });
        tag.put("Cores", list);
        tag.putInt("globalLimit", globalLimit);
        return tag;
    }

    public void syncToAll(ServerLevel level) {
        SyncProtectionPayload packet = new SyncProtectionPayload(new HashMap<>(this.allCores));
        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            PacketDistributor.sendToPlayer(player, packet);
        }
    }

    public static ProtectionDataManager get(Level level) {
        if (level instanceof ServerLevel serverLevel) {
            // Lado Servidor: Usamos la Factory para cargar/crear
            return serverLevel.getDataStorage().computeIfAbsent(
                    new SavedData.Factory<>(
                            ProtectionDataManager::new,
                            ProtectionDataManager::load,
                            null // Opcional: DataFixer
                    ),
                    "protection_data"
            );
        } else {
            // Lado Cliente: Usamos una instancia única para el cliente
            return ClientData.INSTANCE;
        }
    }

    // Clase interna simple para manejar los datos en el cliente
    private static class ClientData {
        private static final ProtectionDataManager INSTANCE = new ProtectionDataManager();
        public static ProtectionDataManager getInstance() {
            return INSTANCE;
        }
    }
}