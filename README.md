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
The syntax `shopID:itemID` allows you to use any item from another shop as an ingredient, making the system very flexible. -->

### 5. LINK Mode

```yaml
items:
  deepslate_coal:
    typeDynaShop: LINK
    link: ores:1 # target shopID:itemID
    buyPrice: -1 # Optional, will be ignored
    sellPrice: 25
```

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

---

## Limits & Cooldowns

In an item's config:

```yaml
limit:
  buy: 100
  sell: 100
  cooldown: 3600 # in seconds (1 hour)
```

- **buy/sell**: Per-player buy/sell limit per period.
- **cooldown**: Period duration (in seconds).

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