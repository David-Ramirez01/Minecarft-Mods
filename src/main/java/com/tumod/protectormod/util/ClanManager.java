package com.tumod.protectormod.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClanManager extends SavedData {
    private final Map<String, UUID> clanOwners = new HashMap<>();

    public static ClanManager get(ServerLevel level) {
        // CORRECCIÓN: Se añade el HolderLookup.Provider al Factory
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(
                                ClanManager::new,
                                ClanManager::load,
                                null), // El tercer argumento es opcional (DataFixTypes)
                        "protector_clans");
    }

    // CORRECCIÓN: Firma del método load actualizada
    public static ClanManager load(CompoundTag tag, HolderLookup.Provider provider) {
        ClanManager data = new ClanManager();
        CompoundTag clans = tag.getCompound("Clans");
        for (String key : clans.getAllKeys()) {
            data.clanOwners.put(key, clans.getUUID(key));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag clans = new CompoundTag();
        clanOwners.forEach(clans::putUUID);
        tag.put("Clans", clans);
        return tag;
    }

    public boolean createClan(String name, UUID owner) {
        if (clanOwners.containsKey(name.toLowerCase()) || clanOwners.containsValue(owner)) {
            return false;
        }
        clanOwners.put(name.toLowerCase(), owner);
        setDirty();
        return true;
    }
}