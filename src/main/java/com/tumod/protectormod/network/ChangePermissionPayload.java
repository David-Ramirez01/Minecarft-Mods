package com.tumod.protectormod.network;

import com.tumod.protectormod.ProtectorMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ChangePermissionPayload(BlockPos pos, String playerName, String permissionType, boolean value) implements CustomPacketPayload {

    public static final Type<ChangePermissionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ProtectorMod.MOD_ID, "change_permission"));

    public static final StreamCodec<ByteBuf, ChangePermissionPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ChangePermissionPayload::pos,
            ByteBufCodecs.STRING_UTF8, ChangePermissionPayload::playerName,
            ByteBufCodecs.STRING_UTF8, ChangePermissionPayload::permissionType,
            ByteBufCodecs.BOOL, ChangePermissionPayload::value,
            ChangePermissionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}