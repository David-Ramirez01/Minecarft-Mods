package com.tumod.protectormod.blockentity;

import com.tumod.protectormod.block.ProtectionCoreBlock;
import com.tumod.protectormod.menu.ProtectionCoreMenu;
import com.tumod.protectormod.registry.*;
import com.tumod.protectormod.util.ClanSavedData;
import com.tumod.protectormod.util.ProtectionDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.items.ItemStackHandler;


import java.util.*;
import java.util.stream.Collectors;

public class ProtectionCoreBlockEntity extends BlockEntity implements MenuProvider {

    private int coreLevel = 1;
    private int range = 10;
    public int adminRadius = 128;
    private UUID ownerUUID;
    protected String clanName = "";
    private String ownerName = "Protector";

    private final Map<String, Boolean> flags = new HashMap<>();



    // MEJORA: Ahora usamos UUID como clave primaria y un cache para nombres
    protected final Map<UUID, PlayerPermissions> permissionsMap = new HashMap<>();
    private final Map<UUID, String> nameCache = new HashMap<>();

    private final ItemStackHandler inventory = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            // En NeoForge, usamos este método en lugar de setChanged()
            ProtectionCoreBlockEntity.this.setChanged();
            ProtectionCoreBlockEntity.this.markDirtyAndUpdate();
        }
    };

    public void initializeDefaultFlags() {
        this.flags.clear();
        for (String f : getAllFlagKeys()) {
            // Permitir entrar y hambre por defecto, el resto protegido
            if (f.equals("entry") || f.equals("hunger") || f.equals("fire-spread")) {
                flags.put(f, true);
            } else {
                flags.put(f, false);
            }
        }
    }

    public List<String> getAllFlagKeys() {
        return List.of("pvp", "explosions", "break", "build", "interact", "chests",
                "mob-spawn", "mob-grief", "fire-spread", "fire-damage",
                "use-buckets", "item-pickup", "item-drop", "crop-trample",
                "lighter", "damage-animals", "villager-trade", "entry",
                "enderpearl", "fall-damage", "hunger");
    }
    public ProtectionCoreBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.PROTECTION_CORE_BE.get(), pos, state);
    }

    public ProtectionCoreBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        initializeDefaultFlags();
    }

    // --- INTEGRACIÓN CON MANAGER (REEMPLAZA LOADED_CORES) ---

    @Override
    public void onLoad() {
        super.onLoad();
        if (this.level instanceof ServerLevel serverLevel) {
            if (this.getBlockState().getValue(ProtectionCoreBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
                ProtectionDataManager data = ProtectionDataManager.get(serverLevel);
                data.addCore(this.worldPosition, getOwnerUUID(), getRadius());
                data.syncToAll(serverLevel); // <--- NUEVO: Sincroniza con clientes
            }
        }
    }

    @Override
    public void setRemoved() {
        if (this.level instanceof ServerLevel serverLevel) {
            ProtectionDataManager data = ProtectionDataManager.get(serverLevel);
            data.removeCore(this.worldPosition);
            data.syncToAll(serverLevel); // <--- NUEVO: Sincroniza con clientes
        }
        super.setRemoved();
    }

    public void setRadius(int newRadius) {
        if (isAdmin()) {
            this.adminRadius = newRadius;
        } else {
            this.range = newRadius;
        }
        this.markDirtyAndUpdate();
    }

    // Añadir a ProtectionCoreBlockEntity.java

    public ItemStackHandler getInventory() {
        return this.inventory;
    }

    public boolean isInside(BlockPos targetPos) {
        return this.worldPosition.distSqr(targetPos) <= (double) (this.range * this.range);
    }

    public int getCoreLevel() {
        return this.coreLevel;
    }

    public String getClanName() {
        // Si no usas clanes aún, devuelve un String vacío o el campo que tengas
        return this.clanName != null ? this.clanName : "";
    }

    public void setClanName(String name) {
        this.clanName = name;
        this.markDirtyAndUpdate();
    }

    public void setCoreLevelClient(int level) {
        this.coreLevel = level;
    }


    // --- LÓGICA DE PERMISOS (REFACTORIZADA A UUID) ---

    public boolean isTrusted(Player player) {
        // 1. Dueño o Admin (Prioridad máxima)
        if (player.getUUID().equals(this.getOwnerUUID()) || player.hasPermissions(2)) return true;

        // 2. Lógica de Clan
        if (this.level instanceof ServerLevel serverLevel) {
            var clanData = ClanSavedData.get(serverLevel);
            // Obtenemos la instancia del clan del jugador
            var playerClanInstance = clanData.getClanByMember(player.getUUID());

            // Verificamos si existe el clan Y si el nombre coincide con el del núcleo
            if (playerClanInstance != null) {
                // Prueba con .name si .getName() falla
                String clanNameStr = playerClanInstance.name;

                if (clanNameStr != null && clanNameStr.equalsIgnoreCase(this.clanName)) {
                    return true;
                }
            }
        }

        // 3. Invitados manuales
        PlayerPermissions perms = this.permissionsMap.get(player.getUUID());
        return perms != null && perms.canBuild;
    }

    public boolean hasPermission(Player player, String type) {
        if (player.getUUID().equals(ownerUUID) || player.hasPermissions(2)) return true;

        PlayerPermissions perms = permissionsMap.get(player.getUUID());
        if (perms == null) return false;

        return switch (type.toLowerCase()) {
            case "build" -> perms.canBuild;
            case "interact" -> perms.canInteract;
            case "chests" -> perms.canOpenChests;
            default -> false;
        };
    }

    public void updatePermission(UUID playerUUID, String playerName, String type, boolean value) {
        PlayerPermissions perms = permissionsMap.computeIfAbsent(playerUUID, k -> new PlayerPermissions());
        nameCache.put(playerUUID, playerName); // Actualizamos el cache de nombres

        switch (type) {
            case "build" -> perms.canBuild = value;
            case "interact" -> perms.canInteract = value;
            case "chests" -> perms.canOpenChests = value;
        }
        markDirtyAndUpdate();
    }

    public List<String> getTrustedNames() {
        return new ArrayList<>(nameCache.values());
    }

    // --- MEJORAS Y RADIOS ---

    public void upgrade(ServerPlayer player) {
        int siguienteNivel = this.coreLevel + 1;
        if (siguienteNivel > 5) return;

        int radioFuturo = obtenerRadioPorNivel(siguienteNivel);

        if (this.level instanceof ServerLevel sLevel) {
            // 1. VALIDACIÓN DE SOLAPAMIENTO
            if (!isAdmin()) {
                boolean overlaps = ProtectionDataManager.get(sLevel).getAllCores().entrySet().stream()
                        .filter(entry -> !entry.getKey().equals(this.worldPosition))
                        .anyMatch(entry -> {
                            double dist = Math.sqrt(this.worldPosition.distSqr(entry.getKey()));
                            return dist < (radioFuturo + entry.getValue().radius());
                        });

                if (overlaps) {
                    player.displayClientMessage(Component.literal("§c[!] No hay espacio. Choca con otra zona."), true);
                    player.playSound(SoundEvents.VILLAGER_NO, 1.0F, 1.0F);
                    return;
                }
            }

            // 2. VALIDACIÓN DE MATERIALES
            if (!canUpgrade()) {
                player.displayClientMessage(Component.literal("§c[!] No tienes los materiales necesarios."), true);
                return;
            }

            // 3. CONSUMO DE ITEMS (CORREGIDO PARA ITEMSTACKHANDLER)
            // Usamos extractItem(slot, amount, simulate)
            this.inventory.extractItem(0, 1, false);
            int cantidadAConsumir = (this.coreLevel == 1) ? 64 : 32;
            this.inventory.extractItem(1, cantidadAConsumir, false);

            // 4. ACTUALIZACIÓN DE NIVEL Y BLOQUES
            this.coreLevel++;
            this.range = radioFuturo; // Actualizamos la variable local de radio
            actualizarEstadosBloque();

            // 5. ACTUALIZACIÓN DE DATOS Y MANAGER
            ProtectionDataManager manager = ProtectionDataManager.get(sLevel);
            // Registramos el nuevo radio en el manager global
            manager.addCore(this.worldPosition, getOwnerUUID(), this.range);
            manager.syncToAll(sLevel);

            // 6. EFECTOS Y PERSISTENCIA
            efectosMejora();
            this.markDirtyAndUpdate();

            player.displayClientMessage(Component.literal("§a[!] ¡Núcleo mejorado al nivel " + this.coreLevel + "!"), true);
            player.playSound(SoundEvents.PLAYER_LEVELUP, 1.0F, 1.2F);
        }
    }

    public PlayerPermissions getPermissionsFor(String playerName) {
        // Buscamos el UUID en el cache de nombres que ya tienes
        return permissionsMap.entrySet().stream()
                .filter(entry -> nameCache.getOrDefault(entry.getKey(), "").equalsIgnoreCase(playerName))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(new PlayerPermissions()); // Si no existe, devuelve uno con todo en false
    }

    // 1. Retorna los nombres de todos los que tienen algún permiso desde el cache de nombres
    public List<String> getPlayersWithAnyPermission() {
        return new ArrayList<>(this.nameCache.values());
    }

    // 2. Actualiza permisos buscando el UUID a través del cache o el servidor
    public void updatePermission(String playerName, String permission, boolean value) {
        // Buscamos el UUID en el cache de nombres
        UUID targetUUID = nameCache.entrySet().stream()
                .filter(e -> e.getValue().equalsIgnoreCase(playerName))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);

        // Si el jugador está online, obtenemos su UUID real para mayor precisión
        if (this.level instanceof ServerLevel serverLevel) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayerByName(playerName);
            if (player != null) {
                targetUUID = player.getUUID();
            }
        }

        if (targetUUID != null) {
            // Usamos el método que ya tienes que acepta UUID
            this.updatePermission(targetUUID, playerName, permission, value);
        }
    }

    // 3. Elimina a un jugador buscando su UUID por nombre
    public void removePlayerPermissions(String playerName) {
        UUID targetUUID = nameCache.entrySet().stream()
                .filter(e -> e.getValue().equalsIgnoreCase(playerName))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null);

        if (targetUUID != null) {
            this.permissionsMap.remove(targetUUID);
            this.nameCache.remove(targetUUID);
            this.markDirtyAndUpdate();
        }
    }

    // Para verificar si puede editar flags (Usado en handleUpdateFlag)
    public boolean canPlayerEditFlag(Player player, String flagId) {
        if (player.hasPermissions(2)) return true; // Admin
        return player.getUUID().equals(this.getOwnerUUID()); // Solo el dueño
    }

    private void actualizarEstadosBloque() {
        if (this.level == null || this.level.isClientSide) return;

        BlockState estadoBase = this.level.getBlockState(this.worldPosition);

        // 1. Verificamos que sea nuestro bloque antes de cambiar nada
        if (estadoBase.getBlock() instanceof ProtectionCoreBlock) {
            // Actualizamos la parte inferior
            BlockState nuevoEstadoLower = estadoBase.setValue(ProtectionCoreBlock.LEVEL, this.coreLevel);
            this.level.setBlock(this.worldPosition, nuevoEstadoLower, 3);

            // 2. Buscamos y actualizamos la parte superior
            BlockPos upperPos = this.worldPosition.above();
            BlockState estadoUpper = this.level.getBlockState(upperPos);

            if (estadoUpper.getBlock() instanceof ProtectionCoreBlock &&
                    estadoUpper.getValue(ProtectionCoreBlock.HALF) == DoubleBlockHalf.UPPER) {

                this.level.setBlock(upperPos, estadoUpper.setValue(ProtectionCoreBlock.LEVEL, this.coreLevel), 3);
            }
        }

        // 3. Forzamos la sincronización de la BlockEntity
        this.setChanged();
        this.level.sendBlockUpdated(this.worldPosition, estadoBase, estadoBase, 3);
    }

    // --- FLAGS Y CONFIGURACIÓN ---

    public static final List<String> BASIC_FLAGS = List.of("pvp", "build", "chests", "interact", "villager-trade", "fire-damage");
    public static final List<String> ADMIN_FLAGS = List.of("explosions", "mob-spawn", "entry", "fall-damage", "fire-spread", "lighter", "item-pickup");

    public void setFlag(String flag, boolean value) {
        flags.put(flag, value);
        markDirtyAndUpdate();
    }

    public boolean getFlag(String flag) {

        if (flags.containsKey(flag)) {
            return flags.get(flag);
        }

        return flag.equals("entry") || flag.equals("hunger") || flag.equals("fire-spread");
    }

    // --- PERSISTENCIA NBT (ACTUALIZADA A UUID) ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("CoreLevel", this.coreLevel);
        tag.putInt("AdminRadius", this.adminRadius);
        tag.putString("ClanName", this.clanName);
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
        tag.putString("OwnerName", this.ownerName);

        // Guardar Flags
        CompoundTag flagsTag = new CompoundTag();
        flags.forEach(flagsTag::putBoolean);
        tag.put("CoreFlags", flagsTag);

        // Guardar Permisos con UUID
        ListTag permsList = new ListTag();
        permissionsMap.forEach((uuid, perms) -> {
            CompoundTag pTag = new CompoundTag();
            pTag.putUUID("uuid", uuid);
            pTag.putString("name", nameCache.getOrDefault(uuid, "Unknown"));
            pTag.putBoolean("build", perms.canBuild);
            pTag.putBoolean("interact", perms.canInteract);
            pTag.putBoolean("chests", perms.canOpenChests);
            permsList.add(pTag);
        });
        tag.put("PermissionsList", permsList);

        // Inventario
        tag.put("Inventory", this.inventory.serializeNBT(registries));
        tag.putInt("Range", this.range);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.coreLevel = tag.getInt("CoreLevel");
        this.adminRadius = tag.getInt("AdminRadius");
        this.clanName = tag.getString("ClanName");
        if (tag.hasUUID("Owner")) this.ownerUUID = tag.getUUID("Owner");
        this.ownerName = tag.getString("OwnerName");

        this.flags.clear();
        CompoundTag flagsTag = tag.getCompound("CoreFlags");
        for (String key : flagsTag.getAllKeys()) this.flags.put(key, flagsTag.getBoolean(key));

        this.permissionsMap.clear();
        this.nameCache.clear();
        ListTag permsList = tag.getList("PermissionsList", 10);
        for (int i = 0; i < permsList.size(); i++) {
            CompoundTag pTag = permsList.getCompound(i);
            UUID uuid = pTag.getUUID("uuid");
            this.nameCache.put(uuid, pTag.getString("name"));
            this.permissionsMap.put(uuid, new PlayerPermissions(pTag.getBoolean("build"), pTag.getBoolean("interact"), pTag.getBoolean("chests")));
        }

        if (tag.contains("Inventory")) {
            this.inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        }
        this.range = tag.getInt("Range");
        if (this.level != null && !this.level.isClientSide) {
            markDirtyAndUpdate();
        }
    }

    // --- UTILIDADES ---

    public void markDirtyAndUpdate() {
        this.setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    private int obtenerRadioPorNivel(int nivel) {
        return switch (nivel) {
            case 1 -> 8; case 2 -> 16; case 3 -> 32; case 4 -> 64; case 5 -> 128;
            default -> 8;
        };
    }

    public UUID getOwnerUUID() { return this.ownerUUID != null ? this.ownerUUID : UUID.randomUUID(); }
    public String getOwnerName() { return (clanName != null && !clanName.isEmpty()) ? clanName : ownerName; }
    public void setOwner(UUID uuid, String name) { this.ownerUUID = uuid; this.ownerName = name; markDirtyAndUpdate(); }
    public int getRadius() { return isAdmin() ? adminRadius : obtenerRadioPorNivel(coreLevel); }
    public boolean isAdmin() { return getBlockState().is(ModBlocks.ADMIN_PROTECTOR.get()); }

    public void setAdminRadius(int newRadius) {
        this.adminRadius = newRadius;
        this.markDirtyAndUpdate();
    }

    private void efectosMejora() {
        if (this.level instanceof ServerLevel sl) {
            sl.playSound(null, worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1f, 1f);
            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, worldPosition.getX()+0.5, worldPosition.getY()+1, worldPosition.getZ()+0.5, 30, 0.5, 0.5, 0.5, 0.15);
        }
    }

    public boolean canUpgrade() {
        // 1. Validaciones básicas
        if (isAdmin() || coreLevel >= 5) return false;

        // 2. Obtener items usando getStackInSlot (Cambio clave)
        ItemStack up = inventory.getStackInSlot(0);
        ItemStack mat = inventory.getStackInSlot(1);

        // 3. Verificar el objeto de mejora
        if (!up.is(com.tumod.protectormod.registry.ModItems.PROTECTION_UPGRADE.get())) return false;

        // 4. Lógica de materiales por nivel
        return switch (coreLevel) {
            case 1 -> mat.is(net.minecraft.world.item.Items.IRON_INGOT) && mat.getCount() >= 64;
            case 2 -> mat.is(net.minecraft.world.item.Items.GOLD_INGOT) && mat.getCount() >= 32;
            case 3 -> mat.is(net.minecraft.world.item.Items.DIAMOND) && mat.getCount() >= 32;
            case 4 -> mat.is(net.minecraft.world.item.Items.NETHERITE_INGOT) && mat.getCount() >= 32;
            default -> false;
        };
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries); // Forzamos que guarde TODA nuestra info personalizada
        return tag;
    }

    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
        return new ProtectionCoreMenu(isAdmin() ? ModMenus.ADMIN_CORE_MENU.get() : ModMenus.PROTECTION_CORE_MENU.get(), id, inv, this);
    }
    @Override public Component getDisplayName() { return Component.literal("Protection Core"); }

    public static class PlayerPermissions {
        // Estas variables deben coincidir con los nombres que usas en saveAdditional
        public boolean canBuild = false;
        public boolean canInteract = false;
        public boolean canOpenChests = false;

        // Constructor vacío (necesario para cargar desde NBT)
        public PlayerPermissions() {}

        // Constructor rápido (opcional, para conveniencia)
        public PlayerPermissions(boolean build, boolean interact, boolean chests) {
            this.canBuild = build;
            this.canInteract = interact;
            this.canOpenChests = chests;
        }
    }
}
