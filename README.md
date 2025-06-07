# ShopGUI+ DynaShop (Addon)

A complete **wiki** for ShopGUIPlus-DynaShop, covering all main features, operating modes, and configuration for each mode.

---

# 📖 ShopGUIPlus-DynaShop Wiki

## Table of Contents

- Main Features
- DynaShop Modes
- General Configuration
- Item Configuration by Mode
- Cache & Performance Management
- Dynamic Placeholders
- Limits & Cooldowns
- Reload & Best Practices
- Example ShopGUIPlus Config
- FAQ

---

## Main Features

- **Dynamic pricing**: Buy/sell prices evolve based on supply and demand.
- **Dynamic stock**: Items can have limited stock, affecting price.
- **Recipe-based pricing**: Automatic price calculation based on crafting recipe.
- **Configurable cache system**: Choose between performance (`full` cache) and real-time data (`realtime`).
- **Dynamic placeholders**: Display prices, stock, min/max, etc. in item lores.
- **Transaction limits**: Per-player buy/sell limits per period.
- **Full ShopGUI+ compatibility**: Supports all shop types, pages, selection menus, etc.

---

## DynaShop Modes

Each item can work in a specific **DynaShop mode**:

| Mode         | Description                                                      | Config key (`typeDynaShop`) |
|--------------|------------------------------------------------------------------|-----------------------------|
| `DYNAMIC`    | Dynamic price, no stock management                               | `DYNAMIC`                   |
| `STOCK`      | Dynamic price + stock management (limited quantity)              | `STOCK`                     |
| `STATIC_STOCK` | Fixed price, but with stock management                         | `STATIC_STOCK`              |
| `RECIPE`     | Price automatically calculated from crafting recipe              | `RECIPE`                    |
| `LINK`       | Item inherits price from another item (shopID:itemID)            | `LINK`                      |

---

## General Configuration

In your DynaShop `config.yml`:

```yaml
cache:
  mode: "full" # "full" (performance) or "realtime" (fresh data)
  durations:
    price: 30
    display: 10
    recipe: 300
    stock: 20
    calculated: 60

gui-refresh:
  default-items: 1000   # ms between each normal inventory refresh
  critical-items: 300   # ms for critical items (e.g. very low stock)
```

---

## Item Configuration by Mode

### 1. DYNAMIC Mode

```yaml
items:
  diamond:
    typeDynaShop: DYNAMIC
    buyPrice: 1000
    buyDynamic:
      min: 800
      max: 1200
      growth: 1.01
      decay: 0.99
    sellPrice: 900
    sellDynamic:
      min: 700
      max: 1100
      growth: 1.01
      decay: 0.99
```

### 2. STOCK Mode

```yaml
items:
  iron:
    typeDynaShop: STOCK
    buyPrice: 100
    sellPrice: 80
    stock:
      base: 1000
      min: 0
      max: 10000
      buyModifier: 1.0
      sellModifier: 1.0
```

### 3. STATIC_STOCK Mode

```yaml
items:
  gold:
    typeDynaShop: STATIC_STOCK
    buyPrice: 200
    sellPrice: 150
    stock:
      base: 500
      min: 0
      max: 5000
```

### 4. RECIPE Mode

```yaml
items:
  diamond_block:
    typeDynaShop: RECIPE
    recipe:
      type: SHAPED
      pattern:
        - "XXX"
        - "XXX"
        - "XXX"
      ingredients:
        X: diamond
    buyPrice: 0 # Optional, will be calculated
    sellPrice: 0 # Optional, will be calculated

  custom_mix:
    typeDynaShop: RECIPE
    recipe:
      type: SHAPELESS
      ingredients:
        A: minerais:emerald # reference to another item in a shop
        B: IRON_INGOT
        C: minerais:diamond
    buyPrice: 0
    sellPrice: 0

  iron_ingot_from_ore:
    typeDynaShop: RECIPE
    recipe:
      type: FURNACE
      input: minerais:iron_ore # reference to another item in a shop
    buyPrice: 0
    sellPrice: 0
```

- **type:** `SHAPED`, `SHAPELESS` ou `FURNACE`
- **ingredients:**  
  - For each symbol, you can use either a material name (e.g., `DIAMOND`) or a reference `shopID:itemID` (e.g., `minerais:iron_ore`).
  - For `FURNACE`, use `input:` instead of `ingredients:`.

**Note:**  
The syntax `shopID:itemID` allows you to use any item from another shop as an ingredient, making the system very flexible.

### 5. LINK Mode

```yaml
items:
  deepslate_coal:
    typeDynaShop: LINK
    link: ores:1 # target shopID:itemID
    buyPrice: -1 # Optional, will be ignored
    sellPrice: 25
```

### Advanced: Different type for buy and sell

You can define a different DynaShop type for buying and selling the same item using `buyType` and `sellType`:

