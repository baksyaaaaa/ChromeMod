package com.chromeboost.mixin;

import com.chromeboost.Compat;
import com.chromeboost.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Particles that start far from you are never created (you could not see them anyway), and a burst of thousands in one
 * tick (big explosions, lots of mobs dying) is capped. Ticking and drawing a particle costs more than creating it, so not
 * creating them is where the saving is.
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleMixin {
    private static long chromeboost$tickId = -1;
    private static int chromeboost$made;

    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true, require = 0)
    private void chromeboost$limit(ParticleOptions options, double x, double y, double z, double vx, double vy, double vz,
                                   CallbackInfoReturnable<Particle> cir) {
        if (!Compat.particleCullActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Settings s = Settings.get();
        if (s.particleDistance > 0) {
            double dx = x - mc.player.getX(), dy = y - mc.player.getY(), dz = z - mc.player.getZ();
            if (dx * dx + dy * dy + dz * dz > (double) s.particleDistance * s.particleDistance) {
                cir.setReturnValue(null);
                return;
            }
        }
        if (s.particleCap > 0) {
            long tick = mc.level.getGameTime();
            if (tick != chromeboost$tickId) {
                chromeboost$tickId = tick;
                chromeboost$made = 0;
            }
            if (++chromeboost$made > s.particleCap) cir.setReturnValue(null);
        }
    }
}
