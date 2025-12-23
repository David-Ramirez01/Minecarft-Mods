package com.tumod.protectormod.network;

import com.tumod.protectormod.ProtectorMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpdateAdminCorePayload(BlockPos pos, int radius, boolean pvp, boolean explosions) implements CustomPacketPayload {

    // En UpdateAdminCorePayload.java
    public static final Type<UpdateAdminCorePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("protectormod", "update_admin_core"));

    public static final StreamCodec<FriendlyByteBuf, UpdateAdminCorePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, UpdateAdminCorePayload::pos,
            ByteBufCodecs.VAR_INT, UpdateAdminCorePayload::radius,
            ByteBufCodecs.BOOL, UpdateAdminCorePayload::pvp,
            ByteBufCodecs.BOOL, UpdateAdminCorePayload::explosions,
            UpdateAdminCorePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}