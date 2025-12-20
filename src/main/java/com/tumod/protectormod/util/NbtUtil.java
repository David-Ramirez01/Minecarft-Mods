package com.tumod.protectormod.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class NbtUtil {

    // Guardar un UUID de forma segura
    public static void putUUID(CompoundTag tag, String key, UUID uuid) {
        if (uuid != null) {
            tag.putUUID(key, uuid);
        }
    }

    // Leer un UUID con verificación
    public static UUID getUUID(CompoundTag tag, String key) {
        return (tag != null && tag.hasUUID(key)) ? tag.getUUID(key) : null;
    }

    // Guardar Set de UUIDs (Trusted Players)
    public static void putUUIDSet(CompoundTag tag, String key, Set<UUID> uuids) {
        ListTag list = new ListTag();
        for (UUID uuid : uuids) {
            CompoundTag uuidTag = new CompoundTag();
            uuidTag.putUUID("id", uuid); // Cambiado a "id" por convención de Minecraft
            list.add(uuidTag);
        }
        tag.put(key, list);
    }

    // Leer como lista para cargar en la BE
    public static List<UUID> getUUIDList(CompoundTag tag, String key) {
        List<UUID> uuids = new ArrayList<>();
        // Tag.TAG_COMPOUND es 10, lo cual es correcto
        if (tag.contains(key, Tag.TAG_LIST)) {
            ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                if (entry.hasUUID("id")) {
                    uuids.add(entry.getUUID("id"));
                }
            }
        }
        return uuids;
    }
}