package com.chromeboost.mixin;

import com.chromeboost.Compat;
import com.chromeboost.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Distance culling for entities: small things (dropped items, XP orbs, arrows) and mobs far away are simply not drawn.
 * Their behaviour is untouched (this is only about drawing them), players, bosses, glowing entities and anything you
 * ride are never skipped. Skipping the draw saves the render-state building and the draw calls for every one of them.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityCullMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void chromeboost$cull(Entity entity, Frustum frustum, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
        if (!Compat.entityCullActive()) return;
        if (entity instanceof Player || entity instanceof EnderDragon || entity instanceof WitherBoss) return;
        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.getCameraEntity() || entity.isCurrentlyGlowing() || (mc.player != null && entity == mc.player.getVehicle())) return;
        Settings s = Settings.get();
        boolean small = entity instanceof ItemEntity || entity instanceof ExperienceOrb || entity instanceof Projectile;
        int limit = small ? s.itemDistance : s.mobDistance;
        if (limit <= 0) return;
        double dx = entity.getX() - camX, dy = entity.getY() - camY, dz = entity.getZ() - camZ;
        if (dx * dx + dy * dy + dz * dz > (double) limit * limit) cir.setReturnValue(false);
    }
}
