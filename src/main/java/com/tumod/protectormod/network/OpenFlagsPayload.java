package com.tumod.protectormod.network;

import com.tumod.protectormod.ProtectorMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenFlagsPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<OpenFlagsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ProtectorMod.MOD_ID, "open_flags"));

    public static final StreamCodec<ByteBuf, OpenFlagsPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenFlagsPayload::pos,
            OpenFlagsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
