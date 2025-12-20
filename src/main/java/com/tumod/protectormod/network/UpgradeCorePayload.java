package com.tumod.protectormod.network;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.menu.ProtectionCoreMenu;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UpgradeCorePayload() implements CustomPacketPayload {

    public static final Type<UpgradeCorePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ProtectorMod.MOD_ID, "upgrade_core"));

    // Usamos FriendlyByteBuf porque no estamos enviando datos complejos de registros
    public static final StreamCodec<FriendlyByteBuf, UpgradeCorePayload> CODEC =
            StreamCodec.unit(new UpgradeCorePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final UpgradeCorePayload payload, final IPayloadContext context) {
        // context.enqueueWork asegura que esto corra en el hilo principal del servidor
        context.enqueueWork(() -> {
            var player = context.player();

            // Verificación de seguridad: El jugador debe tener el menú abierto
            if (player.containerMenu instanceof ProtectionCoreMenu menu) {
                ProtectionCoreBlockEntity core = menu.getCore();

                // Validamos que el core exista, no esté roto y el jugador tenga permisos
                if (core != null && !core.isRemoved() && core.isTrusted(player)) {
                    if (core.canUpgrade()) {
                        core.upgrade();
                        // Nota: El método core.upgrade() debería llamar a setChanged()
                        // para sincronizar los cambios con el cliente.
                    }
                }
            }
        });
    }
}



