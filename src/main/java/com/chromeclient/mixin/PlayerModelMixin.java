package com.chromeclient.mixin;

import com.chromeclient.Buddy;
import com.chromeclient.BuddyEmotes;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Only for the buddy: its silly waddle walk, its emotes, and a big head.
 * Every player shares one model, so everything is set back to normal for everyone else.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void chromeclient$buddy(AvatarRenderState state, CallbackInfo ci) {
        PlayerModel model = (PlayerModel) (Object) this;
        boolean buddy = state.id == Buddy.BUDDY_ID;
        boolean posed = false;

        if (buddy) {
            int emote = Buddy.emote;
            if (emote != BuddyEmotes.NONE) {
                float t = state.ageInTicks - Buddy.emoteStart;
                float w = BuddyEmotes.weight(emote, t);
                if (w > 0f) {
                    float[] p = BuddyEmotes.pose(emote, t);
                    blend(model.head, w, p[BuddyEmotes.HX], p[BuddyEmotes.HY], p[BuddyEmotes.HZ]);
                    blend(model.rightArm, w, p[BuddyEmotes.RAX], p[BuddyEmotes.RAY], p[BuddyEmotes.RAZ]);
                    blend(model.leftArm, w, p[BuddyEmotes.LAX], p[BuddyEmotes.LAY], p[BuddyEmotes.LAZ]);
                    blend(model.rightLeg, w, p[BuddyEmotes.RLX], 0f, p[BuddyEmotes.RLZ]);
                    blend(model.leftLeg, w, p[BuddyEmotes.LLX], 0f, p[BuddyEmotes.LLZ]);
                    posed = true;
                }
            } else {
                // silly waddle: fast wide leg swing with the feet splayed out, arms flapping, head wobbling
                Buddy.walkBlend += ((Buddy.walking ? 1f : 0f) - Buddy.walkBlend) * 0.2f;
                float w = Buddy.walkBlend;
                if (w > 0.02f) {
                    float a = state.ageInTicks * 0.5f;   // same rhythm as the body sway and bounce
                    float s = Mth.sin(a);
                    blend(model.rightLeg, w, s * 1.0f, 0f, 0.14f);
                    blend(model.leftLeg, w, -s * 1.0f, 0f, -0.14f);
                    blend(model.rightArm, w, -s * 0.9f, 0f, 0.3f + 0.2f * Math.abs(Mth.cos(a)));
                    blend(model.leftArm, w, s * 0.9f, 0f, -0.3f - 0.2f * Math.abs(Mth.cos(a)));
                    blend(model.head, w, 0.05f, 0f, 0.14f * s);
                    posed = true;
                }
            }
        }

        if (posed) {
            // the outer layers follow the parts they sit on
            model.hat.xRot = model.head.xRot;
            model.hat.yRot = model.head.yRot;
            model.hat.zRot = model.head.zRot;
            model.rightSleeve.xRot = model.rightArm.xRot;
            model.rightSleeve.yRot = model.rightArm.yRot;
            model.rightSleeve.zRot = model.rightArm.zRot;
            model.leftSleeve.xRot = model.leftArm.xRot;
            model.leftSleeve.yRot = model.leftArm.yRot;
            model.leftSleeve.zRot = model.leftArm.zRot;
            model.rightPants.xRot = model.rightLeg.xRot;
            model.rightPants.yRot = model.rightLeg.yRot;
            model.rightPants.zRot = model.rightLeg.zRot;
            model.leftPants.xRot = model.leftLeg.xRot;
            model.leftPants.yRot = model.leftLeg.yRot;
            model.leftPants.zRot = model.leftLeg.zRot;
        }

        float k = buddy ? Buddy.HEAD_SCALE : 1.0f;
        model.head.xScale = k;
        model.head.yScale = k;
        model.head.zScale = k;
        model.hat.xScale = k;
        model.hat.yScale = k;
        model.hat.zScale = k;
    }

    private static void blend(ModelPart part, float w, float x, float y, float z) {
        part.xRot = Mth.lerp(w, part.xRot, x);
        part.yRot = Mth.lerp(w, part.yRot, y);
        part.zRot = Mth.lerp(w, part.zRot, z);
    }
}
