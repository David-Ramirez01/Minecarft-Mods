package com.tumod.protectormod.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class ClanSavedData extends SavedData {

    public int serverMaxCores = 3;
    private final Map<String, ClanInstance> clans = new HashMap<>();

    public ClanSavedData() {}

    public static class ClanInstance {
        public String name;
        public UUID leaderUUID;
        public String leaderName;
        public BlockPos corePos;
        public int maxMembers = 8;
        public Set<UUID> members = new HashSet<>();

        public ClanInstance(String name, UUID leaderUUID, String leaderName, BlockPos pos) {
            this.name = name;
            this.leaderUUID = leaderUUID;
            this.leaderName = leaderName;
            this.corePos = pos;
            this.members.add(leaderUUID);
        }
    }

    // --- MÉTODOS PARA EL BLOQUE CORE (Creación y Conteo) ---

    public boolean tryCreateClan(String name, UUID owner, String ownerName, BlockPos pos) {
        // Si el nombre ya existe (ignorando mayúsculas) o el jugador ya es líder de otro clan, fallar
        if (clans.containsKey(name.toLowerCase()) || getClanByLeader(owner) != null) {
            return false;
        }

        clans.put(name.toLowerCase(), new ClanInstance(name, owner, ownerName, pos));
        setDirty();
        return true;
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

    // Este es un alias por si tu código usa getCoresCount
    public int getCoresCount(UUID playerUUID) {
        return getPlayerCoreCount(playerUUID);
    }

    public static ClanSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(
                        ClanSavedData::new,
                        ClanSavedData::load,
                        null), "protector_clans");
    }

    // --- MÉTODOS DE BÚSQUEDA QUE FALTABAN PARA CLANCOMMANDS ---

    public ClanInstance getClanByLeader(UUID leaderUUID) {
        return clans.values().stream()
                .filter(c -> c.leaderUUID.equals(leaderUUID))
                .findFirst().orElse(null);
    }

    public ClanInstance getClanByMember(UUID playerUUID) {
        for (ClanInstance clan : clans.values()) {
            if (clan.leaderUUID.equals(playerUUID) || clan.members.contains(playerUUID)) {
                return clan;
            }
        }
        return null;
    }

    public String getClanOfPlayer(UUID playerUUID) {
        ClanInstance clan = getClanByMember(playerUUID);
        return (clan != null) ? clan.name : "";
    }

    public ClanInstance getClan(String name) {
        return (name == null || name.isEmpty()) ? null : clans.get(name.toLowerCase());
    }

    public boolean hasClan(UUID playerUUID) {
        return getClanByMember(playerUUID) != null;
    }

    public void deleteClan(UUID ownerUUID) {
        clans.entrySet().removeIf(entry -> entry.getValue().leaderUUID.equals(ownerUUID));
        setDirty();
    }

    // --- PERSISTENCIA CORREGIDA (Guarda Miembros y Límites) ---

    public static ClanSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        ClanSavedData data = new ClanSavedData();
        data.serverMaxCores = tag.contains("MaxCoresLimit") ? tag.getInt("MaxCoresLimit") : 3;

        if (tag.contains("ClanList")) {
            CompoundTag list = tag.getCompound("ClanList");
            for (String key : list.getAllKeys()) {
                CompoundTag cTag = list.getCompound(key);
                ClanInstance clan = new ClanInstance(
                        cTag.getString("Name"),
                        cTag.getUUID("Leader"),
                        cTag.getString("LeaderName"),
                        BlockPos.of(cTag.getLong("Pos"))
                );
                // Cargar límite y miembros
                clan.maxMembers = cTag.contains("MaxMembers") ? cTag.getInt("MaxMembers") : 8;
                if (cTag.contains("MembersList")) {
                    ListTag membersTag = cTag.getList("MembersList", Tag.TAG_STRING);
                    for (int i = 0; i < membersTag.size(); i++) {
                        clan.members.add(UUID.fromString(membersTag.getString(i)));
                    }
                }
                data.clans.put(key, clan);
            }
        }
        return data;
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
            cTag.putInt("MaxMembers", clan.maxMembers);

            // Guardar lista de miembros
            ListTag membersTag = new ListTag();
            for (UUID memberUUID : clan.members) {
                membersTag.add(StringTag.valueOf(memberUUID.toString()));
            }
            cTag.put("MembersList", membersTag);

            list.put(id, cTag);
        });
        tag.put("ClanList", list);
        return tag;
    }
}