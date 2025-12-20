package com.tumod.protectormod.network;

import com.tumod.protectormod.ProtectorMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf; // Cambiado
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ShowAreaClientPayload(BlockPos pos, int radius) implements CustomPacketPayload {

    public static final Type<ShowAreaClientPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ProtectorMod.MOD_ID, "show_area_client"));

    // Usamos RegistryFriendlyByteBuf para que coincida con lo que espera NeoForge
    public static final StreamCodec<RegistryFriendlyByteBuf, ShowAreaClientPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ShowAreaClientPayload::pos,
            ByteBufCodecs.VAR_INT, ShowAreaClientPayload::radius,
            ShowAreaClientPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

