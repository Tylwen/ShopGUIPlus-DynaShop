# Configuration Discord Exemple pour DynaShop

## config.yml - Section Discord
```yaml
discord:
  # Active ou désactive l'intégration Discord
  enabled: true
  
  # Configuration des canaux Discord
  channels:
    # Canal où les utilisateurs peuvent utiliser les commandes
    commands: "123456789012345678"
    
    # Canal pour les annonces automatiques (restock, prix, etc.)
    announcements: "987654321098765432"
  
  # Seuil de stock faible pour les alertes
  low-stock-threshold: 10
  
  # Configuration des annonces automatiques
  announcements:
    # Annonce les restocks automatiques
    auto-restock: true
    
    # Annonce les changements de prix significatifs (>5%)
    price-changes: true
    
    # Annonce les stocks faibles
    low-stock: true
  
  # Configuration des permissions
  permissions:
    # Rôles ayant accès aux commandes d'administration
    admin-roles:
      - "Admin"
      - "Moderator"
      - "Staff"
    
    # Rôles ayant accès à toutes les commandes (pas d'admin)
    user-roles:
      - "Player"
      - "Member"
      - "@everyone"
  
  # Configuration des limites
  limits:
    # Nombre maximum de résultats pour les commandes de recherche
    max-search-results: 15
    
    # Nombre maximum d'items dans le top
    max-top-items: 10
    
    # Cooldown entre les commandes (en secondes)
    command-cooldown: 3
  
  # Configuration des embeds
  embeds:
    # Couleur par défaut des embeds (format hex)
    default-color: "#3498db"
    
    # Couleurs spéciales
    success-color: "#2ecc71"
    error-color: "#e74c3c"
    warning-color: "#f39c12"
    
    # Footer personnalisé
    footer:
      text: "DynaShop v1.6.0"
      icon: "https://your-server.com/icon.png"
```

