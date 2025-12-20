package com.tumod.protectormod.blockentity;

import com.tumod.protectormod.menu.ProtectionCoreMenu;
import com.tumod.protectormod.network.SyncCoreLevelPayload;
import com.tumod.protectormod.registry.ModBlockEntities;
import com.tumod.protectormod.registry.ModItems;
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
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ProtectionCoreBlockEntity extends BlockEntity implements MenuProvider {

    // Lista estática para que los eventos puedan consultar áreas protegidas rápidamente
    public static final Set<ProtectionCoreBlockEntity> CORES = new HashSet<>();

    private int coreLevel = 1;
    private UUID owner;
    private final Set<UUID> trustedPlayers = new HashSet<>();

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

    // === LÓGICA DE PROTECCIÓN (Llamada por ProtectionEvent) ===

    public UUID getOwnerUUID() { return this.owner; }

    // Método solicitado por el sistema de eventos para validar acceso
    public boolean isTrusted(Player player) {
        if (player.getUUID().equals(owner)) return true;
        return trustedPlayers.contains(player.getUUID());
    }

    // Calcula si una posición está dentro del cubo de protección
    public boolean isInside(BlockPos targetPos) {
        int r = getRadius();
        // Obtenemos la distancia absoluta en cada eje desde el centro (el Core)
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


    // === GESTIÓN DE NBT ===

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("CoreLevel", coreLevel);
        if (owner != null) tag.putUUID("Owner", owner);

        ListTag trustedList = new ListTag();
        for (UUID uuid : trustedPlayers) {
            CompoundTag uTag = new CompoundTag();
            uTag.putUUID("id", uuid);
            trustedList.add(uTag);
        }
        tag.put("Trusted", trustedList);

        // Guardado de inventario 1.21.1
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

        this.trustedPlayers.clear();
        if (tag.contains("Trusted")) {
            ListTag list = tag.getList("Trusted", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                trustedPlayers.add(list.getCompound(i).getUUID("id"));
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

    // === MEJORAS Y SINCRONIZACIÓN ===

    public void upgrade() {
        if (!canUpgrade()) return;
        this.inventory.getItem(0).shrink(1);
        int cost = (this.coreLevel == 1) ? 64 : 32;
        this.inventory.getItem(1).shrink(cost);
        this.coreLevel++;
        markDirtyAndUpdate();
        if (this.level != null && !this.level.isClientSide) {
            PacketDistributor.sendToPlayersTrackingChunk(
                    (ServerLevel) this.level,
                    new ChunkPos(this.worldPosition),
                    new SyncCoreLevelPayload(this.worldPosition, this.coreLevel)
            );
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

    public void setCoreLevelClient(int level) {
        this.coreLevel = level;
        setChanged();
    }

    // === MÉTODOS REQUERIDOS ===

    public void setOwner(UUID uuid) { this.owner = uuid; markDirtyAndUpdate(); }
    public SimpleContainer getInventory() { return this.inventory; }
    public int getCoreLevel() { return this.coreLevel; }

    @Override
    public Component getDisplayName() { return Component.literal("Protection Core"); }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new ProtectionCoreMenu(id, inv, this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void markDirtyAndUpdate() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
}


