# Système de Recettes Amélioré - Guide d'Utilisation

## Vue d'Ensemble

Le système de recettes amélioré de DynaShop introduit une validation native des recettes Minecraft, une sélection intelligente de recettes et une gestion robuste des cas d'ambiguïté. Cette amélioration garantit que vos recettes RECIPE respectent fidèlement la logique de crafting de Minecraft vanilla.

## Nouvelles Fonctionnalités

### 1. **Validation Native Minecraft**
- Utilisation de l'API Bukkit (`Bukkit.getRecipesFor()`) pour récupérer les recettes officielles
- Validation automatique des recettes configurées contre les recettes Minecraft
- Détection des divergences et suggestions d'amélioration

### 2. **Gestion des Recettes Multiples**
- Détection automatique de toutes les variantes de recettes pour un item
- Sélection intelligente de la meilleure recette selon différents critères
- Support des items ayant plusieurs recettes (ex: bâton avec différents types de bois)

### 3. **Critères de Sélection Avancés**
- **Coût minimum** : Sélectionne la recette la moins chère
- **Disponibilité maximum** : Privilégie les ingrédients les plus disponibles
- **Équilibré** : Balance entre coût, disponibilité et profit
- **Maximisation du profit** : Optimise la marge bénéficiaire

### 4. **Types de Recettes Supportés**
- `ShapedRecipe` : Recettes de craft avec pattern
- `ShapelessRecipe` : Recettes de craft sans ordre
- `FurnaceRecipe` : Four classique
- `BlastingRecipe` : Haut fourneau
- `SmokingRecipe` : Fumoir
- `CampfireRecipe` : Feu de camp
- `StonecuttingRecipe` : Tailleur de pierre
- `SmithingRecipe` : Table de forge

## Configuration

### Options Principales (`config.yml`)

```yaml
recipe:
  # Validation des recettes
  use-minecraft-validation: true       # Activer la validation Minecraft
  enforce-minecraft-compliance: false  # Forcer l'utilisation des recettes Minecraft
  prefer-vanilla: true                 # Préférer les recettes vanilla
  allow-custom: true                   # Autoriser les recettes personnalisées
  vanilla-bonus: 1.0                   # Bonus pour les recettes conformes
  min-profit-margin: 0.1               # Marge bénéficiaire minimum
  
  # Critères de sélection
  optimization-criteria: "balanced"    # Critère de sélection par défaut
```

### Options de Critères

| Critère | Description | Utilisation |
|---------|-------------|-------------|
| `minimum_cost` | Coût total minimum | Économie de ressources |
| `maximum_availability` | Disponibilité maximum | Éviter les ruptures |
| `balanced` | Équilibre optimal | Usage général |
| `profit_maximization` | Profit maximum | Maximiser les revenus |

## Utilisation

### 1. **Migration Automatique**

Le système est rétrocompatible. Les recettes existantes continueront de fonctionner avec les améliorations suivantes :

- Validation automatique au démarrage
- Avertissements pour les recettes non-conformes
- Suggestions d'amélioration dans les logs

### 2. **Configuration d'une Recette**

```yaml
# Exemple : Recette de bâton
STICK:
  recipe:
    type: SHAPED
    pattern:
      - "X"
      - "X"
    ingredients:
      X: "minerais:OAK_PLANKS"
```

Le système va :
1. Valider que cette recette existe dans Minecraft
2. Vérifier si d'autres variantes existent (autres types de bois)
3. Sélectionner la meilleure selon vos critères
4. Calculer le prix optimal

### 3. **Recettes Multiples**

Si Minecraft permet plusieurs recettes pour un même item :

```yaml
# Le système détectera automatiquement :
# - Bâton avec planches de chêne
# - Bâton avec planches de bouleau
# - Bâton avec planches de sapin
# - etc.

# Et sélectionnera la meilleure selon :
# - Le coût des ingrédients
# - La disponibilité en stock
# - Les critères configurés
```

## Logs et Debugging

### Messages de Validation

```
[INFO] Recette sélectionnée pour STICK: coût=1.50, stock=100, vanilla=true
[WARNING] Recette configurée ne correspond pas aux recettes Minecraft (similarité: 75%)
[INFO] Aucune recette Minecraft trouvée pour CUSTOM_ITEM - autorisation de la recette custom
```

### Debugging Avancé

Activez le debug dans `config.yml` :

```yaml
debug: true
```

Pour obtenir des informations détaillées sur :
- Le processus de validation
- La sélection de recettes
- Les calculs de coût
- Les performances du cache

## Avantages

### 1. **Fidélité à Minecraft**
- Respect des règles de crafting officielles
- Compatibilité avec les mises à jour Minecraft
- Prévention des recettes "fantaisistes"

### 2. **Performance**
- Cache intelligent des validations
- Calculs optimisés
- Sélection rapide des meilleures recettes

### 3. **Flexibilité**
- Support des recettes personnalisées
- Critères de sélection configurables
- Migration progressive possible

### 4. **Robustesse**
- Gestion des cas d'erreur
- Fallback vers l'ancien système
- Validation en temps réel

## Migration et Compatibilité

### Étapes de Migration

1. **Sauvegarde** : Sauvegardez votre configuration actuelle
2. **Activation** : Définissez `use-minecraft-validation: true`
3. **Vérification** : Consultez les logs pour les avertissements
4. **Optimisation** : Ajustez les critères selon vos besoins

### Compatibilité

- ✅ **Recettes existantes** : Fonctionnent sans modification
- ✅ **Recettes personnalisées** : Autorisées par défaut
- ✅ **Performance** : Améliorée par le cache
- ✅ **Rétrocompatibilité** : Complète avec l'ancien système

## Dépannage

### Problèmes Courants

**Recette non validée**
```
[WARNING] Recette configurée ne correspond pas aux recettes Minecraft
```
**Solution** : Vérifiez que votre recette correspond exactement aux recettes Minecraft ou activez `allow-custom: true`

**Performance lente**
```
[DEBUG] Cache miss pour la validation de recette
```
**Solution** : Le cache se peuple automatiquement. Les performances s'améliorent après quelques utilisations.

**Recette non trouvée**
```
[WARNING] Prix non trouvé pour l'ingrédient: DIAMOND
```
**Solution** : Assurez-vous que tous les ingrédients sont configurés dans vos shops.

## Exemples Pratiques

### Exemple 1 : Sword en Diamant

```yaml
DIAMOND_SWORD:
  recipe:
    type: SHAPED
    pattern:
      - " X "
      - " X "
      - " Y "
    ingredients:
      X: "minerais:DIAMOND"
      Y: "outils:STICK"
```

### Exemple 2 : Pain (Shapeless)

```yaml
BREAD:
  recipe:
    type: SHAPELESS
    ingredients:
      - "agriculture:WHEAT:3"
```

### Exemple 3 : Lingot de Fer (Four)

```yaml
IRON_INGOT:
  recipe:
    type: FURNACE
    input: "minerais:IRON_ORE"
```

Le système garantit que ces recettes correspondent exactement aux mécaniques Minecraft et sélectionne automatiquement les meilleures variantes disponibles.
