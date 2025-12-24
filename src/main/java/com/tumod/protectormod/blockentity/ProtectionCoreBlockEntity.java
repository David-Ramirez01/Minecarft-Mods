package com.tumod.protectormod.blockentity;

import com.tumod.protectormod.menu.ProtectionCoreMenu;
import net.minecraft.sounds.SoundSource;
import com.tumod.protectormod.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ProtectionCoreBlockEntity extends BlockEntity implements MenuProvider {

    public static final Set<ProtectionCoreBlockEntity> CORES = new HashSet<>();

    private int coreLevel = 1;
    private int radius = 10;
    private int adminRadius = 128;
    private UUID ownerUUID;
    private String clanName = "";

    private final Map<String, Boolean> flags = new HashMap<>();
    private final Map<String, PlayerPermissions> permissionsMap = new HashMap<>();

    public static class PlayerPermissions {
        public boolean canBuild = false;      // Cambiar a public
        public boolean canInteract = false;   // Cambiar a public
        public boolean canOpenChests = false; // Cambiar a public

        public PlayerPermissions() {}

        public PlayerPermissions(boolean b, boolean i, boolean c) {
            this.canBuild = b;
            this.canInteract = i;
            this.canOpenChests = c;
        }
    }

    // Dentro de ProtectionCoreBlockEntity.java
    public PlayerPermissions getPermissionsFor(String name) {
        // Si el jugador no está en el mapa, devolvemos un objeto con permisos en 'false'
        return permissionsMap.getOrDefault(name, new PlayerPermissions());
    }

    public void upgrade() {
        // 1. Verificación de nivel máximo
        if (this.coreLevel >= 5) return;

        // 2. Validación de materiales
        if (canUpgrade()) {
            // 3. Determinar cantidad de materiales a remover según el nivel actual
            // Nivel 1 requiere 64, niveles superiores requieren 32 (según tu lógica de canUpgrade)
            int materialCountToRemove = (this.coreLevel == 1) ? 64 : 32;

            // Consumir ítems
            this.inventory.removeItem(0, 1); // Quita 1 Protection Upgrade
            this.inventory.removeItem(1, materialCountToRemove); // Quita la cantidad justa del material

            // 4. Incrementar nivel
            this.coreLevel++;

            // 5. Marcar para guardar (Persistencia)
            this.setChanged();

            // 6. Sincronización Lado Servidor -> Lado Cliente
            if (this.level instanceof ServerLevel serverLevel) {
                // Actualiza el bloque para todos los jugadores cercanos (importante para la GUI)
                serverLevel.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);

                // CORRECCIÓN DE SONIDO: SoundSource está en net.minecraft.sounds
                serverLevel.playSound(null, this.worldPosition,
                        net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,
                        net.minecraft.sounds.SoundSource.BLOCKS, // Ubicación correcta
                        1.0F, 1.0F);
            }
        }
    }



    // --- MÉTODOS PARA EL ADMIN CORE ---

    /**
     * Atajo para cambiar la flag de PvP
     */
    public void setPvpEnabled(boolean enabled) {
        this.setFlag("pvp", enabled);
    }

    /**
     * Atajo para cambiar la flag de Explosiones
     * Nota: Si la flag es "explosions", true significa que HAY explosiones.
     */
    public void setExplosionsDisabled(boolean disabled) {
        this.setFlag("explosions", !disabled);
    }

    /**
     * Cambia el radio masivo del Admin Core
     */
    public void setAdminRadius(int newRadius) {
        this.adminRadius = newRadius;
        this.markDirtyAndUpdate();
    }

