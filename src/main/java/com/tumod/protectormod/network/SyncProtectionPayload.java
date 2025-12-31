package com.tumod.protectormod.network;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.util.ProtectionDataManager.CoreEntry;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public record SyncProtectionPayload(Map<BlockPos, CoreEntry> allCores) implements CustomPacketPayload {

    public static final Type<SyncProtectionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ProtectorMod.MOD_ID, "sync_protections"));

    // 1. Definimos CÓMO se envía un CoreEntry individual (Posición, UUID y Radio)
    public static final StreamCodec<ByteBuf, CoreEntry> CORE_ENTRY_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CoreEntry::pos,
            UUIDUtil.STREAM_CODEC, CoreEntry::owner,
            ByteBufCodecs.VAR_INT, CoreEntry::radius,
            CoreEntry::new
    );

    // 2. Definimos CÓMO se envía el Payload completo (el Mapa)
    public static final StreamCodec<ByteBuf, SyncProtectionPayload> STREAM_CODEC = StreamCodec.composite(
            // Usamos el CORE_ENTRY_CODEC que acabamos de crear arriba
            ByteBufCodecs.map(HashMap::new, BlockPos.STREAM_CODEC, CORE_ENTRY_CODEC),
            SyncProtectionPayload::allCores, // Debe coincidir con el nombre del campo en el record
            SyncProtectionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}