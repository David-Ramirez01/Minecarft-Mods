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
        this.core = menu.getBlockEntity(); // Cambiado para usar el getter correcto
        this.imageWidth = 176;
        this.imageHeight = 166; // Altura estándar de GUI pequeña
    }

    @Override
    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;

        // 1. Entrada de Radio (Para que el Admin defina el área)
        this.radiusInput = new EditBox(this.font, x + 80, y + 25, 50, 18, Component.literal("Radio"));
        this.radiusInput.setValue(String.valueOf(core.getRadius()));
        this.radiusInput.setFilter(s -> s.matches("\\d*"));
        this.addRenderableWidget(this.radiusInput);

        // 2. BOTÓN PRINCIPAL: Abrir Menú de las 20 Flags
        // Este botón reemplaza a los interruptores individuales para limpiar la GUI
        this.addRenderableWidget(Button.builder(Component.literal("🚩 CONFIGURAR FLAGS"), b -> {
            // Aquí abrimos la sub-pantalla que ya maneja todas las protecciones
            this.minecraft.setScreen(new FlagsScreen(this, core));
        }).bounds(x + 20, y + 55, 136, 20).build());

        // 3. Botón de Confirmar Radio y Sincronizar
        this.addRenderableWidget(Button.builder(Component.literal("✅ Aplicar Cambios"), b -> {
            applyChanges();
        }).bounds(x + 20, y + 80, 136, 20).build());
    }

    private void applyChanges() {
        try {
            int newRadius = Integer.parseInt(radiusInput.getValue());
            // Sincronizamos con el servidor usando tu Payload
            PacketDistributor.sendToServer(new UpdateAdminCorePayload(
                    core.getBlockPos(),
                    newRadius,
                    core.getFlag("pvp"),
                    !core.getFlag("explosions") // Invertido si tu payload espera 'disabled'
            ));
            this.minecraft.player.displayClientMessage(Component.literal("§aÁrea de administración actualizada."), true);
        } catch (NumberFormatException e) {
            radiusInput.setValue("128");
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics, mouseX, mouseY, partialTicks);
        super.render(graphics, mouseX, mouseY, partialTicks);

        // Dibujamos los textos informativos
        graphics.drawString(this.font, "§4§lADMIN PROTECTOR", this.leftPos + 35, this.topPos + 8, 0xFFFFFF, false);
        graphics.drawString(this.font, "Radio:", this.leftPos + 25, this.topPos + 30, 0x404040, false);

        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
    }
}
