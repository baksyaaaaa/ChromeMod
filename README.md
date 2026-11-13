# Chrome Client mod

Press **Right Shift** in game to open the menu. Everything is switched on or off there and saved to `config/chromeclient.json`.

- **HUD info** (top left): FPS, CPS (left | right), ping, coordinates, facing direction, clock. Text color is switchable.
- **Keystrokes**: WASD, both mouse buttons and space, lit up while pressed.
- **Armor status**: your armor pieces with the durability each has left.
- **Move HUD**: in the menu, "Move HUD" lets you drag the info block, keystrokes and armor status plus the game's own hotbar, health, food, armor points, air, XP bar and level, item name, action bar, titles, player list, subtitles, leaderboard, boss bar and potion effects. Positions are saved. (Chat and the crosshair stay put on purpose.)
- **Zoom**: hold **C**.
- **Auto sprint**: hold forward and you sprint.

Built for Minecraft 1.21.11 with Fabric + Fabric API. Build: push to GitHub, open Actions, download the artifact, unzip it and put the `.jar` in your mods folder.
Remove any older Chrome Client jar from the mods folder first (two jars with the same mod id will crash the game).

## Buddy

A little buddy with a big head, wearing any account's skin, follows you around and does idle animations (waves, looks around, hops, dances, spins) when you stop. Only you see it.

Set it up in the Right Shift menu (toggle, copy a UUID then press Paste UUID, copy a name then Paste name) or with commands:

- `/buddy <uuid>` – whose skin it wears (turns it on)
- `/buddy name <name>` – what it's called
- `/buddy size <0.2-1.5>` – how big (default 0.5)
- `/buddy` – on / off


## Environment
Right Shift > Environment. Pick a theme (Green, Blue, Red, Yellow or Custom colour) and tune the sky + fog tint, the haze over the screen, how many particles there are, how fast they move and how far around you they appear. Each theme has its own particles and speed: green = drifting spores, blue = slow snow and cold flames, red = fast embers and ash, yellow = swirling sparks. Settings are saved in `config/chrome-environment.json`.

## Freelook
Hold Left Alt (rebind it in Controls) and move the mouse: the camera turns, your character keeps facing the same way. Let go and it snaps back. Switch it on/off in the Right Shift menu.

## YT Music and world lyrics
The YT Music window comes from the separate "YT Music In-Game" mod (it needs Rinku + Fabric API in `mods`). With it installed, the Right Shift menu has a **YT Music** button that opens its player, and a **Lyrics** screen: while a song plays, each lyric line flies up around you as big glowing words hanging in the world in front of you, and a small "now singing" pill at the top fills in the line as it is sung. Tune Distance, Size, Scatter, Sync offset, Glow, Shadow and the pill there. Time-synced lyrics come from lrclib.net; songs without them show nothing.

### Music drop-down
Press J (or Right Shift > YT Music): the pill at the top centre drops down into a small player with play / pause, previous, next, a seek bar, volume, the line being sung, and a **Full player** button. The YT Music mod's own top-right bar is switched off (its U key brings it back). If lyrics feel early or late, change **Sync offset** in Right Shift > Lyrics (negative = later).

## New menu (2.18)
Right Shift opens a glass menu: tabs on top (HUD, Render, Misc, Music, Cosmetics), modules on the left, their settings on the right (mouse wheel scrolls long lists).
- **Top bar**: time, name, FPS and a ping meter at the top centre (HUD > Top bar).
- **Cosmetics**: wardrobe with a try-on preview (hats, capes, wings). Picks are saved; they show on your own player in the world (third person or F5).

## 2.19
- **Cosmetics**: 11 hats, 8 capes, 5 wings built from many small boxes. The cape hangs at an angle and swings back when you run, wings flap, the halo floats.
- **Effects** tab: particles (sparkles, hearts, flames, snow, magic, notes, soul fire, petals, electric, glow) with an on/off switch, placement (around you / trail / wings) and amount.
- **World lyrics**: words now hang at different angles (Turn away slider) and have a flowing gradient (Colors: Ice, Violet, Sunset, Neon).

## 2.20 World text
Right Shift > Render > World text. Big floating text that hangs in the world and keeps a **fixed direction** (it never turns towards you or the camera). Click "Place here", then change the text (| = new line), size, style, colours (gradient + flow), glow, thickness (3D layers), motion (Still / Sweep / Loop), speed and range. Works in first and third person and hides behind blocks.

