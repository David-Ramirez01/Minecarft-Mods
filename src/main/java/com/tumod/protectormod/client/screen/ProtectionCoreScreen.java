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

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ProtectorMod.MOD_ID, "textures/gui/protection_core.png");

    private EditBox nameInput;
    private Button upgradeButton, buildBtn, interactBtn, chestsBtn;

    private boolean buildToggle = false;
    private boolean interactToggle = false;
    private boolean chestsToggle = false;
    private int suggestionIndex = -1;
    private Button clanButton;
    private String lastCheckedPlayer = "";
    private String selectedGuest = "";
    private EditBox addPlayerInput;

    public ProtectionCoreScreen(ProtectionCoreMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 222;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    protected void init() {
        super.init();
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // Obtenemos el nivel actual para las validaciones
        int currentLevel = this.menu.getCore().getCoreLevel();

        // 1. CUADRO DE TEXTO
        this.nameInput = new EditBox(this.font, x + 10, y + 32, 90, 12, Component.literal(""));
        this.nameInput.setMaxLength(16);
        this.nameInput.setHint(Component.literal("Nombre ").withStyle(ChatFormatting.WHITE));
        this.addRenderableWidget(this.nameInput);
        this.addRenderableWidget(Button.builder(Component.literal("✓"), b -> {
            handlePlayerAdd();
        }).bounds(x + 110, y + 32 , 20, 12).build());

        // 2. BOTONES DE PERMISOS
        this.buildBtn = this.addRenderableWidget(Button.builder(Component.literal("B: OFF"), b -> {
            buildToggle = !buildToggle;
            sendPermission(nameInput.getValue(), "build", buildToggle);
        }).bounds(x + 10, y + 55, 50, 20).build());

        this.clanButton = this.addRenderableWidget(Button.builder(Component.literal("Crear Clan"), b -> {
            if (this.menu.getCore().getTrustedNames().size() >= 3) {
                this.minecraft.setScreen(new CreateClanScreen(this, this.menu.getCore()));
            } else {
                this.minecraft.player.displayClientMessage(
                        Component.literal("§cNecesitas al menos 3 invitados para formar un clan."), true);
            }
        }).bounds(x + 10, y + 78, 50, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Flags"), b -> {
            this.minecraft.setScreen(new FlagsScreen(this, this.menu.getCore()));
        }).bounds(x + 63, y + 78, 50, 20).build());

        this.interactBtn = this.addRenderableWidget(Button.builder(Component.literal("I: OFF"), b -> {
            interactToggle = !interactToggle;
            sendPermission(nameInput.getValue(), "interact", interactToggle);
        }).bounds(x + 63, y + 55, 50, 20).build());

        this.chestsBtn = this.addRenderableWidget(Button.builder(Component.literal("C: OFF"), b -> {
            chestsToggle = !chestsToggle;
            sendPermission(nameInput.getValue(), "chests", chestsToggle);
        }).bounds(x + 116, y + 55, 50, 20).build());

        // 3. BOTONES DE ACCIÓN (Modificado Upgrade)
        this.upgradeButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.protectormod.protection_core.upgrade"), b -> {
                    // RECOMENDACIÓN: Enviar la posición del bloque en el Payload
                    PacketDistributor.sendToServer(new UpgradeCorePayload(this.menu.getCore().getBlockPos()));
                    net.minecraft.client.Minecraft.getInstance().player.playSound(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 1.0F);
                })
                .bounds(x + 75, y + 105, 60, 20)
                .build());

        // LÓGICA DE BLOQUEO NIVEL 5
        if (currentLevel >= 5) {
            this.upgradeButton.active = false;
            this.upgradeButton.setMessage(Component.literal("MAX"));
        }

        this.addRenderableWidget(Button.builder(Component.translatable("gui.protectormod.protection_core.show_area"), b -> {
                    var core = this.menu.getCore();
                    PacketDistributor.sendToServer(new ShowAreaPayload(core.getBlockPos(), core.getRadius()));
                })
                .bounds(x + 137, y + 105, 30, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("X"), b -> this.onClose())
                .bounds(x + imageWidth - 20, y + 4, 16, 16).build());
    }

    private void sendPermission(String player, String type, boolean value) {
        PacketDistributor.sendToServer(new ChangePermissionPayload(this.menu.getCore().getBlockPos(), player, type, value));
    }

    // En ProtectionCoreScreen.java
    @Override
    protected void containerTick() {
        super.containerTick();
        // Esto asegura que si el nivel cambia mientras la GUI está abierta, el botón se apague
        if (this.menu.getCore().getCoreLevel() >= 5 && this.upgradeButton.active) {
            this.upgradeButton.active = false;
            this.upgradeButton.setMessage(Component.literal("MAX"));
        }
    }

    private void handlePlayerAdd() {
        String name = this.nameInput.getValue().trim();
        if (!name.isEmpty()) {
            // Al dar OK, activamos por defecto el permiso de "Interactuar" para que el jugador se guarde
            sendPermission(name, "interact", true);

            // Feedback visual
            this.minecraft.player.displayClientMessage(
                    Component.literal("§aAñadido: §f" + name), true);

            // Opcional: Limpiar el cuadro después de añadir
            this.nameInput.setValue("");
        }
    }

    private void renderUpgradeRequirements(GuiGraphics graphics) {
        int x = (this.width - this.imageWidth) / 2 - 105;
        int y = (this.height - this.imageHeight) / 2 + 150;

        var core = this.menu.getCore();
        if (core == null) return;

        int level = core.getCoreLevel();

        graphics.fill(x, y, x + 100, y + 60, 0x88000000);
        graphics.drawString(this.font, "§e§lRequisitos:", x + 5, y + 5, 0xFFFFFF);

        if (level >= 5) {
            graphics.drawString(this.font, "§a✔ Máximo Nivel", x + 5, y + 25, 0xFFFFFF);
            return;
        }

        String material = switch (level) {
            case 1 -> "64x Hierro";
            case 2 -> "32x Oro";
            case 3 -> "32x Diamante";
            case 4 -> "32x Netherite";
            default -> "???";
        };

        // FIX: Uso de getStackInSlot para ItemStackHandler
        boolean hasUpgradeItem = core.getInventory().getStackInSlot(0).is(ModItems.PROTECTION_UPGRADE.get());
        boolean hasMaterials = core.canUpgrade();

        renderRequirement(graphics, x + 5, y + 22, "1x Mejora", hasUpgradeItem);
        renderRequirement(graphics, x + 5, y + 35, material, hasMaterials);
    }

    private void renderRequirement(GuiGraphics graphics, int x, int y, String text, boolean met) {
        String prefix = met ? "§a[✔] " : "§c[X] ";
        graphics.drawString(this.font, prefix + "§7" + text, x, y, 0xFFFFFF);
    }


    private void renderNameSuggestions(GuiGraphics graphics, int mouseX, int mouseY) {
        String input = nameInput.getValue();
        // Solo sugerir si hay texto y la caja tiene el foco
        if (input.length() < 2 || !nameInput.isFocused()) return;

        int x = (this.width - this.imageWidth) / 2 + 10;
        int y = (this.height - this.imageHeight) / 2 + 45; // 32 (pos y) + 12 (alto) + 1 (margen)

        var connection = net.minecraft.client.Minecraft.getInstance().getConnection();
        if (connection == null) return;

        List<String> matches = connection.getOnlinePlayers().stream()
                .map(p -> p.getProfile().getName())
                .filter(name -> name.toLowerCase().startsWith(input.toLowerCase()) && !name.equals(input))
                .limit(3)
                .toList();

        int offset = 0;
        for (int i = 0; i < matches.size(); i++) {
            String suggestion = matches.get(i);
            int sugY = y + offset;
            int width = 90; // Ancho exacto de tu nameInput

            // Fondo: Si el índice de TAB coincide, resaltamos el fondo
            int bgColor = (i == suggestionIndex) ? 0xEE444444 : 0xCC222222;
            graphics.fill(x, sugY, x + width, sugY + 12, bgColor);

            boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= sugY && mouseY <= sugY + 12;
            if (hovered) graphics.fill(x, sugY, x + width, sugY + 12, 0x44FFFFFF);

            graphics.drawString(this.font, "§b" + suggestion, x + 5, sugY + 2, hovered ? 0xFFFFFF : 0xAAAAAA, false);

            if (hovered && net.minecraft.client.Minecraft.getInstance().mouseHandler.isLeftPressed()) {
                nameInput.setValue(suggestion);
                suggestionIndex = -1;
            }
            offset += 13;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics, mouseX, mouseY, partialTicks);
        super.render(graphics, mouseX, mouseY, partialTicks);

        var core = this.menu.getCore();
        if (core == null) return;

        List<String> guests = core.getTrustedNames();
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // --- 1. TÍTULOS ---
        graphics.drawString(this.font, "§6§lInvitar Jugadores", x + 10, y + 22, 0xFFFFFF, false);

        // --- 2. LÓGICA DE CLAN Y UPGRADE ---
        // (Tu lógica de botones de clan y mejora está perfecta aquí)

        // --- 3. LÓGICA DE PERMISOS DINÁMICOS ---
        String currentInput = nameInput.getValue().trim();
        boolean hasName = !currentInput.isEmpty();

        this.buildBtn.active = hasName;
        this.interactBtn.active = hasName;
        this.chestsBtn.active = hasName;

        if (hasName) {
            // MEJORA: Solo pedimos los permisos al core si hemos cambiado de jugador
            // Esto permite que el estado del botón sea fluido mientras el usuario hace clic
            if (!currentInput.equalsIgnoreCase(lastCheckedPlayer)) {
                var perms = core.getPermissionsFor(currentInput);
                this.buildToggle = perms.canBuild;
                this.interactToggle = perms.canInteract;
                this.chestsToggle = perms.canOpenChests;
                lastCheckedPlayer = currentInput;
            }
        } else {
            lastCheckedPlayer = "";
            this.buildToggle = this.interactToggle = this.chestsToggle = false;
        }

        // Actualización de etiquetas de los botones
        this.buildBtn.setMessage(Component.literal("B: " + (buildToggle ? "§aON" : "§cOFF")));
        this.interactBtn.setMessage(Component.literal("I: " + (interactToggle ? "§aON" : "§cOFF")));
        this.chestsBtn.setMessage(Component.literal("C: " + (chestsToggle ? "§aON" : "§cOFF")));

        // --- 4. RENDERIZADO FINAL ---
        // (Tus métodos de renderizado adicionales...)
        renderGuestList(graphics, mouseX, mouseY);
        renderUpgradeRequirements(graphics);
        renderNameSuggestions(graphics, mouseX, mouseY);

        this.renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderGuestList(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        var core = this.menu.getCore();
        if (core == null) return;

        List<String> guests = core.getTrustedNames();
        int listX = x - 105;
        int listY = y + 10;

        graphics.fill(listX, listY, listX + 100, listY + 135, 0x88000000);
        graphics.drawString(this.font, "§6§lJugadores", listX + 5, listY + 5, 0xFFFFFF);

        // FIX: Usar el nombre del dueño guardado en la BlockEntity para evitar problemas de red/cache
        String ownerName = core.getOwnerName();
        if (ownerName == null || ownerName.isEmpty()) ownerName = "Dueño";

        int ownerY = listY + 20;
        graphics.drawString(this.font, "§e★ §f" + ownerName, listX + 5, ownerY, 0xFFAA00);

        int offset = 1;
        for (int i = 0; i < guests.size(); i++) {
            String name = guests.get(i);
            if (name.equalsIgnoreCase(ownerName)) continue;

            int entryY = listY + 20 + (offset * 14);
            int removeBtnX = listX + 85;

            boolean isHoveringRemove = mouseX >= removeBtnX - 2 && mouseX <= removeBtnX + 12 &&
                    mouseY >= entryY - 2 && mouseY <= entryY + 10;

            graphics.drawString(this.font, name, listX + 5, entryY, 0xAAAAAA);
            graphics.drawString(this.font, "§c[X]", removeBtnX, entryY, isHoveringRemove ? 0xFFFFFF : 0xCC0000);

            offset++;
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // Título original
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752);

        // MOSTRAR NIVEL:
        // Obtenemos el nivel desde el menu (que a su vez lo saca de la BlockEntity)
        int level = this.menu.getCore().getCoreLevel();
        String levelText = "Nivel: " + level;

        // Si es nivel máximo, lo ponemos en un color dorado o verde
        int color = (level >= 5) ? 0xFFAA00 : 0x404040;

        // Dibujamos el texto del nivel (ajusta las coordenadas X e Y a tu gusto)
        guiGraphics.drawString(this.font, levelText, 100 , 8, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Clic Izquierdo
            int x = (this.width - this.imageWidth) / 2;
            int y = (this.height - this.imageHeight) / 2;

            var core = this.menu.getCore();
            if (core == null) return super.mouseClicked(mouseX, mouseY, button);

            List<String> guests = core.getTrustedNames();
            int listX = x - 105;
            int listY = y + 10; // Alineado con listY de renderGuestList

            // Iteramos los invitados para detectar clics en la lista lateral
            int offset = 1; // Empezamos después del Owner
            for (int i = 0; i < guests.size(); i++) {
                String name = guests.get(i);
                if (name.equalsIgnoreCase(core.getOwnerName())) continue;

                int entryY = listY + 20 + (offset * 14);
                int removeBtnX = listX + 85;

                // 1. ELIMINAR INVITADO (Clic en la [X])
                if (mouseX >= removeBtnX - 2 && mouseX <= removeBtnX + 12 &&
                        mouseY >= entryY - 2 && mouseY <= entryY + 10) {

                    PacketDistributor.sendToServer(new ChangePermissionPayload(core.getBlockPos(), name, "remove", false));
                    playClickSound();
                    return true;
                }

                // 2. SELECCIONAR INVITADO (Clic en el Nombre)
                if (mouseX >= listX + 5 && mouseX <= removeBtnX - 5 &&
                        mouseY >= entryY && mouseY <= entryY + 10) {

                    this.nameInput.setValue(name);

                    // ACTUALIZACIÓN INMEDIATA: Buscamos los permisos en la BE para actualizar la UI
                    var perms = core.getPermissionsFor(name);
                    this.buildToggle = perms.canBuild;
                    this.interactToggle = perms.canInteract;
                    this.chestsToggle = perms.canOpenChests;

                    playClickSound();
                    return true;
                }
                offset++;
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
        if (keyCode == 257 || keyCode == 335) { // ENTER o NUMPAD ENTER
            handlePlayerAdd();
            return true;
        }

        if (keyCode == 258) { // Tecla TAB
            String input = nameInput.getValue();
            var connection = net.minecraft.client.Minecraft.getInstance().getConnection();

            if (connection != null && input.length() >= 2) {
                List<String> matches = connection.getOnlinePlayers().stream()
                        .map(p -> p.getProfile().getName())
                        .filter(name -> name.toLowerCase().startsWith(input.toLowerCase()))
                        .toList();

                if (!matches.isEmpty()) {
                    suggestionIndex = (suggestionIndex + 1) % matches.size();
                    nameInput.setValue(matches.get(suggestionIndex));
                    return true;
                }
            }
        }
        // Si estás escribiendo, ESC quita el foco, no cierra la GUI de golpe
        if (this.nameInput.isFocused()) {
            if (keyCode == 256) { // ESC
                this.nameInput.setFocused(false);
                return true;
            }
            return this.nameInput.keyPressed(keyCode, scanCode, modifiers);
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
