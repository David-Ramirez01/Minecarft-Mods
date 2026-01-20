package com.tumod.protectormod.client.screen;

import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.network.UpdateFlagPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.List;

public class FlagsScreen extends Screen {
    private final ProtectionCoreBlockEntity core;
    private final Screen lastScreen;

    public FlagsScreen(Screen lastScreen, ProtectionCoreBlockEntity core) {
        super(Component.literal("Configuración de Zona"));
        this.lastScreen = lastScreen;
        this.core = core;
    }

    @Override
    protected void init() {
        int startX = this.width / 2 - 145; // Centrado para dos columnas
        int startY = 45;
        int columnWidth = 150;

        // --- UNIFICACIÓN DE TODAS LAS FLAGS ---
        List<String> allFlags = new ArrayList<>();
        allFlags.addAll(ProtectionCoreBlockEntity.BASIC_FLAGS);
        allFlags.addAll(ProtectionCoreBlockEntity.ADMIN_FLAGS);

        // --- RENDERIZADO DINÁMICO EN COLUMNAS ---
        for (int i = 0; i < allFlags.size(); i++) {
            String flagId = allFlags.get(i);

            // Calculamos columna (0 o 1) y fila
            int column = i % 2;
            int row = i / 2;

            int posX = startX + (column * columnWidth);
            int posY = startY + (row * 22);

            createFlagButton(flagId, posX, posY);
        }

        // Botón Volver
        this.addRenderableWidget(Button.builder(Component.literal("§lVolver"),
                        b -> this.minecraft.setScreen(lastScreen))
                .bounds(this.width / 2 - 50, this.height - 35, 100, 20).build());
    }

    private void createFlagButton(String flagId, int x, int y) {
        boolean active = core.getFlag(flagId);

        // Mantenemos el icono visual para diferenciar el tipo de flag, aunque todos las vean
        boolean isAdminFlag = ProtectionCoreBlockEntity.ADMIN_FLAGS.contains(flagId);
        String prefix = isAdminFlag ? "§4⚙ " : "§6• ";

        this.addRenderableWidget(Button.builder(
                Component.literal(prefix + capitalize(flagId) + ": ")
                        .append(active ? Component.literal("§aON") : Component.literal("§cOFF")),
                b -> {
                    PacketDistributor.sendToServer(new UpdateFlagPayload(core.getBlockPos(), flagId));
                    core.setFlag(flagId, !active);
                    this.rebuildWidgets(); // Refresca los botones para mostrar el cambio de ON/OFF
                }).bounds(x, y, 140, 20).build());
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        // Reemplaza guiones por espacios y pone la primera letra en mayúscula
        return str.substring(0, 1).toUpperCase() + str.substring(1).replace("-", " ");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics, mouseX, mouseY, partialTicks);

        // Título centralizado
        graphics.drawCenteredString(this.font, "§b§lCONFIGURACIÓN GLOBAL DE FLAGS", this.width / 2, 25, 0xFFFFFF);

        // Nota informativa opcional en la parte inferior
        graphics.drawCenteredString(this.font, "§7§oUsa ⚙ para flags de sistema y • para básicas", this.width / 2, this.height - 55, 0xAAAAAA);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }
}
