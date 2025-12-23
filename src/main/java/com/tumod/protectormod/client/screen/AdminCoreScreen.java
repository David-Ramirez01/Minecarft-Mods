package com.tumod.protectormod.client.screen;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.menu.ProtectionCoreMenu;
import com.tumod.protectormod.network.UpdateAdminCorePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class AdminCoreScreen extends AbstractContainerScreen<ProtectionCoreMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ProtectorMod.MOD_ID, "textures/gui/admin_core.png");
    private EditBox radiusInput;
    private final ProtectionCoreBlockEntity core;

    public AdminCoreScreen(ProtectionCoreMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.core = menu.getCore();
        this.imageWidth = 176;
        this.imageHeight = 180;
    }

    @Override
    protected void init() {
        super.init();
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // 1. Cuadro de texto para el Radio
        this.radiusInput = new EditBox(this.font, x + 80, y + 40, 50, 18, Component.literal("Radio"));
        this.radiusInput.setValue(String.valueOf(core.getRadius()));
        this.radiusInput.setFilter(s -> s.matches("\\d*")); // Solo permite números
        this.addRenderableWidget(this.radiusInput);

        // 2. Botón Switch para PvP
        this.addRenderableWidget(Button.builder(
                Component.literal(core.isPvpEnabled() ? "§aPvP: ON" : "§cPvP: OFF"),
                b -> {
                    boolean newState = !core.isPvpEnabled();
                    // Aquí enviarías el paquete al servidor (ej: UpdateAdminFlagsPayload)
                    core.setPvpEnabled(newState);
                    b.setMessage(Component.literal(newState ? "§aPvP: ON" : "§cPvP: OFF"));
                    sendUpdate();
                }
        ).bounds(x + 20, y + 70, 136, 20).build());

        // 3. Botón Switch para Explosiones
        this.addRenderableWidget(Button.builder(
                Component.literal(core.areExplosionsDisabled() ? "§cExplosiones: OFF" : "§aExplosiones: ON"),
                b -> {
                    boolean newState = !core.areExplosionsDisabled();
                    core.setExplosionsDisabled(newState);
                    b.setMessage(Component.literal(newState ? "§cExplosiones: OFF" : "§aExplosiones: ON"));
                    sendUpdate();
                }
        ).bounds(x + 20, y + 95, 136, 20).build());

        // 4. Botón de Confirmar Radio
        this.addRenderableWidget(Button.builder(Component.literal("Asignar Radio"), b -> {
            try {
                int newRadius = Integer.parseInt(radiusInput.getValue());
                core.setAdminRadius(newRadius);
                sendUpdate(); // <--- AÑADE ESTO para enviar el paquete al servidor
                this.minecraft.player.displayClientMessage(Component.literal("§aRadio actualizado a: " + newRadius), true);
            } catch (NumberFormatException e) {
                radiusInput.setValue("128");
            }
        }).bounds(x + 20, y + 130, 136, 20).build());
    }

    private void sendUpdate() {
        int radius;
        try {
            radius = Integer.parseInt(this.radiusInput.getValue());
        } catch (NumberFormatException e) {
            radius = core.getRadius();
        }

        // Enviamos todos los valores actuales al servidor
        PacketDistributor.sendToServer(new UpdateAdminCorePayload(
                core.getBlockPos(),
                radius,
                core.isPvpEnabled(),
                core.areExplosionsDisabled()
        ));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics, mouseX, mouseY, partialTicks);
        super.render(graphics, mouseX, mouseY, partialTicks);
        this.renderTooltip(graphics, mouseX, mouseY);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.drawString(this.font, "Configuración Admin", x + 35, y + 10, 0x404040, false);
        graphics.drawString(this.font, "Radio:", x + 20, y + 45, 0x404040, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Dibuja el fondo de la GUI
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.radiusInput.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
