package com.tumod.protectormod.blockentity;

import com.tumod.protectormod.block.ProtectionCoreBlock;
import com.tumod.protectormod.menu.ProtectionCoreMenu;
import com.tumod.protectormod.registry.*;
import com.tumod.protectormod.util.ClanSavedData;
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
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
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
    private String ownerName = "Protector";

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
        if (this.level != null && !this.level.isClientSide) {
            // Forzamos que se registre si es la parte inferior
            if (this.getBlockState().getValue(ProtectionCoreBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {

                // Usamos una copia para evitar errores de modificación concurrente
                LOADED_CORES.removeIf(c -> c.getBlockPos().equals(this.worldPosition));
                LOADED_CORES.add(this);

                // DEBUG: Descomenta esto para ver en consola si el Admin Core se registra
                // if(isAdmin()) System.out.println("Admin Core registrado en: " + this.worldPosition);
            }
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

    public void upgrade(ServerPlayer player) {
        // 1. Determinar el radio del siguiente nivel
        int siguienteNivel = this.coreLevel + 1;
        if (siguienteNivel > 5) return;

        int radioFuturo = obtenerRadioPorNivel(siguienteNivel);

        // 2. VALIDACIÓN DE SUPERPOSICIÓN
        if (!esAdminCore()) {
            if (!puedeExpandirseA(radioFuturo)) {
                player.displayClientMessage(Component.literal("§c[!] No hay espacio suficiente. El radio choca con otra zona."), true);
                player.playSound(SoundEvents.VILLAGER_NO, 1.0F, 1.0F);
                return;
            }
        }

        // 3. Validaciones de materiales
        if (!canUpgrade()) {
            player.displayClientMessage(Component.literal("§c[!] No tienes los materiales necesarios."), true);
            return;
        }

        // 4. Procesar Mejora
        int materialCount = (this.coreLevel == 1) ? 64 : 32;
        this.inventory.removeItem(0, 1);
        this.inventory.removeItem(1, materialCount);

        // --- CAMBIO DE NIVEL Y ACTUALIZACIÓN DE BLOQUE ---
        // --- CAMBIO DE NIVEL Y ACTUALIZACIÓN DE BLOQUE ---
        this.coreLevel++;

        if (this.level != null) {
            // ACTUALIZAR PARTE INFERIOR (Donde está esta BlockEntity)
            BlockState currentState = this.level.getBlockState(this.worldPosition);
            if (currentState.hasProperty(ProtectionCoreBlock.LEVEL)) {
                BlockState newState = currentState.setValue(ProtectionCoreBlock.LEVEL, this.coreLevel);
                this.level.setBlock(this.worldPosition, newState, 3);
                this.level.sendBlockUpdated(this.worldPosition, currentState, newState, 3);
            }

            // ACTUALIZAR PARTE SUPERIOR (El bloque de arriba)
            BlockPos upperPos = this.worldPosition.above();
            BlockState upperState = this.level.getBlockState(upperPos);

            // Verificamos que sea el mismo tipo de bloque antes de actualizarlo
            if (upperState.is(currentState.getBlock()) && upperState.hasProperty(ProtectionCoreBlock.LEVEL)) {
                BlockState newUpperState = upperState.setValue(ProtectionCoreBlock.LEVEL, this.coreLevel);
                this.level.setBlock(upperPos, newUpperState, 3);
                this.level.sendBlockUpdated(upperPos, upperState, newUpperState, 3);
            }
        }

        // Dentro de upgrade() en la BlockEntity:
        this.setChanged(); // Guarda en disco
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3); // Avisa al render
        }

        // 5. Efectos visuales y sonido
        if (this.level instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, this.worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.0F, 1.0F);
            serverLevel.playSound(null, this.worldPosition, SoundEvents.TOTEM_USE, SoundSource.BLOCKS, 0.8F, 1.2F);

            serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                    this.worldPosition.getX() + 0.5,
                    this.worldPosition.getY() + 1.0,
                    this.worldPosition.getZ() + 0.5,
                    30, 0.5, 0.5, 0.5, 0.15
            );
        }

        player.displayClientMessage(Component.literal("§a[!] ¡Núcleo mejorado al nivel " + this.coreLevel + "!"), true);
        this.markDirtyAndUpdate();
    }

    // Verifica si este núcleo es el de Admin
    private boolean esAdminCore() {
        return this.getBlockState().is(com.tumod.protectormod.registry.ModBlocks.ADMIN_PROTECTOR.get());
    }

    // Lógica matemática: ¿Choca mi radio futuro con el radio actual de otros?
    public boolean puedeExpandirseA(int radioFuturo) {
        for (var otherCore : getLoadedCores()) {
            if (otherCore == this) continue;

            // Calculamos la distancia entre centros
            double distancia = Math.sqrt(this.worldPosition.distSqr(otherCore.getBlockPos()));
            int radioOtro = otherCore.getRange();

            // Si la distancia es menor a la suma de los radios, se están solapando
            if (distancia < (radioFuturo + radioOtro)) {
                return false;
            }
        }
        return true;
    }

    // Define cuánto crece el radio según el nivel (Ajusta los números a tu gusto)
    private int obtenerRadioPorNivel(int nivel) {
        return switch (nivel) {
            case 1 -> 16;
            case 2 -> 32;
            case 3 -> 48;
            case 4 -> 64;
            case 5 -> 80;
            default -> 16;
        };
    }

    public static final List<String> BASIC_FLAGS = List.of(
            "pvp", "build", "chests", "interact", "villager-trade", "fire-damage" // Añadido aquí
    );

    public static final List<String> ADMIN_FLAGS = List.of(
            "explosions", "mob-spawn", "entry", "fall-damage", "fire-spread", "lighter", "item-pickup"
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
        return obtenerRadioPorNivel(this.coreLevel);
    }

    public int getRange() {
        return getRadius();
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
            // FALSE = Bloqueado (Protegido)
            // TRUE = Permitido (Libre)

            if (f.equals("entry") || f.equals("hunger")) {
                flags.put(f, true); // Permitir entrar y tener hambre por defecto
            } else {
                flags.put(f, false); // Todo lo demás bloqueado (PvP, Build, Explosiones)
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
        return List.of("pvp", "explosions", "break", "build", "interact", "chests",
                "mob-spawn", "mob-grief", "fire-spread", "fire-damage", // <--- Nueva
                "use-buckets", "item-pickup", "item-drop", "crop-trample",
                "lighter", "damage-animals", "villager-trade", "entry",
                "enderpearl", "fall-damage", "hunger");
    }

    /**
     * Controla si el fuego puede propagarse en el área.
     * @param enabled true permite el fuego, false lo bloquea.
     */
    public void setFireSpread(boolean enabled) {
        this.setFlag("fire-spread", enabled);
    }

    /**
     * Controla si las entidades reciben daño por fuego/lava.
     */
    public void setFireDamage(boolean enabled) {
        this.setFlag("fire-damage", enabled);
    }

    public boolean getFlag(String flag) {
        // Si la flag no existe en el mapa, devolvemos un valor seguro según la flag
        if (!flags.containsKey(flag)) {
            if (flag.equals("build") || flag.equals("break") || flag.equals("entry")) return true;
            return false;
        }
        return flags.get(flag);
    }


    public void setFlag(String flag, boolean value) { flags.put(flag, value); markDirtyAndUpdate(); }

    // --- PERSISTENCIA NBT ---

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("CoreLevel", this.coreLevel);
        tag.putInt("AdminRadius", this.adminRadius);
        tag.putString("ClanName", this.clanName);
        if (ownerUUID != null) tag.putUUID("Owner", ownerUUID);
        if (ownerName != null) tag.putString("OwnerName", ownerName);

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
        if (tag.contains("OwnerName")) this.ownerName = tag.getString("OwnerName");

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

    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        // Expandimos el cuadro de renderizado para cubrir 2 bloques de altura (0 a 2)
        // Esto evita que el modelo desaparezca al mirar hacia arriba
        return new net.minecraft.world.phys.AABB(worldPosition).expandTowards(0, 1, 0);
    }

    public void setCoreLevelClient(int level) {
        this.coreLevel = level;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries); // Empaqueta el ownerName y las flags
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        // Crea el paquete de red para enviar al cliente
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            // Carga los datos recibidos en la instancia del cliente
            loadAdditional(tag, registries);

            // Si el bloque tiene niveles (Admin Core), forzamos un refresco visual
            if (level != null && level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
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

    // En ProtectionCoreBlockEntity.java
    public String getOwnerName() {
        // Si hay un clan, mostramos el clan, si no, el nombre del dueño guardado
        if (this.clanName != null && !this.clanName.isEmpty()) {
            return this.clanName;
        }
        return this.ownerName;
    }

    public void setOwner(UUID uuid, String name) {
        this.ownerUUID = uuid;
        this.ownerName = name;
        this.markDirtyAndUpdate();
    }

    // Mantén este por compatibilidad, pero usa el de arriba al colocar el bloque
    public void setOwner(UUID uuid) {
        this.ownerUUID = uuid;
        this.markDirtyAndUpdate();
    }


    public void setClanName(String name) { this.clanName = name; markDirtyAndUpdate(); }
    public String getClanName() { return this.clanName; }
    public int getCoreLevel() { return this.coreLevel; }
    public SimpleContainer getInventory() { return this.inventory; }
}