```yaml
items:
  gold_block:
    typeDynaShop: DYNAMIC         # General type (fallback) or set NONE
    dynaShop:
      buyType: RECIPE              # Use RECIPE mode for buying
      sellType: DYNAMIC           # Use DYNAMIC mode for selling
    buyPrice: 500
    sellPrice: 300
    recipe:
      type: SHAPED
      pattern:
        - "XXX"
        - "XXX"
        - "XXX"
      ingredients:
        X: ores:gold_ingot
    buyDynamic:
      min: 400
      max: 600
    sellDynamic:
      min: 250
      max: 350
```

- `dynaShop.buyType`: Type used for buying (e.g. `DYNAMIC`, `RECIPE`, `LINK`)
- `dynaShop.sellType`: Type used for selling

If not set, the plugin uses the general `typeDynaShop` for both buy and sell.

**This allows you to, for example, have a fixed price for selling but a dynamic price for buying, or use stock only for one direction.**

---

## Cache & Performance Management

- **`full` mode**: Prices, stock, recipes, etc. are cached for better performance.
- **`realtime` mode**: Prices are recalculated every time, no cache (always fresh data, higher server load).
- **Cache durations**: Configurable in `config.yml` (see above).

**Tip**:  
For large servers, use `full` mode.  
For testing or highly dynamic shops, use `realtime`.

---

## Dynamic Placeholders

In item lores (in shops or selection menus), you can use:

- `%dynashop_current_buyPrice%`: Current buy price
- `%dynashop_current_sellPrice%`: Current sell price
- `%dynashop_current_buyMinPrice%` / `%dynashop_current_buyMaxPrice%`
- `%dynashop_current_sellMinPrice%` / `%dynashop_current_sellMaxPrice%`
- `%dynashop_current_buy%` / `%dynashop_current_sell%`: Formatted price (with min/max if applicable)
- `%dynashop_current_stock%`: Current stock
- `%dynashop_current_maxstock%`: Max stock
- `%dynashop_current_stock_ratio%`: Current/max stock
- `%dynashop_current_colored_stock_ratio%`: Current/max stock with color based on level

**Lines with placeholders will be automatically hidden if the value is "N/A" or "-1" (configurable).**

Collecte des informations sur l’espace de travailVoici un guide clair pour configurer le GUI de ShopGUIPlus **avec les placeholders dynamiques DynaShop** en anglais, basé sur ton config.yml.  
Ce guide explique comment personnaliser les lores et boutons pour afficher les prix dynamiques, le stock, etc.

---

## 🛒 ShopGUIPlus GUI & DynaShop Placeholders Setup

### 1. Lore format for items (shop, buy, sell, sell all)

Replace the default `%buy%` and `%sell%` with DynaShop placeholders for dynamic prices and stock:

```yaml
shopItemLoreFormat:
  # Lore for items in the main shop GUI
  item:
    - ""
    - "&7&l» &fBuy price: &c%dynashop_current_buy%"
    - "&7&l» &fSell price: &a%dynashop_current_sell%"
    - "&7&l» &fStock: &a%dynashop_current_colored_stock_ratio%"
    - ""
    - "&f➢ Right-click to &asell"
    - "&f➤ Middle-click to &bsell all"
    - "&f➣ Left-click to &cbuy"
    - ""
  # Lore for items in the buy GUI (amount selection)
  itemBuyGUI:
    - ""
    - "&7&l» &fBuy price: &c%dynashop_current_buy%"
    - "&7&l» &fStock: &a%dynashop_current_colored_stock_ratio%"
    - ""
  # Lore for items in the sell GUI (amount selection)
  itemSellGUI:
    - ""
    - "&7&l» &fSell price: &a%dynashop_current_sell%"
    - "&7&l» &fStock: &a%dynashop_current_colored_stock_ratio%"
    - ""
  # Lore for the sell all button in the sell GUI
  itemSellGUISellAll:
    - ""
    - "&7&l» &fSell all for: &a%dynashop_current_sell%"
    - "&7&l» &fStock: &a%dynashop_current_colored_stock_ratio%"
    - ""
    - "&f➤ Click to &bsell all"
    - ""
```

### 2. Placeholders you can use

- `%dynashop_current_buyPrice%` : Raw buy price
- `%dynashop_current_sellPrice%` : Raw sell price
- `%dynashop_current_buyMinPrice%` / `%dynashop_current_buyMaxPrice%`
- `%dynashop_current_sellMinPrice%` / `%dynashop_current_sellMaxPrice%`
- `%dynashop_current_buy%` / `%dynashop_current_sell%` : Formatted price (with min/max if applicable)
- `%dynashop_current_stock%` : Current stock
- `%dynashop_current_maxstock%` : Max stock
- `%dynashop_current_stock_ratio%` : Current/max stock
- `%dynashop_current_colored_stock_ratio%` : Current/max stock with color

**Tip:**  
Lines with placeholders are automatically hidden if the value is "N/A" or "-1" (see `hideBuyPriceForUnbuyable` and `hideSellPriceForUnsellable` in your config).

---

### 3. Buttons and GUI elements

You can also use DynaShop placeholders in the lore of your custom buttons (for example, in bulk buy/sell GUIs):

