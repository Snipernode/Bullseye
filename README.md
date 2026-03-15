# Bullseye Generator Tutorial

This tutorial covers the native Bullseye generator system.

It is built to be cleaner than the reference plugins:

- generated configs go to `plugins/Bullseye/addons/generated`
- generated resource-pack assets go to `plugins/Bullseye/resourcepack/addons/generated`
- generated output is tracked in `plugins/Bullseye/addons/generated/generator.yml`

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

If you want Bullseye to replace existing generated content, add `overwrite` at the end of the command.

## 1. Generate a full custom item

Command:

```text
/bullseye generate all ruby_blade IRON_SWORD auto Ruby Blade
```

What this creates:

- `items.yml` entry for `ruby_blade`
- `models.yml` entry for `ruby_blade_model`
- generated texture `ruby_blade.png`
- generated item model json
- rebuilt Bullseye resource pack

Use this when you want a fast starting point for a new custom item.

## 2. Generate only one layer

Item only:

```text
/bullseye generate item bullseye_token PAPER auto Bullseye Token
```

Texture only:

```text
/bullseye generate texture bullseye_token gem
```

Model only:

```text
/bullseye generate model bullseye_token_model bullseye_token bullseye_token
```

## 3. Generate a block scaffold

Command:

```text
/bullseye generate block marble_column auto Marble Column
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
/bullseye generate furniture tavern_chair auto Tavern Chair
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
/bullseye generate mob ash_hound WOLF Ash Hound
```

What this creates:

- spawn egg item: `ash_hound_spawn_egg`
- model entry: `ash_hound_model`
- mob entry: `ash_hound`
- empty drop table: `ash_hound_loot`
- starter skill: `ash_hound_passive_skill`

This is the recommended starting point for Bullseye-native mobs.

## 6. Generate a recipe scaffold

Shaped recipe:

```text
/bullseye generate recipe ruby_blade shaped
```

Shapeless recipe:

```text
/bullseye generate recipe bullseye_token shapeless
```

Bullseye chooses a sensible default pattern based on the base material.

## 7. Generate a menu

Command:

```text
/bullseye generate menu arsenal 54 &8Arsenal
```

This creates:

- a generated menu definition
- a close button
- a close mechanic

After that, add buttons into the menu.

## 8. Generate menu buttons

Give an item:

```text
/bullseye generate button arsenal 20 ruby_blade give
```

Give a specific item:

```text
/bullseye generate button arsenal 21 PAPER give bullseye_token
```

Open another menu:

```text
/bullseye generate button arsenal 22 COMPASS open_menu forge
```

Open the live browser:

```text
/bullseye generate button arsenal 23 COMPASS open_browser items
```

Close the menu:

```text
/bullseye generate button arsenal 49 BARRIER close
```

## 9. Generate paged browser menus

Generate item pages:

```text
/bullseye generate browser items
```

Generate mob pages:

```text
/bullseye generate browser mobs
```

Custom prefix:

```text
/bullseye generate browser items loot_pages
```

What this creates:

- multiple generated menus like `loot_pages_page_1`
- generated previous/next mechanics
- generated entry buttons for all current Bullseye items or mobs
- a live-browser button that opens the dynamic Bullseye browser

This gives you snapshot pages built from the current registry, which is useful for curated GUIs, staff tools, and content review.

## 10. Import a folder of names

If you have a folder of files and want Bullseye to convert the file names into item/model scaffolds:

```text
/bullseye generate import "G:/Peach Tree Studios Clients/BatchNames" names PAPER
```

This creates generated item/model entries from file names.

## 11. Import a folder of textures

If you already have PNGs:

```text
/bullseye generate import "G:/Peach Tree Studios Clients/MyTextures" textures PAPER
```

This will:

- copy PNGs into Bullseye’s generated pack addon
- create item entries
- create model entries
- generate item-model json files

## 12. Import BBModels

If you have Blockbench models:

```text
/bullseye generate import "G:/Peach Tree Studios Clients/Champions/BBModels" bbmodels
```

This will:

- create Bullseye item entries
- create Bullseye model entries
- copy primary textures
- dump sibling blueprint textures
- copy the `.bbmodel` files into the generated addon

## 13. Editing after generation

Once content exists, use the editor system to refine it.

Model editor:

```text
/bullseye editor model begin ruby_blade_model
/bullseye editor model set scale 1.2 1.2 1.2
/bullseye editor model tool
/bullseye editor model save
```

Furniture editor:

```text
/bullseye editor furniture begin tavern_chair
/bullseye editor furniture set seat true
/bullseye editor furniture tool
/bullseye editor furniture save
```

Spawner editor:

```text
/bullseye editor spawner begin ash_hound_den
/bullseye editor spawner set mob ash_hound
/bullseye editor spawner save
```

## 14. Recommended workflow

For a normal item:

1. Generate the asset bundle.
2. Check it in `/bullseye browse items`.
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
/bullseye reload
/bullseye pack rebuild
/bullseye pack send <player>
```

Then test:

- `/bullseye browse items`
- `/bullseye browse mobs`
- `/bullseye menu open <generated_menu_id>`
- `/bullseye mob spawn <generated_mob_id>`

## 16. Generated file locations

Configs:

```text
plugins/Bullseye/addons/generated/
```

Pack assets:

```text
plugins/Bullseye/resourcepack/addons/generated/
```

Manifest:

```text
plugins/Bullseye/addons/generated/generator.yml
```

## 17. Good practice

- Use `auto` for `custom-model-data` unless you need a fixed slot.
- Keep generated content in the generated addon until it stabilizes.
- Use `overwrite` only when you mean to replace an existing generated scaffold.
- Replace placeholder textures and starter mechanics once the scaffold is confirmed working.
