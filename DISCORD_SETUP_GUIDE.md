# 🚀 Installation et Configuration du Bot Discord DynaShop

## 📋 Prérequis

- ✅ Plugin DynaShop installé et configuré
- ✅ Plugin ShopGUIPlus installé et configuré
- ✅ Plugin DiscordSRV installé et configuré
- ✅ Un bot Discord créé et configuré
- ✅ Java 17+ pour la compatibilité

## 🔧 Installation Étape par Étape

### Étape 1 : Configuration du Bot Discord

1. **Créer un bot Discord** (si pas déjà fait)
   - Allez sur https://discord.com/developers/applications
   - Créez une nouvelle application
   - Allez dans l'onglet "Bot"
   - Créez un bot et copiez le token

2. **Configurer les permissions du bot**
   ```
   Permissions requises :
   - Send Messages (Envoyer des messages)
   - Send Messages in Threads (Envoyer dans les fils)
   - Embed Links (Intégrer des liens)
   - Attach Files (Joindre des fichiers)
   - Read Message History (Lire l'historique)
   - Use External Emojis (Utiliser des emojis externes)
   - Add Reactions (Ajouter des réactions)
   ```

3. **Inviter le bot sur votre serveur**
   - Générez un lien d'invitation avec les permissions appropriées
   - Ajoutez le bot à votre serveur Discord

### Étape 2 : Configuration DiscordSRV

1. **Configurer DiscordSRV** (`plugins/DiscordSRV/config.yml`)
   ```yaml
   BotToken: "VOTRE_TOKEN_BOT_ICI"
   
   Channels:
     global: "ID_CANAL_GENERAL"
     dynashop-commands: "ID_CANAL_COMMANDES"
     dynashop-announcements: "ID_CANAL_ANNONCES"
   
   DiscordChatChannelMinecraftToDiscord: true
   DiscordChatChannelDiscordToMinecraft: true
   ```

2. **Redémarrer le serveur** pour appliquer les changements

### Étape 3 : Configuration DynaShop

1. **Configurer l'intégration Discord** (`plugins/DynaShop/config.yml`)
   ```yaml
   discord:
     enabled: true
     channels:
       commands: "ID_CANAL_COMMANDES"
       announcements: "ID_CANAL_ANNONCES"
     low-stock-threshold: 10
   ```

2. **Personnaliser les messages** (`plugins/DynaShop/lang.yml`)
   - Copiez la configuration complète depuis `DISCORD_CONFIG_EXAMPLE.md`
   - Adaptez les messages à votre serveur

### Étape 4 : Test et Validation

1. **Test des commandes de base**
   ```
   !dynashop help
   !dynashop shops
   !dynashop prix <shop> <item>
   ```

2. **Test des commandes avancées**
   ```
   !dynashop search diamond
   !dynashop compare apple
   !dynashop top expensive
   !dynashop stats
   ```

3. **Test des permissions admin**
   ```
   !dynashop reload
   !dynashop admin restock <shop> <item>
   ```

## 🎯 Configuration Avancée

### Rôles et Permissions

1. **Créer les rôles Discord**
   - `DynaShop Admin` : Accès à toutes les commandes
   - `DynaShop User` : Accès aux commandes de consultation
   - `DynaShop VIP` : Accès étendu (si souhaité)

2. **Configurer les permissions**
   ```yaml
   discord:
     permissions:
       admin-roles:
         - "DynaShop Admin"
         - "Admin"
         - "Moderator"
       user-roles:
         - "DynaShop User"
         - "Member"
         - "@everyone"
   ```

### Canaux Spécialisés

1. **Canal des commandes**
   - Créez un canal `#dynashop-commands`
   - Configurez les permissions pour limiter l'accès si nécessaire

2. **Canal des annonces**
   - Créez un canal `#dynashop-announcements`
   - Configurez pour que seul le bot puisse y écrire

### Personnalisation des Messages

1. **Thème de couleurs**
   ```yaml
   discord:
     embeds:
       success-color: "#2ecc71"  # Vert
       error-color: "#e74c3c"    # Rouge
       warning-color: "#f39c12"  # Orange
       info-color: "#3498db"     # Bleu
   ```

2. **Emojis personnalisés**
   ```yaml
   discord:
     emojis:
       success: ":white_check_mark:"
       error: ":x:"
       warning: ":warning:"
       loading: ":hourglass:"
       shop: ":convenience_store:"
       item: ":package:"
       money: ":moneybag:"
   ```

