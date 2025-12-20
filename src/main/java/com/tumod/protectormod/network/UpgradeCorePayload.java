package com.tumod.protectormod.network;

import com.tumod.protectormod.ProtectorMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record UpgradeCorePayload() implements CustomPacketPayload {

    public static final Type<UpgradeCorePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ProtectorMod.MOD_ID, "upgrade_core"));

    public static final StreamCodec<ByteBuf, UpgradeCorePayload> CODEC =
            StreamCodec.unit(new UpgradeCorePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}



