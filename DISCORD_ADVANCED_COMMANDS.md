# 🏪 DynaShop - Commandes Discord Avancées

## 📋 Table des Matières
1. [Commandes de Base](#commandes-de-base)
2. [Commandes Avancées](#commandes-avancées)
3. [Commandes d'Administration](#commandes-dadministration)
4. [Personnalisation des Messages](#personnalisation-des-messages)
5. [Permissions et Sécurité](#permissions-et-sécurité)

---

## 🔧 Commandes de Base

### `!dynashop help`
Affiche l'aide complète avec toutes les commandes disponibles.

### `!dynashop prix <shop> <item>`
Affiche les prix d'achat et de vente d'un item dans un shop spécifique.

### `!dynashop stock <shop> <item>`
Affiche le stock actuel d'un item avec pourcentage et indicateurs visuels.

### `!dynashop shops`
Liste tous les shops disponibles avec leurs noms et IDs.

### `!dynashop lowstock`
Affiche tous les items avec un stock faible selon le seuil configuré.

---

## 🚀 Commandes Avancées

### `!dynashop search <terme>`
**Recherche intelligente d'items dans tous les shops**
- Recherche par nom d'item ou ID
- Affiche les prix si disponibles
- Limite automatique à 15 résultats
- Tri par pertinence

**Exemple :**
```
!dynashop search diamond
!dynashop search épée
```

### `!dynashop compare <item>`
**Comparaison de prix entre tous les shops**
- Trouve tous les shops vendant l'item
- Affiche les prix d'achat et de vente
- Identifie les meilleurs deals
- Tri par prix croissant

**Exemple :**
```
!dynashop compare diamond_sword
!dynashop compare pomme
```

### `!dynashop top <expensive|cheap>`
**Classement des items les plus/moins chers**
- Top 10 des items les plus chers ou les moins chers
- Médailles pour les 3 premiers (🥇🥈🥉)
- Affichage avec shop et prix

**Exemple :**
```
!dynashop top expensive
!dynashop top cheap
```

### `!dynashop stats [shop]`
**Statistiques complètes**
- **Globales** : stats de tous les shops
- **Spécifiques** : stats d'un shop particulier
- Nombre d'items, types, valeurs, stock faible

**Exemple :**
```
!dynashop stats
!dynashop stats food
```

### `!dynashop info <shop>`
**Informations détaillées d'un shop**
- Nombre total d'items
- Répartition par type (stock, dynamique)
- Valeur totale des stocks
- Liste des items populaires
- Alertes de stock faible

**Exemple :**
```
!dynashop info weapons
!dynashop info food
```

### `!dynashop trend <shop> <item>`
**Tendances et historique des prix**
- Évolution des prix sur 24h
- Indicateurs de tendance (↑↓→)
- Graphiques visuels avec couleurs
- Prédictions de prix

**Exemple :**
```
!dynashop trend weapons diamond_sword
!dynashop trend food apple
```

---

## 🔧 Commandes d'Administration

> ⚠️ **Attention :** Ces commandes nécessitent les permissions Discord appropriées (Administrateur ou rôle Moderator/Admin)

### `!dynashop reload`
**Rechargement de la configuration**
- Recharge tous les fichiers de configuration
- Met à jour les paramètres Discord
- Confirmation visuelle du succès

### `!dynashop admin restock <shop> <item>`
**Restock forcé d'un item**
- Remet l'item au stock maximum
- Notification visuelle
- Logs d'activité

**Exemple :**
```
!dynashop admin restock weapons diamond_sword
```

### `!dynashop admin clear <shop>`
**Vidage complet d'un shop**
- Remet tous les items à zéro
- Demande de confirmation
- Compte des items affectés

**Exemple :**
```
!dynashop admin clear weapons
```

### `!dynashop admin reset`
**Reset global (DANGEREUX)**
- Remet TOUS les stocks à zéro
- Demande double confirmation
- Action irréversible

---

## 🎨 Personnalisation des Messages

Tous les messages Discord peuvent être personnalisés via le fichier `lang.yml` :

```yaml
discord:
  commands:
    help:
      title: "🏪 Mon Shop - Commandes"
      description: "Voici mes commandes :"
      # ... autres clés
    
    search:
      title: "🔍 Recherche: %term%"
      no_results: "❌ Aucun résultat pour: **%term%**"
      # ... autres clés
```

### Variables disponibles :
- `%shop%` - Nom du shop
- `%item%` - Nom de l'item
- `%term%` - Terme de recherche
- `%count%` - Nombre d'éléments
- `%price%` - Prix formaté
- `%stock%` - Stock actuel
- `%percentage%` - Pourcentage

---

## 🔐 Permissions et Sécurité

### Permissions requises :
- **Utilisateur normal** : Toutes les commandes de consultation
- **Moderator/Admin** : Commandes d'administration (`reload`, `admin`)
- **Administrateur Discord** : Accès complet

### Sécurité :
- Vérification des permissions avant chaque action
- Logs de toutes les actions administratives
- Confirmation requise pour les actions dangereuses
- Limitation des résultats pour éviter le spam

### Configuration des canaux :
```yaml
discord:
  enabled: true
  channels:
    commands: "123456789"      # Canal pour les commandes
    announcements: "987654321"  # Canal pour les annonces
  low-stock-threshold: 10       # Seuil de stock faible
```

---

## 🌟 Fonctionnalités Intelligentes

### Recherche Floue
- Suggestions automatiques pour les commandes inconnues
- Calcul de distance de Levenshtein pour les corrections
- Messages d'erreur contextuels

### Gestion des Erreurs
- Messages d'erreur personnalisés
- Suggestions de commandes alternatives
- Logs détaillés pour le debugging

### Performance
- Traitement asynchrone pour éviter les blocages
- Limitation des résultats pour les grandes listes
- Cache intelligent pour les données fréquemment consultées

### Expérience Utilisateur
- Embeds colorés selon le contexte
- Emojis contextuels pour une meilleure lisibilité
- Timestamps sur tous les messages
- Pagination automatique pour les longs résultats

---

## 📊 Exemples d'Utilisation

### Scénario 1 : Recherche d'item
```
Utilisateur: !dynashop search diamond
Bot: 🔍 Résultats de recherche: diamond
     🏪 Diamond Sword dans Weapons - Achat: 500€ | Vente: 300€
     🏪 Diamond Pickaxe dans Tools - Achat: 300€ | Vente: 180€
     ... et 3 autres résultats
```

### Scénario 2 : Comparaison de prix
```
Utilisateur: !dynashop compare apple
Bot: ⚖️ Comparaison: Apple
     🏪 Food Shop
       💳 Achat: 5€
       💰 Vente: 3€
     🏪 General Store
       💳 Achat: 7€
       💰 Vente: 4€
     🏆 Meilleur prix d'achat: Food Shop - 5€
```

### Scénario 3 : Administration
```
Admin: !dynashop admin restock weapons diamond_sword
Bot: ✅ Restock Effectué
     Diamond Sword dans Weapons Shop a été restocké à 100
```

---

## 🔄 Changelog

### Version 1.6.0
- ✅ Ajout des commandes avancées (search, compare, top, stats)
- ✅ Système de permissions Discord
- ✅ Commandes d'administration
- ✅ Personnalisation complète des messages
- ✅ Recherche intelligente avec suggestions
- ✅ Gestion d'erreurs améliorée
- ✅ Performance optimisée
- ✅ Documentation complète

---

## 📞 Support

Pour toute question ou problème :
1. Vérifiez la configuration dans `config.yml`
2. Consultez les logs du serveur
3. Testez les permissions Discord
4. Vérifiez que DiscordSRV est bien configuré

Les commandes sont conçues pour être robustes et gérer la plupart des cas d'erreur automatiquement.