## 2.21 Real 3D text
World text and the world lyrics are now real text entities in the world (client-only, nobody else sees them), not HUD drawings. The game itself draws them in 3D with a fixed direction, so they never turn towards you, hide behind blocks and look right from any angle. If World text reads mirrored, change **Facing** by 180. Wings: all boxes of one colour are merged into one part, so they no longer lag.

## 2.21.1
- Fixed a crash when pressing previous track (lyrics index -1).
- Lyrics and World text never spawn inside blocks (they stop short of walls) and appear closer to the middle of the screen.

## 2.21.2
- Wings and cape no longer jerk when you walk or jump (smoothed speed, continuous flap).

## 2.22.1
- Wizard hat and bunny ears sit right on the head (no gap).

## 2.22.2
- Freelook no longer feels locked when you look straight up or down (the offset stops at the limit).

## 2.22.3
- Removed the custom cape option.

## 2.23 Cosmetics for other players
Cosmetics sync automatically through the built-in server. Players who use the mod see each other hats, capes, wings and particles. "Show mine to others" turns your own sharing off.

## 2.24 More visuals
Right Shift > HUD: **Potion list**, **Keybind list**, **Target HUD**, **Performance** (FPS graph, RAM, CPU) and **Watermark**: each sits in a corner you pick, with its own offset and size. Right Shift > Render: **Crosshair** (shape, size, gap, thickness, colour, outline, opens when moving) and **Jump ripples** (a ring of particles when you jump). No minimap.

## 2.25
- **Hand position** (Render): move, shrink or tilt the item in your hand.
- **Swing trail** (Render): an arc of particles each swing.
- **Block outline** (Render): coloured outline on the block you look at.
- **Menu theme** (Misc): Evolution, Neon, Ocean, Rose, Light.

## 2.25.1
- Fixed the storage search screen build error (click handling).

## 2.26.0
- Our own particles (`Fx`): glowing orbs, twinkling stars, hearts, flames, snow, petals, sparks, lightning. Used by cosmetic effects, environment themes, jump ripples and swing trail. Hidden behind blocks.
- Storage tracker removed.
- Wings sit higher on the back.
- Block outline redrawn as solid lines.
- Freelook: the camera offset was added twice, which made up/down feel locked. Fixed.

## 2.27.0
- Cosmetic effects are now one shape each (`Aura`): star ring, heart, fire ring, snowflake, rune circle, music ribbon, soul spiral, petal spiral, lightning, gyroscope; a ribbon for "behind your feet"; light feathers for "wings". Much cheaper than particles.
- Fewer / cheaper particles for the environment themes, ripples and swing trail.

## 2.27.1
- Jump ripples are now expanding shockwave rings on the ground; the swing trail is a curved slash that sweeps through the air and fades. Same styles as before (colours).
- Environment particles: fewer, to cut lag.

## 2.28.0
- Low fire (Render): the fire on your screen is drawn lower; Fire height slider (100 = normal, 0 = hidden).

## 2.28.1
- Effects are capped per frame and use fewer segments; the "behind your feet" ribbon is lighter. Fixes FPS dropping while running.

## 2.29.1
- Removed the Void knight helmet and the Outfits page again.

## 2.30.0
- Wings are now real painted feathers: each wing is dozens of thin feathers at their own angles, with a hand-painted high-resolution texture (barbs, glowing rims, gradients, pointed tips). Angel, Demon, Butterfly, Crystal and Phoenix each have their own look. The menu preview still shows the older flat shape.

## 2.31.0
- Capes are now painted cloth that bends and sways when you move (four pieces hanging under each other). All 8 old capes got new painted textures (damask, ermine collar, stars + moon, frost, flames...).
- 4 new capes: Warden (dark with glowing cyan sculk veins, ribs and heart, ragged hem), Cherry, Ender, Creeper.

## 2.31.1
- Removed the Rainbow cape (the capes after it moved up one place in the list).

## 2.31.2
- Warden cape renamed to "Twisted". Removed the Ender cape.

## 2.31.3
- Twisted cape redone to match the real one: near-black sculk-navy cloth, a pale bone spine down the middle, teal sculk creeping up from the hem.

## 2.32.0
- Capes: removed Crimson, Royal and Gold; all capes now have a straight bottom edge (no cut-out waves).
- Every text in the Right Shift menu is kept inside its box (too-long text is shortened with "...").

## 2.32.1
- Twisted cape is now the real cape's pixel art, copied pixel for pixel from the picture.

## 2.33.0
- Removed the Flame and Emerald capes. Creeper cape: smaller face (eyes, nose and a small mouth).

