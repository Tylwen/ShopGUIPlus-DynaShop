# ShopGUI+ DynaShop (Addon)

Collecte des informations sur l’espace de travailVoici un **wiki complet** pour ShopGUIPlus-DynaShop, couvrant toutes les fonctionnalités principales, les modes de fonctionnement, et la configuration nécessaire pour chaque mode.

---

# 📖 Wiki ShopGUIPlus-DynaShop

## Sommaire

- Fonctionnalités principales
- Types de mode DynaShop
- Configuration générale
- Configuration des items par mode
- Gestion du cache et des performances
- Placeholders dynamiques
- Limites et cooldowns
- Reload et bonnes pratiques

---

## Fonctionnalités principales

- **Prix dynamiques** : Les prix d’achat/vente évoluent selon l’offre et la demande.
- **Stock dynamique** : Les items peuvent avoir un stock limité, influençant le prix.
- **Prix par recette** : Calcul automatique du prix d’un item selon sa recette de craft.
- **Système de cache configurable** : Choix entre performance (full cache) et données en temps réel (realtime).
- **Placeholders dynamiques** : Affichage des prix, stocks, min/max, etc. dans les lores.
- **Limites de transaction** : Limites d’achat/vente par joueur, par période.
- **Compatibilité complète ShopGUI+** : Supporte tous les types de shops, pages, menus de sélection, etc.

---

## Types de mode DynaShop

Chaque item peut fonctionner selon un **type de mode** :

| Mode           | Description                                                                 | Clé de config (`typeDynaShop`) |
|----------------|-----------------------------------------------------------------------------|-------------------------------|
| `DYNAMIC`      | Prix évolutif selon l’offre/demande, sans stock                             | `DYNAMIC`                     |
| `STOCK`        | Prix évolutif + gestion de stock (quantité limitée)                         | `STOCK`                       |
| `STATIC_STOCK` | Prix fixe, mais gestion de stock (quantité limitée)                         | `STATIC_STOCK`                |
| `RECIPE`       | Prix calculé automatiquement selon la recette de craft                      | `RECIPE`                      |
| `LINK`         | L’item hérite du prix d’un autre item (shopID:itemID)                      | `LINK`                        |

---

## Configuration générale

Dans config.yml du plugin DynaShop :

```yaml
cache:
  mode: "full" # "full" (performances) ou "realtime" (données fraîches)
  durations:
    price: 30
    display: 10
    recipe: 300
    stock: 20
    calculated: 60

gui-refresh:
  default-items: 1000   # ms entre chaque refresh d’inventaire normal
  critical-items: 300   # ms pour les items critiques (ex: stock très faible)
```

---

## Configuration des items par mode

### 1. Mode DYNAMIC

```yaml
items:
  diamant:
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

### 2. Mode STOCK

```yaml
items:
  fer:
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

### 3. Mode STATIC_STOCK

```yaml
items:
  or:
    typeDynaShop: STATIC_STOCK
    buyPrice: 200
    sellPrice: 150
    stock:
      base: 500
      min: 0
      max: 5000
```

### 4. Mode RECIPE

```yaml
items:
  bloc_de_diamant:
    typeDynaShop: RECIPE
    recipe:
      type: SHAPED
      pattern:
        - "XXX"
        - "XXX"
        - "XXX"
      ingredients:
        X: diamant
    buyPrice: 0 # Peut être omis, sera calculé
    sellPrice: 0 # Peut être omis, sera calculé
```

### 5. Mode LINK

```yaml
items:
  charbon_deepslate:
    typeDynaShop: LINK
    link: minerais:1 # shopID:itemID cible
    buyPrice: -1 # Optionnel, sera ignoré
    sellPrice: 25
```

---

## Gestion du cache et des performances

- **Mode `full`** : Les prix, stocks, recettes, etc. sont mis en cache pour de meilleures performances.
- **Mode `realtime`** : Les prix sont recalculés à chaque affichage, sans utiliser le cache (pour des données toujours fraîches, mais plus de charge serveur).
- **Durées de cache** : Configurables dans config.yml (voir plus haut).

**Astuce** :  
Pour un serveur avec beaucoup de joueurs, privilégiez le mode `full`.  
Pour des tests ou un shop très dynamique, utilisez `realtime`.

---

## Placeholders dynamiques

Dans les lores des items (dans les shops ou menus de sélection), vous pouvez utiliser :

- `%dynashop_current_buyPrice%` : Prix d’achat actuel
- `%dynashop_current_sellPrice%` : Prix de vente actuel
- `%dynashop_current_buyMinPrice%` / `%dynashop_current_buyMaxPrice%`
- `%dynashop_current_sellMinPrice%` / `%dynashop_current_sellMaxPrice%`
- `%dynashop_current_buy%` / `%dynashop_current_sell%` : Prix formaté (avec min/max si applicable)
- `%dynashop_current_stock%` : Stock actuel
- `%dynashop_current_maxstock%` : Stock max
- `%dynashop_current_stock_ratio%` : Stock actuel/max
- `%dynashop_current_colored_stock_ratio%` : Stock actuel/max avec couleur selon le niveau

**Les lignes contenant des placeholders seront automatiquement masquées si la valeur est "N/A" ou "-1" selon la config.**

---

## Limites et cooldowns

Dans la config d’un item :

```yaml
limit:
  buy: 100
  sell: 100
  cooldown: 3600 # en secondes (1h)
```

- **buy/sell** : Limite d’achat/vente par joueur et par période.
- **cooldown** : Durée de la période (en secondes).

---

## Reload et bonnes pratiques

- Utilisez la commande `/dynashop reload` pour recharger la config et les shops.
- **Ne jamais appeler onDisable/onEnable manuellement** dans le code.
- Après un reload, le cache et les configs sont réinitialisés selon le mode choisi.

---

## Exemples de configuration ShopGUIPlus

Dans `plugins/ShopGUIPlus/shops/monshop.yml` :

```yaml
monshop:
  name: "Magasin &l»&r MonShop #%page%"
  items:
    diamant:
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

- **Q : Peut-on mixer plusieurs modes dans un même shop ?**  
  **R : Oui**, chaque item peut avoir son propre `typeDynaShop`.

- **Q : Comment forcer le recalcul d’un prix ?**  
  **R : Utilisez le mode `realtime` ou augmentez la durée du cache pour le mode `full`.**

- **Q : Les placeholders ne s’affichent pas ?**  
  **R : Vérifiez que l’itemId est bien renseigné dans la map interne lors de l’ouverture du menu de sélection.**

---

Pour plus de détails, consultez les fichiers de config d’exemple et les commentaires dans le code source.