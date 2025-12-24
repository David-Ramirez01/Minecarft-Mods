package com.tumod.protectormod.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClanSavedData extends SavedData {
    // Unificamos en un solo mapa de Nombre -> UUID del Dueño
    private final Map<String, UUID> clans = new HashMap<>();

    // Constructor vacío requerido para el Factory
    public ClanSavedData() {}

    public static ClanSavedData get(ServerLevel level) {
        // CORRECCIÓN: El Factory requiere el Provider de registros en versiones modernas
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(
                        ClanSavedData::new,
                        ClanSavedData::load,
                        null), "protector_clans");
    }

    public boolean tryCreateClan(String name, UUID owner) {
        String lowerName = name.toLowerCase();
        // Verificamos si el nombre está ocupado o si el jugador ya es dueño de uno
        if (clans.containsKey(lowerName) || clans.containsValue(owner)) {
            return false;
        }
        clans.put(lowerName, owner);
        setDirty(); // Marca para guardar en disco
        return true;
    }

    public boolean isLeader(String clanName, UUID playerUUID) {
        if (clanName == null || clanName.isEmpty()) return false;

        // Suponiendo que usas un mapa donde la llave es el nombre del clan
        // y el valor es el UUID del creador.
        UUID leaderUUID = this.clans.get(clanName);

        return leaderUUID != null && leaderUUID.equals(playerUUID);
    }

    // CORRECCIÓN: Se añade HolderLookup.Provider
    public static ClanSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        ClanSavedData data = new ClanSavedData();
        CompoundTag list = tag.getCompound("ClanList");
        for (String key : list.getAllKeys()) {
            data.clans.put(key, list.getUUID(key));
        }
        return data;
    }

    // En ClanSavedData.java

    // Método para eliminar el clan por completo
    public boolean deleteClan(UUID ownerUUID) {
        // Buscamos si este UUID es dueño de algún clan
        String clanToRemove = null;
        for (Map.Entry<String, UUID> entry : clans.entrySet()) {
            if (entry.getValue().equals(ownerUUID)) {
                clanToRemove = entry.getKey();
                break;
            }
        }

        if (clanToRemove != null) {
            clans.remove(clanToRemove);
            setDirty();
            return true;
        }
        return false;
    }

    // Método para verificar si alguien ya tiene clan (Dueño o miembro)
    public boolean hasClan(UUID playerUUID) {
        // Verificamos si es dueño
        if (clans.containsValue(playerUUID)) return true;

        // Si tienes un sistema de miembros aparte, aquí checarías si está en alguna lista de invitados
        return false;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag list = new CompoundTag();
        clans.forEach(list::putUUID);
        tag.put("ClanList", list);
        return tag;
    }

    public String getClanOfPlayer(UUID playerUUID) {
        return clans.entrySet().stream()
                .filter(entry -> entry.getValue().equals(playerUUID))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("");
    }
}
