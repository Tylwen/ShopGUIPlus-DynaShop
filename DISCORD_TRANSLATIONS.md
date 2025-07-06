# 🎉 Nouvelle Fonctionnalité : Traductions Discord Personnalisables

## 📋 Aperçu

Toutes les messages Discord du plugin DynaShop peuvent maintenant être personnalisés via le fichier `lang.yml`. Cela permet à chaque serveur de modifier les textes selon ses préférences, sa langue ou son style.

## 🚀 Fonctionnalités Ajoutées

### ✅ Messages Personnalisables
- **Commandes d'aide** : Titres, descriptions et champs
- **Messages d'erreur** : Tous les messages d'erreur des commandes
- **Affichage des prix** : Titres et champs des embeds de prix
- **Affichage du stock** : Titres dynamiques selon le niveau de stock
- **Liste des shops** : Formatage de la liste des boutiques
- **Stock faible** : Messages et formatage des items en rupture
- **Annonces automatiques** : Restockage, stock faible, changements de prix

### 🎨 Personnalisation Complète
- **Émojis** : Tous les émojis peuvent être changés
- **Couleurs** : Gestion des couleurs d'embed selon le contexte
- **Variables dynamiques** : Support des placeholders comme `%item%`, `%shop%`, `%stock%`, etc.
- **Formatage flexible** : Texte, gras, italique, couleurs Discord

## 📝 Configuration

### Structure dans `lang.yml`
```yaml
discord:
  commands:
    help:
      title: "🏪 DynaShop - Commandes Discord"
      description: "Voici les commandes disponibles :"
      field_prices: "📊 Prix"
      field_prices_desc: "`!dynashop prix <shop> <item>` - Voir les prix d'un item"
      # ... plus de champs
    
    errors:
      usage_prices: "❌ Usage: `!dynashop prix <shop> <item>`"
      shop_not_found: "❌ Shop non trouvé: %shop%"
      item_not_found: "❌ Item non trouvé: %item% dans le shop %shop%"
      # ... plus d'erreurs
    
    prices:
      title: "💰 Prix de %item%"
      description: "Shop: **%shop%**"
      field_buy: "💳 Prix d'achat"
      field_sell: "💰 Prix de vente"
      field_type: "🏪 Type"
    
    stock:
      title_good: "✅ Stock de %item%"
      title_warning: "⚠️ Stock de %item%"
      title_critical: "🚨 Stock de %item%"
      # ... plus de champs
    
    shops:
      title: "🏪 Liste des Shops"
      footer: "Total: %total% shops"
      more_shops: "... et %count% autres shops"
    
    lowstock:
      title: "⚠️ Items avec Stock Faible"
      no_items: "✅ Aucun item avec un stock faible trouvé !"
      item_format: "⚠️ **%item%** dans %shop%: %stock%"
  
  announcements:
    restock:
      title: "🔄 Shop Restocké !"
      description: "**%item%** a été restocké dans **%shop%**"
      # ... plus de champs
    
    lowstock:
      title_warning: "⚠️ Stock Faible !"
      title_critical: "🚨 Stock Faible !"
      # ... plus de champs
    
    price_change:
      title_increase: "📈 Changement de Prix !"
      title_decrease: "📉 Changement de Prix !"
      description_increase: "Le prix de **%type%** de **%item%** dans **%shop%** a augmenté"
      description_decrease: "Le prix de **%type%** de **%item%** dans **%shop%** a diminué"
      type_buy: "achat"
      type_sell: "vente"
      # ... plus de champs
```

## 🔧 Variables Disponibles

### Variables Générales
- `%item%` - Nom de l'item
- `%shop%` - Nom du shop
- `%stock%` - Stock actuel
- `%total%` - Total des shops
- `%count%` - Nombre d'éléments
- `%threshold%` - Seuil de stock faible

### Variables Spécifiques
- `%type%` - Type de prix (achat/vente)
- `%percentage%` - Pourcentage de stock

## 🎯 Exemples d'Usage

### Personnalisation Française
```yaml
discord:
  commands:
    help:
      title: "🏪 DynaShop - Commandes Discord"
      description: "Voici les commandes disponibles :"
```

### Personnalisation Anglaise
```yaml
discord:
  commands:
    help:
      title: "🏪 DynaShop - Discord Commands"
      description: "Here are the available commands:"
```

### Style Personnalisé
```yaml
discord:
  commands:
    help:
      title: "⚡ DYNASHOP ⚡"
      description: ">>> **Commandes disponibles** ✨"
```

## 🛠️ Implémentation Technique

### Classe `LangConfig`
- Nouvelle méthode `getDiscordMessage(String key, String defaultValue)`
- Support des variables avec `getDiscordMessage(String key, String defaultValue, String[]... replacements)`
- Gestion automatique des valeurs par défaut

### Classe `DiscordSRVManager`
- Remplacement de tous les messages codés en dur
- Utilisation de `plugin.getLangConfig().getDiscordMessage()`
- Support complet des variables dynamiques

## 🎨 Conseils de Personnalisation

### Émojis
- Utilisez des émojis Unicode ou Discord custom
- Testez la compatibilité avec votre serveur Discord

### Formatage Discord
- `**texte**` pour le gras
- `*texte*` pour l'italique
- `>>> texte` pour les citations
- `\`texte\`` pour le code inline

### Couleurs
- Les couleurs des embeds changent automatiquement selon le contexte
- Rouge pour les erreurs critiques
- Orange pour les avertissements
- Vert pour les succès
- Bleu pour les informations

## 🔄 Migration

### Avant
```java
event.getChannel().sendMessage("❌ Shop non trouvé: " + shopId).queue();
```

### Après
```java
event.getChannel().sendMessage(plugin.getLangConfig().getDiscordMessage(
    "discord.commands.errors.shop_not_found", 
    "❌ Shop non trouvé: %shop%", 
    new String[]{"%shop%", shopId}
)).queue();
```

## 🚀 Avantages

1. **Flexibilité** : Personnalisation complète des messages
2. **Multilingue** : Support facile de plusieurs langues
3. **Maintenance** : Modification des textes sans recompilation
4. **Consistance** : Tous les messages suivent le même système
5. **Évolutivité** : Ajout facile de nouveaux messages

## 📚 Documentation

Toutes les clés de traduction sont documentées dans le fichier `lang.yml` avec des valeurs par défaut sensées. Les valeurs par défaut sont en français pour correspondre au style du plugin.

---

*Cette fonctionnalité améliore considérablement l'expérience utilisateur en permettant une personnalisation complète de l'interface Discord du plugin.*
