package com.tumod.protectormod.client.screen;

import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.network.UpdateFlagPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.List;
import net.minecraft.network.chat.Component;

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
        int startX = this.width / 2 - 135; // Centro de la pantalla desplazado a la izquierda
        int startY = 40;
        int columnWidth = 140;
        int buttonHeight = 20;

        List<String> allFlags = core.getAllFlagKeys();

        for (int i = 0; i < allFlags.size(); i++) {
            String flagId = allFlags.get(i);

            // Calculamos posición en 2 columnas
            int column = i / 10; // 10 botones por columna
            int row = i % 10;
            int x = startX + (column * columnWidth);
            int y = startY + (row * (buttonHeight + 2));

            // Creamos el botón dinámicamente
            this.addRenderableWidget(Button.builder(
                    Component.literal(capitalize(flagId) + ": ")
                            .append(core.getFlag(flagId) ? Component.literal("§aON") : Component.literal("§cOFF")),
                    b -> {
                        PacketDistributor.sendToServer(new UpdateFlagPayload(core.getBlockPos(), flagId));
                        // Invertimos localmente para feedback visual instantáneo
                        core.setFlag(flagId, !core.getFlag(flagId));
                        this.rebuildWidgets();
                    }).bounds(x, y, 130, buttonHeight).build());
        }

        // Botón Volver (al final, centrado abajo)
        this.addRenderableWidget(Button.builder(Component.literal("§lVolver"),
                        b -> this.minecraft.setScreen(lastScreen))
                .bounds(this.width / 2 - 60, this.height - 30, 120, 20).build());
    }

    // Método auxiliar para que los nombres se vean bonitos (ej: "pvp" -> "Pvp")
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).replace("-", " ");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics, mouseX, mouseY, partialTicks);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