## 🔍 Dépannage

### Problèmes Courants

1. **Le bot ne répond pas**
   - Vérifiez que DiscordSRV est connecté
   - Vérifiez les permissions du bot
   - Vérifiez les logs du serveur

2. **Commandes non reconnues**
   - Vérifiez la configuration du canal
   - Vérifiez que l'intégration Discord est activée
   - Redémarrez le serveur

3. **Erreurs de permissions**
   - Vérifiez les rôles Discord
   - Vérifiez la configuration des permissions
   - Vérifiez que l'utilisateur a les bons rôles

### Logs et Debugging

1. **Activer les logs détaillés**
   ```yaml
   discord:
     debug: true
     log-level: "DEBUG"
   ```

2. **Vérifier les logs**
   ```
   [DynaShop] Discord command received: !dynashop help
   [DynaShop] User has permission: true
   [DynaShop] Command executed successfully
   ```

## 📊 Monitoring et Maintenance

### Surveillance

1. **Monitorer les performances**
   - Vérifiez les temps de réponse
   - Surveillez l'utilisation mémoire
   - Vérifiez les logs d'erreur

2. **Statistiques d'utilisation**
   - Commandes les plus utilisées
   - Utilisateurs les plus actifs
   - Erreurs fréquentes

### Maintenance

1. **Mises à jour régulières**
   - Sauvegardez la configuration
   - Mettez à jour les plugins
   - Testez les nouvelles fonctionnalités

2. **Nettoyage des données**
   - Purgez les anciens logs
   - Nettoyez les caches
   - Optimisez les performances

## 🎨 Personnalisation Avancée

### Hooks et Extensions

1. **Hooks pour d'autres plugins**
   ```java
   // Exemple d'intégration avec PlaceholderAPI
   public class DynaShopDiscordHook {
       @EventHandler
       public void onPriceChange(PriceChangeEvent event) {
           discordManager.announcePriceChange(
               event.getShop(), 
               event.getItem(), 
               event.getOldPrice(), 
               event.getNewPrice()
           );
       }
   }
   ```

2. **Commandes personnalisées**
   ```yaml
   discord:
     custom-commands:
       "!myshop":
         description: "Affiche mes shops personnels"
         permission: "dynashop.myshop"
         response: "Vos shops personnels : %player_shops%"
   ```

### Interface Web

1. **Dashboard Discord**
   - Créez un dashboard web pour gérer les configurations
   - Intégrez les statistiques en temps réel
   - Permettez la gestion des permissions

2. **API REST**
   ```java
   @RestController
   public class DynaShopDiscordAPI {
       @GetMapping("/api/discord/stats")
       public Map<String, Object> getDiscordStats() {
           return discordManager.getStats();
       }
   }
   ```

## 🌟 Conseils et Bonnes Pratiques

### Performance

1. **Optimisation des requêtes**
   - Utilisez des caches intelligents
   - Limitez les requêtes simultanées
   - Implémentez une pagination efficace

2. **Gestion de la charge**
   - Limitez le nombre de commandes par utilisateur
   - Implémentez des cooldowns
   - Utilisez des threads séparés

### Sécurité

1. **Protection contre le spam**
   - Limitez les commandes par minute
   - Implémentez des anti-flood
   - Surveillez les tentatives d'abus

2. **Validation des données**
   - Validez toutes les entrées utilisateur
   - Échappez les caractères spéciaux
   - Vérifiez les permissions à chaque action

### Expérience Utilisateur

1. **Messages clairs**
   - Utilisez des messages d'erreur explicites
   - Proposez des suggestions
   - Documentez les commandes

2. **Feedback visuel**
   - Utilisez des emojis appropriés
   - Colorez les embeds selon le contexte
   - Ajoutez des indicateurs de progression

## 📞 Support et Communauté

### Ressources

- 📚 Documentation officielle DynaShop
- 🔗 Wiki DiscordSRV
- 💬 Forum de support Spigot
- 🐛 Rapport de bugs GitHub

### Contribution

- 🔧 Contribuez au code source
- 📝 Améliorez la documentation
- 🌐 Traduisez les messages
- 💡 Proposez des nouvelles fonctionnalités

---

✅ **Félicitations !** Votre bot Discord DynaShop est maintenant configuré et prêt à l'emploi !
