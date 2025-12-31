package com.tumod.protectormod.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpgradeCorePayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<UpgradeCorePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("protectormod", "upgrade_core"));

    // Codec para leer y escribir en el buffer de red
    public static final StreamCodec<FriendlyByteBuf, UpgradeCorePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, UpgradeCorePayload::pos,
            UpgradeCorePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}



