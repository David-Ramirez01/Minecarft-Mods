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
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ProtectionCoreMenu extends AbstractContainerMenu {
    private final ProtectionCoreBlockEntity core; // Usaremos solo 'core' para evitar confusiones
    private final ContainerLevelAccess access;

    // 🔹 CONSTRUCTOR PARA EL SERVIDOR
    public ProtectionCoreMenu(MenuType<?> type, int id, Inventory playerInv, ProtectionCoreBlockEntity core) {
        super(type, id);

        if (core == null) throw new IllegalStateException("ProtectionCoreBlockEntity is null");

        this.core = core;
        this.access = ContainerLevelAccess.create(core.getLevel(), core.getBlockPos());

        // VERIFICACIÓN: ¿Es un Admin Core?
        boolean isAdminCore = core.getBlockState().is(ModBlocks.ADMIN_PROTECTOR.get());

        // Solo añadimos los slots si NO es el Admin Core
        if (!isAdminCore) {
            // Slot 0: Mejora
            this.addSlot(new Slot(core.getInventory(), 0, 15, 105) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(ModItems.PROTECTION_UPGRADE.get());
                }
            });

            // Slot 1: Coste
            this.addSlot(new Slot(core.getInventory(), 1, 35, 105) {
                @Override
                public boolean mayPlace(ItemStack stack) {
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
        }

        addPlayerInventory(playerInv);
        addPlayerHotbar(playerInv);
    }

    // 🔹 CONSTRUCTOR PARA EL CLIENTE (Invocado por NeoForge)
    public ProtectionCoreMenu(int id, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(ModMenus.PROTECTION_CORE_MENU.get(), id, playerInv, getBlockEntity(playerInv, buf));
    }

    // 🔹 MÉTODO PÚBLICO para el registro de Admin Core
    public static ProtectionCoreMenu createAdminMenu(int id, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        return new ProtectionCoreMenu(ModMenus.ADMIN_CORE_MENU.get(), id, playerInv, getBlockEntity(playerInv, buf));
    }

    // 🔹 CAMBIADO A PUBLIC: Ahora AdminCoreScreen puede usarlo si lo necesita
    public static ProtectionCoreBlockEntity getBlockEntity(Inventory inv, RegistryFriendlyByteBuf buf) {
        var pos = buf.readBlockPos();
        var be = inv.player.level().getBlockEntity(pos);

        if (be instanceof ProtectionCoreBlockEntity coreBE) {
            return coreBE;
        }
        throw new IllegalStateException("BlockEntity at " + pos + " is missing!");
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, ModBlocks.PROTECTION_CORE.get())
                || stillValid(this.access, player, ModBlocks.ADMIN_PROTECTOR.get());
    }

    // --- MÉTODOS DE ACCESO PARA LA SCREEN ---

    public ProtectionCoreBlockEntity getBlockEntity() {
        return this.core;
    }

    public ProtectionCoreBlockEntity getCore() {
        return this.core;
    }

    // --- LÓGICA DE SLOTS ---

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Si es Admin Core, no hay slots de máquina (containerSlots = 0)
        boolean isAdmin = core.getBlockState().is(ModBlocks.ADMIN_PROTECTOR.get());
        int containerSlots = isAdmin ? 0 : 2;

        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index < containerSlots) {
                if (!this.moveItemStackTo(stack, containerSlots, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Solo movemos a la máquina si hay slots disponibles
                if (containerSlots > 0) {
                    if (!this.moveItemStackTo(stack, 0, containerSlots, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    return ItemStack.EMPTY; // No hay donde mover en el Admin Core
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
}