## 2.33.1
- Creeper cape face: mouth sized like the real creeper's (between the two earlier tries).

## 2.34.0
- 23 official-style Minecraft capes copied pixel for pixel from the cape picker picture: Cheapsh0t, Cobalt, dannyBstyle, JulianClark, Mapmaker, Migrator, Millionth Sale, Minecon 2011/2012/2013/2015/2016, Moderator, Mojang, Mojang Studios, Mojang (old), MrMessiah, Prismarine, Scrolls Champ, Translator, Translator (CN), Turtle, Valentine. Creeper removed.
- Capes now show on the elytra, like normal capes (cape pattern on the wings, same folded / sneaking / gliding poses).

## 2.34.2
- Removed the Minecon 2013, Translator and Translator (CN) capes.

## 2.35.0
- Cosmetics sharing no longer needs a link typed in: the mod uses the built-in `CosmeticSync.DEFAULT_URL`. The "Sharing link" button is gone.

## 2.35.1
- Removed the Cheapsh0t cape.

## 2.35.2
- fabric.mod.json now accepts Minecraft 1.21.11 and newer (`>=1.21.11`).

## 2.36.0
- The GitHub build now makes one jar per Minecraft version (1.21.11 and 26.1.2, artifacts named chrome-client-<version>). 26.1+ uses build-26.gradle (new Loom, Java 25, no mappings). Each version builds on its own, so one failing does not stop the other.

## 2.36.1
- Removed the World text (floating text) feature.

## 2.37.0
- Removed Jump ripples, Swing trail and the "Trail" place for cosmetic effects (effects now: Around you / Wings).

## 2.38.0
- Removed the Environment module (sky / fog tint, haze, particles).

## 2.39.0
- All 11 hats rebuilt: round crowns, horns, brims and cones made of thin painted planks (hat_N.png textures) instead of blocks. Crown has 8 jewelled points with pearls, velvet inside and gems. Menu previews are taken from the same 3D shapes.

## 2.40.0
- 5 new original hats: Headphones, Shades, Cowboy, Flower crown, Bandana (same painted-plank style as the other hats).

## 2.40.1
- Calmer, more natural colours on all hats and wings (no neon): antique gold, deep navy, burgundy, cream; wings now look like real feathers (angel ivory, demon dark leather, butterfly monarch orange with dark edge, crystal ice, phoenix ember).

## 2.41.0
- Wings rebuilt from scratch: real layered feathers (angel, crystal, phoenix), leathery bat membranes on bones (demon), painted monarch wing panels (butterfly). Hats: removed the baked per-plank shading and outlines that made colours alternate.

## 2.41.1
- Removed the painted shine/highlight streaks from all hats.

## 2.41.2
- Less lag: wings now use about 3x fewer planks (angel 226->84, phoenix 256->88, crystal 106->68), hats 20-40% fewer (rounder shapes kept), and other players farther than 48 blocks are not drawn.

## 2.42.19
- Cape swings half as far when you walk or run.
- Elytra with a cape: uses the game's own elytra pose numbers, the game's grey elytra is no longer drawn under it, and the texture now has the real elytra wing outline with the left half of the cape on each wing (mirrored), instead of a full rectangle.
- HUD restyled like the menu: Info, Pay timer (with a big time and a progress bar), Keystrokes and Armor are dark glass cards with an accent edge.
- The two text-input screens (lyrics offset/others and buddy name/skin) got the menu's glass panel and buttons.

## 2.42.18
- Block outline removed (it was drawn with thousands of screen rectangles and cost frames).

## 2.42.17
- Freelook: back to the camera hook that worked for turning, now picking the yaw/pitch arguments by slot instead of by order (so up/down is picked up too).

## 2.42.16
- Capes now move exactly like the game's own capes: one stiff board, 6 degrees off the back, swinging out with running speed, leaning into turns and rolling when you strafe (it uses the game's own cape numbers). The bendy cloth sway is gone.

## 2.42.15
- Freelook: camera hook rewritten (both yaw and pitch are added by re-issuing the camera rotation call, instead of per-argument edits), so looking up and down works again, not only left and right.

## 2.42.14
- All kill effects removed (the Kill effects tab, the 3D preview and the card animation are gone).

## 2.42.13
- Shark kill effect removed.
- Playing cards kill effect redone and simplified: three small cards zip through the victim along three slash lines, then the figure falls apart into five pieces that tumble down while the cards flutter to the ground. No whirlwind, no sparks.
- Effects around you (rings, stars, ...) now follow the game's real camera, so they stay put in third person.

