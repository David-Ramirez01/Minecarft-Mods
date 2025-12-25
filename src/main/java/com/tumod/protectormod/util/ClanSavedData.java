package com.tumod.protectormod.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClanSavedData extends SavedData {

    public int serverMaxCores = 3;
    // Mapa de Nombre (Lower) -> Datos del Clan
    private final Map<String, ClanInstance> clans = new HashMap<>();

    public ClanSavedData() {}

    // --- CLASE INTERNA PARA DATOS COMPLETOS ---
    public static class ClanInstance {
        public String name;
        public UUID leaderUUID;
        public String leaderName; // Guardamos el nombre para el comando info
        public BlockPos corePos;


        public ClanInstance(String name, UUID leaderUUID, String leaderName, BlockPos pos) {
            this.name = name;
            this.leaderUUID = leaderUUID;
            this.leaderName = leaderName;
            this.corePos = pos;
        }
    }

    public static ClanSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(
                        ClanSavedData::new,
                        ClanSavedData::load,
                        null), "protector_clans");
    }

    public boolean tryCreateClan(String name, UUID owner, String ownerName, BlockPos pos) {
        if (clans.containsKey(name.toLowerCase()) || hasClan(owner)) return false;

        clans.put(name.toLowerCase(), new ClanInstance(name, owner, ownerName, pos));
        setDirty();
        return true;
    }

    public Collection<String> getAllClanNames() {
        return clans.values().stream().map(c -> c.name).toList();
    }

    public ClanInstance getClan(String name) {
        return clans.get(name.toLowerCase());
    }

    public String getClanOfPlayer(UUID playerUUID) {
        return clans.values().stream()
                .filter(c -> c.leaderUUID.equals(playerUUID))
                .map(c -> c.name)
                .findFirst().orElse("");
    }

    public boolean hasClan(UUID playerUUID) {
        return clans.values().stream().anyMatch(c -> c.leaderUUID.equals(playerUUID));
    }

    public void deleteClan(UUID ownerUUID) {
        clans.entrySet().removeIf(entry -> entry.getValue().leaderUUID.equals(ownerUUID));
        setDirty();
    }

    // --- PERSISTENCIA  ---
    public static ClanSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        ClanSavedData data = new ClanSavedData();

        // Leemos el límite. Si no existe, por defecto será 3.
        data.serverMaxCores = tag.contains("MaxCoresLimit") ? tag.getInt("MaxCoresLimit") : 3;

        if (tag.contains("ClanList")) {
            CompoundTag list = tag.getCompound("ClanList");
            for (String key : list.getAllKeys()) {
                CompoundTag cTag = list.getCompound(key);
                data.clans.put(key, new ClanInstance(
                        cTag.getString("Name"),
                        cTag.getUUID("Leader"),
                        cTag.getString("LeaderName"),
                        BlockPos.of(cTag.getLong("Pos"))
                ));
            }
        }
        return data;
    }

    public int getCoresCount(UUID playerUUID) {
        int count = 0;
        for (ClanInstance clan : clans.values()) {
            if (clan.leaderUUID.equals(playerUUID)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("MaxCoresLimit", serverMaxCores);
        CompoundTag list = new CompoundTag();
        clans.forEach((id, clan) -> {
            CompoundTag cTag = new CompoundTag();
            cTag.putString("Name", clan.name);
            cTag.putUUID("Leader", clan.leaderUUID);
            cTag.putString("LeaderName", clan.leaderName);
            cTag.putLong("Pos", clan.corePos.asLong());
            list.put(id, cTag);
        });
        tag.put("ClanList", list);
        return tag;
    }

    public boolean isLeader(String clanName, UUID playerUUID) {
        if (clanName == null || clanName.isEmpty()) return false;

        // Buscamos el objeto clan por su nombre (llave del mapa)
        ClanInstance clan = this.clans.get(clanName.toLowerCase());

        // Verificamos si existe y si el UUID coincide con el líder guardado
        return clan != null && clan.leaderUUID.equals(playerUUID);
    }

    public int getPlayerCoreCount(UUID playerUUID) {
        int count = 0;
        for (ClanInstance clan : clans.values()) {
            if (clan.leaderUUID.equals(playerUUID)) {
                count++;
            }
        }
        return count;
    }
}