// --- MÉTODOS DE PERMISOS ---

    /**
     * Elimina completamente a un jugador del mapa de permisos (invitados)
     */
    public void removePlayerPermissions(String playerName) {
        if (this.permissionsMap.containsKey(playerName)) {
            this.permissionsMap.remove(playerName);
            this.markDirtyAndUpdate();
        }
    }

    // Dentro de ProtectionCoreBlockEntity.java
    public boolean canUpgrade() {
        // Los bloques de admin no suben de nivel
        if (this.getBlockState().is(ModBlocks.ADMIN_PROTECTOR.get())) return false;
        if (this.coreLevel >= 5) return false;

        ItemStack upgradeItem = inventory.getItem(0); // Slot de la mejora
        ItemStack materialItem = inventory.getItem(1); // Slot del material

        // Verificar si el ítem de mejora está presente
        if (!upgradeItem.is(ModItems.PROTECTION_UPGRADE.get())) return false;

        // Verificar el coste por nivel
        return switch (this.coreLevel) {
            case 1 -> materialItem.is(Items.IRON_INGOT) && materialItem.getCount() >= 64;
            case 2 -> materialItem.is(Items.GOLD_INGOT) && materialItem.getCount() >= 32;
            case 3 -> materialItem.is(Items.DIAMOND) && materialItem.getCount() >= 32;
            case 4 -> materialItem.is(Items.NETHERITE_INGOT) && materialItem.getCount() >= 32;
            default -> false;
        };
    }

    public void setCoreLevelClient(int level) {
        this.coreLevel = level;
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
        initializeDefaultFlags();
    }

    public ProtectionCoreBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        initializeDefaultFlags();
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

    // === MÉTODOS AÑADIDOS PARA COMPATIBILIDAD CON CLANCOMMANDS ===

    public Map<String, PlayerPermissions> getPermissionsMap() {
        return this.permissionsMap;
    }

    public void initializeDefaultFlags() {
        for (String f : getAllFlagKeys()) {
            flags.put(f, false); // Por defecto, las protecciones están activas (flag false)
        }
    }

    public List<String> getAllFlagKeys() {
        return List.of(
                "pvp", "explosions", "break", "build", "interact",
                "chests", "mob-spawn", "mob-grief", "fire-spread", "use-buckets",
                "item-pickup", "item-drop", "crop-trample", "lighter", "damage-animals",
                "villager-trade", "entry", "enderpearl", "fall-damage", "hunger"
        );
    }

    public void resetToDefault() {
        this.permissionsMap.clear();
        this.clanName = "";
        initializeDefaultFlags();
        markDirtyAndUpdate();
    }

    // === GESTIÓN DE FLAGS Y PERMISOS ===

    public boolean getFlag(String flag) {
        return flags.getOrDefault(flag, false);
    }

    public void setFlag(String flag, boolean value) {
        flags.put(flag, value);
        markDirtyAndUpdate();
    }

    public boolean isTrusted(Player player) {
        // 1. El dueño y los administradores siempre son de confianza
        if (player.getUUID().equals(this.getOwnerUUID()) || player.hasPermissions(2)) {
            return true;
        }

        // 2. Verificamos si el nombre del jugador está en el mapa de permisos con "build" activo
        PlayerPermissions perms = this.permissionsMap.get(player.getName().getString());
        return perms != null && perms.canBuild;
    }

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
        if (player.getUUID().equals(ownerUUID) || player.hasPermissions(2)) return true;
        PlayerPermissions perms = permissionsMap.get(player.getName().getString());
        if (perms == null) return false;

        return switch (type) {
            case "build" -> perms.canBuild;
            case "interact" -> perms.canInteract;
            case "chests" -> perms.canOpenChests;
            default -> false;
        };
    }

    public List<String> getTrustedNames() {
        return new ArrayList<>(permissionsMap.keySet());
    }

    // === LÓGICA DE ÁREA ===

    public boolean isInside(BlockPos targetPos) {
        int r = getRadius();
        return Math.abs(targetPos.getX() - this.worldPosition.getX()) <= r &&
                Math.abs(targetPos.getY() - this.worldPosition.getY()) <= r &&
                Math.abs(targetPos.getZ() - this.worldPosition.getZ()) <= r;
    }

    public int getRadius() {
        if (this.getBlockState().is(ModBlocks.ADMIN_PROTECTOR.get())) return this.adminRadius;
        return switch (this.coreLevel) {
            case 2 -> 16; case 3 -> 32; case 4 -> 48; case 5 -> 64;
            default -> 8;
        };
    }

    public boolean areaOverlaps(BlockPos otherPos, int otherRadius) {
        int thisRadius = this.getRadius();
        return Math.abs(this.worldPosition.getX() - otherPos.getX()) <= (thisRadius + otherRadius) &&
                Math.abs(this.worldPosition.getY() - otherPos.getY()) <= (thisRadius + otherRadius) &&
                Math.abs(this.worldPosition.getZ() - otherPos.getZ()) <= (thisRadius + otherRadius);
    }

    // === NBT Y SINCRONIZACIÓN ===

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("CoreLevel", this.coreLevel);
        tag.putInt("AdminRadius", this.adminRadius);
        tag.putString("ClanName", this.clanName);
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);

        CompoundTag flagsTag = new CompoundTag();
        flags.forEach(flagsTag::putBoolean);
        tag.put("CoreFlags", flagsTag);

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
        this.adminRadius = tag.getInt("AdminRadius");
        this.clanName = tag.getString("ClanName");
        if (tag.hasUUID("Owner")) this.ownerUUID = tag.getUUID("Owner");

        if (tag.contains("CoreFlags")) {
            CompoundTag flagsTag = tag.getCompound("CoreFlags");
            for (String key : flagsTag.getAllKeys()) {
                this.flags.put(key, flagsTag.getBoolean(key));
            }
        }

        this.permissionsMap.clear();
        if (tag.contains("PermissionsData")) {
            CompoundTag permsTag = tag.getCompound("PermissionsData");
            for (String name : permsTag.getAllKeys()) {
                CompoundTag pTag = permsTag.getCompound(name);
                this.permissionsMap.put(name, new PlayerPermissions(
                        pTag.getBoolean("build"), pTag.getBoolean("interact"), pTag.getBoolean("chests")));
            }
        }

        this.inventory.clearContent();
        if (tag.contains("Items")) {
            ListTag items = tag.getList("Items", 10);
            for (int i = 0; i < items.size(); i++) {
                CompoundTag itemTag = items.getCompound(i);
                int slot = itemTag.getInt("Slot");
                ItemStack stack = ItemStack.parse(registries, itemTag).orElse(ItemStack.EMPTY);
                if (slot >= 0 && slot < inventory.getContainerSize()) inventory.setItem(slot, stack);
            }
        }
    }

    public void setClanName(String name) {
        this.clanName = name;
        this.setChanged(); // Esto asegura que el nombre se guarde en el archivo de la región (.mca)
    }

    public void markDirtyAndUpdate() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        // Esta lógica detecta el bloque físico en el mundo
        if (this.getBlockState().is(ModBlocks.ADMIN_PROTECTOR.get())) {
            return new ProtectionCoreMenu(ModMenus.ADMIN_CORE_MENU.get(), id, inv, this);
        }
        return new ProtectionCoreMenu(ModMenus.PROTECTION_CORE_MENU.get(), id, inv, this);
    }

    public void setOwner(UUID uuid) { this.ownerUUID = uuid; this.setChanged(); }
    public UUID getOwnerUUID() { return this.ownerUUID != null ? this.ownerUUID : net.minecraft.Util.NIL_UUID; }
    public SimpleContainer getInventory() { return this.inventory; }
    public int getCoreLevel() { return this.coreLevel; }
    public String getClanName() { return this.clanName; }

    @Override public Component getDisplayName() { return Component.literal("Protection Core"); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { CompoundTag tag = new CompoundTag(); saveAdditional(tag, registries); return tag; }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