## 2.42.12
- Shark remodelled: longer, sleeker body, pointed snout, sharp triangular teeth, pink gums. Mouth stays clean when closed.

## 2.42.11
- Wings sit lower on the back (about 3 px lower).

## 2.42.10
- Cosmetics sharing service address is built in (cosmetics-md7h.onrender.com) and can't be changed; other mod users now see your hats, capes, wings and effects.
- Build for Minecraft 26.1.2 dropped from the workflow; only 1.21.11 is built.

## 2.42.9
- Shark: fins are now rooted inside the body (dorsal, pectoral, pelvic, second dorsal, anal) instead of floating next to it.

## 2.42.8
- Shark redone in the style of the movie shark: bulky slate-blue body with a white belly, blunt snout, tiny black eyes, big triangular dorsal fin, broad pectoral fins, crescent tail, big serrated triangular teeth inside the jaw (no teeth through the lip when it is closed), and the jaw opens wider for the bite.

## 2.42.7
- Shark rebuilt from scratch: real mouth line (a curved smile under the snout, closed by default, teeth only inside the jaw), jaw turns around a proper hinge, ragged countershading, long sickle pectoral fins with dark tips, tall dorsal fin, crescent tail with a longer upper lobe, second dorsal / anal / pelvic fins, gill slits, lateral line, eyes and nostrils.

## 2.42.6
- Shark mouth fixed: closed by default (a thin mouth line, no teeth showing), the jaw hinge sits under the eye like a real shark, and it only opens wide for the bite, then snaps shut.

## 2.42.5
- Shark remodelled: smooth torpedo body, proper countershading, second dorsal, anal and pelvic fins, tail keel, bigger eyes, nostrils, lateral line, more teeth. The body wobble while leaping is much smaller.

## 2.42.4
- Kill effects: "Watch it in 3D" opens a slim bar at the bottom of the screen and plays the real 3D effect on a little Steve in front of you in a loop (arrows switch the effect, Replay, Back). The flat preview on the try-on card is gone.

## 2.42.3
- "Play preview" now plays the full 3D effect on a little Steve standing in front of you (he disappears when he is cut up / eaten).

## 2.42.2
- Kill effects page: the try-on figure now plays the chosen effect (cards or shark) in a loop, and the figure disappears when it is cut up / eaten. Picking another effect restarts it.

## 2.42.1
- Hats cut down to Halo, Cat ears (fixed: the ears no longer stick out past the head), Bunny ears and a new Void ring (thin glowing purple ring around the head).
- Wings "Demon" is now **Void bat**: black bat wings with glowing purple edges.
- The shark is blue.

## 2.42.0
- Hats are real smooth 3D models now (lathes and swept tubes with smooth shading, no boxes, no crossed planks): top hat, crown, wizard, viking, halo, cat ears, bunny ears, horns, pirate tricorn, santa, party, headphones, shades, cowboy, flower crown, bandana. They live in `assets/chromeclient/meshes` and are drawn through `submitCustomGeometry`.
- New **Kill effects** page in the Cosmetics tab (own models drawn in the world when something you were hitting dies; "Play preview" tries it in front of you):
  - **Playing cards**: a whirlwind of cards closes in, slashes through in an X with bright cut lines, bursts and flutters to the ground, a joker spins over the spot.
  - **Shark**: a fin cruises over the ground, then a shark leaps out of the floor, opens its jaws, snaps shut on the spot and dives away, with water rings and drops.

## 2.41.3
- Hats: parts no longer vanish. Overlapping planks get a tiny unique depth offset (no more z-fighting), brims/halo/caps are thicker so they do not disappear edge-on, crown points, cat/bunny ears and the pirate feather are crossed so they stay visible from the side.

## 2.42.20
- Text is now white in every dark theme.
- Effects around you follow the game camera directly (third person fixed).

## 2.42.21
- HUD text (keystrokes, timer, info) is always white.

## 2.42.22
- Cosmetics of other players are checked every second.

## 2.42.23
- Cosmetic check runs as fast as the service answers; other players effects are hidden behind walls.

## 2.42.24
- The top bar can be moved and resized in the HUD editor.

## 2.42.25
- Cat ears redone: flat pointed ears with pink insides.

## 2.42.26
- Cat ears are furry; halo no longer has a dark top.

## 2.42.27
- Cat ears: soft white fluff instead of spikes. Particle wings redrawn as feathered wings, follow smoothly. Effects hidden behind walls use several sight points.