## lang.yml - Section Discord Complète
```yaml
discord:
  commands:
    help:
      title: "🏪 DynaShop - Commandes Discord"
      description: "Voici les commandes disponibles :"
      field_prices: "📊 Prix"
      field_prices_desc: "`!dynashop prix <shop> <item>` - Voir les prix d'un item"
      field_stock: "📦 Stock"
      field_stock_desc: "`!dynashop stock <shop> <item>` - Voir le stock d'un item"
      field_shops: "🏪 Shops"
      field_shops_desc: "`!dynashop shops` - Lister tous les shops"
      field_lowstock: "⚠️ Stock faible"
      field_lowstock_desc: "`!dynashop lowstock` - Voir les items avec stock faible"
      field_search: "🔍 Recherche"
      field_search_desc: "`!dynashop search <terme>` - Rechercher des items"
      field_compare: "⚖️ Comparaison"
      field_compare_desc: "`!dynashop compare <item>` - Comparer les prix"
      field_top: "🏆 Top"
      field_top_desc: "`!dynashop top <expensive|cheap>` - Items les + chers/moins chers"
      field_stats: "📈 Statistiques"
      field_stats_desc: "`!dynashop stats [shop]` - Voir les statistiques"
      field_info: "ℹ️ Informations"
      field_info_desc: "`!dynashop info <shop>` - Infos détaillées d'un shop"
      field_trend: "📊 Tendances"
      field_trend_desc: "`!dynashop trend <shop> <item>` - Voir les tendances de prix"
      field_admin: "🔧 Admin"
      field_admin_desc: "`!dynashop admin <action>` - Commandes d'administration"
      field_reload: "🔄 Reload"
      field_reload_desc: "`!dynashop reload` - Recharger la configuration"
      footer: "DynaShop"
    
    errors:
      usage_prices: "❌ Usage: `!dynashop prix <shop> <item>`"
      usage_stock: "❌ Usage: `!dynashop stock <shop> <item>`"
      usage_search: "❌ Usage: `!dynashop search <terme>`"
      usage_compare: "❌ Usage: `!dynashop compare <item>`"
      usage_top: "❌ Usage: `!dynashop top <expensive|cheap>`"
      usage_info: "❌ Usage: `!dynashop info <shop>`"
      usage_admin: "❌ Usage: `!dynashop admin <restock|clear|reset>`"
      usage_admin_restock: "❌ Usage: `!dynashop admin restock <shop> <item>`"
      usage_admin_clear: "❌ Usage: `!dynashop admin clear <shop>`"
      usage_trend: "❌ Usage: `!dynashop trend <shop> <item>`"
      no_permission: "❌ Vous n'avez pas la permission d'utiliser cette commande."
      did_you_mean: "💡 Peut-être vouliez-vous dire: `!dynashop %suggestion%`"
      reload_error: "❌ Erreur lors du rechargement de la configuration."
      info_error: "❌ Erreur lors de la récupération des informations."
      admin_error: "❌ Erreur lors de l'exécution de la commande admin."
      admin_restock_error: "❌ Erreur lors du restock."
      admin_clear_error: "❌ Erreur lors du vidage."
      admin_reset_error: "❌ Erreur lors du reset."
      trend_error: "❌ Erreur lors de la récupération des tendances."
      unknown_command: "❌ Commande inconnue. Utilisez `!dynashop help`"
      shop_not_found: "❌ Shop non trouvé: %shop%"
      item_not_found: "❌ Item non trouvé: %item% dans le shop %shop%"
      shop_or_item_not_found: "❌ Shop ou item non trouvé."
      no_shops: "❌ Aucun shop trouvé."
      no_stock_system: "❌ Cet item n'a pas de système de stock."
      price_error: "❌ Impossible de récupérer les prix pour cet item."
      stock_error: "❌ Erreur lors de la récupération du stock."
      shops_error: "❌ Erreur lors de la récupération des shops."
      lowstock_error: "❌ Erreur lors de la récupération du stock faible."
      search_error: "❌ Erreur lors de la recherche."
      compare_error: "❌ Erreur lors de la comparaison."
      top_error: "❌ Erreur lors du classement."
      stats_error: "❌ Erreur lors de la récupération des statistiques."
    
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
      description: "Shop: **%shop%**"
      field_current: "📦 Stock Actuel"
      field_max: "📊 Stock Maximum"
      field_percentage: "📈 Pourcentage"
    
    shops:
      title: "🏪 Liste des Shops"
      footer: "Total: %total% shops"
      more_shops: "... et %count% autres shops"
    
    lowstock:
      title: "⚠️ Items avec Stock Faible"
      no_items: "✅ Aucun item avec un stock faible trouvé !"
      footer: "Seuil: %threshold%"
      item_format: "⚠️ **%item%** dans %shop%: %stock%"
    
    search:
      title: "🔍 Résultats de recherche: %term%"
      no_results: "❌ Aucun résultat trouvé pour: **%term%**"
      more_results: "... et %count% autres résultats"
      footer: "Trouvé: %count% résultats"
    
    compare:
      title: "⚖️ Comparaison: %item%"
      no_results: "❌ Aucun item trouvé pour: **%item%**"
      best_buy: "🏆 Meilleur prix d'achat"
      best_sell: "🏆 Meilleur prix de vente"
      footer: "Shops comparés: %count%"
    
    top:
      title_expensive: "🏆 Items les plus chers"
      title_cheap: "🏆 Items les moins chers"
      no_results: "❌ Aucun item avec prix trouvé."
      footer: "Top %count% sur %total% items"
    
    stats:
      global_title: "📈 Statistiques Globales"
      shop_title: "📈 Statistiques: %shop%"
      total_shops: "🏪 Total Shops"
      total_items: "📦 Total Items"
      stock_items: "📊 Items avec Stock"
      dynamic_items: "⚡ Items Dynamiques"
      low_stock_items: "⚠️ Stock Faible"
      total_value: "💰 Valeur Stock Total"
      avg_price: "💰 Prix Moyen"
      footer: "DynaShop Stats"
    
    reload:
      title: "🔄 Configuration Rechargée"
      description: "La configuration de DynaShop a été rechargée avec succès !"
    
    info:
      title: "🏪 Informations: %shop%"
      description: "Détails complets du shop **%shop%**"
      field_total_items: "📦 Total Items"
      field_stock_items: "📊 Items avec Stock"
      field_dynamic_items: "⚡ Items Dynamiques"
      field_low_stock: "⚠️ Stock Faible"
      field_total_value: "💰 Valeur Totale"
      field_popular_items: "🔥 Items Populaires"
      footer: "Shop ID: %id%"
      no_items: "Aucun item disponible"
    
    trend:
      title: "📊 Tendance: %item%"
      description: "Shop: **%shop%**"
      field_current_buy: "💳 Prix d'achat actuel"
      field_current_sell: "💰 Prix de vente actuel"
      field_trend: "📈 Tendance"
      footer: "Données basées sur les 24 dernières heures"
      stable: "Prix stable"
      increasing: "Prix en hausse"
      decreasing: "Prix en baisse"
    
    admin:
      restock_success: "✅ Restock Effectué"
      restock_description: "**%item%** dans **%shop%** a été restocké à %stock%"
      clear_success: "✅ Shop Vidé"
      clear_description: "**%shop%** a été vidé. %count% items ont été remis à zéro."
      reset_confirm: "⚠️ Confirmation Reset"
      reset_description: "Cette action va remettre à zéro TOUS les stocks de TOUS les shops.\n**Cette action est irréversible !**\n\nPour confirmer, utilisez: `!dynashop admin reset CONFIRM`"
  
  announcements:
    restock:
      title: "🔄 Shop Restocké !"
      description: "**%item%** a été restocké dans **%shop%**"
      field_new_stock: "Nouveau Stock"
      field_percentage: "Pourcentage"
      footer: "DynaShop"
    
    lowstock:
      title_warning: "⚠️ Stock Faible !"
      title_critical: "🚨 Stock Faible !"
      description: "**%item%** dans **%shop%** a un stock faible"
      field_current: "Stock Actuel"
      field_percentage: "Pourcentage"
      footer: "DynaShop"
    
    price_change:
      title_increase: "📈 Changement de Prix !"
      title_decrease: "📉 Changement de Prix !"
      description_increase: "Le prix de **%type%** de **%item%** dans **%shop%** a augmenté"
      description_decrease: "Le prix de **%type%** de **%item%** dans **%shop%** a diminué"
      field_old_price: "Ancien Prix"
      field_new_price: "Nouveau Prix"
      field_change: "Changement"
      footer: "DynaShop"
      type_buy: "achat"
      type_sell: "vente"
```

