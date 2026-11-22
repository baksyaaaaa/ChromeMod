#!/usr/bin/env python3
"""Rewrites the 1.21.11 sources to Minecraft 26.x names (the GitHub build runs this for 26.x only)."""
import re, pathlib, sys
SRC = pathlib.Path("src/main/java")
RULES = [
    (r"\bGuiGraphics\b", "GuiGraphicsExtractor"),
    (r"\bvoid render\(GuiGraphicsExtractor", "void extractRenderState(GuiGraphicsExtractor"),
    (r"\bvoid renderBackground\(GuiGraphicsExtractor", "void extractBackground(GuiGraphicsExtractor"),
    (r"\bsuper\.render\(", "super.extractRenderState("),
    (r"\bsuper\.renderBackground\(", "super.extractBackground("),
    (r"\.drawString\(", ".text("),
    (r"\.drawCenteredString\(", ".centeredText("),
    (r"\.renderItem\(", ".item("),
    (r"\.renderFakeItem\(", ".fakeItem("),
    (r"\.renderItemDecorations\(", ".itemDecorations("),
    # Minecraft's own API moved
    (r"\.setScreen\(", ".gui.setScreen("),
    (r"\b(mc|client|minecraft|Minecraft\.getInstance\(\))\.screen\b(?!\()", r"\1.gui.screen()"),
    (r"(\w+)\.options\.hideGui\b", r"\1.gui.hud.isHidden()"),
    (r"\.getMainCamera\(\)", ".mainCamera()"),
    (r"\.displayClientMessage\((.+), true\)", r".sendOverlayMessage(\1)"),
    (r"\.displayClientMessage\((.+), false\)", r".sendSystemMessage(\1)"),
    (r"minecraft\.getWindow\(\)\.toggleFullScreen\(\)", "minecraft.getWindow().setFullscreen(!minecraft.options.fullscreen().get())"),
    (r"InputConstants\.Type\.KEYSYM", "InputConstants.Type.KEYBOARD"),
    (r"keybinding\.v1\.KeyBindingHelper", "keymapping.v1.KeyMappingHelper"),
    (r"KeyBindingHelper\.registerKeyBinding\(", "KeyMappingHelper.registerKeyMapping("),
    (r"PlayerFaceRenderer\.draw\(", "PlayerFaceExtractor.extractRenderState("),
    (r"\bPlayerFaceRenderer\b", "PlayerFaceExtractor"),
    (r"(\w+)::render\b", r"\1::extractRenderState"),
    (r"\boriginal\.render\(", "original.extractRenderState("),
    (r"\.mulPose\((com\.mojang\.math\.Axis\.\w+\.rotation(?:Degrees)?\(.*\))\);", r".rotate(\1);"),
    (r"RenderTypes\.entityCutoutNoCull\(", "RenderTypes.entityCutout("),
]
for f in SRC.rglob("*.java"):
    s = f.read_text(encoding="utf-8")
    o = s
    for a, b in RULES:
        s = re.sub(a, b, s)
    if s != o:
        f.write_text(s, encoding="utf-8")

# files that are rewritten by hand for 26.x (kept in src26/, copied over the originals)
import shutil
S26 = pathlib.Path("src26/java")
if S26.is_dir():
    for f in S26.rglob("*.java"):
        dst = SRC / f.relative_to(S26)
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(f, dst)
