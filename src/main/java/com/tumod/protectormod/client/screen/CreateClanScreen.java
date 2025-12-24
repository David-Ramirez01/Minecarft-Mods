package com.tumod.protectormod.client.screen;

import com.tumod.protectormod.network.CreateClanPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public class CreateClanScreen extends Screen {
    private final Screen lastScreen;
    private final BlockEntity core;
    private EditBox clanNameInput;

    public CreateClanScreen(Screen lastScreen, BlockEntity core) {
        super(Component.literal("Crear Nuevo Clan"));
        this.lastScreen = lastScreen;
        this.core = core;
    }

    @Override
    protected void init() {
        int x = this.width / 2;
        int y = this.height / 2;

        this.clanNameInput = new EditBox(this.font, x - 100, y - 20, 200, 20, Component.literal("Nombre del Clan"));
        this.clanNameInput.setMaxLength(16);
        this.addRenderableWidget(this.clanNameInput);

        // Botón Confirmar
        this.addRenderableWidget(Button.builder(Component.literal("✅ Confirmar"), b -> {
            String name = this.clanNameInput.getValue().trim();
            if (!name.isEmpty()) {
                PacketDistributor.sendToServer(new CreateClanPayload(core.getBlockPos(), name));
                this.minecraft.setScreen(null);
            }
        }).bounds(x - 105, y + 25, 100, 20).build());

        // Botón Volver
        this.addRenderableWidget(Button.builder(Component.literal("❌ Cancelar"), b -> {
            this.minecraft.setScreen(lastScreen);
        }).bounds(x + 5, y + 25, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics, mouseX, mouseY, partialTicks);

        // Título principal
        graphics.drawCenteredString(this.font, "§6§lFUNDAR CLAN", this.width / 2, this.height / 2 - 50, 0xFFFFFF);

        // LA ADVERTENCIA
        graphics.drawCenteredString(this.font, "§c⚠ ¡Este nombre no podrá cambiarse después!", this.width / 2, this.height / 2 + 5, 0xFFFFFF);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }
}