## DiscordSRV - Config.yml
```yaml
# Configuration de DiscordSRV pour DynaShop
BotToken: "YOUR_BOT_TOKEN_HERE"

# Canaux Discord
Channels:
  global: "123456789012345678"  # Canal général
  dynashop-commands: "123456789012345678"  # Canal pour les commandes DynaShop
  dynashop-announcements: "987654321098765432"  # Canal pour les annonces

# Configuration des commandes
DiscordChatChannelMinecraftToDiscord: true
DiscordChatChannelDiscordToMinecraft: true

# Permissions
DiscordChatChannelRolesRequiredToUseMinecraftChatInDiscord: []
DiscordChatChannelBroadcastDiscordMessagesToMinecraft: true

# Gestion des erreurs
DiscordChatChannelTranslateMentions: true
DiscordChatChannelEmojiBehavior: name
```

## Exemples d'usage avancés

### Commandes avec alias
```yaml
# Dans votre plugin, vous pouvez ajouter des alias :
discord:
  aliases:
    "!ds": "!dynashop"
    "!shop": "!dynashop"
    "!prix": "!dynashop prix"
    "!stock": "!dynashop stock"
    "!recherche": "!dynashop search"
```

### Configuration des rôles
```yaml
discord:
  roles:
    admin:
      - "Admin"
      - "Moderator"
      - "Staff"
    user:
      - "Player"
      - "Member"
      - "@everyone"
    vip:
      - "VIP"
      - "Premium"
      - "Donator"
```

### Messages personnalisés par serveur
```yaml
discord:
  server-specific:
    "123456789012345678":  # ID du serveur
      commands:
        help:
          title: "🏪 Serveur Aventure - Shops"
          description: "Commandes spéciales pour notre serveur !"
```

Cette configuration vous permet de personnaliser entièrement l'expérience Discord de votre serveur DynaShop !
