# TTT Generator Tutorial

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

TTT chooses a sensible default pattern based on the base material.

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

Open another menu:

```text
/ttt generate button arsenal 22 COMPASS open_menu forge
```

Open the live browser:

```text
/ttt generate button arsenal 23 COMPASS open_browser items
```

Close the menu:

```text
/ttt generate button arsenal 49 BARRIER close
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

If you have a folder of files and want TTT to convert the file names into item/model scaffolds:

```text
/ttt generate import "G:/Peach Tree Studios Clients/BatchNames" names PAPER
```

This creates generated item/model entries from file names.

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

Spawner editor:

```text
/ttt editor spawner begin ash_hound_den
/ttt editor spawner set mob ash_hound
/ttt editor spawner save
```

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

- Use `auto` for `custom-model-data` unless you need a fixed slot.
- Keep generated content in the generated addon until it stabilizes.
- Use `overwrite` only when you mean to replace an existing generated scaffold.
- Replace placeholder textures and starter mechanics once the scaffold is confirmed working.
