package com.tumod.protectormod.network;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ClientPayloadHandler {

    public static void handleSyncCore(SyncCoreLevelPayload payload, IPayloadContext context) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            var be = level.getBlockEntity(payload.pos());
            if (be instanceof ProtectionCoreBlockEntity core) {
                core.setCoreLevelClient(payload.level());
            }
        }
    }

    public static void handleShowArea(final ShowAreaClientPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level == null) return;

            BlockPos center = payload.pos();
            int r = payload.radius();

            // Dibujamos un "anillo" de partículas en el borde del radio
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    // Dibujamos solo si es el borde exterior para no saturar de partículas
                    if (Math.abs(x) == r || Math.abs(z) == r) {
                        double px = center.getX() + x + 0.5;
                        double pz = center.getZ() + z + 0.5;

                        // Añadimos partículas en varias alturas para que se vea como una barrera
                        for (int yOffset = 0; yOffset <= 2; yOffset++) {
                            level.addParticle(
                                    ParticleTypes.HAPPY_VILLAGER,
                                    px, center.getY() + yOffset + 0.5, pz,
                                    0, 0, 0
                            );
                        }
                    }
                }
            }
        });
    }


}