```yaml
amountSelectionGUIBulkBuy:
  buttons:
    buy1:
      item:
        material: CHEST
        quantity: 1
        name: "&aBuy 1 stack"
        lore:
          - "&7Price: &c%dynashop_current_buy%"
      value: 1
      slot: 0
    # ...repeat for buy2, buy3, etc.
    cancel:
      item:
        material: RED_STAINED_GLASS
        quantity: 1
        name: "&c&lCancel"
      slot: 13
```

Same for `amountSelectionGUIBulkSell` (replace `%buy%`/`%sell%` with `%dynashop_current_buy%`/`%dynashop_current_sell%`).

---

### 4. Example for the main menu categories

You can customize the main menu items as usual, but you can also add dynamic info in the lore if you want:

```yaml
shopMenuItems:
  1:
    item:
      material: GRASS_BLOCK
      quantity: 1
      name: "&9&lBlocks"
      lore:
        - "&7Click to open the blocks shop!"
    shop: "blocks"
    slot: 11
  # ...etc.
```

---

### 5. Advanced: Show min/max prices

If you want to show min/max prices, add these lines in your lore:

```yaml
- "&7Min: &a%dynashop_current_buyMinPrice% &7/ Max: &c%dynashop_current_buyMaxPrice%"
- "&7Min: &a%dynashop_current_sellMinPrice% &7/ Max: &c%dynashop_current_sellMaxPrice%"
```

---

### 6. Tips

- You can use all DynaShop placeholders in any lore or button.
- Placeholders work in all GUIs: shop, buy, sell, bulk buy/sell.
- For more info, see your config.yml and the [ShopGUIPlus documentation](https://docs.brcdev.net/#/shopgui/shops-items-setup).

---

**Result:**  
Your GUIs will always display up-to-date prices and stock, and your players will see dynamic info everywhere!

---

## Limits & Cooldowns

You can limit the number of buy/sell transactions per player, per item, and per period.  
The system supports both **numeric cooldowns** (in seconds) and **periods** (`DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`, `FOREVER`).

### Example configuration

```yaml
limit:
  buy: 100           # Max items a player can buy per period/cooldown
  sell: 50           # Max items a player can sell per period/cooldown
  cooldown: 3600     # Cooldown in seconds (here: 1 hour)
```

Or with a period:

```yaml
limit:
  buy: 1000
  sell: 1000
  cooldown: DAILY    # Reset every day at midnight
```

**Available periods:**  
- `DAILY` (reset every day at midnight)
- `WEEKLY` (reset every Monday at midnight)
- `MONTHLY` (reset every 1st of the month)
- `YEARLY` (reset every January 1st)
- `FOREVER` (never resets)

### How it works

- The plugin tracks transactions per player, per shop, per item, and per type (buy/sell).
- When the limit is reached, the player cannot buy/sell more until the cooldown/period resets.
- The plugin displays a message with the remaining time or amount.
- Limits are stored in the database and cleaned up automatically.

### Commands

- `/resetlimits <player> [shopID] [itemID]`  
  Reset all limits for a player, or for a specific item.

### Advanced

- You can use different limits for each item.
- If you set `cooldown` to a number, it is interpreted as seconds.
- If you set `cooldown` to a string (`DAILY`, `WEEKLY`, ...), it uses the corresponding period.
- If you omit `limit.buy` or `limit.sell`, there is no limit for that action.

### Example with different periods

```yaml
items:
  diamond:
    typeDynaShop: DYNAMIC
    buyPrice: 1000
    limit:
      buy: 10
      sell: 5
      cooldown: DAILY

  gold:
    typeDynaShop: DYNAMIC
    buyPrice: 500
    limit:
      buy: 100
      sell: 100
      cooldown: 3600 # 1 hour
```

**Note:**  
Limits are enforced even if the server restarts.  
You can monitor and clean up old transaction data automatically (see plugin logs for details).
---

## Reload & Best Practices

- Use `/dynashop reload` to reload config and shops.
- **Never call onDisable/onEnable manually** in code.
- After reload, cache and configs are reset according to the chosen mode.

---

## Example ShopGUIPlus Configuration

In `plugins/ShopGUIPlus/shops/myshop.yml`:

```yaml
myshop:
  name: "Shop &l»&r MyShop #%page%"
  items:
    diamond:
      type: item
      item:
        material: DIAMOND
        quantity: 1
      typeDynaShop: DYNAMIC
      buyPrice: 1000
      sellPrice: 900
      buyDynamic:
        min: 800
        max: 1200
      sellDynamic:
        min: 700
        max: 1100
      slot: 0
      page: 1
```

---

## FAQ

- **Q: Can I mix several modes in the same shop?**  
  **A: Yes**, each item can have its own `typeDynaShop`.

- **Q: How to force a price recalculation?**  
  **A: Use `realtime` mode or lower the cache duration for `full` mode.**

- **Q: Placeholders not showing?**  
  **A: Make sure the itemId is set in the internal map when opening the selection menu.**

---

For more details, check the example config files and comments in the source code.