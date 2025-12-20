package com.tumod.protectormod.menu;

import com.tumod.protectormod.blockentity.ProtectionCoreBlockEntity;
import com.tumod.protectormod.registry.ModBlocks;
import com.tumod.protectormod.registry.ModItems;
import com.tumod.protectormod.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ProtectionCoreMenu extends AbstractContainerMenu {

    private final ProtectionCoreBlockEntity core;
    private final ContainerLevelAccess access;

    // 🔹 CONSTRUCTOR PARA EL SERVIDOR
    public ProtectionCoreMenu(int id, Inventory playerInv, ProtectionCoreBlockEntity core) {
        super(ModMenus.PROTECTION_CORE_MENU.get(), id);

        if (core == null) {
            throw new IllegalStateException("ProtectionCoreBlockEntity is null");
        }

        this.core = core;
        this.access = ContainerLevelAccess.create(core.getLevel(), core.getBlockPos());

        // Slot 0: Mejora (Solo acepta el ítem de mejora)
        this.addSlot(new Slot(core.getInventory(), 0, 16, 117) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.PROTECTION_UPGRADE.get());
            }
        });

// Slot 1: Coste (En el constructor de ProtectionCoreMenu)
        this.addSlot(new Slot(core.getInventory(), 1, 144, 117) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                // Usamos la lógica de validación que ya tienes en la BlockEntity
                int currentLevel = core.getCoreLevel();
                return switch (currentLevel) {
                    case 1 -> stack.is(Items.IRON_INGOT);
                    case 2 -> stack.is(Items.GOLD_INGOT);
                    case 3 -> stack.is(Items.DIAMOND);
                    case 4 -> stack.is(Items.NETHERITE_INGOT);
                    default -> false;
                };
            }
        });

        addPlayerInventory(playerInv);
        addPlayerHotbar(playerInv);
    }

    // 🔹 CONSTRUCTOR PARA EL CLIENTE (Invocado por NeoForge)
    public ProtectionCoreMenu(int id, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(id, playerInv, getBlockEntity(playerInv, buf));
    }

    private static ProtectionCoreBlockEntity getBlockEntity(Inventory inv, RegistryFriendlyByteBuf buf) {
        var pos = buf.readBlockPos();
        var be = inv.player.level().getBlockEntity(pos);

        if (be instanceof ProtectionCoreBlockEntity core) {
            return core;
        }
        throw new IllegalStateException("BlockEntity at " + pos + " is missing!");
    }

    @Override
    public boolean stillValid(Player player) {
        // Vinculamos la validez al bloque PROTECTION_CORE
        return stillValid(this.access, player, ModBlocks.PROTECTION_CORE.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            int containerSlots = 2; // Slots de la máquina (0 y 1)

            if (index < containerSlots) {
                // De la máquina al inventario del jugador
                if (!this.moveItemStackTo(stack, containerSlots, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Del inventario del jugador a la máquina
                if (!this.moveItemStackTo(stack, 0, containerSlots, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }

    private void addPlayerInventory(Inventory inv) {
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 140 + row * 18));
            }
        }
    }
    private void addPlayerHotbar(Inventory inv) {
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(inv, col, 8 + col * 18, 198));
        }
    }

    // Dentro de ProtectionCoreMenu.java
    public void handleUpgradeRequest() {
        // Verificamos que estamos operando sobre la BlockEntity
        if (this.core != null) {
            this.core.upgrade();
            // Al llamar a upgrade(), la BE consume los ítems y sube el nivel
        }
    }

    public ProtectionCoreBlockEntity getCore() {
        return core;
    }
}


