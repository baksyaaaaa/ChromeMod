# Chrome Boost

A small client-side Fabric performance mod for Minecraft 1.21.11. No settings screen: it runs with sensible defaults (advanced: edit `config/chrome-boost.json`).
Every module is something that measurably cuts work; nothing here is a placebo.

| Module | What it does | Why it helps |
|---|---|---|
| Entity culling | Dropped items / XP / arrows beyond 32 m and mobs beyond 96 m are not drawn (behaviour unchanged; players, bosses, glowing entities and your vehicle are never skipped) | fewer render states and draw calls in farms, item piles and mob crowds |
| Particle limit | Particles that start more than 32 m away are not created; at most 400 are created per tick | ticking/drawing particles is costlier than skipping them; stops explosion lag spikes |
| Background FPS cap | While the window is not in front, FPS is capped (15) | frees CPU/GPU and battery |
| Adaptive render distance | Optional. Steps your render distance down/up one chunk (20 s apart) to keep a target FPS, hands your own value back when you leave | removes sustained low FPS without you touching settings |
| Memory care | One background garbage collection after you leave a world; one warning when memory is nearly full | world data is garbage right then; a full heap is the main cause of constant stutter |

Compatibility: modules switch themselves off when a mod already doing the same job is installed (Entity Culling / More Culling for entities, Particle Core / Particular for particles). Chrome Boost does not touch chunk meshing, lighting or the renderer, so it is safe next to Sodium, Lithium, FerriteCore, ModernFix, C2ME and ImmediatelyFast.
Not included on purpose: chunk meshing, lighting and engine rewrites (Sodium/Lithium do those far better), and fake "RAM cleaners".
Config file: `config/chrome-boost.json`.