## 2.42.28
- Cat ears rebuilt as cupped, curved ears: fur outside, pink inside, white fur tufts.

## 2.42.29
- Cat ears rounder and bent backwards and outwards.

## 2.42.30
- Cat ears: no fur inside, bent forward.

## 2.42.31
- Cat ears fit inside the width of the head.

## 2.42.32
- Cat ears sit on the top corners of the head and stay inside its width.

## 2.42.33
- Cat ears flush with the outer edges of the head, no sideways tilt.

## 2.42.34
- Cat ears turned 30 degrees away from the centre, feet on the edges of the head.

## 2.42.35
- Cat ears wider and sunk into the head: no gap.

## 2.42.36
- Cat ears lower. Cosmetics and effects are hidden while the player is invisible.

## 2.42.37
- Top bar removed (FPS, ping and time show in the Info card again).

## 2.42.38
- Cat ears: the mirrored ear had flipped lighting (glowed), fixed. Freelook: second camera hook; the mouse is only taken over when a hook is running.

## 2.42.39
- Particle wings lower.

## 2.42.40
- Model wings lower.

## 2.42.41
- Eating/drinking no longer breaks with the hand position mod. Cosmetic particles (Effects) removed.

## 2.42.42
- Cape picker shows the actual cape (its back panel), bigger cells, 4 per row.

## 2.42.43
- Cape picker: no seams, the 4 cape pieces are stitched correctly.

## 2.42.44
- Midnight, Snow, Twisted and Cherry capes repainted in full detail (12x resolution, cloth folds, embroidered trim, glow). New capes: Aurora, Ember, Abyss, Galaxy.

## 2.42.45
- Custom capes redone in the plain vanilla style: flat colours, simple border, one small emblem, no shine.
- Twisted cape restored to the original (exact) design.

## 2.42.46
- Twisted cape back to the original exact design.

## 2.42.47
- Hand position: while you eat or drink only the hand using the item is left alone, the other hand keeps your size/position.

## 2.42.48
- Freelook rewritten: the camera offset is added to your rotation only while the camera is set up (fixes up/down being stuck).

## 2.42.49
- Cat ears: matte (no shine): even lighting and slightly darker colours.

## 2.42.50
- Cat ears: fatter from the side (about 60% deeper), calmer colours (no light grey tips) and matte lighting.

## 2.42.51
- Cat ears: the two layers of the ear now meet at the edges (no gap / ripped look), still fat from the side.
- **2.42.52**: cat ears are closed at the tip, fat from the side and fully matte (no shine).
- **2.42.53**: menu clicks (tabs, buttons) also use the game's own click event, so they cannot be missed.
- **2.42.54**: cat ears no longer change brightness when you turn your head (that was the shine).
- **2.42.55**: emote wheel (press **B**, switch it in menu > Misc > Emotes): Wave, Dance, Cheer, Bow, Jump, Jog. Move or jump to stop. Best seen in third person (F5).
- **2.42.56**: emote wheel: removed Dance and Cheer, added the Obsession dance.
- **2.42.56**: new "Star" emote: you stand on a glowing blue six-pointed star with your arms out (emote wheel, B).
- **2.42.57**: the Dance emote is now the Fortnite dance from the video (back kicks, arms sweeping across the chest, then elbows out).
- **2.42.58**: Dance emote now copies the Fortnite dance from the video (replaces Obsession); fixed the build.
- **2.42.59**: Star emote: you sit on the star (legs forward) and float up with it, rocking slightly.
- **2.42.60**: Star emote platform is now a flag (white, two blue stripes, blue six-pointed star).
- **2.42.61**: emote wheel is now Griddy, Levitate (The Honored One) and Star; the other emotes were removed.
- **2.42.62**: calmer Griddy; emote wheel writes lines to latest.log (search ChromeEmotes) to find why it fails.
- **2.42.63**: emote wheel and the Star/Griddy/Levitate emotes removed again.
- **2.42.64**: new cape "Web" (black with a silver spiderweb and a small purple spider).
- **2.42.65**: Web cape is now red with a dark web.
- **2.42.66**: Web cape redrawn with a proper hanging spiderweb and a small spider.
- **2.42.67**: Web cape simplified to chunky pixels like the other capes.
- **2.42.68**: Web cape redone with thick clean lines (five threads, three rings, plain spider).
- **2.42.69**: Web cape back to the detailed web design (2.42.66).
- **2.42.70**: Web cape back to the chunky pixel design (2.42.67).
