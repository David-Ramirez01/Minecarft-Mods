package com.tumod.protectormod.client.screen;

import com.tumod.protectormod.ProtectorMod;
import com.tumod.protectormod.menu.ProtectionCoreMenu;
import com.tumod.protectormod.network.*;
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

    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ProtectorMod.MOD_ID, "textures/gui/protection_core.png");
    private static final ResourceLocation FONDO_INVITADOS = ResourceLocation.fromNamespaceAndPath(ProtectorMod.MOD_ID, "textures/gui/fondo_invitados.png");
    private static final ResourceLocation SLOT_TEXTURE = ResourceLocation.fromNamespaceAndPath(ProtectorMod.MOD_ID, "textures/gui/slot.png");
    private static final ResourceLocation TEXTURA_INPUT = ResourceLocation.fromNamespaceAndPath(ProtectorMod.MOD_ID, "textures/gui/input_nombre.png");

    private EditBox nameInput;
    private Button upgradeButton, buildBtn, interactBtn, chestsBtn;
    private boolean buildToggle = false;
    private boolean interactToggle = false;
    private boolean chestsToggle = false;
    private int suggestionIndex = -1;
    private String lastCheckedPlayer = "";
    private Button clanBtn;

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
        int currentLevel = this.menu.getCore().getCoreLevel();

        // 1. INPUT DE NOMBRE
        this.nameInput = new EditBox(this.font, x + 14, y + 38, 87, 9, Component.empty());
        this.nameInput.setBordered(false); // Eliminamos el borde de Minecraft
        this.nameInput.setMaxLength(16);
        this.nameInput.setHint(Component.literal("Nombre").withStyle(ChatFormatting.GRAY));
        this.addRenderableWidget(this.nameInput);
        this.addRenderableWidget(new CheckButtonCustom(x + 105, y + 33, 32, 16, b -> handlePlayerAdd()));

        // 2. BOTONES DE PERMISOS
        this.buildBtn = this.addRenderableWidget(new CustomTexturedButton(x + 10, y + 55, 50, 20, Component.literal("B: OFF"), b -> {
            buildToggle = !buildToggle;
            sendPermission(nameInput.getValue(), "build", buildToggle);
        }));

        this.interactBtn = this.addRenderableWidget(new CustomTexturedButton(x + 63, y + 55, 50, 20, Component.literal("I: OFF"), b -> {
            interactToggle = !interactToggle;
            sendPermission(nameInput.getValue(), "interact", interactToggle);
        }));

        this.chestsBtn = this.addRenderableWidget(new CustomTexturedButton(x + 116, y + 55, 50, 20, Component.literal("C: OFF"), b -> {
            chestsToggle = !chestsToggle;
            sendPermission(nameInput.getValue(), "chests", chestsToggle);
        }));

        // 3. CLAN Y AJUSTES
        this.clanBtn = this.addRenderableWidget(new CustomTexturedButton(x + 10, y + 78, 50, 20, Component.literal("Clan"), b -> {
            // La condición de >= 3 ya está aquí, pero el botón estará desactivado visualmente antes
            if (this.menu.getCore().getTrustedNames().size() >= 3) {
                this.minecraft.setScreen(new CreateClanScreen(this, this.menu.getCore()));
            }
        }));

        this.addRenderableWidget(new CustomTexturedButton(x + 63, y + 78, 50, 20, Component.literal("Ajustes"), b -> {
            this.minecraft.setScreen(new FlagsScreen(this, this.menu.getCore()));
        }));

        // 4. UPGRADE Y ÁREA
        this.upgradeButton = this.addRenderableWidget(new CustomTexturedButton(x + 64, y + 105, 50, 20,
                Component.translatable("Mejorar"), b -> {
            PacketDistributor.sendToServer(new UpgradeCorePayload(this.menu.getCore().getBlockPos()));
            playClickSound();
        }));

        this.addRenderableWidget(new CustomTexturedButton(x + 116, y + 105, 50, 20,
                Component.literal("Área"), b -> {
            var core = this.menu.getCore();
            PacketDistributor.sendToServer(new ShowAreaPayload(core.getBlockPos(), core.getRadius()));
        }));

        // Botón Cerrar (X)
        this.addRenderableWidget(new CloseButtonCustom(x + imageWidth - 18, y + 4, 14, 14, Component.empty(), b -> this.onClose()));
    }

    private void renderLateralPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        var core = this.menu.getCore();
        if (core == null) return;

        int listX = x - 105;
        int listY = y + 10;

        // 1. FONDO (574x1005 escalado a 100x205 para que quepa todo verticalmente)
        graphics.blit(FONDO_INVITADOS, listX, listY, 0, 0, 100, 205, 100, 205);

        // 2. JUGADORES (Arriba)
        graphics.drawString(this.font, "§6§lJugadores", listX + 11, listY + 12, 0xFFFFFF);
        String ownerName = core.getOwnerName();
        if (ownerName == null || ownerName.isEmpty()) ownerName = "Dueño";
        graphics.drawString(this.font, "§e★ §f" + ownerName, listX + 11, listY + 24, 0xFFAA00);

        List<String> guests = core.getTrustedNames();
        int offset = 1;
        for (String name : guests) {
            if (name.equalsIgnoreCase(ownerName)) continue;
            int entryY = listY + 24 + (offset * 13);
            int removeBtnX = listX + 82;

            boolean hoverRemove = mouseX >= removeBtnX && mouseX <= removeBtnX + 12 && mouseY >= entryY && mouseY <= entryY + 10;
            graphics.drawString(this.font, name, listX + 11, entryY, 0xAAAAAA);
            graphics.drawString(this.font, "§c[X]", removeBtnX, entryY, hoverRemove ? 0xFFFFFF : 0xCC0000);
            offset++;
        }

        // 3. REQUISITOS (Abajo - Posición fija en el fondo lateral)
        int reqY = listY + 145;
        graphics.drawString(this.font, "§e§lRequisitos:", listX + 15, reqY, 0xFFFFFF);

        int level = core.getCoreLevel();
        if (level >= 5) {
            graphics.drawString(this.font, "§a✔ Máximo Nivel", listX + 15, reqY + 15, 0xFFFFFF);
        } else {
            String material = switch (level) {
                case 1 -> "64x Hierro";
                case 2 -> "32x Oro";
                case 3 -> "32x Diamante";
                case 4 -> "32x Netherite";
                default -> "???";
            };
            boolean hasUpgrade = core.getInventory().getStackInSlot(0).is(ModItems.PROTECTION_UPGRADE.get());
            boolean hasMats = core.canUpgrade();

            renderRequirement(graphics, listX + 15, reqY + 15, "1x Mejora", hasUpgrade);
            renderRequirement(graphics, listX + 15, reqY + 28, material, hasMats);
        }
    }

    private void renderRequirement(GuiGraphics graphics, int x, int y, String text, boolean met) {
        String prefix = met ? "§a[✔] " : "§c[X] ";
        graphics.drawString(this.font, prefix + "§7" + text, x, y, 0xFFFFFF);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics, mouseX, mouseY, partialTicks);
        super.render(graphics, mouseX, mouseY, partialTicks);

        var core = this.menu.getCore();
        if (core == null) return;

        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        Component tituloCustom = Component.literal("§b§l§oProtection Core");
        int tituloWidth = this.font.width(tituloCustom);
        graphics.drawString(this.font, tituloCustom, x + (this.imageWidth - tituloWidth) / 2, y + 10, 0xFFFFFF, true);

        graphics.drawString(this.font, "§6§lInvitar Jugadores", x + 10, y + 22, 0xFFFFFF, false);

        if (this.clanBtn != null) {
            this.clanBtn.active = core.getTrustedNames().size() >= 3;
        }

        String input = nameInput.getValue().trim();
        boolean hasName = !input.isEmpty();
        this.buildBtn.active = this.interactBtn.active = this.chestsBtn.active = hasName;

        if (hasName && !input.equalsIgnoreCase(lastCheckedPlayer)) {
            var perms = core.getPermissionsFor(input);
            this.buildToggle = perms.canBuild;
            this.interactToggle = perms.canInteract;
            this.chestsToggle = perms.canOpenChests;
            lastCheckedPlayer = input;
        }

        this.buildBtn.setMessage(Component.literal("B: " + (buildToggle ? "§aON" : "§cOFF")));
        this.interactBtn.setMessage(Component.literal("I: " + (interactToggle ? "§aON" : "§cOFF")));
        this.chestsBtn.setMessage(Component.literal("C: " + (chestsToggle ? "§aON" : "§cOFF")));

        renderLateralPanel(graphics, mouseX, mouseY);
        renderNameSuggestions(graphics, mouseX, mouseY);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        graphics.blit(TEXTURE, x, y, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
        graphics.blit(SLOT_TEXTURE, x + 14, y + 104, 0, 0, 18, 18, 18, 18);
        graphics.blit(SLOT_TEXTURE, x + 34, y + 104, 0, 0, 18, 18, 18, 18);
        graphics.blit(TEXTURA_INPUT, x + 10, y + 35, 0, 0, 91, 13, 128, 32);
    }


    private void handlePlayerAdd() {
        String name = this.nameInput.getValue().trim();
        if (!name.isEmpty()) {
            sendPermission(name, "interact", true);
            this.minecraft.player.displayClientMessage(Component.literal("§aAñadido: §f" + name), true);
            this.nameInput.setValue("");
        }
    }

    private void sendPermission(String player, String type, boolean value) {
        PacketDistributor.sendToServer(new ChangePermissionPayload(this.menu.getCore().getBlockPos(), player, type, value));
    }

    private void playClickSound() {
        this.minecraft.player.playSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 0.6F, 1.2F);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int x = (this.width - this.imageWidth) / 2;
            int y = (this.height - this.imageHeight) / 2;
            var core = this.menu.getCore();
            if (core != null) {
                int listX = x - 105;
                int listY = y + 10;
                List<String> guests = core.getTrustedNames();
                int offset = 1;
                for (String name : guests) {
                    if (name.equalsIgnoreCase(core.getOwnerName())) continue;
                    int entryY = listY + 18 + (offset * 13);
                    if (mouseX >= listX + 82 && mouseX <= listX + 95 && mouseY >= entryY && mouseY <= entryY + 10) {
                        PacketDistributor.sendToServer(new ChangePermissionPayload(core.getBlockPos(), name, "remove", false));
                        playClickSound();
                        return true;
                    }
                    if (mouseX >= listX + 5 && mouseX <= listX + 80 && mouseY >= entryY && mouseY <= entryY + 10) {
                        this.nameInput.setValue(name);
                        playClickSound();
                        return true;
                    }
                    offset++;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderNameSuggestions(GuiGraphics graphics, int mouseX, int mouseY) {
        String input = nameInput.getValue();
        if (input.length() < 2 || !nameInput.isFocused()) return;
        int x = (this.width - this.imageWidth) / 2 + 10;
        int y = (this.height - this.imageHeight) / 2 + 45;
        var connection = this.minecraft.getConnection();
        if (connection == null) return;
        List<String> matches = connection.getOnlinePlayers().stream()
                .map(p -> p.getProfile().getName())
                .filter(name -> name.toLowerCase().startsWith(input.toLowerCase()) && !name.equals(input))
                .limit(3).toList();
        for (int i = 0; i < matches.size(); i++) {
            String suggestion = matches.get(i);
            int sugY = y + (i * 13);
            graphics.fill(x, sugY, x + 90, sugY + 12, (i == suggestionIndex) ? 0xEE444444 : 0xCC222222);
            graphics.drawString(this.font, "§b" + suggestion, x + 5, sugY + 2, 0xAAAAAA, false);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {

        Component invTitleCustom = Component.literal("§b§o" + this.playerInventoryTitle.getString());
        graphics.drawString(this.font, invTitleCustom, this.inventoryLabelX, this.inventoryLabelY, 0xFFFFFF, true);

    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.nameInput.canConsumeInput()) {
            if (keyCode == 256) {
                this.nameInput.setFocused(false);
                return true;
            }
            return this.nameInput.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
