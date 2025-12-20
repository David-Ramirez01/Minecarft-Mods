package com.tumod.protectormod.blockentity;

import com.tumod.protectormod.menu.ProtectionCoreMenu;
import com.tumod.protectormod.network.SyncCoreLevelPayload;
import com.tumod.protectormod.registry.ModBlockEntities;
import com.tumod.protectormod.registry.ModItems;
import com.tumod.protectormod.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ProtectionCoreBlockEntity extends BlockEntity implements MenuProvider {

    public static final Set<ProtectionCoreBlockEntity> CORES = new HashSet<>();

    private int coreLevel = 1;
    private UUID owner;

    // Estructura de Permisos Unificada
    private final Map<String, PlayerPermissions> permissionsMap = new HashMap<>();

    public static class PlayerPermissions {
        public boolean canBuild = false;
        public boolean canInteract = false;
        public boolean canOpenChests = false;

        public PlayerPermissions() {}

        public PlayerPermissions(boolean b, boolean i, boolean c) {
            this.canBuild = b;
            this.canInteract = i;
            this.canOpenChests = c;
        }
    }

    private final SimpleContainer inventory = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            ProtectionCoreBlockEntity.this.markDirtyAndUpdate();
        }
    };

    public ProtectionCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PROTECTION_CORE_BE.get(), pos, state);
    }

    public ProtectionCoreBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level != null && !this.level.isClientSide) {
            CORES.add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CORES.remove(this);
    }

    // === SISTEMA DE PERMISOS ===

    public void updatePermission(String playerName, String type, boolean value) {
        PlayerPermissions perms = permissionsMap.computeIfAbsent(playerName, k -> new PlayerPermissions());
        switch (type) {
            case "build" -> perms.canBuild = value;
            case "interact" -> perms.canInteract = value;
            case "chests" -> perms.canOpenChests = value;
        }
        markDirtyAndUpdate();
    }

    public boolean hasPermission(Player player, String type) {
        if (player.getUUID().equals(owner) || player.hasPermissions(2)) return true;
        PlayerPermissions perms = permissionsMap.get(player.getName().getString());
        if (perms == null) return false;

        return switch (type) {
            case "build" -> perms.canBuild;
            case "interact" -> perms.canInteract;
            case "chests" -> perms.canOpenChests;
            default -> false;
        };
    }

    public boolean isTrusted(Player player) {
        return hasPermission(player, "build");
    }

    public List<String> getTrustedNames() {
        return permissionsMap.entrySet().stream()
                .filter(entry -> {
                    PlayerPermissions p = entry.getValue();
                    // Solo incluimos si tiene algún permiso activo
                    return p.canBuild || p.canInteract || p.canOpenChests;
                })
                .map(Map.Entry::getKey)
                .toList();
    }

    // === LÓGICA DE ÁREA ===

    public boolean isInside(BlockPos targetPos) {
        int r = getRadius();
        int distX = Math.abs(targetPos.getX() - this.worldPosition.getX());
        int distY = Math.abs(targetPos.getY() - this.worldPosition.getY());
        int distZ = Math.abs(targetPos.getZ() - this.worldPosition.getZ());
        return distX <= r && distY <= r && distZ <= r;
    }

    public int getRadius() {
        return switch (this.coreLevel) {
            case 2 -> 16;
            case 3 -> 32;
            case 4 -> 48;
            case 5 -> 64;
            default -> 8;
        };
    }

    // === NBT (GUARDADO PERSISTENTE) ===

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("CoreLevel", coreLevel);
        if (owner != null) tag.putUUID("Owner", owner);

        CompoundTag permsTag = new CompoundTag();
        permissionsMap.forEach((name, perms) -> {
            CompoundTag pTag = new CompoundTag();
            pTag.putBoolean("build", perms.canBuild);
            pTag.putBoolean("interact", perms.canInteract);
            pTag.putBoolean("chests", perms.canOpenChests);
            permsTag.put(name, pTag);
        });
        tag.put("PermissionsData", permsTag);

        ListTag items = new ListTag();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack st = inventory.getItem(i);
            if (!st.isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putInt("Slot", i);
                items.add(st.save(registries, itemTag));
            }
        }
        tag.put("Items", items);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.coreLevel = tag.getInt("CoreLevel");
        if (tag.hasUUID("Owner")) this.owner = tag.getUUID("Owner");

        this.permissionsMap.clear();
        if (tag.contains("PermissionsData")) {
            CompoundTag permsTag = tag.getCompound("PermissionsData");
            for (String name : permsTag.getAllKeys()) {
                CompoundTag pTag = permsTag.getCompound(name);
                this.permissionsMap.put(name, new PlayerPermissions(
                        pTag.getBoolean("build"),
                        pTag.getBoolean("interact"),
                        pTag.getBoolean("chests")
                ));
            }
        }

        this.inventory.clearContent();
        if (tag.contains("Items")) {
            ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < items.size(); i++) {
                CompoundTag itemTag = items.getCompound(i);
                int slot = itemTag.getInt("Slot");
                ItemStack stack = ItemStack.parse(registries, itemTag).orElse(ItemStack.EMPTY);
                if (slot >= 0 && slot < inventory.getContainerSize()) {
                    inventory.setItem(slot, stack);
                }
            }
        }
    }

    // === OTROS ===

