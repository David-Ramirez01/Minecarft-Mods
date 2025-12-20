package com.tumod.protectormod.network;

import com.tumod.protectormod.ProtectorMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncCoreLevelPayload(BlockPos pos, int level) implements CustomPacketPayload {

    public static final Type<SyncCoreLevelPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ProtectorMod.MOD_ID, "sync_core_level"));

    // Usamos los codecs integrados de Minecraft para que sea más limpio y menos propenso a errores
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncCoreLevelPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SyncCoreLevelPayload::pos,
            ByteBufCodecs.VAR_INT, SyncCoreLevelPayload::level,
            SyncCoreLevelPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

}

