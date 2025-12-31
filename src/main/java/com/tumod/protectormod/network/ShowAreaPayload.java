package com.tumod.protectormod.network;

import com.tumod.protectormod.ProtectorMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ShowAreaPayload(BlockPos pos, int radius) implements CustomPacketPayload {

    public static final Type<ShowAreaPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ProtectorMod.MOD_ID, "show_area"));

    public static final StreamCodec<FriendlyByteBuf, ShowAreaPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ShowAreaPayload::pos,
            net.minecraft.network.codec.ByteBufCodecs.VAR_INT, ShowAreaPayload::radius,
            ShowAreaPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Lógica en el Servidor
    public static void handle(final ShowAreaPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();

        });
    }
}