// Dentro de ProtectionCoreBlockEntity.java

    public void upgrade() {
        if (!canUpgrade()) return;

        // Consumir ítems
        this.inventory.getItem(0).shrink(1);
        int cost = (this.coreLevel == 1) ? 64 : 32;
        this.inventory.getItem(1).shrink(cost);

        // Subir nivel
        this.coreLevel++;
        markDirtyAndUpdate();

        if (this.level != null && !this.level.isClientSide) {
            // 1. Sonido para todos los jugadores cerca (Nivel del sonido aumenta con el nivel del core)
            this.level.playSound(null, this.worldPosition,
                    ModSounds.CORE_UPGRADE.get(),
                    net.minecraft.sounds.SoundSource.BLOCKS,
                    1.0F, 0.5F + (this.coreLevel * 0.1F));

            // 2. Sincronizar nivel con clientes
            PacketDistributor.sendToPlayersTrackingChunk((ServerLevel) this.level,
                    new ChunkPos(this.worldPosition),
                    new SyncCoreLevelPayload(this.worldPosition, this.coreLevel));
        }
    }

    public boolean canUpgrade() {
        if (coreLevel >= 5) return false;
        ItemStack upgr = inventory.getItem(0);
        ItemStack cost = inventory.getItem(1);
        if (!upgr.is(ModItems.PROTECTION_UPGRADE.get())) return false;
        return switch (coreLevel) {
            case 1 -> cost.is(Items.IRON_INGOT) && cost.getCount() >= 64;
            case 2 -> cost.is(Items.GOLD_INGOT) && cost.getCount() >= 32;
            case 3 -> cost.is(Items.DIAMOND) && cost.getCount() >= 32;
            case 4 -> cost.is(Items.NETHERITE_INGOT) && cost.getCount() >= 32;
            default -> false;
        };
    }

    public void markDirtyAndUpdate() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public UUID getOwnerUUID() { return owner; }
    public void setOwner(UUID uuid) { this.owner = uuid; markDirtyAndUpdate(); }
    public SimpleContainer getInventory() { return this.inventory; }
    public int getCoreLevel() { return this.coreLevel; }
    public void setCoreLevelClient(int level) { this.coreLevel = level; }

    @Override public Component getDisplayName() { return Component.literal("Protection Core"); }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) { return new ProtectionCoreMenu(id, inv, this); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { CompoundTag tag = new CompoundTag(); saveAdditional(tag, registries); return tag; }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
