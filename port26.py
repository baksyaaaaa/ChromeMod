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
]
for f in SRC.rglob("*.java"):
    s = f.read_text(encoding="utf-8")
    o = s
    for a, b in RULES:
        s = re.sub(a, b, s)
    if s != o:
        f.write_text(s, encoding="utf-8")
