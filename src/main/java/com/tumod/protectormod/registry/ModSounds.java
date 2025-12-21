package com.tumod.protectormod.registry;

import com.tumod.protectormod.ProtectorMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, ProtectorMod.MOD_ID);

    // Cambiamos Supplier por DeferredHolder para mejor compatibilidad con NeoForge
    public static final net.neoforged.neoforge.registries.DeferredHolder<SoundEvent, SoundEvent> CORE_UPGRADE =
            registerSoundEvent("core_upgrade");

    private static net.neoforged.neoforge.registries.DeferredHolder<SoundEvent, SoundEvent> registerSoundEvent(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(ProtectorMod.MOD_ID, name)));
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}
