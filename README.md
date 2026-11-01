# Track Trace Trigger (TTT) Generator Tutorial

This tutorial covers the native TTT generator system.

It is built to be cleaner than the reference plugins:

- generated configs go to `plugins/TTT/addons/generated`
- generated resource-pack assets go to `plugins/TTT/resourcepack/addons/generated`
- generated output is tracked in `plugins/TTT/addons/generated/generator.yml`

## What the generator can create

- items
- textures
- display models
- complete item bundles
- blocks
- furniture
- mobs
- recipes
- menus
- menu buttons
- paged item browsers
- paged mob browsers
- batch imports from folders

## Basic rule

If you want TTT to replace existing generated content, add `overwrite` at the end of the command.

## 1. Generate a full custom item

Command:

```text
/ttt generate all ruby_blade IRON_SWORD auto Ruby Blade
```

What this creates:

- `items.yml` entry for `ruby_blade`
- `models.yml` entry for `ruby_blade_model`
- generated texture `ruby_blade.png`
- generated item model json
- rebuilt TTT resource pack

Use this when you want a fast starting point for a new custom item.

## 2. Generate only one layer

Item only:

```text
/ttt generate item ttt_token PAPER auto TTT Token
```

Texture only:

```text
/ttt generate texture ttt_token gem
```

Model only:

```text
/ttt generate model ttt_token_model ttt_token ttt_token
```

## 3. Generate a block scaffold

Command:

```text
/ttt generate block marble_column auto Marble Column
```

What this creates:

- item entry: `marble_column_item`
- block entry: `marble_column`
- starter mechanic: `marble_column_interact`
- generated block-style placeholder texture

This is the fastest way to stand up a new placeable custom block.

## 4. Generate furniture

Command:

```text
/ttt generate furniture tavern_chair auto Tavern Chair
```

What this creates:

- item entry: `tavern_chair_item`
- model entry: `tavern_chair_model`
- furniture entry: `tavern_chair`
- starter interaction mechanic

Generated furniture defaults to `seat: true`, so it behaves like a chair scaffold immediately.

## 5. Generate a mob scaffold

Command:

```text
/ttt generate mob ash_hound WOLF Ash Hound
```

What this creates:

- spawn egg item: `ash_hound_spawn_egg`
- model entry: `ash_hound_model`
- mob entry: `ash_hound`
- empty drop table: `ash_hound_loot`
- starter skill: `ash_hound_passive_skill`

This is the recommended starting point for TTT-native mobs.

## 6. Generate a recipe scaffold

Shaped recipe:

```text
/ttt generate recipe ruby_blade shaped
```

Shapeless recipe:

```text
/ttt generate recipe ttt_token shapeless
```

TTT chooses a sensible default shaped pattern based on the base material.
Shapeless recipes instead receive a default ingredient list.

## 7. Generate a menu

Command:

```text
/ttt generate menu arsenal 54 &8Arsenal
```

This creates:

- a generated menu definition
- a close button
- a close mechanic

After that, add buttons into the menu.

## 8. Generate menu buttons

Give an item:

```text
/ttt generate button arsenal 20 ruby_blade give
```

Give a specific item:

```text
/ttt generate button arsenal 21 PAPER give ttt_token
```

Open another menu (create the target menu first):

```text
/ttt generate menu forge 27 &8Forge
/ttt generate button arsenal 22 COMPASS open_menu forge
```

Open the live browser:

```text
/ttt generate button arsenal 23 COMPASS open_browser items
```

Close the menu (slot 49 already holds the default close button, so replacing it needs `overwrite`):

```text
/ttt generate button arsenal 49 BARRIER close overwrite
```

## 9. Generate paged browser menus

Generate item pages:

```text
/ttt generate browser items
```

Generate mob pages:

```text
/ttt generate browser mobs
```

Custom prefix:

```text
/ttt generate browser items loot_pages
```

What this creates:

- multiple generated menus like `loot_pages_page_1`
- generated previous/next mechanics
- generated entry buttons for all current TTT items or mobs
- a live-browser button that opens the dynamic TTT browser

This gives you snapshot pages built from the current registry, which is useful for curated GUIs, staff tools, and content review.

## 10. Import a folder of names

If you have a folder of files and want TTT to convert the regular file names into item/model scaffolds:

```text
/ttt generate import "G:/Peach Tree Studios Clients/BatchNames" names PAPER
```

This creates generated item/model entries from regular file names (subdirectories are skipped).

## 11. Import a folder of textures

If you already have PNGs:

```text
/ttt generate import "G:/Peach Tree Studios Clients/MyTextures" textures PAPER
```

This will:

