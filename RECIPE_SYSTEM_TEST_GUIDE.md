# Test du Système de Recettes Amélioré

## Configuration de Test

Pour tester le nouveau système, ajoutez ces configurations à vos fichiers :

### 1. Configuration dans `config.yml`

```yaml
# Activer le nouveau système
recipe:
  use-minecraft-validation: true
  prefer-vanilla: true
  allow-custom: true
  optimization-criteria: "balanced"

# Activer le debug pour voir les logs
debug: true
```

### 2. Exemple de Recette de Test

```yaml
# Dans votre shop config (ex: tools.yml)
DIAMOND_SWORD:
  item:
    material: DIAMOND_SWORD
    quantity: 1
  buyPrice: -1
  sellPrice: 300
  
  # Configuration DynaShop
  dynashop:
    type: RECIPE
    
    recipe:
      type: SHAPED
      pattern:
        - " X "
        - " X "
        - " Y "
      ingredients:
        X: "minerais:DIAMOND"      # Référence à l'item diamant dans le shop "minerais"
        Y: "outils:STICK"          # Référence au bâton dans le shop "outils"
```

## Tests à Effectuer

### Test 1 : Validation de Recette Vanilla

**Objectif** : Vérifier que le système valide correctement une recette Minecraft standard

**Étapes** :
1. Configurez l'épée en diamant comme ci-dessus
2. Redémarrez le serveur ou utilisez `/dynashop reload`
3. Vérifiez les logs pour voir :
   ```
   [INFO] Recette sélectionnée pour DIAMOND_SWORD: coût=X, stock=Y, vanilla=true
   [DEBUG] Utilisation du calculateur amélioré avec validation Minecraft pour tools:DIAMOND_SWORD
   ```

**Résultat attendu** : Validation réussie avec similarité 100%

### Test 2 : Recette avec Variantes Multiples

**Objectif** : Tester la sélection automatique entre plusieurs recettes possibles

```yaml
STICK:
  item:
    material: STICK
    quantity: 1
  
  dynashop:
    type: RECIPE
    recipe:
      type: SHAPED
      pattern:
        - "X"
        - "X"
      ingredients:
        X: "agriculture:OAK_PLANKS"  # Le système trouvera automatiquement d'autres variantes
```

**Résultat attendu** : Le système détecte toutes les variantes (chêne, bouleau, épicéa, etc.) et sélectionne la moins chère

### Test 3 : Recette Personnalisée

**Objectif** : Vérifier que les recettes custom sont acceptées

```yaml
CUSTOM_TOOL:
  item:
    material: IRON_PICKAXE
    quantity: 1
  
  dynashop:
    type: RECIPE
    recipe:
      type: SHAPED
      pattern:
        - "XXX"
        - " Y "
        - " Y "
      ingredients:
        X: "special:RARE_METAL"     # Item qui n'existe pas dans Minecraft
        Y: "outils:STICK"
```

**Résultat attendu** : Avertissement mais acceptation de la recette custom

### Test 4 : Recette Non-Conforme

**Objectif** : Tester la gestion des recettes incorrectes

```yaml
WRONG_BREAD:
  item:
    material: BREAD
    quantity: 1
  
  dynashop:
    type: RECIPE
    recipe:
      type: SHAPED
      pattern:
        - "XXX"
        - "XXX"
        - "XXX"
      ingredients:
        X: "agriculture:WHEAT"      # Recette incorrecte (le pain n'a pas de pattern)
```

**Résultat attendu** : Avertissement de non-conformité et utilisation de la recette Minecraft

## Commandes de Test

### Tester le Calcul de Prix

```
/dynashop price tools DIAMOND_SWORD
```

### Recharger la Configuration

```
/dynashop reload
```

### Vérifier les Logs

Surveillez la console pour ces types de messages :

```
[INFO] Recette sélectionnée: coût=15.50, stock=25, vanilla=true
[WARNING] Recette configurée ne correspond pas aux recettes Minecraft (similarité: 75%)
[DEBUG] Validation contre 3 recettes Minecraft trouvées
[INFO] Aucune recette Minecraft trouvée pour CUSTOM_ITEM - autorisation de la recette custom
```

## Vérification des Performances

### Test de Charge

1. Configurez 10-20 items de type RECIPE
2. Utilisez plusieurs joueurs simultanément
3. Vérifiez que les temps de réponse restent acceptables

### Cache

1. Premier accès : Temps plus long (validation)
2. Accès suivants : Très rapide (cache)
3. Vérifiez les logs de cache :
   ```
   [DEBUG] Cache hit pour la validation de tools:DIAMOND_SWORD
   [DEBUG] Cache miss - validation de la recette tools:NEW_ITEM
   ```

## Problèmes Possibles et Solutions

### Erreur : "No enclosing instance"
**Cause** : Problème de compilation
**Solution** : Recompiler le plugin complètement

### Erreur : "ItemStack null"
**Cause** : Référence d'item incorrecte
**Solution** : Vérifier que les items référencés existent dans les shops

### Performance Lente
**Cause** : Cache froid au démarrage
**Solution** : Normal, les performances s'améliorent après quelques utilisations

### Recette Non Validée
**Cause** : Divergence avec Minecraft
**Solution** : Vérifier la recette ou activer `allow-custom: true`

## Métriques de Succès

✅ **Compilation** : Le plugin compile sans erreur
✅ **Démarrage** : Le serveur démarre sans problème
✅ **Validation** : Les recettes vanilla sont validées à 100%
✅ **Sélection** : Les meilleures recettes sont sélectionnées automatiquement
✅ **Performance** : Temps de réponse acceptables (<10ms après cache)
✅ **Logs** : Messages clairs et informatifs
✅ **Compatibilité** : Ancien système fonctionne toujours
✅ **Configuration** : Options flexibles et documentées

## Prochaines Étapes

1. **Test en Production** : Déployer sur un serveur de test
2. **Feedback Utilisateurs** : Recueillir les retours des administrateurs
3. **Optimisations** : Ajuster selon les besoins spécifiques
4. **Documentation** : Mise à jour de la documentation utilisateur
5. **Formation** : Former les équipes sur les nouvelles fonctionnalités
