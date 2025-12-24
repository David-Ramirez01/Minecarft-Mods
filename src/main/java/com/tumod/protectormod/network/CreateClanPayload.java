package com.tumod.protectormod.network;

import com.tumod.protectormod.ProtectorMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CreateClanPayload(BlockPos pos, String clanName) implements CustomPacketPayload {

    public static final Type<CreateClanPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ProtectorMod.MOD_ID, "create_clan"));

    public static final StreamCodec<ByteBuf, CreateClanPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CreateClanPayload::pos,
            ByteBufCodecs.STRING_UTF8, CreateClanPayload::clanName,
            CreateClanPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
