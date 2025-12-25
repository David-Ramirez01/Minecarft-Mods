package com.tumod.protectormod.blockentity;

import com.tumod.protectormod.menu.ProtectionCoreMenu;
import com.tumod.protectormod.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import java.util.concurrent.ConcurrentHashMap;

public class ProtectionCoreBlockEntity extends BlockEntity implements MenuProvider {

    // Registro estático para acceso desde Comandos/Eventos
    private static final Set<ProtectionCoreBlockEntity> LOADED_CORES = ConcurrentHashMap.newKeySet();

    private int coreLevel = 1;
    protected int adminRadius = 128;
    private UUID ownerUUID;
    private String clanName = "";

    private final Map<String, Boolean> flags = new HashMap<>();
    private final Map<String, PlayerPermissions> permissionsMap = new HashMap<>();

    private final SimpleContainer inventory = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            ProtectionCoreBlockEntity.this.markDirtyAndUpdate();
        }
    };

    // --- CONSTRUCTORES ---

    public ProtectionCoreBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.PROTECTION_CORE_BE.get(), pos, state);
    }

    public ProtectionCoreBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        initializeDefaultFlags();
    }

    // --- LÓGICA DE CARGA Y REGISTRO ---

    public static Set<ProtectionCoreBlockEntity> getLoadedCores() {
        return LOADED_CORES;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!this.level.isClientSide) {
            // Limpiamos cualquier referencia vieja en la misma posición para evitar duplicados
            LOADED_CORES.removeIf(core -> core.isRemoved() || core.getBlockPos().equals(this.worldPosition));
            LOADED_CORES.add(this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        LOADED_CORES.remove(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED_CORES.remove(this);
    }

    public boolean isInside(BlockPos targetPos) {
        int r = getRadius();
        return Math.abs(targetPos.getX() - this.worldPosition.getX()) <= r &&
                Math.abs(targetPos.getZ() - this.worldPosition.getZ()) <= r;
    }

    public boolean areaOverlaps(BlockPos otherPos, int otherRadius) {
        int thisRadius = this.getRadius();
        // Calculamos la distancia en los 3 ejes
        int dx = Math.abs(this.worldPosition.getX() - otherPos.getX());
        int dz = Math.abs(this.worldPosition.getZ() - otherPos.getZ());

        // Si la distancia entre centros es menor a la suma de los radios, hay superposición
        return dx < (thisRadius + otherRadius) &&
                dz < (thisRadius + otherRadius);
    }

    public boolean isTrusted(Player player) {
        // El dueño y los administradores siempre son de confianza
        if (player.getUUID().equals(this.getOwnerUUID()) || player.hasPermissions(2)) {
            return true;
        }

        // Verificamos si el nombre del jugador está en el mapa de permisos con "build" activo
        PlayerPermissions perms = this.permissionsMap.get(player.getName().getString());
        return perms != null && perms.canBuild;
    }

    public List<String> getTrustedNames() {
        return new ArrayList<>(this.permissionsMap.keySet());
    }

    public boolean hasPermission(Player player, String type) {
        // 1. El dueño y los administradores de Minecraft (OP) siempre tienen permiso
        if (player.getUUID().equals(ownerUUID) || player.hasPermissions(2)) {
            return true;
        }

        // 2. Buscamos al jugador en el mapa de invitados por su nombre
        PlayerPermissions perms = permissionsMap.get(player.getName().getString());
        if (perms == null) return false;

        // 3. Verificamos el tipo de permiso solicitado
        return switch (type.toLowerCase()) {
            case "build" -> perms.canBuild;
            case "interact" -> perms.canInteract;
            case "chests" -> perms.canOpenChests;
            default -> false;
        };
    }

    public static void cleanInvalidCores() {
        LOADED_CORES.removeIf(core ->
                core.isRemoved() ||
                        core.getLevel() == null ||
                        core.getLevel().getBlockEntity(core.getBlockPos()) != core
        );
    }

    // --- GESTIÓN DE CLANES Y PERMISOS ---

    public void clearTrustedPlayers() {
        this.permissionsMap.clear();
        this.markDirtyAndUpdate();
    }

    public void resetToDefault() {
        this.permissionsMap.clear();
        this.clanName = "";
        this.coreLevel = 1;
        initializeDefaultFlags();
        this.markDirtyAndUpdate();
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

    public PlayerPermissions getPermissionsFor(String name) {
        return permissionsMap.getOrDefault(name, new PlayerPermissions());
    }

    // --- MEJORAS Y RADIOS ---

    public void upgrade() {
        if (this.coreLevel >= 5 || !canUpgrade()) return;

        int materialCount = (this.coreLevel == 1) ? 64 : 32;
        this.inventory.removeItem(0, 1);
        this.inventory.removeItem(1, materialCount);

        this.coreLevel++;

        if (this.level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, this.worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        this.markDirtyAndUpdate();
    }

    public static final List<String> BASIC_FLAGS = List.of(
            "pvp", "build", "chests", "interact", "explosions" , "villager-trade"
    );

    public static final List<String> ADMIN_FLAGS = List.of(
            "explosions", "mob-spawn", "entry", "fall-damage", "fire-spread" , "lighter" , "item-pickup"
    );

    // En ProtectionCoreBlockEntity.java
    public Set<String> getPlayersWithAnyPermission() {
        // Si usas un Map<String, Map<String, Boolean>> para los permisos:
        return this.permissionsMap.keySet();
    }

    public boolean canPlayerEditFlag(Player player, String flag) {
        // 1. Si es un administrador de Minecraft (OP), puede editar TODO
        if (player.hasPermissions(2)) return true;

        // 2. Si el jugador es el dueño del core:
        if (player.getUUID().equals(this.getOwnerUUID())) {
            // Solo puede editar las flags básicas
            return BASIC_FLAGS.contains(flag);
        }

        return false;
    }


    public boolean canUpgrade() {
        if (isAdmin() || this.coreLevel >= 5) return false;

        ItemStack upgradeItem = inventory.getItem(0);
        ItemStack materialItem = inventory.getItem(1);

        if (!upgradeItem.is(ModItems.PROTECTION_UPGRADE.get())) return false;

        return switch (this.coreLevel) {
            case 1 -> materialItem.is(Items.IRON_INGOT) && materialItem.getCount() >= 64;
            case 2 -> materialItem.is(Items.GOLD_INGOT) && materialItem.getCount() >= 32;
            case 3 -> materialItem.is(Items.DIAMOND) && materialItem.getCount() >= 32;
            case 4 -> materialItem.is(Items.NETHERITE_INGOT) && materialItem.getCount() >= 32;
            default -> false;
        };
    }

    public int getRadius() {
        if (isAdmin()) return this.adminRadius;
        return switch (this.coreLevel) {
            case 2 -> 16; case 3 -> 32; case 4 -> 48; case 5 -> 64;
            default -> 8;
        };
    }

    public int getAdminRadius() {
        return this.adminRadius;
    }

    public boolean isAdmin() {
        return this.getBlockState().is(ModBlocks.ADMIN_PROTECTOR.get());
    }

    // --- FLAGS ---

    public void initializeDefaultFlags() {
        this.flags.clear();
        for (String f : getAllFlagKeys()) {
            if (f.equals("entry")) {
                flags.put(f, true);
            } else {
                flags.put(f, false);
            }
        }
    }



    // --- MÉTODOS PARA EL MANEJADOR DE PAQUETES (NETWORKING) ---

    /**
     * Elimina a un jugador de la lista de invitados por su nombre.
     */
    public void removePlayerPermissions(String playerName) {
        if (this.permissionsMap.containsKey(playerName)) {
            this.permissionsMap.remove(playerName);
            this.markDirtyAndUpdate();
        }
    }

    /**
     * Cambia el radio masivo del Admin Core.
     */
    public void setAdminRadius(int newRadius) {
        this.adminRadius = newRadius;
        this.markDirtyAndUpdate();
    }

    /**
     * Activa o desactiva el PvP en el área.
     */
    public void setPvpEnabled(boolean enabled) {
        this.setFlag("pvp", enabled);
        // El método setFlag ya llama a markDirtyAndUpdate()
    }

    /**
     * Activa o desactiva las explosiones en el área.
     */
    public void setExplosionsDisabled(boolean disabled) {
        // En tu lógica de flags: false = protegido (explosiones desactivadas)
        // Por lo tanto, si disabled es true, ponemos la flag en false.
        this.setFlag("explosions", !disabled);
    }

    public List<String> getAllFlagKeys() {
        return List.of("pvp", "explosions", "break", "build", "interact", "chests", "mob-spawn", "mob-grief", "fire-spread", "use-buckets", "item-pickup", "item-drop", "crop-trample", "lighter", "damage-animals", "villager-trade", "entry", "enderpearl", "fall-damage", "hunger");
    }

    public boolean getFlag(String flag) { return flags.getOrDefault(flag, false); }
    public void setFlag(String flag, boolean value) { flags.put(flag, value); markDirtyAndUpdate(); }

    // --- PERSISTENCIA NBT ---

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

        this.flags.clear();
        if (tag.contains("CoreFlags")) {
            CompoundTag flagsTag = tag.getCompound("CoreFlags");
            for (String key : flagsTag.getAllKeys()) this.flags.put(key, flagsTag.getBoolean(key));
        }

        this.permissionsMap.clear();
        if (tag.contains("PermissionsData")) {
            CompoundTag permsTag = tag.getCompound("PermissionsData");
            for (String name : permsTag.getAllKeys()) {
                CompoundTag pTag = permsTag.getCompound(name);
                this.permissionsMap.put(name, new PlayerPermissions(pTag.getBoolean("build"), pTag.getBoolean("interact"), pTag.getBoolean("chests")));
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

    // --- SINCRONIZACIÓN ---

    public void markDirtyAndUpdate() {
        this.setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void setCoreLevelClient(int level) {
        this.coreLevel = level;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    // --- GUI Y OTROS ---

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new ProtectionCoreMenu(isAdmin() ? ModMenus.ADMIN_CORE_MENU.get() : ModMenus.PROTECTION_CORE_MENU.get(), id, inv, this);
    }

    @Override public Component getDisplayName() { return Component.literal("Protection Core"); }

    public static class PlayerPermissions {
        public boolean canBuild, canInteract, canOpenChests;
        public PlayerPermissions() {}
        public PlayerPermissions(boolean b, boolean i, boolean c) { this.canBuild = b; this.canInteract = i; this.canOpenChests = c; }
    }

    // Getters y Setters básicos

    public UUID getOwnerUUID() {
        return this.ownerUUID != null ? this.ownerUUID : UUID.nameUUIDFromBytes("none".getBytes());
    }

    public void setOwner(UUID uuid) { this.ownerUUID = uuid; markDirtyAndUpdate(); }
    public void setClanName(String name) { this.clanName = name; markDirtyAndUpdate(); }
    public String getClanName() { return this.clanName; }
    public int getCoreLevel() { return this.coreLevel; }
    public SimpleContainer getInventory() { return this.inventory; }
}