- copy PNGs into TTT’s generated pack addon
- create item entries
- create model entries
- generate item-model json files

## 12. Import BBModels

If you have Blockbench models:

```text
/ttt generate import "G:/Peach Tree Studios Clients/Champions/BBModels" bbmodels
```

This will:

- create TTT item entries
- create TTT model entries
- copy primary textures
- dump sibling blueprint textures
- copy the `.bbmodel` files into the generated addon
- convert `java_block` / `java_item` cube geometry into renderable vanilla item models
  (coordinates are clamped to the vanilla `-16..32` range, sibling textures are mapped
  per texture index)

Rigged formats (`free`, `skin`, entity-style) cannot be expressed as vanilla geometry,
so they keep placeholder item models and render through the display-entity blueprint
defined in `models.yml`. The result message tells you which path each import took.

## 13. Editing after generation

Once content exists, use the editor system to refine it.

Model editor:

```text
/ttt editor model begin ruby_blade_model
/ttt editor model set scale 1.2 1.2 1.2
/ttt editor model tool
/ttt editor model save
```

Furniture editor:

```text
/ttt editor furniture begin tavern_chair
/ttt editor furniture set seat true
/ttt editor furniture tool
/ttt editor furniture save
```

Spawner editor (starting a session for an unknown id creates a new spawner definition on save):

```text
/ttt editor spawner begin ash_hound_den
/ttt editor spawner set mob ash_hound
/ttt editor spawner save
```

Place it afterwards with `/ttt spawner place ash_hound_den` or your preferred placement flow.

## 14. Recommended workflow

For a normal item:

1. Generate the asset bundle.
2. Check it in `/ttt browse items`.
3. Tweak its model with the editor if needed.
4. Add a recipe.
5. Add it to a generated menu or browser page.

For a block:

1. Generate the block scaffold.
2. Test placement and break behavior.
3. Replace the placeholder mechanic with real mechanics.

For furniture:

1. Generate the furniture scaffold.
2. Adjust offsets and scale with the model/furniture editor.
3. Replace the placeholder texture/model later if needed.

For a mob:

1. Generate the mob scaffold.
2. Adjust health, damage, and speed.
3. Fill out the drop table.
4. Replace the starter skill with your real combat or passive skill logic.

## 15. Testing checklist

Run these after generation:

```text
/ttt reload
/ttt pack rebuild
/ttt pack send <player>
```

Then test:

- `/ttt browse items`
- `/ttt browse mobs`
- `/ttt menu open <generated_menu_id>`
- `/ttt mob spawn <generated_mob_id>`

## 16. Generated file locations

Configs:

```text
plugins/TTT/addons/generated/
```

Pack assets:

```text
plugins/TTT/resourcepack/addons/generated/
```

Manifest:

```text
plugins/TTT/addons/generated/generator.yml
```

## 17. Good practice
- Use `auto` for `custom-model-data` unless you need a fixed slot. Automatic allocation avoids collisions across loaded and generated items sharing the same base material.
- Keep generated content in the generated addon until it stabilizes.
- Use `overwrite` only when you mean to replace an existing generated scaffold.
- Replace placeholder textures and starter mechanics once the scaffold is confirmed working.

## 18. Idle display animations (Trace)

Mob and furniture models can animate procedurally. Add to any `models.yml` entry:

```yaml
models:
  tavern_chair_model:
    idle-animation:
      type: sway        # spin | bob | pulse | sway (omit or use none to disable)
      period-ticks: 80 # full cycle length
      amount: 5.0      # degrees for spin/sway, blocks for bob, fraction for pulse
      phase-ticks: 0   # stagger multiple displays
```

Animation runs in display-transformation space with client-side interpolation, so mobs
keep following their hosts while bobbing, spinning shopkeepers rotate in place, and
pulsing loot pops without any packets or bone rigs.

## 19. Cutscenes

Define camera paths in `cutscenes.yml` (time in ticks, 20 = 1 second):

```yaml
cutscenes:
  vault_reveal:
    skippable: true
    freeze-movement: true
    keyframes:
      - at: 0
        location: {world: world, x: 0.5, y: 70.0, z: -14.5, yaw: 0.0, pitch: 0.0}
        title: "&6&lThe Vault"
        subtitle: "&7Something moved down there."
      - at: 80
        location: {world: world, x: 0.5, y: 66.0, z: 4.5, yaw: 0.0, pitch: -15.0}
        say: "&eThe vault door grinds open..."
        mechanics: [vault_reveal_open]
```

```text
/ttt cutscene list
/ttt cutscene play vault_reveal
/ttt cutscene play vault_reveal Notch
/ttt cutscene stop
```

