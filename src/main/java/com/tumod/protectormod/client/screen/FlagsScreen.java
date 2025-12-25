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
        int startX = this.width / 2 - 145; // Un poco más a la izquierda para centrar las dos columnas
        int startY = 45;
        int buttonHeight = 20;
        int columnWidth = 150;

        boolean isOp = this.minecraft.player.hasPermissions(2);

        // --- COLUMNA 1: FLAGS BÁSICAS (Para todos) ---
        List<String> basics = ProtectionCoreBlockEntity.BASIC_FLAGS;
        for (int i = 0; i < basics.size(); i++) {
            createFlagButton(basics.get(i), startX, startY + (i * 22));
        }

        // --- COLUMNA 2: FLAGS RESTRINGIDAS (Solo Admins) ---
        if (isOp) {
            List<String> restricted = ProtectionCoreBlockEntity.ADMIN_FLAGS;
            for (int i = 0; i < restricted.size(); i++) {
                // Dibujamos en la segunda columna (startX + columnWidth)
                createFlagButton(restricted.get(i), startX + columnWidth, startY + (i * 22));
            }
        }

        // Botón Volver
        this.addRenderableWidget(Button.builder(Component.literal("§lVolver"),
                        b -> this.minecraft.setScreen(lastScreen))
                .bounds(this.width / 2 - 50, this.height - 35, 100, 20).build());
    }

    private void createFlagButton(String flagId, int x, int y) {
        boolean active = core.getFlag(flagId);
        // Usamos un color diferente para las flags de admin para que se distingan
        boolean isAdminFlag = ProtectionCoreBlockEntity.ADMIN_FLAGS.contains(flagId);
        String prefix = isAdminFlag ? "§4⚙ " : "§6• ";

        this.addRenderableWidget(Button.builder(
                Component.literal(prefix + capitalize(flagId) + ": ")
                        .append(active ? Component.literal("§aON") : Component.literal("§cOFF")),
                b -> {
                    PacketDistributor.sendToServer(new UpdateFlagPayload(core.getBlockPos(), flagId));
                    core.setFlag(flagId, !active);
                    this.rebuildWidgets(); // Refresca los botones para mostrar el nuevo estado
                }).bounds(x, y, 140, 20).build());
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).replace("-", " ");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics, mouseX, mouseY, partialTicks);

        // Títulos de columnas
        graphics.drawCenteredString(this.font, "§e§lFLAGS BÁSICAS", this.width / 2 - 75, 30, 0xFFFFFF);
        if (this.minecraft.player.hasPermissions(2)) {
            graphics.drawCenteredString(this.font, "§4§lADMINISTRACIÓN", this.width / 2 + 75, 30, 0xFFFFFF);
        }

        super.render(graphics, mouseX, mouseY, partialTicks);
    }
}
