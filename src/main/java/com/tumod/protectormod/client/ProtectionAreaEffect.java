package com.tumod.protectormod.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;

public class ProtectionAreaEffect {

    private final BlockPos center;
    private final int radius;
    private int ticksLeft;

    public ProtectionAreaEffect(BlockPos center, int radius, int durationTicks) {
        this.center = center;
        this.radius = radius;
        this.ticksLeft = durationTicks;
    }

    // Devuelve false si ya terminó
    public boolean tick(ClientLevel level) {
        if (ticksLeft-- <= 0) return false;

        for (int x = -radius; x <= radius; x += Math.max(1, radius / 10)) {
            for (int z = -radius; z <= radius; z += Math.max(1, radius / 10)) {
                if (Math.abs(x) == radius || Math.abs(z) == radius) {
                    level.addParticle(
                            ParticleTypes.HAPPY_VILLAGER,
                            center.getX() + x + 0.5,
                            center.getY() + 1.2,
                            center.getZ() + z + 0.5,
                            0, 0.05, 0
                    );
                }
            }
        }
        return true;
    }
}