The camera eases between keyframes (smoothstep, shortest yaw path), viewers are frozen
and restored to their original spot afterwards, sneak skips when allowed, and keyframe
`mechanics` fire under a `cutscene:` trigger section in `mechanics.yml`:

```yaml
mechanics:
  vault_reveal_open:
    cutscene:
      - type: sound
        sound: BLOCK_CHEST_OPEN
```

## 20. Custom armor sets (Tool)

Generate a full dyed leather set with a pack equipment asset:

```text
/ttt generate armor ember_guard #C1440E
```

This creates `ember_guard_helmet/chestplate/leggings/boots` (leather-tier armor values),
`assets/ttt/equipment/ember_guard.json` with `humanoid` + `humanoid_leggings` layers,
placeholder `entity/armor/ember_guard_layer_1/2.png` textures to repaint, and equippable
items that work two ways: natively via the `minecraft:equippable` component
(`ttt:ember_guard` asset id, right-click to equip) plus a TTT right-click fallback for
clients that ignore the component. Manual items can join in with an `armor` section:

```yaml
items:
  ember_guard_chestplate:
    base: LEATHER_CHESTPLATE
    armor:
      slot: chest        # head | chest | legs | feet
      asset: ember_guard  # assets/ttt/equipment/<asset>.json
      dye: "#C1440E"     # leather tint
      equip-sound: ITEM_ARMOR_EQUIP_LEATHER
```

Honest limit: worn armor uses the vanilla humanoid shape with your textures — custom
geometry is not something vanilla clients render, with or without TTT.

## 21. Boss brains: phases, boss bars, holograms

Mobs can fight back in phases. Add to any `mobs.yml` entry:

```yaml
mobs:
  inferno_brute:
    bossbar:
      title: "&6&lInferno Brute"
      color: RED
      style: SOLID
    hit-hologram: true
    hit-hologram-format: "&c-{damage}"
    phases:
      - below-health: 0.66
        title: "&6The brute enrages!"
        speed: 0.32
        mechanics: [brute_enrage_roar]
      - below-health: 0.33
        title: "&c&lFINAL STAND"
        speed: 0.4
        mechanics: [brute_last_stand]
```

Each phase fires once per life when health crosses its threshold (tracked per entity, so
two brutes phase independently), swaps movement speed, announces to nearby players, and
fires `mechanics` under a `mob_phase:` trigger. The boss bar tracks live health for
everyone in range; hit holograms float damage numbers off struck mobs. Two new mechanic
actions back it up anywhere: `leap` (mob or player lunges: `power`, `height`) and
`bossbar` (timed bar for quest moments: `text`, `color`, `progress`, `seconds`).

## 22. Bone rigs: bodies with structure

Single displays are billboards with delusions of grandeur. Rigs give a model a skeleton —
one display per BBModel group, positioned at pivots, with heads that track players and
limbs that swing while the host moves. Enable per model:

```yaml
models:
  ash_hound_model:
    rig:
      enabled: true
      scale: 1.0
      swing-degrees: 25.0
      head-groups: [head]     # optional; auto-detected by name
      limb-groups: [leg_l, leg_r]
```

On the next `/ttt reload`, TTT parses `addons/generated/blueprints/<blueprint>.bbmodel`,
writes one `<model>__<group>` item per bone (base `BONE`, auto custom-model-data),
group-sliced model JSONs, and shared `<model>_rig_texN.png` textures mapped from the
blueprint's sibling PNGs — then mobs and furniture using that model spawn articulated
automatically, falling back to the single display if anything is missing. Roles
auto-detect by name (`head/skull`, `arm/leg/wing/tail/...`); hierarchies are flattened
(absolute placement, rest pose preserved), and full timeline keyframe playback is out of
scope — procedural motion only.

## 23. Illusions: vision you can't trust

Client-side fake blocks. The server never changes; chosen viewers just see something
else. Mirage walls, hidden vault doors, ghost bridges:

```yaml
illusions:
  vault_door:
    world: world
    from: [100, 64, 200]
    to: [102, 66, 200]
    material: STONE
    viewers: all              # all | permission:<node> | Name1,Name2
    join-apply: false
```

```text
/ttt illusion list
/ttt illusion show vault_door
/ttt illusion hide vault_door Notch
```

Regions are capped at 20,000 blocks, `material` accepts full block states
(`"OAK_STAIRS[facing=north]"`), and mechanics can flip them mid-fight:

```yaml
mechanics:
  vault_reveal_open:
    cutscene:
      - type: illusion_hide
        illusion: vault_door
        audience: nearby:30
```

`audience` is `self` by default or `nearby:<radius>` around the event location. Hiding
restores the true block data each viewer would otherwise see.
