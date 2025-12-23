package com.tumod.protectormod.client.screen;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.menu.ProtectionCoreMenu;
import com.tumod.protectormod.network.ChangePermissionPayload;
import com.tumod.protectormod.network.CreateClanPayload;
import com.tumod.protectormod.network.ShowAreaPayload;
import com.tumod.protectormod.network.UpgradeCorePayload;
import com.tumod.protectormod.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class ProtectionCoreScreen extends AbstractContainerScreen<ProtectionCoreMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ProtectorMod.MOD_ID, "textures/gui/protection_core.png");

    private EditBox nameInput;
    private Button upgradeButton, buildBtn, interactBtn, chestsBtn;

    private boolean buildToggle = false;
    private boolean interactToggle = false;
    private boolean chestsToggle = false;
    private Button clanButton;
    private String selectedGuest = ""; //

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
        this.nameInput = new EditBox(this.font, x + 12, y + 35, 150, 12, Component.literal(""));
        this.nameInput.setMaxLength(16);
        this.nameInput.setHint(Component.literal("Escribe un nombre...").withStyle(ChatFormatting.WHITE));
        this.addRenderableWidget(this.nameInput);

        // 2. BOTONES DE PERMISOS (Compactos: 50px)
        this.buildBtn = this.addRenderableWidget(Button.builder(Component.literal("B: OFF"), b -> {
            buildToggle = !buildToggle;
            sendPermission(nameInput.getValue(), "build", buildToggle);
        }).bounds(x + 10, y + 55, 50, 20).build());

        // Botón de Clan (Inicialmente desactivado)
        this.clanButton = this.addRenderableWidget(Button.builder(Component.literal("Crear Clan"), b -> {
            String clanName = this.nameInput.getValue().trim();
            if (!clanName.isEmpty() && this.menu.getCore().getTrustedNames().size() >= 3) {
                PacketDistributor.sendToServer(new CreateClanPayload(this.menu.getCore().getBlockPos(), clanName));
                this.minecraft.setScreen(null);
            }else{
                this.minecraft.player.displayClientMessage(Component.literal("§cEscribe el nombre del clan arriba y ten 3+ invitados."), true);
            }
        }).bounds(x + 10, y + 78, 50, 20).build());
        this.clanButton.active = false;


        this.interactBtn = this.addRenderableWidget(Button.builder(Component.literal("I: OFF"), b -> {
            interactToggle = !interactToggle;
            sendPermission(nameInput.getValue(), "interact", interactToggle);
        }).bounds(x + 63, y + 55, 50, 20).build());

        this.chestsBtn = this.addRenderableWidget(Button.builder(Component.literal("C: OFF"), b -> {
            chestsToggle = !chestsToggle;
            sendPermission(nameInput.getValue(), "chests", chestsToggle);
        }).bounds(x + 116, y + 55, 50, 20).build());

        // 3. BOTONES DE ACCIÓN (A la derecha para dejar espacio a los slots en x=15 y x=35)
        this.upgradeButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.protectormod.protection_core.upgrade"), b -> {
                    PacketDistributor.sendToServer(new UpgradeCorePayload());
                    net.minecraft.client.Minecraft.getInstance().player.playSound(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
                })
                .bounds(x + 75, y + 100, 60, 20)
                .build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.protectormod.protection_core.show_area"), b -> {
                    var core = this.menu.getCore();
                    PacketDistributor.sendToServer(new ShowAreaPayload(core.getBlockPos(), core.getRadius()));
                })
                .bounds(x + 137, y + 100, 30, 20).build());

        // Botón cerrar
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
        List<String> guests = this.menu.getCore().getTrustedNames();
        this.clanButton.active = guests.size() >= 3;

        int currentLevel = this.menu.getCore().getCoreLevel();
        boolean isMax = currentLevel >= 5;

        // --- LÓGICA DE INTERFAZ DINÁMICA ---
        boolean hasName = !nameInput.getValue().trim().isEmpty();
        this.buildBtn.active = hasName;
        this.interactBtn.active = hasName;
        this.chestsBtn.active = hasName;

        // El botón de mejora se apaga físicamente en nivel 5
        this.upgradeButton.active = !isMax;

        this.buildBtn.setMessage(Component.literal("B:" + (buildToggle ? "§aON" : "§cOFF")));
        this.interactBtn.setMessage(Component.literal("I:" + (interactToggle ? "§aON" : "§cOFF")));
        this.chestsBtn.setMessage(Component.literal("C:" + (chestsToggle ? "§aON" : "§cOFF")));

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Renderizado de Nivel con traducción
        Component levelText = Component.translatable("gui.protectormod.protection_core.level", currentLevel);
        if (isMax) levelText = Component.translatable(" §6§l(MAX)");
        graphics.drawString(this.font, levelText, x + 12, y + 15, 0x404040, false);

        renderGuestList(graphics, mouseX, mouseY);
        renderUpgradeRequirements(graphics);
        renderNameSuggestions(graphics, mouseX, mouseY);

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
            graphics.drawString(this.font, "§a✔ Completado", x + 5, y + 25, 0xFFFFFF);
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

        var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
        if (connection == null) return;

        List<String> matches = connection.getOnlinePlayers().stream()
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

        // Fondo de la lista
        graphics.fill(listX, listY, listX + 100, listY + 130, 0x88000000);
        graphics.drawString(this.font, "§6Invitados:", listX + 5, listY + 5, 0xFFFFFF);

        for (int i = 0; i < guests.size(); i++) {
            String name = guests.get(i);
            int entryY = listY + 20 + (i * 14);
            int removeBtnX = listX + 85;

            boolean isHoveringRemove = mouseX >= removeBtnX - 2 && mouseX <= removeBtnX + 12 &&
                    mouseY >= entryY - 2 && mouseY <= entryY + 10;

            boolean isHoveringName = mouseX >= listX + 5 && mouseX <= removeBtnX - 5 &&
                    mouseY >= entryY && mouseY <= entryY + 9;

            graphics.drawString(this.font, name, listX + 5, entryY, isHoveringName ? 0xFFFFAA : 0xAAAAAA);
            graphics.drawString(this.font, "§c[X]", removeBtnX, entryY, isHoveringRemove ? 0xFFFFFF : 0xCC0000);

        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Clic izquierdo
            int x = (this.width - this.imageWidth) / 2;
            int y = (this.height - this.imageHeight) / 2;

            List<String> guests = this.menu.getCore().getTrustedNames();
            int listX = x - 105;
            int listY = y + 20;

            for (int i = 0; i < guests.size(); i++) {
                String name = guests.get(i);
                int entryY = listY + 20 + (i * 14);
                int removeBtnX = listX + 85;

                // Lógica para el botón [X] (Eliminar)
                if (mouseX >= removeBtnX - 2 && mouseX <= removeBtnX + 12 &&
                        mouseY >= entryY - 2 && mouseY <= entryY + 10) {
                    removeAllPermissions(name);
                    playClickSound();
                    return true;
                }

                // Lógica para clic en el NOMBRE (Seleccionar y ver permisos)
                if (mouseX >= listX + 5 && mouseX <= removeBtnX - 5 &&
                        mouseY >= entryY && mouseY <= entryY + 10) {
                    this.nameInput.setValue(name);
                    // Buscamos los permisos de este jugador en el Core
                    var perms = this.menu.getCore().getPermissionsFor(name);
                    if (perms != null) {
                        // Actualizamos los estados internos para que los botones cambien de color/texto
                        this.buildToggle = perms.canBuild;
                        this.interactToggle = perms.canInteract;
                        this.chestsToggle = perms.canOpenChests;
                    }

                    playClickSound();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void playClickSound() {
        net.minecraft.client.Minecraft.getInstance().player.playSound(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.6F, 1.2F);
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.nameInput.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        // Si el cuadro de texto está enfocado, evitamos que la tecla 'E' cierre la GUI
        if (this.nameInput.isFocused() && keyCode != 256) { // 256 es ESC
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
