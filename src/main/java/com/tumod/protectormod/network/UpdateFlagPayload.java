package com.tumod.protectormod.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpdateFlagPayload(BlockPos pos, String flag) implements CustomPacketPayload {

    public static final Type<UpdateFlagPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("protectormod", "update_flag"));


    public static final StreamCodec<FriendlyByteBuf, UpdateFlagPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, UpdateFlagPayload::pos,
            ByteBufCodecs.STRING_UTF8, UpdateFlagPayload::flag,
            UpdateFlagPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
