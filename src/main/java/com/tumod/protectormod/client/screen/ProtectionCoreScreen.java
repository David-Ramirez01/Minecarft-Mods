package com.tumod.protectormod.client.screen;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.menu.ProtectionCoreMenu;
import com.tumod.protectormod.network.ChangePermissionPayload;
import com.tumod.protectormod.network.ShowAreaPayload;
import com.tumod.protectormod.network.UpgradeCorePayload;
import com.tumod.protectormod.registry.ModItems; // <--- ESTE ES EL IMPORT QUE FALTABA
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class ProtectionCoreScreen extends AbstractContainerScreen<ProtectionCoreMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ProtectorMod.MOD_ID, "textures/gui/protection_core.png");

    private EditBox nameInput;
    private Button upgradeButton; // Guardamos referencia para activar/desactivar

    private boolean buildToggle = false;
    private boolean interactToggle = false;
    private boolean chestsToggle = false;

    public ProtectionCoreScreen(ProtectionCoreMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // 1. CUADRO DE TEXTO
        this.nameInput = new EditBox(this.font, x + 12, y + 40, 150, 12, Component.literal("Nombre..."));
        this.nameInput.setMaxLength(16);
        this.nameInput.setHint(Component.literal("Escribe un nombre...").withStyle(net.minecraft.ChatFormatting.GRAY));
        this.addRenderableWidget(this.nameInput);

        // 2. BOTONES DE PERMISOS
        this.addRenderableWidget(Button.builder(Component.literal("Bloques: OFF"), b -> {
            String target = nameInput.getValue();
            if (!target.isEmpty()) {
                buildToggle = !buildToggle;
                sendPermission(target, "build", buildToggle);
                b.setMessage(Component.literal("Bloques: " + (buildToggle ? "§aON" : "§cOFF")));
            }
        }).bounds(x + 10, y + 60, 75, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Interac: OFF"), b -> {
            String target = nameInput.getValue();
            if (!target.isEmpty()) {
                interactToggle = !interactToggle;
                sendPermission(target, "interact", interactToggle);
                b.setMessage(Component.literal("Interac: " + (interactToggle ? "§aON" : "§cOFF")));
            }
        }).bounds(x + 90, y + 60, 75, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Cofres: OFF"), b -> {
            String target = nameInput.getValue();
            if (!target.isEmpty()) {
                chestsToggle = !chestsToggle;
                sendPermission(target, "chests", chestsToggle);
                b.setMessage(Component.literal("Cofres: " + (chestsToggle ? "§aON" : "§cOFF")));
            }
        }).bounds(x + 10, y + 85, 75, 20).build());

        // 3. BOTONES DE ACCIÓN
        // Dentro de init() en ProtectionCoreScreen.java
        this.upgradeButton = Button.builder(Component.literal("Mejorar"), b -> {
                    PacketDistributor.sendToServer(new UpgradeCorePayload());
                    // Opcional: Sonido de click de interfaz
                    net.minecraft.client.Minecraft.getInstance().player.playSound(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
                })
                .bounds(x + 42, y + 115, 60, 20)
                .build();

        // Desactivar si ya es nivel máximo
        this.upgradeButton.active = this.menu.getCore().getCoreLevel() < 5;
        this.addRenderableWidget(this.upgradeButton);

        this.addRenderableWidget(Button.builder(Component.literal("Area"), b -> {
                    var core = this.menu.getCore();
                    PacketDistributor.sendToServer(new ShowAreaPayload(core.getBlockPos(), core.getRadius()));
                })
                .bounds(x + 106, y + 115, 60, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("X"), b -> this.onClose())
                .bounds(x + imageWidth - 20, y + 4, 16, 16).build());
    }

    private void sendPermission(String player, String type, boolean value) {
        PacketDistributor.sendToServer(new ChangePermissionPayload(this.menu.getCore().getBlockPos(), player, type, value));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics, mouseX, mouseY, partialTicks);
        super.render(graphics, mouseX, mouseY, partialTicks);

        renderGuestList(graphics, mouseX, mouseY);
        renderUpgradeRequirements(graphics);
        renderNameSuggestions(graphics, mouseX, mouseY);

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        if (this.menu.getCore().getCoreLevel() >= 5) {
            graphics.drawString(this.font, "§6¡NIVEL MÁXIMO!", x + 45, y + 105, 0xFFAA00);
        }

        this.renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderUpgradeRequirements(GuiGraphics graphics) {
        int x = (this.width + this.imageWidth) / 2 + 5;
        int y = (this.height - this.imageHeight) / 2 + 20;
        var core = this.menu.getCore();
        int level = core.getCoreLevel();

        graphics.fill(x, y, x + 100, y + 85, 0x88000000);
        graphics.drawString(this.font, "§eRequisitos:", x + 5, y + 5, 0xFFFFFF);

        if (level >= 5) {
            graphics.drawString(this.font, "§aCompletado", x + 5, y + 25, 0xFFFFFF);
            return;
        }

        String material = switch (level) {
            case 1 -> "64x Hierro";
            case 2 -> "32x Oro";
            case 3 -> "32x Diamante";
            case 4 -> "32x Netherite";
            default -> "";
        };

        boolean hasUpgradeItem = core.getInventory().getItem(0).is(ModItems.PROTECTION_UPGRADE.get());
        boolean hasMaterials = core.canUpgrade();

        renderRequirement(graphics, x + 5, y + 25, "1x Mejora", hasUpgradeItem);
        renderRequirement(graphics, x + 5, y + 40, material, hasMaterials);
    }

    private void renderRequirement(GuiGraphics graphics, int x, int y, String text, boolean met) {
        String prefix = met ? "§a[✔] " : "§c[X] ";
        graphics.drawString(this.font, prefix + "§7" + text, x, y, 0xFFFFFF);
    }

    private void renderNameSuggestions(GuiGraphics graphics, int mouseX, int mouseY) {
        String input = nameInput.getValue();
        if (input.length() < 2) return;

        int x = (this.width - this.imageWidth) / 2 + 12;
        int y = (this.height - this.imageHeight) / 2 + 52;

        var players = net.minecraft.client.Minecraft.getInstance().getConnection().getOnlinePlayers();
        List<String> matches = players.stream()
                .map(p -> p.getProfile().getName())
                .filter(name -> name.toLowerCase().startsWith(input.toLowerCase()) && !name.equals(input))
                .limit(3)
                .toList();

        int offset = 0;
        for (String suggestion : matches) {
            int sugY = y + offset;
            graphics.fill(x, sugY, x + 150, sugY + 12, 0xCC222222);
            boolean hovered = mouseX >= x && mouseX <= x + 150 && mouseY >= sugY && mouseY <= sugY + 12;
            graphics.drawString(this.font, "§b" + suggestion, x + 5, sugY + 2, hovered ? 0xFFFFFF : 0xAAAAAA);

            if (hovered && net.minecraft.client.Minecraft.getInstance().mouseHandler.isLeftPressed()) {
                nameInput.setValue(suggestion);
            }
            offset += 13;
        }
    }

    private void renderGuestList(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        List<String> guests = this.menu.getCore().getTrustedNames();
        int listX = x - 105;
        int listY = y + 20;

        graphics.fill(listX, listY, listX + 100, listY + 130, 0x88000000);
        graphics.drawString(this.font, "§6Invitados:", listX + 5, listY + 5, 0xFFFFFF);

        for (int i = 0; i < guests.size(); i++) {
            String name = guests.get(i);
            int entryY = listY + 20 + (i * 14);

            int removeBtnX = listX + 85;
            boolean isHoveringRemove = mouseX >= removeBtnX && mouseX <= removeBtnX + 10 && mouseY >= entryY && mouseY <= entryY + 9;
            boolean isHoveringName = mouseX >= listX + 5 && mouseX <= removeBtnX - 2 && mouseY >= entryY && mouseY <= entryY + 9;

            graphics.drawString(this.font, name, listX + 5, entryY, isHoveringName ? 0xFFFFAA : 0xAAAAAA);
            graphics.drawString(this.font, "§c[X]", removeBtnX, entryY, isHoveringRemove ? 0xFFFFFF : 0xCC0000);

            if (net.minecraft.client.Minecraft.getInstance().mouseHandler.isLeftPressed()) {
                if (isHoveringRemove) removeAllPermissions(name);
                else if (isHoveringName) this.nameInput.setValue(name);
            }
        }
    }

    private void removeAllPermissions(String targetName) {
        sendPermission(targetName, "build", false);
        sendPermission(targetName, "interact", false);
        sendPermission(targetName, "chests", false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);
    }
}
