package com.tumod.protectormod.client.screen;

import com.tumod.protectormod.ProtectorMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class CustomTexturedButton extends Button {
    // Ruta de tu textura personalizada de 50x60
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ProtectorMod.MOD_ID, "textures/gui/boton_custom.png");

    public CustomTexturedButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int vOffset = !this.active ? 0 : (this.isHoveredOrFocused() ? 40 : 20);

        // Dibujamos el botón en 3 partes: Esquina Izquierda, Centro (estirado), Esquina Derecha
        // Esto permite que botones de 20px, 60px o 100px usen la misma textura de 50px

        // Esquina Izquierda (3px)
        graphics.blit(TEXTURE, this.getX(), this.getY(), 0, vOffset, 3, this.height, 50, 60);

        // Centro (Estirado)
        graphics.blit(TEXTURE, this.getX() + 3, this.getY(), 3, vOffset, this.width - 6, this.height, 50, 60);

        // Esquina Derecha (3px)
        graphics.blit(TEXTURE, this.getX() + this.width - 3, this.getY(), 47, vOffset, 3, this.height, 50, 60);

        // Texto
        int color = this.active ? 0xFFFFFF : 0xA0A0A0;
        graphics.drawCenteredString(Minecraft.getInstance().font, this.getMessage(),
                this.getX() + this.width / 2, this.getY() + (this.height - 8) / 2, color);
    }
}