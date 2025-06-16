// Variables globales
let priceChart = null;
let volumeChart = null;
let candlestickChart = null;
let currentShop = null;
let currentItem = null;
let currentPeriod = '1m';
let currentChartData = null;
let currentStats = null;
let currentShopType = null;
let currentTheme = localStorage.getItem('theme') || 'light';
let autoRefreshInterval = localStorage.getItem('refreshInterval') || 'disabled';
let refreshTimerId = null;
let translations = {};
let currentLanguage = 'fr';

// // Initialisation de la page
// document.addEventListener('DOMContentLoaded', function() {
//     // Mettre à jour l'heure
//     updateCurrentTime();
//     // Mettre à jour toutes les 60 secondes
//     // setInterval(updateCurrentTime, 60000); // Mettre à jour toutes les 60 secondes
//     setInterval(updateCurrentTime, 1000); // Mettre à jour toutes les secondes

//     // Charger les shops
//     loadShops();
    
//     // Initialiser le thème
//     initTheme();
    
//     // Configurer les événements
//     document.getElementById('language-select').addEventListener('change', async (e) => {
//         currentLanguage = e.target.value;
//         await loadTranslations(currentLanguage);
//         translateUI();
        
//         // Enregistrer la préférence de langue
//         localStorage.setItem('preferredLanguage', currentLanguage);
//     });
//     document.getElementById('shop-select').addEventListener('change', onShopChange);
//     document.getElementById('item-select').addEventListener('change', onItemChange);
//     document.getElementById('period-select').addEventListener('change', onPeriodChange);
//     document.getElementById('theme-switch').addEventListener('click', toggleTheme);
//     window.addEventListener('beforeunload', () => {stopAutoRefresh();});

//     // Charger les paramètres de l'URL si présents
//     loadParamsFromUrl();

//     // Configurer l'actualisation automatique
//     const refreshSelect = document.getElementById('refresh-interval');
//     if (refreshSelect) {
//         refreshSelect.value = autoRefreshInterval;
//         refreshSelect.addEventListener('change', (e) => {
//             changeRefreshInterval(e.target.value);
//         });
//     }

//     // Démarrer l'actualisation automatique
//     startAutoRefresh();
// });

// Initialisation de la page
document.addEventListener('DOMContentLoaded', function() {
    // Remplacer tout ce bloc par un appel à initApp
    initApp();
});

// Fonction principale pour initialiser l'application
async function initApp() {
    // Détecter la langue du navigateur
    const savedLanguage = localStorage.getItem('preferredLanguage');
    if (savedLanguage) {
        currentLanguage = savedLanguage;
    } else {
        currentLanguage = detectBrowserLanguage();
    }
    
    // Charger les traductions
    await loadTranslations(currentLanguage);
    
    // Traduire l'interface
    translateUI();
    
    // Continuer avec l'initialisation normale
    initTheme();
    updateCurrentTime();
    loadShops();
    setupEventListeners();
    // Initialiser l'analyse de marché (NOUVEAU: après le chargement des traductions)
    if (typeof MarketAnalytics === 'function') {
        window.marketAnalytics = new MarketAnalytics();
    }
    startAutoRefresh();
    
    // Charger les paramètres de l'URL si présents
    loadParamsFromUrl();
    
    // Mettre à jour l'heure toutes les secondes
    setInterval(updateCurrentTime, 1000);
}

// Ajoutez ce code pour gérer le sélecteur personnalisé
document.addEventListener('DOMContentLoaded', function() {
    const customSelect = document.querySelector('.custom-select-container');
    const selected = customSelect.querySelector('.custom-select-selected');
    const dropdown = customSelect.querySelector('.custom-select-dropdown');
    const realSelect = document.getElementById('language-select');
    
    // Afficher/masquer la liste déroulante
    selected.addEventListener('click', function() {
        dropdown.style.display = dropdown.style.display === 'block' ? 'none' : 'block';
    });
    
    // Masquer la liste lors d'un clic en dehors
    document.addEventListener('click', function(e) {
        if (!customSelect.contains(e.target)) {
            dropdown.style.display = 'none';
        }
    });
    
    // Sélectionner une option
    dropdown.querySelectorAll('.custom-select-option').forEach(option => {
        option.addEventListener('click', function() {
            const value = this.dataset.value;
            const text = this.textContent;
            
            // Mettre à jour l'élément sélectionné
            selected.textContent = text;
            selected.className = 'custom-select-selected ' + value;
            
            // Mettre à jour le vrai select
            realSelect.value = value;
            
            // Déclencher l'événement change
            realSelect.dispatchEvent(new Event('change'));
            
            // Fermer la liste
            dropdown.style.display = 'none';
        });
    });
    
    // Initialiser avec la valeur actuelle
    function updateCustomSelectFromReal() {
        const value = realSelect.value;
        const option = realSelect.options[realSelect.selectedIndex];
        selected.textContent = option.textContent;
        selected.className = 'custom-select-selected ' + value;
    }
    
    updateCustomSelectFromReal();
    realSelect.addEventListener('change', updateCustomSelectFromReal);
});

// Fonction pour configurer tous les écouteurs d'événements
function setupEventListeners() {
    // Configurer les événements
    document.getElementById('language-select').addEventListener('change', async (e) => {
        currentLanguage = e.target.value;
        e.target.setAttribute('data-current', currentLanguage);
        await loadTranslations(currentLanguage);
        translateUI();
        
        if (priceChart) {
            // Mettre à jour les titres des axes
            priceChart.options.scales.y.title.text = getTranslation("stats.price");
            if (priceChart.data.datasets.length > 0) {
                if (priceChart.data.datasets[0]) {
                    priceChart.data.datasets[0].label = getTranslation("stats.buyPrice");
                }
                if (priceChart.data.datasets[1]) {
                    priceChart.data.datasets[1].label = getTranslation("stats.sellPrice");
                }
            }
            priceChart.update();
        }
        
        if (volumeChart) {
            // Mettre à jour les titres des axes et légendes
            volumeChart.options.scales.y.title.text = getTranslation("stats.volume");
            if (volumeChart.data.datasets.length > 0) {
                volumeChart.data.datasets[0].label = getTranslation("stats.volume");
            }
            volumeChart.update();
        }
        
        // Mettre à jour les statistiques
        if (currentStats) {
            updateStats(currentChartData, currentStats);
        }
        
        // Mettre à jour les infos du shop
        if (currentShopType) {
            updateShopTypeDisplay(currentShopType);
        }
        
        // Enregistrer la préférence de langue
        localStorage.setItem('preferredLanguage', currentLanguage);

        // Déclencher un événement personnalisé pour le changement de langue
        document.dispatchEvent(new CustomEvent('language-changed'));
    });
    
    document.getElementById('shop-select').addEventListener('change', onShopChange);
    document.getElementById('item-select').addEventListener('change', onItemChange);
    document.getElementById('period-select').addEventListener('change', onPeriodChange);
    document.getElementById('theme-switch').addEventListener('click', toggleTheme);
    window.addEventListener('beforeunload', () => {stopAutoRefresh();});

    // window.marketAnalytics = new MarketAnalytics();
    
    // // Ajouter un écouteur d'événements pour le bouton d'analyse
    // document.getElementById('analyze-trends-btn').addEventListener('click', function() {
    //     const shopId = document.getElementById('shop-select').value;
    //     const itemId = document.getElementById('item-select').value;
    //     const period = document.getElementById('period-select').value || '1m';
        
    //     window.marketAnalytics.displayMarketAnalysis(shopId, itemId, period);
    // });

    // S'assurer que MarketAnalytics est initialisé
    if (typeof MarketAnalytics === 'function' && !window.marketAnalytics) {
        window.marketAnalytics = new MarketAnalytics();
    }
    
    // Ajouter un écouteur d'événements pour le bouton d'analyse
    const analyzeBtn = document.getElementById('analyze-trends-btn');
    if (analyzeBtn) {
        analyzeBtn.addEventListener('click', function() {
            const container = document.getElementById('market-analysis-container');
            // Basculer l'affichage
            if (container.style.display === 'block') {
                container.style.display = 'none';
            } else {
                container.style.display = 'block';
                const shopId = document.getElementById('shop-select').value;
                const itemId = document.getElementById('item-select').value;
                // const period = document.getElementById('period-select').value || '1d';
                const trendPeriod = document.getElementById('trend-period-select').value;
                
                if (window.marketAnalytics) {
                    // window.marketAnalytics.displayMarketAnalysis(shopId, itemId, period);
                    window.marketAnalytics.displayMarketAnalysisWithDays(shopId, itemId, parseInt(trendPeriod));
                }
            }
        });
    }
    // Ajouter des écouteurs pour les changements d'item et shop qui mettront à jour l'analyse si visible
    document.getElementById('shop-select').addEventListener('change', refreshAnalysisIfVisible);
    document.getElementById('item-select').addEventListener('change', refreshAnalysisIfVisible);
    document.getElementById('trend-period-select').addEventListener('change', refreshAnalysisIfVisible);
    
    // Configurer l'actualisation automatique
    const refreshSelect = document.getElementById('refresh-interval');
    if (refreshSelect) {
        refreshSelect.value = autoRefreshInterval;
        refreshSelect.addEventListener('change', (e) => {
            changeRefreshInterval(e.target.value);
        });
    }
}

// Fonction pour rafraîchir l'analyse si le conteneur est visible
function refreshAnalysisIfVisible() {
    const container = document.getElementById('market-analysis-container');
    if (container && container.style.display === 'block' && window.marketAnalytics) {
        const shopId = document.getElementById('shop-select').value;
        const itemId = document.getElementById('item-select').value;
        const trendPeriod = document.getElementById('trend-period-select').value;
        
        if (shopId && itemId) {
            window.marketAnalytics.displayMarketAnalysisWithDays(shopId, itemId, parseInt(trendPeriod));
        }
    }
}

// Détecte la langue du navigateur
function detectBrowserLanguage() {
    const lang = navigator.language || navigator.userLanguage;
    const shortLang = lang.split('-')[0]; // 'fr-FR' -> 'fr'
    
    // Vérifier si nous avons une traduction pour cette langue, sinon utiliser l'anglais
    const supportedLanguages = ['en', 'fr', 'es', 'hi', 'zh', 'ar', 'pt'];
    return supportedLanguages.includes(shortLang) ? shortLang : 'en';
}

// Charge les traductions pour une langue spécifique
async function loadTranslations(lang) {
    try {
        const response = await fetch(`assets/locales/${lang}.json`);
        translations = await response.json();
    } catch (error) {
        console.error(`Failed to load translations for ${lang}, falling back to English`, error);
        // Fallback à l'anglais si la langue demandée n'est pas disponible
        if (lang !== 'en') {
            await loadTranslations('en');
        }
    }
}

// Fonction qui traduit l'interface utilisateur
function translateUI() {
    // Traduire tous les éléments avec un attribut data-i18n
    document.querySelectorAll('[data-i18n]').forEach(element => {
        const key = element.getAttribute('data-i18n');
        const text = getTranslation(key);
        if (text) element.textContent = text;
    });
    
    // Traduire les options de sélection
    translateSelectOptions();
    
    // Mettre à jour le sélecteur de langue
    const langSelect = document.getElementById('language-select');
    if (langSelect) {
        langSelect.value = currentLanguage;
        langSelect.setAttribute('data-current', currentLanguage);
    }
    
    // Gérer les langues RTL comme l'arabe
    if (currentLanguage === 'ar') {
        document.documentElement.setAttribute('dir', 'rtl');
        document.body.classList.add('rtl');
    } else {
        document.documentElement.setAttribute('dir', 'ltr');
        document.body.classList.remove('rtl');
    }
}

// Fonction qui traduit les options de sélection
function translateSelectOptions() {
    // Période
    const periodSelect = document.getElementById('period-select');
    if (periodSelect) {
        Array.from(periodSelect.options).forEach(option => {
            const value = option.value;
            const text = getTranslation(`periods.${value}`);
            if (text) option.textContent = text;
        });
    }
    
    // Actualisation
    const refreshSelect = document.getElementById('refresh-interval');
    if (refreshSelect) {
        Array.from(refreshSelect.options).forEach(option => {
            const value = option.value;
            if (value === 'disabled') {
                option.textContent = getTranslation('refresh.disabled');
            } else {
                option.textContent = getTranslation('refresh.minutes').replace('{0}', value);
            }
        });
    }
}

// Fonction pour obtenir une traduction par sa clé
function getTranslation(key) {
    // Gestion des clés imbriquées: 'charts.priceEvolution' -> translations.charts.priceEvolution
    return key.split('.').reduce((obj, i) => obj && obj[i], translations) || key;
}

// Remplacer window.onload par notre fonction initApp
window.onload = initApp;

// Mise à jour de l'heure
function updateCurrentTime() {
    const now = new Date();
    document.getElementById('current-time').textContent = now.toLocaleString();
}

// Initialisation du thème
function initTheme() {
    // Appliquer le thème sauvegardé
    if (currentTheme === 'dark') {
        document.documentElement.setAttribute('data-theme', 'dark');
        document.getElementById('theme-switch').innerHTML = `<i class="fas fa-sun"></i><span>${getTranslation("theme.light")}</span>`;
    } else {
        document.documentElement.setAttribute('data-theme', 'light');
        document.getElementById('theme-switch').innerHTML = `<i class="fas fa-moon"></i><span>${getTranslation("theme.dark")}</span>`;
    }
    
    // Mettre à jour les graphiques avec le thème actuel
    updateChartsTheme();
}

// Changement de thème
function toggleTheme() {
    if (currentTheme === 'light') {
        currentTheme = 'dark';
        document.documentElement.setAttribute('data-theme', 'dark');
        document.getElementById('theme-switch').innerHTML = `<i class="fas fa-sun"></i><span>${getTranslation("theme.light")}</span>`;
    } else {
        currentTheme = 'light';
        document.documentElement.setAttribute('data-theme', 'light');
        document.getElementById('theme-switch').innerHTML = `<i class="fas fa-moon"></i><span>${getTranslation("theme.dark")}</span>`;
    }
    
    // Sauvegarder la préférence
    localStorage.setItem('theme', currentTheme);
    
    // Mettre à jour les graphiques
    updateChartsTheme();
    
    // Déclencher un événement personnalisé pour notifier les autres composants
    document.dispatchEvent(new CustomEvent('theme-changed'));
}

// Fonction pour indiquer visuellement qu'une actualisation est en cours
function toggleRefreshingIndicator(active) {
    const refreshSelect = document.getElementById('refresh-interval');
    
    if (active) {
        refreshSelect.classList.add('active');
        document.querySelector('header .select-group').classList.add('refreshing');
    } else {
        refreshSelect.classList.remove('active');
        document.querySelector('header .select-group').classList.remove('refreshing');
    }
}

// Modifier la fonction startAutoRefresh pour ajouter l'indicateur
function startAutoRefresh() {
    // Arrêter l'intervalle existant si présent
    stopAutoRefresh();
    
    // Si désactivé, ne rien faire de plus
    if (autoRefreshInterval === 'disabled') {
        return;
    }
    
    // Ajouter l'indicateur visuel
    toggleRefreshingIndicator(true);
    
    // Convertir l'intervalle en millisecondes
    const intervalMs = parseInt(autoRefreshInterval) * 60 * 1000;
    
    // Créer un nouvel intervalle
    refreshTimerId = setInterval(() => {
        // Ne rafraîchir que si un shop et un item sont sélectionnés
        if (currentShop && currentItem) {
            console.log(`Actualisation automatique (${autoRefreshInterval} min)`);
            loadPriceData(currentShop, currentItem, currentPeriod);
        }
    }, intervalMs);
}

// Modifier la fonction stopAutoRefresh également
function stopAutoRefresh() {
    if (refreshTimerId !== null) {
        clearInterval(refreshTimerId);
        refreshTimerId = null;
        toggleRefreshingIndicator(false);
    }
}

// Mise à jour des thèmes des graphiques
function updateChartsTheme() {
    // Définir les couleurs selon le thème
    const gridColor = getComputedStyle(document.documentElement).getPropertyValue('--chart-grid-color').trim();
    const textColor = getComputedStyle(document.documentElement).getPropertyValue('--text-color').trim();
    
    // Mettre à jour les options globales de Chart.js
    Chart.defaults.color = textColor;
    Chart.defaults.scale.grid.color = gridColor;
    
    // Au lieu de redessiner les graphiques, mettre à jour leurs options
    if (priceChart) {
        // Mettre à jour les couleurs de la grille
        priceChart.options.scales.x.grid.color = gridColor;
        priceChart.options.scales.y.grid.color = gridColor;
        
        // Mettre à jour les couleurs du texte
        priceChart.options.scales.x.ticks.color = textColor;
        priceChart.options.scales.y.ticks.color = textColor;
        priceChart.options.scales.x.title.color = textColor;
        priceChart.options.scales.y.title.color = textColor;
        
        // Rafraîchir le graphique sans le redessiner complètement
        priceChart.update();
    }
    
    if (volumeChart) {
        // Même chose pour volume
        volumeChart.options.scales.x.grid.color = gridColor;
        volumeChart.options.scales.y.grid.color = gridColor;
        volumeChart.options.scales.x.ticks.color = textColor;
        volumeChart.options.scales.y.ticks.color = textColor;
        volumeChart.options.scales.x.title.color = textColor;
        volumeChart.options.scales.y.title.color = textColor;
        volumeChart.update();
    }
    
    if (candlestickChart) {
        // Et pour le graphique en chandelier
        candlestickChart.options.scales.x.grid.color = gridColor;
        candlestickChart.options.scales['y-acheter'].grid.color = gridColor;
        candlestickChart.options.scales['y-vendre'].grid.color = gridColor;
        
        candlestickChart.options.scales.x.ticks.color = textColor;
        candlestickChart.options.scales['y-acheter'].ticks.color = textColor;
        candlestickChart.options.scales['y-vendre'].ticks.color = textColor;
        
        candlestickChart.options.scales.x.title.color = textColor;
        candlestickChart.options.scales['y-acheter'].title.color = textColor;
        candlestickChart.options.scales['y-vendre'].title.color = textColor;
        
        // Mettre à jour les couleurs de légende
        if (candlestickChart.options.plugins && candlestickChart.options.plugins.legend) {
            candlestickChart.options.plugins.legend.labels.color = textColor;
        }
        
        candlestickChart.update();
    }
}

// Fonction pour démarrer l'actualisation automatique
function startAutoRefresh() {
    // Arrêter l'intervalle existant si présent
    stopAutoRefresh();
    
    // Si désactivé, ne rien faire de plus
    if (autoRefreshInterval === 'disabled') {
        return;
    }
    
    // Convertir l'intervalle en millisecondes
    const intervalMs = parseInt(autoRefreshInterval) * 60 * 1000;
    
    // Créer un nouvel intervalle
    refreshTimerId = setInterval(() => {
        // Ne rafraîchir que si un shop et un item sont sélectionnés
        if (currentShop && currentItem) {
            console.log(`Actualisation automatique (${autoRefreshInterval} min)`);
            loadPriceData(currentShop, currentItem, currentPeriod);
        }
    }, intervalMs);
}

// Fonction pour arrêter l'actualisation automatique
function stopAutoRefresh() {
    if (refreshTimerId !== null) {
        clearInterval(refreshTimerId);
        refreshTimerId = null;
    }
}

// Fonction pour changer l'intervalle d'actualisation
function changeRefreshInterval(interval) {
    autoRefreshInterval = interval;
    localStorage.setItem('refreshInterval', interval);
    
    // Mettre à jour l'affichage du bouton/sélecteur
    updateRefreshDisplay();
    
    // Redémarrer l'actualisation avec le nouvel intervalle
    startAutoRefresh();
}

// Fonction pour mettre à jour l'affichage du sélecteur
function updateRefreshDisplay() {
    const refreshSelect = document.getElementById('refresh-interval');
    if (refreshSelect) {
        refreshSelect.value = autoRefreshInterval;
    }
}

// async function loadShops() {
//     fetch('/api/shops')
//         .then(response => response.json())
//         .then(shops => {
//             const shopSelect = document.getElementById('shop-select');
            
//             // Vider la liste actuelle (sauf la première option)
//             while (shopSelect.options.length > 1) {
//                 shopSelect.remove(1);
//             }
            
//             // Trier les boutiques par nom
//             shops.sort((a, b) => a.name.localeCompare(b.name));
            
//             // Ajouter les options de boutiques
//             shops.forEach(shop => {
//                 const option = document.createElement('option');
//                 option.value = shop.id;  // L'ID comme valeur
//                 option.textContent = shop.name;  // Le nom comme texte affiché
//                 shopSelect.appendChild(option);
//             });
            
//             // Réactiver le select
//             shopSelect.disabled = false;
            
//             // Si un paramètre shop est présent dans l'URL, sélectionner cette boutique
//             const urlParams = new URLSearchParams(window.location.search);
//             const shopParam = urlParams.get('shop');
//             if (shopParam) {
//                 shopSelect.value = shopParam;
//                 shopSelect.dispatchEvent(new Event('change'));
//             }
//         })
//         .catch(error => console.error('Erreur lors du chargement des boutiques:', error));
// }

// Chargement des items d'un shop
// async function loadItems(shopId) {
//     try {
//         const response = await fetch(`/api/items?shop=${shopId}`);
//         const items = await response.json();
        
//         const itemSelect = document.getElementById('item-select');
//         itemSelect.innerHTML = '<option value="">Choisir un item</option>';
//         itemSelect.disabled = false;
        
//         items.forEach(item => {
//             const option = document.createElement('option');
//             option.value = item.id;
//             option.textContent = item.name || item.id;
//             itemSelect.appendChild(option);
//         });
//     } catch (error) {
//         console.error("Erreur lors du chargement des items:", error);
//     }
// }

async function loadShops() {
    fetch('/api/shops')
        .then(response => response.json())
        .then(shops => {
            const shopSelect = document.getElementById('shop-select');
            
            // Vider la liste actuelle
            // shopSelect.innerHTML = `<option value="">${getTranslation("selectors.chooseShop")}</option>`;
            shopSelect.innerHTML = `<option value=""></option>`;

            // Trier les boutiques par nom
            shops.sort((a, b) => a.name.localeCompare(b.name));
            
            // Ajouter les options de boutiques
            shops.forEach(shop => {
                const option = document.createElement('option');
                option.value = shop.id;
                option.textContent = shop.name;
                shopSelect.appendChild(option);
            });
            
            // Ajouter un champ de recherche pour le select
            // enhanceSelectWithSearch('shop-select', "Rechercher une boutique...");
            enhanceSelectWithSearch('shop-select', `${getTranslation("selectors.chooseShop")}`);
            
            // Réactiver le select
            shopSelect.disabled = false;
            
            // Si un paramètre shop est présent dans l'URL, sélectionner cette boutique
            const urlParams = new URLSearchParams(window.location.search);
            const shopParam = urlParams.get('shop');
            if (shopParam) {
                shopSelect.value = shopParam;
                shopSelect.dispatchEvent(new Event('change'));
            }
        })
        .catch(error => console.error('Erreur lors du chargement des boutiques:', error));
}

async function loadItems(shopId) {
    try {
        // Supprimer l'ancien sélecteur amélioré s'il existe
        const oldContainer = document.querySelector('.select-group .searchable-select-container');
        if (oldContainer && oldContainer.parentElement.querySelector('#item-select')) {
            oldContainer.remove();
            document.getElementById('item-select').style.display = ''; // Réafficher le select original temporairement
        }
        
        const response = await fetch(`/api/items?shop=${shopId}`);
        const items = await response.json();
        
        const itemSelect = document.getElementById('item-select');
        itemSelect.innerHTML = '<option value=""></option>';
        itemSelect.disabled = false;
        
        items.forEach(item => {
            const option = document.createElement('option');
            option.value = item.id;
            option.textContent = item.name || item.id;
            itemSelect.appendChild(option);
        });
        
        // Ajouter le champ de recherche pour ce select
        enhanceSelectWithSearch('item-select',`${getTranslation("selectors.chooseItem")}`);

        // Si un paramètre item est présent dans l'URL et correspond à ce shop
        const urlParams = new URLSearchParams(window.location.search);
        const itemParam = urlParams.get('item');
        if (itemParam) {
            itemSelect.value = itemParam;
            itemSelect.dispatchEvent(new Event('change'));
        }
    } catch (error) {
        console.error("Erreur lors du chargement des items:", error);
    }
}

// Fonction pour ajouter une recherche à un select sans jQuery/Select2
function enhanceSelectWithSearch(selectId, placeholder) {
    const select = document.getElementById(selectId);
    if (!select) return;
    
    // Vérifier si un sélecteur amélioré existe déjà pour ce select
    const parent = select.parentNode;
    const existingContainer = parent.querySelector('.searchable-select-container');
    if (existingContainer) {
        existingContainer.remove();
    }
    
    // Cacher le select original
    select.style.display = 'none';
    
    // Créer le conteneur de notre select amélioré
    const container = document.createElement('div');
    container.className = 'searchable-select-container';
    select.parentNode.insertBefore(container, select);
    
    // Créer le champ de recherche
    const input = document.createElement('input');
    input.type = 'text';
    input.placeholder = placeholder;
    input.className = 'searchable-select-input';
    container.appendChild(input);

    input.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            const firstOption = dropdown.querySelector('.searchable-select-option.selected') || dropdown.querySelector('.searchable-select-option');
            if (firstOption) {
                firstOption.click();
            }
        } else if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
            e.preventDefault();
            const options = Array.from(dropdown.querySelectorAll('.searchable-select-option'));
            if (options.length === 0) return;
            let selectedIndex = options.findIndex(opt => opt.classList.contains('selected'));
            if (selectedIndex === -1) selectedIndex = 0;
            else {
                options[selectedIndex].classList.remove('selected');
                if (e.key === 'ArrowDown') selectedIndex = (selectedIndex + 1) % options.length;
                else if (e.key === 'ArrowUp') selectedIndex = (selectedIndex - 1 + options.length) % options.length;
            }
            options[selectedIndex].classList.add('selected');
            // Mettre à jour l'input avec le texte de l'option sélectionnée
            input.value = options[selectedIndex].textContent;
            // Scroll to the selected option if needed
            options[selectedIndex].scrollIntoView({ block: 'nearest' });
        }
    });
    
    // Créer la liste déroulante
    const dropdown = document.createElement('div');
    dropdown.className = 'searchable-select-dropdown';
    dropdown.style.display = 'none';
    container.appendChild(dropdown);
    
    // Fonction pour remplir la liste déroulante
    function populateDropdown(filter = '') {
        dropdown.innerHTML = '';
        const options = Array.from(select.options);
        
        options.forEach(option => {
            // Ignorer l'option vide
            if (!option.value) return;
            
            // Filtrer par texte de recherche
            if (filter && !option.textContent.toLowerCase().includes(filter.toLowerCase())) return;
            
            const item = document.createElement('div');
            item.className = 'searchable-select-option';
            item.textContent = option.textContent;
            item.dataset.value = option.value;
            
            item.addEventListener('click', () => {
                select.value = option.value;
                input.value = option.textContent;
                dropdown.style.display = 'none';
                
                // Déclencher l'événement change
                select.dispatchEvent(new Event('change'));
            });
            
            dropdown.appendChild(item);

            const firstOption = dropdown.querySelector('.searchable-select-option');
            if (firstOption) firstOption.classList.add('selected');
        });
    }
    
    // Événements pour montrer/cacher la liste
    input.addEventListener('focus', () => {
        populateDropdown();
        dropdown.style.display = 'block';
    });
    
    input.addEventListener('input', () => {
        populateDropdown(input.value);
        dropdown.style.display = 'block';
    });
    
    // Fermer quand on clique ailleurs
    document.addEventListener('click', (e) => {
        if (!container.contains(e.target)) {
            dropdown.style.display = 'none';
        }
    });
    
    // Mise à jour de l'input quand la valeur du select change
    const updateInputFromSelect = () => {
        const selectedOption = select.options[select.selectedIndex];
        if (selectedOption) {
            input.value = selectedOption.textContent;
        }
    };
    
    // Observer les changements de valeur du select
    select.addEventListener('change', updateInputFromSelect);
    
    // Initialiser avec la valeur actuelle
    updateInputFromSelect();
}

// Chargement des données de prix
async function loadPriceData(shopId, itemId, period = '1d') {
    try {

        // 1. Charger d'abord les métadonnées
        const statsResponse = await fetch(`/api/price-stats?shop=${encodeURIComponent(shopId)}&item=${encodeURIComponent(itemId)}`);
        const stats = await statsResponse.json();
        
        // Charger le type de DynaShop
        const typeResponse = await fetch(`/api/shop-type?shop=${encodeURIComponent(shopId)}&item=${encodeURIComponent(itemId)}`);
        const typeData = await typeResponse.json();
        
        // 2. Déterminer la granularité optimale en fonction du nombre de points
        let granularity = stats.recommendedGranularity || 'auto';
        let maxPoints = 2000; // Limiter à 2000 points maximum pour les performances
        
        // 3. Charger les données réelles avec les paramètres adaptés
        const dataResponse = await fetch(`/api/prices?shop=${encodeURIComponent(shopId)}&item=${encodeURIComponent(itemId)}` + `&period=${period}&granularity=${granularity}&maxPoints=${maxPoints}`);
        
        const chartData = await dataResponse.json();
        
        // Afficher le type de DynaShop
        displayShopType(typeData);
        
        // 4. Mettre à jour les graphiques
        updateCharts(chartData);

        // 5. Afficher les statistiques générales
        updateStats(chartData, stats); // Ordre corrigé des paramètres
    } catch (error) {
        console.error('Erreur lors du chargement des prix:', error);
    }
}

// Affichage du type de DynaShop
function displayShopType(typeData) {
    const shopTypeElement = document.getElementById('shop-type');
    
    // Fonction pour obtenir une description conviviale
    const getTypeDescription = (type) => {
        currentShopType = type; // Sauvegarder le type actuel pour une utilisation ultérieure
        switch(type) {
            case 'DYNAMIC': return `${getTranslation("stats.shopType.dynamic")}`;
            case 'STOCK': return `${getTranslation("stats.shopType.stock")}`;
            case 'STATIC_STOCK': return `${getTranslation("stats.shopType.static_stock")}`;
            case 'RECIPE': return `${getTranslation("stats.shopType.recipe")}`;
            case 'LINK': return `${getTranslation("stats.shopType.linked")}`;
            case 'NONE': return `${getTranslation("stats.shopType.static")}`;
            default: return type || `${getTranslation("stats.shopType.unknown")}`;
        }
    };
    // updateShopTypeDisplay(getTypeDescription(currentShopType)); // Mettre à jour l'affichage du type de shop

    // Déterminer si des liens sont présents
    const hasLinks = typeData.buy === 'LINK' || typeData.sell === 'LINK' || typeData.general === 'LINK';
    
    // Formatage avec couleurs selon le type
    let html = '';
    
    // Affichage différent si les types d'achat et vente sont différents
    if (typeData.buy !== typeData.sell) {
        html = `${getTranslation("stats.buy")}: <span class="type-buy">${getTypeDescription(typeData.buy)}</span>`;

        // Ajouter le type réel si différent et si c'est un LINK
        if (typeData.buy === 'LINK' && typeData.realBuy && typeData.realBuy !== 'LINK') {
            html += ` <small>(${getTypeDescription(typeData.realBuy)})</small>`;
        }

        html += `<br>${getTranslation("stats.sell")}: <span class="type-sell">${getTypeDescription(typeData.sell)}</span>`;

        // Ajouter le type réel si différent et si c'est un LINK
        if (typeData.sell === 'LINK' && typeData.realSell && typeData.realSell !== 'LINK') {
            html += ` <small>(${getTypeDescription(typeData.realSell)})</small>`;
        }
    } else {
        html = getTypeDescription(typeData.general);
        
        // Ajouter le type réel si c'est un LINK
        if (typeData.general === 'LINK' && typeData.realBuy && typeData.realSell) {
            if (typeData.realBuy === typeData.realSell) {
                html += ` <small>(${getTypeDescription(typeData.realBuy)})</small>`;
            } else {
                html += `<br><small>(${getTranslation("stats.buy")}: ${getTypeDescription(typeData.realBuy)}, 
                       ${getTranslation("stats.sell")}: ${getTypeDescription(typeData.realSell)})</small>`;
            }
        }
    }
    
    // Enlever l'indication générique pour les LINK puisque nous affichons maintenant le type réel
    shopTypeElement.innerHTML = html;
}

// Nouvelle fonction pour mettre à jour l'affichage du type de shop
function updateShopTypeDisplay(type) {
    // Traduire le type de shop
    let displayType = getTranslation(`stats.shopType.${type.toLowerCase()}`) || type;
    document.getElementById('shop-type').textContent = displayType;
}

// Filtrage des données selon la période
function filterDataByPeriod(data, period) {
    const now = luxon.DateTime.now();
    let periodStart;
    
    switch(period) {
        case '1h':
            periodStart = now.minus({ hours: 1 });
            break;
        case '6h':
            periodStart = now.minus({ hours: 6 });
            break;
        case '12h':
            periodStart = now.minus({ hours: 12 });
            break;
        case '1d':
            periodStart = now.minus({ days: 1 });
            break;
        case '1w':
            periodStart = now.minus({ weeks: 1 });
            break;
        case '1m':
            periodStart = now.minus({ months: 1 });
            break;
        default:
            periodStart = now.minus({ days: 1 });
    }
    
    return data.filter(point => {
        const pointDate = luxon.DateTime.fromISO(point.timestamp);
        return pointDate >= periodStart;
    });
}

// Mise à jour des graphiques
// function updateCharts(data) {
//     // Vérifier si on a des données
//     if (!data || data.length === 0) {
//         console.warn('Aucune donnée disponible pour les graphiques');
//         return;
//     }
    
//     // Convertir les timestamps en objets Date pour Chart.js
//     const processedData = data.map(point => ({
//         ...point,
//         // Assurez-vous que timestamp est correctement parsé
//         x: luxon.DateTime.fromISO(point.timestamp)
//     }));
    
//     // Mettre à jour les graphiques
//     updatePriceChart(processedData);
//     updateVolumeChart(processedData);
//     updateCandlestickChart(processedData);
// }
function updateCharts(data) {
    // Vérifier si on a des données
    if (!data || data.length === 0) {
        console.warn('Aucune donnée disponible pour les graphiques');
        return;
    }
    
    // Convertir les timestamps en objets Date pour Chart.js
    const processedData = data.map(point => ({
        ...point,
        // Assurez-vous que timestamp est correctement parsé
        x: luxon.DateTime.fromISO(point.timestamp)
    }));
    
    // Mettre à jour les graphiques en passant également les stats
    updatePriceChart(processedData);
    updateVolumeChart(processedData); // Transmettre les stats ici
    // updateCandlestickChart(processedData); // Transmettre les stats ici
}

// Mise à jour du graphique de prix
function updatePriceChart(data) {
    const ctx = document.getElementById('price-chart').getContext('2d');
    
    // // Extraire et formater les données
    // const chartData = data.map(point => ({
    //     x: luxon.DateTime.fromISO(point.timestamp),
    //     buy: point.closeBuy,
    //     sell: point.closeSell
    // }));
    // const chartData = aggregatedPriceData(data); // Utiliser la fonction d'agrégation
    const buyData = data
        .filter(point => point.closeBuy !== -1 && point.closeBuy !== undefined)
        .map(point => ({ x: luxon.DateTime.fromISO(point.timestamp), y: point.closeBuy }));
    const sellData = data
        .filter(point => point.closeSell !== -1 && point.closeSell !== undefined)
        .map(point => ({ x: luxon.DateTime.fromISO(point.timestamp), y: point.closeSell }));

    // Détruire le graphique existant s'il existe
    if (priceChart) {
        priceChart.destroy();
    }
    
    // Créer un nouveau graphique
    priceChart = new Chart(ctx, {
        type: 'line',
        data: {
            datasets: [
                // N'afficher la courbe que si on a des points valides
                ...(buyData.length > 0 ? [{
                    label: getTranslation("stats.buyPrice"),
                    data: buyData,
                    borderColor: 'rgba(244, 67, 54, 0.9)',
                    backgroundColor: 'rgba(244, 67, 54, 0.2)',
                    // borderColor: 'rgba(255, 99, 132, 1)',
                    // backgroundColor: 'rgba(255, 99, 132, 0.2)',
                    borderWidth: 2,
                    tension: 0.1
                }] : []),
                ...(sellData.length > 0 ? [{
                    label: getTranslation("stats.sellPrice"),
                    data: sellData,
                    borderColor: 'rgba(76, 175, 80, 0.9)',
                    backgroundColor: 'rgba(76, 175, 80, 0.2)',
                    // borderColor: 'rgba(54, 162, 235, 1)',
                    // backgroundColor: 'rgba(54, 162, 235, 0.2)',
                    borderWidth: 2,
                    tension: 0.1
                }] : [])
            ]
        },
        options: {
            responsive: true,
            interaction: {
                mode: 'index',
                intersect: false,
            },
            scales: {
                x: {
                    type: 'time',
                    time: {
                        unit: determineTimeUnit(data.length),
                        // unit: 'hour',
                        tooltipFormat: 'dd/MM HH:mm',
                        displayFormats: {
                            hour: 'HH:mm',
                            day: 'dd/MM'
                        }
                    }
                    // title: {
                    //     display: true,
                    //     text: 'Date/Heure'
                    // }
                    // type: 'category',
                    // title: {
                    //     display: true,
                    //     text: 'Date/Heure'
                    // },
                },
                y: {
                    title: {
                        display: true,
                        text: getTranslation("stats.price")
                    },
                    beginAtZero: false
                }
            }
        }
    });
}

// function normalizeVolume(volume) {
//     if (!volume || volume <= 0) return 0;
//     // Pour rendre les petites valeurs plus visibles
//     return Math.max(0.5, volume);
// }

// // Mise à jour du graphique de volume
// function updateVolumeChart(data) {
//     const ctx = document.getElementById('volume-chart').getContext('2d');
    
//     // Vérifier si nous avons des données de volume
//     const volumeArray = data.map(point => point.volume || 0);
//     const maxVolume = Math.max(...volumeArray);
//     const hasVolumeData = maxVolume > 0;
    
//     console.log('Données de volume:', volumeArray);
//     console.log('Volume maximum:', maxVolume);
    
//     // // Si aucun volume significatif, afficher un message
//     // if (!hasVolumeData) {
//     //     // Nettoyer le canvas existant
//     //     if (volumeChart) {
//     //         volumeChart.destroy();
//     //         volumeChart = null;
//     //     }
        
//     //     ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height);
//     //     ctx.font = '14px Arial';
//     //     ctx.fillStyle = getComputedStyle(document.documentElement).getPropertyValue('--text-color').trim();
//     //     ctx.textAlign = 'center';
//     //     ctx.fillText('Aucune donnée de volume disponible', ctx.canvas.width / 2, ctx.canvas.height / 2);
//     //     return;
//     // }
    
//     // Extraire et formater les données
//     const volumeData = data.map(point => ({
//         x: luxon.DateTime.fromISO(point.timestamp).toFormat('dd/MM HH:mm'),
//         y: point.volume
//         // y: normalizeVolume(point.volume)
//     }));
    
//     // Détruire le graphique existant s'il existe
//     if (volumeChart) {
//         volumeChart.destroy();
//     }
    
//     // Créer un nouveau graphique
//     volumeChart = new Chart(ctx, {
//         type: 'bar',
//         data: {
//             // labels: volumeData.map(item => item.x),
//             datasets: [
//                 {
//                     label: 'Volume',
//                     // data: volumeData,
//                     data: volumeData.map(item => ({ x: item.x, y: item.y })),
//                     backgroundColor: 'rgba(75, 192, 192, 0.6)',
//                     borderColor: 'rgba(75, 192, 192, 1)',
//                     borderWidth: 1
//                 }
//             ]
//         },
//         options: {
//             responsive: true,
//             scales: {
//                 x: {
//                     // Utiliser un axe catégoriel au lieu d'un axe temporel
//                     type: 'category',
//                     title: {
//                         display: true,
//                         text: 'Date/Heure'
//                     },
//                     ticks: {
//                         // Limiter le nombre d'étiquettes affichées
//                         maxTicksLimit: 10,
//                         autoSkip: true
//                     }
//                 },
//                 y: {
//                     title: {
//                         display: true,
//                         text: 'Volume'
//                     },
//                     beginAtZero: true
//                 }
//             }
//         }
//     });
// }

// Mise à jour du graphique de volume
function updateVolumeChart(data) {
    const ctx = document.getElementById('volume-chart').getContext('2d');
    
    // Si aucune donnée, sortir
    if (!data || data.length === 0) {
        return;
    }
    
    // // Vérifier si les données de stats contiennent des infos sur le volume total
    // const hasVolumeStats = stats && typeof stats.totalVolume !== 'undefined';
    // console.log('Données de volume dans stats:', hasVolumeStats ? stats.totalVolume : 'non disponible');
    
    // Regrouper les données par période pour rendre les barres visibles
    const aggregatedData = aggregateVolumeData(data);
    
    // // Vérifier s'il y a des données après agrégation
    // if (Object.keys(aggregatedData).length === 0) {
    //     ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height);
    //     ctx.font = '14px Arial';
    //     ctx.fillStyle = getComputedStyle(document.documentElement).getPropertyValue('--text-color').trim();
    //     ctx.textAlign = 'center';
    //     ctx.fillText('Aucune donnée de volume disponible', ctx.canvas.width / 2, ctx.canvas.height / 2);
    //     return;
    // }
    
    // // Vérifier s'il y a des données de volume significatives
    // const totalVolume = aggregatedData.values.reduce((sum, val) => sum + val, 0);
    // if (totalVolume === 0 && (!hasVolumeStats || stats.totalVolume === 0)) {
    //     ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height);
    //     ctx.font = '14px Arial';
    //     ctx.fillStyle = getComputedStyle(document.documentElement).getPropertyValue('--text-color').trim();
    //     ctx.textAlign = 'center';
    //     ctx.fillText('Aucune donnée de volume disponible', ctx.canvas.width / 2, ctx.canvas.height / 2);
    //     return;
    // }
    
    // Détruire le graphique existant s'il existe
    if (volumeChart) {
        volumeChart.destroy();
    }
    
    // Créer un nouveau graphique avec les données agrégées
    volumeChart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: aggregatedData.labels,
            datasets: [{
                label: getTranslation("stats.volume"),
                data: aggregatedData.values,
                backgroundColor: 'rgba(75, 192, 192, 0.6)',
                borderColor: 'rgba(75, 192, 192, 1)',
                borderWidth: 1
            }]
        },
        options: {
            responsive: true,
            scales: {
                x: {
                    // type: 'category',
                    // title: {
                    //     display: true,
                    //     text: 'Date/Heure'
                    // }
                    // ticks: {
                    //     maxTicksLimit: 10,
                    //     autoSkip: true
                    // }
                    // type: 'time',
                    time: {
                        unit: determineTimeUnit(aggregatedData.values.length),
                        tooltipFormat: 'dd/MM HH:mm',
                        displayFormats: {
                            hour: 'HH:mm',
                            day: 'dd/MM'
                        }
                    },
                    // title: {
                    //     display: true,
                    //     text: 'Date/Heure'
                    // }
                },
                y: {
                    title: {
                        display: true,
                        text: getTranslation("stats.volume")
                    },
                    beginAtZero: true
                }
            }
        }
    });
}

// function updateVolumeChart(data, stats) {
//     const ctx = document.getElementById('volume-chart').getContext('2d');
    
//     // Vérifier si nous avons des données
//     if (!data || data.length === 0) {
//         ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height);
//         ctx.font = '14px Arial';
//         ctx.fillStyle = getComputedStyle(document.documentElement).getPropertyValue('--text-color').trim();
//         ctx.textAlign = 'center';
//         ctx.fillText('Aucune donnée de volume disponible', ctx.canvas.width / 2, ctx.canvas.height / 2);
//         return;
//     }
    
//     // Extraire les données
//     const timestamps = data.map(point => point.timestamp);
//     // const volumes = data.map(point => point.volume);
// //     // Extraire et formater les données
// //     const volumeData = data.map(point => ({
// //         x: luxon.DateTime.fromISO(point.timestamp).toFormat('dd/MM HH:mm'),
// //         y: point.volume
// //         // y: normalizeVolume(point.volume)
// //     }));

//     // Vérifier si les données de volume contiennent des valeurs non nulles
//     const volumeArray = data.map(point => point.volume || 0);
//     const maxVolume = Math.max(...volumeArray);
//     const hasVolumeData = maxVolume > 0;
    
//     // Vérifier aussi les stats pour le volume total
//     const hasVolumeStats = stats && stats.totalVolume && stats.totalVolume > 0;
    
//     if (!hasVolumeData && !hasVolumeStats) {
//         ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height);
//         ctx.font = '14px Arial';
//         ctx.fillStyle = getComputedStyle(document.documentElement).getPropertyValue('--text-color').trim();
//         ctx.textAlign = 'center';
//         ctx.fillText('Aucune donnée de volume disponible', ctx.canvas.width / 2, ctx.canvas.height / 2);
//         return;
//     }

//     const aggregatedData = aggregateVolumeData(data);
    
//     // Détruire le graphique existant s'il existe
//     if (volumeChart) {
//         volumeChart.destroy();
//     }
    
//     // Créer un nouveau graphique
//     volumeChart = new Chart(ctx, {
//         type: 'bar',
//         data: {
//             labels: timestamps,
//             // labels: aggregatedData.labels,
//             datasets: [
//                 {
//                     label: 'Volume',
//                     // data: volumes,
//                     data: aggregatedData.values,
//                     backgroundColor: 'rgba(75, 192, 192, 0.6)',
//                     borderColor: 'rgba(75, 192, 192, 1)',
//                     borderWidth: 1
//                 }
//             ]
//         },
//         options: {
//             responsive: true,
//             scales: {
//                 x: {
//                     type: 'time',
//                     // time: {
//                     //     // unit: determineTimeUnit(data.length),
//                     //     tooltipFormat: 'dd/MM/yyyy HH:mm',
//                     //     // displayFormats: {
//                     //     //     hour: 'mm',
//                     //     //     day: 'dd/MM'
//                     //     // }
//                     //     // tooltipFormat: 'DD T'
//                     // },
//                     title: {
//                         display: true,
//                         text: 'Date/Heure'
//                     }
//                 },
//                 // x: {
//                 //     type: 'category', // Au lieu de 'time'
//                 //     title: {
//                 //         display: true,
//                 //         text: 'Date'
//                 //     }
//                 // },
//                 y: {
//                     title: {
//                         display: true,
//                         text: 'Volume'
//                     },
//                     beginAtZero: true
//                 }
//             }
//         }
//     });
// }

// Fonction pour agréger les données de volume par période
function aggregateVolumeData(data) {
    // Stocker à la fois la clé formatée et la date originale pour pouvoir trier correctement
    const aggregatedWithTime = [];
    const timeFormat = determineTimeFormat(currentPeriod);
    
    // Première étape : agréger les données par période
    const tempAggregated = {};
    const timestamps = {};
    
    data.forEach(point => {
        const timestamp = luxon.DateTime.fromISO(point.timestamp);
        const timeKey = timestamp.toFormat(timeFormat);
        
        // Stocker le timestamp le plus ancien pour chaque clé
        if (!timestamps[timeKey] || timestamp < timestamps[timeKey]) {
            timestamps[timeKey] = timestamp;
        }
        
        // Ajouter le volume au total pour cette période
        if (!tempAggregated[timeKey]) {
            tempAggregated[timeKey] = 0;
        }
        tempAggregated[timeKey] += (point.volume || 0);
    });
    
    // Deuxième étape : créer un tableau avec les données et les timestamps
    for (const timeKey in tempAggregated) {
        aggregatedWithTime.push({
            label: timeKey,
            value: tempAggregated[timeKey],
            timestamp: timestamps[timeKey]
        });
    }
    
    // Trier par ordre chronologique
    aggregatedWithTime.sort((a, b) => a.timestamp - b.timestamp);
    
    // Transformer en deux tableaux pour Chart.js
    const labels = aggregatedWithTime.map(item => item.label);
    const values = aggregatedWithTime.map(item => item.value);
    
    return { labels, values };
}

// Déterminer le format de temps pour l'agrégation en fonction de la période
function determineTimeFormat(period) {
    switch(period) {
        case '1h': return 'HH:mm'; // Par minute pour une période d'une heure
        case '6h': return 'dd/MM HH:mm'; // Par 5 minutes pour une période de 6 heures
        case '12h': return 'dd/MM HH:mm'; // Par 10 minutes pour une période de 12 heures
        case '1d': return 'dd/MM HH:00'; // Par heure pour une période d'un jour
        case '1w': return 'dd/MM'; // Par jour pour une période d'une semaine
        case '1m': return 'dd/MM'; // Par jour pour une période d'un mois
        default: return 'dd/MM HH:00';
    }
}

// Détermination de l'unité de temps en fonction de la quantité de données
function determineTimeUnit(dataLength) {
    if (dataLength <= 24) return 'hour';
    if (dataLength <= 24 * 7) return 'day';
    return 'week';
}

// // Mise à jour du graphique en chandelier
// function updateCandlestickChart(data) {
//     const ctx = document.getElementById('candlestick-chart').getContext('2d');
    
//     // Si aucune donnée, sortir
//     if (!data || data.length === 0) {
//         return;
//     }
    
//     // Filtrer les données invalides (prix à -1) et formater pour le graphique en chandelier
//     const candlestickData = data
//         .filter(point => 
//             point.openBuy !== -1 && point.closeBuy !== -1 && 
//             point.highBuy !== -1 && point.lowBuy !== -1
//         )
//         .map(point => ({
//             x: luxon.DateTime.fromISO(point.timestamp),
//             o: point.openBuy,
//             h: point.highBuy,
//             l: point.lowBuy,
//             c: point.closeBuy
//         }));
    
//     // Créer des données pour les prix de vente si disponibles
//     const sellData = data
//         .filter(point => 
//             point.openSell !== -1 && point.closeSell !== -1 && 
//             point.highSell !== -1 && point.lowSell !== -1
//         )
//         .map(point => ({
//             x: luxon.DateTime.fromISO(point.timestamp),
//             o: point.openSell,
//             h: point.highSell,
//             l: point.lowSell,
//             c: point.closeSell
//         }));
    
//     // Détruire le graphique existant s'il existe
//     if (candlestickChart) {
//         candlestickChart.destroy();
//     }
    
//     // Définir les couleurs selon le thème
//     const textColor = getComputedStyle(document.documentElement).getPropertyValue('--text-color').trim();
//     const gridColor = getComputedStyle(document.documentElement).getPropertyValue('--chart-grid-color').trim();
    
//     // Créer le nouveau graphique
//     candlestickChart = new Chart(ctx, {
//         type: 'candlestick',
//         data: {
//             datasets: [
//                 // Données d'achat
//                 ...(candlestickData.length > 0 ? [{
//                     label: 'Prix d\'achat',
//                     data: candlestickData,
//                     color: {
//                         up: 'rgba(75, 192, 192, 1)',
//                         down: 'rgba(255, 99, 132, 1)',
//                         unchanged: 'rgba(180, 180, 180, 1)',
//                     },
//                     borderColor: {
//                         up: 'rgba(75, 192, 192, 1)',
//                         down: 'rgba(255, 99, 132, 1)',
//                         unchanged: 'rgba(180, 180, 180, 1)',
//                     },
//                     borderWidth: 1,
//                     yAxisID: 'y-acheter'
//                 }] : []),
//                 // Données de vente
//                 ...(sellData.length > 0 ? [{
//                     label: 'Prix de vente',
//                     data: sellData,
//                     color: {
//                         up: 'rgba(54, 162, 235, 0.6)',
//                         down: 'rgba(255, 159, 64, 0.6)',
//                         unchanged: 'rgba(180, 180, 180, 0.6)',
//                     },
//                     borderColor: {
//                         up: 'rgba(54, 162, 235, 1)',
//                         down: 'rgba(255, 159, 64, 1)',
//                         unchanged: 'rgba(180, 180, 180, 1)',
//                     },
//                     borderWidth: 1,
//                     yAxisID: 'y-vendre'
//                 }] : [])
//             ]
//         },
//         options: {
//             responsive: true,
//             interaction: {
//                 mode: 'index',
//                 intersect: false,
//             },
//             scales: {
//                 x: {
//                     type: 'time',
//                     time: {
//                         unit: determineTimeUnit(data.length),
//                         tooltipFormat: 'dd/MM HH:mm',
//                         displayFormats: {
//                             hour: 'HH:mm',
//                             day: 'dd/MM'
//                         }
//                     },
//                     grid: {
//                         color: gridColor
//                     },
//                     ticks: {
//                         color: textColor
//                     },
//                     title: {
//                         display: true,
//                         text: 'Date/Heure',
//                         color: textColor
//                     }
//                 },
//                 'y-acheter': {
//                     position: 'left',
//                     title: {
//                         display: true,
//                         text: 'Prix d\'achat',
//                         color: textColor
//                     },
//                     grid: {
//                         color: gridColor
//                     },
//                     ticks: {
//                         color: textColor
//                     }
//                 },
//                 'y-vendre': {
//                     position: 'right',
//                     title: {
//                         display: true,
//                         text: 'Prix de vente',
//                         color: textColor
//                     },
//                     grid: {
//                         display: false
//                     },
//                     ticks: {
//                         color: textColor
//                     }
//                 }
//             },
//             plugins: {
//                 legend: {
//                     labels: {
//                         color: textColor
//                     }
//                 },
//                 tooltip: {
//                     callbacks: {
//                         label: function(context) {
//                             const dataset = context.dataset;
//                             const point = dataset.data[context.dataIndex];
//                             return [
//                                 `${dataset.label}:`,
//                                 `Ouverture: ${point.o.toFixed(2)}`,
//                                 `Fermeture: ${point.c.toFixed(2)}`,
//                                 `Plus haut: ${point.h.toFixed(2)}`,
//                                 `Plus bas: ${point.l.toFixed(2)}`
//                             ];
//                         }
//                     }
//                 }
//             }
//         }
//     });
// }

// function updateCandlestickChart(data) {
//     const canvasElement = document.getElementById('candlestick-chart');
//     if (!canvasElement) {
//         console.error("Élément canvas 'candlestick-chart' introuvable");
//         return;
//     }
    
//     const ctx = canvasElement.getContext('2d');
    
//     // Si aucune donnée, sortir
//     if (!data || data.length === 0) {
//         console.warn('Aucune donnée pour le graphique en chandelier');
//         return;
//     }
    
//     // Détruire le graphique existant s'il existe
//     if (candlestickChart) {
//         candlestickChart.destroy();
//         candlestickChart = null;
//     }
    
//     // Filtrer les données invalides avec une approche moins restrictive
//     const buyData = data
//         .filter(point => {
//             // Vérifier si un point a au moins certaines données valides
//             const hasValidBuyData = point.openBuy !== undefined && point.closeBuy !== undefined;
            
//             // Reconstruire les points manquants si nécessaire
//             if (hasValidBuyData) {
//                 if (point.highBuy === undefined || point.highBuy === -1) {
//                     point.highBuy = Math.max(point.openBuy, point.closeBuy);
//                 }
//                 if (point.lowBuy === undefined || point.lowBuy === -1) {
//                     point.lowBuy = Math.min(point.openBuy, point.closeBuy);
//                 }
//             }
            
//             return hasValidBuyData && point.openBuy !== -1 && point.closeBuy !== -1;
//         })
//         .map(point => ({
//             x: luxon.DateTime.fromISO(point.timestamp),
//             o: point.openBuy,
//             h: point.highBuy || Math.max(point.openBuy, point.closeBuy),
//             l: point.lowBuy || Math.min(point.openBuy, point.closeBuy),
//             c: point.closeBuy
//         }));
    
//     // Créer des données pour les prix de vente si disponibles avec la même approche
//     const sellData = data
//         .filter(point => {
//             const hasValidSellData = point.openSell !== undefined && point.closeSell !== undefined;
            
//             if (hasValidSellData) {
//                 if (point.highSell === undefined || point.highSell === -1) {
//                     point.highSell = Math.max(point.openSell, point.closeSell);
//                 }
//                 if (point.lowSell === undefined || point.lowSell === -1) {
//                     point.lowSell = Math.min(point.openSell, point.closeSell);
//                 }
//             }
            
//             return hasValidSellData && point.openSell !== -1 && point.closeSell !== -1;
//         })
//         .map(point => ({
//             x: luxon.DateTime.fromISO(point.timestamp),
//             o: point.openSell,
//             h: point.highSell || Math.max(point.openSell, point.closeSell),
//             l: point.lowSell || Math.min(point.openSell, point.closeSell),
//             c: point.closeSell
//         }));
    
//     // Vérifier si nous avons des données après filtrage
//     if (buyData.length === 0 && sellData.length === 0) {
//         console.warn('Aucune donnée valide pour le graphique en chandelier après filtrage');
//         return;
//     }
    
//     // Définir les couleurs selon le thème
//     const textColor = getComputedStyle(document.documentElement).getPropertyValue('--text-color').trim();
//     const gridColor = getComputedStyle(document.documentElement).getPropertyValue('--chart-grid-color').trim();
    
//     try {
//         // S'assurer que le type de graphique 'candlestick' est disponible
//         if (!Chart.controllers.candlestick) {
//             console.error("Le type de graphique 'candlestick' n'est pas disponible. Vérifiez que chartjs-chart-financial est chargé correctement.");
//             return;
//         }
        
//         // Créer le nouveau graphique
//         candlestickChart = new Chart(ctx, {
//             type: 'candlestick',
//             data: {
//                 datasets: [
//                     // Données d'achat
//                     ...(buyData.length > 0 ? [{
//                         label: 'Prix d\'achat',
//                         data: buyData,
//                         color: {
//                             up: 'rgba(75, 192, 192, 1)',
//                             down: 'rgba(255, 99, 132, 1)',
//                             unchanged: 'rgba(180, 180, 180, 1)',
//                         },
//                         borderColor: {
//                             up: 'rgba(75, 192, 192, 1)',
//                             down: 'rgba(255, 99, 132, 1)',
//                             unchanged: 'rgba(180, 180, 180, 1)',
//                         },
//                         borderWidth: 1,
//                         yAxisID: 'y-acheter'
//                     }] : []),
//                     // Données de vente
//                     ...(sellData.length > 0 ? [{
//                         label: 'Prix de vente',
//                         data: sellData,
//                         color: {
//                             up: 'rgba(54, 162, 235, 0.6)',
//                             down: 'rgba(255, 159, 64, 0.6)',
//                             unchanged: 'rgba(180, 180, 180, 0.6)',
//                         },
//                         borderColor: {
//                             up: 'rgba(54, 162, 235, 1)',
//                             down: 'rgba(255, 159, 64, 1)',
//                             unchanged: 'rgba(180, 180, 180, 1)',
//                         },
//                         borderWidth: 1,
//                         yAxisID: 'y-vendre'
//                     }] : [])
//                 ]
//             },
//             options: {
//                 responsive: true,
//                 maintainAspectRatio: true,
//                 interaction: {
//                     mode: 'index',
//                     intersect: false,
//                 },
//                 plugins: {
//                     tooltip: {
//                         callbacks: {
//                             label: function(context) {
//                                 const dataset = context.dataset;
//                                 const point = dataset.data[context.dataIndex];
//                                 return [
//                                     `${dataset.label}:`,
//                                     `Ouverture: ${point.o.toFixed(2)}`,
//                                     `Fermeture: ${point.c.toFixed(2)}`,
//                                     `Plus haut: ${point.h.toFixed(2)}`,
//                                     `Plus bas: ${point.l.toFixed(2)}`
//                                 ];
//                             }
//                         }
//                     },
//                     legend: {
//                         display: true,
//                         labels: {
//                             color: textColor
//                         }
//                     }
//                 },
//                 scales: {
//                     x: {
//                         type: 'time',
//                         time: {
//                             unit: determineTimeUnit(data.length),
//                             tooltipFormat: 'dd/MM HH:mm',
//                             displayFormats: {
//                                 hour: 'HH:mm',
//                                 day: 'dd/MM',
//                                 week: 'dd/MM',
//                                 month: 'MM/yyyy'
//                             }
//                         },
//                         grid: {
//                             color: gridColor
//                         },
//                         ticks: {
//                             color: textColor,
//                             maxTicksLimit: 10,
//                             autoSkip: true
//                         },
//                         title: {
//                             display: true,
//                             text: 'Date/Heure',
//                             color: textColor
//                         }
//                     },
//                     // x: {
//                     //     type: 'category',
//                     //     title: {
//                     //         display: true,
//                     //         text: 'Date/Heure'
//                     //     },
//                     //     grid: {
//                     //         color: gridColor
//                     //     },
//                     //     ticks: {
//                     //         color: textColor
//                     //     }
//                     // },
//                     // x: {
//                     //     type: 'time',
//                     //     time: {
//                     //     unit: 'day',
//                     //     tooltipFormat: 'dd/MM/yyyy',
//                     //     displayFormats: {
//                     //         week: 'dd/MM/yyyy',
//                     //         }
//                     //     }
//                     // },
//                     'y-acheter': {
//                         position: 'left',
//                         title: {
//                             display: true,
//                             text: 'Prix d\'achat',
//                             color: textColor
//                         },
//                         grid: {
//                             color: gridColor
//                         },
//                         ticks: {
//                             color: textColor
//                         }
//                     },
//                     'y-vendre': {
//                         position: 'right',
//                         title: {
//                             display: true,
//                             text: 'Prix de vente',
//                             color: textColor
//                         },
//                         grid: {
//                             display: false
//                         },
//                         ticks: {
//                             color: textColor
//                         }
//                     }
//                 }
//             }
//         });
//     } catch (error) {
//         console.error("Erreur lors de la création du graphique en chandelier:", error);
//     }
// }

// Mise à jour des statistiques
function updateStats(chartData, statsData) {
    // Stocker les données pour pouvoir les mettre à jour lors d'un changement de langue
    currentStats = statsData;
    currentChartData = chartData;

    // Prix actuels (achat et vente)
    if (chartData && chartData.length > 0) {
        const latest = chartData[chartData.length - 1];
        
        // Prix d'achat (masquer si -1)
        const rawBuyPrice = latest.closeBuy;
        const buyPrice = (rawBuyPrice === -1) ? '--' : (rawBuyPrice?.toFixed(2) || '--');
        
        // Prix de vente (masquer si -1)
        const rawSellPrice = latest.closeSell;
        const sellPrice = (rawSellPrice === -1) ? '--' : (rawSellPrice?.toFixed(2) || '--');
        
        // Afficher différemment si les prix sont identiques ou différents
        if ((buyPrice === sellPrice && buyPrice !== '--') || rawSellPrice === 0 || rawSellPrice === -1) {
            document.getElementById('current-price').textContent = buyPrice;
        } else if (rawBuyPrice === -1 && rawSellPrice !== -1) {
            document.getElementById('current-price').textContent = `${getTranslation("stats.sell")}: ` + sellPrice;
        } else if (rawBuyPrice !== -1 && rawSellPrice === -1) {
            document.getElementById('current-price').textContent = `${getTranslation("stats.buy")}: ` + buyPrice;
        } else {
            document.getElementById('current-price').innerHTML = 
                `${getTranslation("stats.buy")}: <span class="buy-price">${buyPrice}</span><br />` +
                `${getTranslation("stats.sell")}: <span class="sell-price">${sellPrice}</span>`;
        }
        
        document.getElementById('last-transaction').textContent = formatTimestamp(latest.timestamp);
    } else if (statsData && statsData.currentBuyPrice) {
        // Utiliser les stats si les données du graphique ne sont pas disponibles
        const rawBuyPrice = statsData.currentBuyPrice;
        const buyPrice = (rawBuyPrice === -1) ? '--' : rawBuyPrice.toFixed(2);
        
        const rawSellPrice = statsData.currentSellPrice;
        const sellPrice = (rawSellPrice === -1) ? '--' : (rawSellPrice?.toFixed(2) || '--');
        
        if ((buyPrice === sellPrice && buyPrice !== '--') || !statsData.currentSellPrice || rawSellPrice === -1) {
            document.getElementById('current-price').textContent = buyPrice;
        } else if (rawBuyPrice === -1 && rawSellPrice !== -1) {
            document.getElementById('current-price').textContent = `${getTranslation("stats.sell")}: ` + sellPrice;
        } else if (rawBuyPrice !== -1 && rawSellPrice === -1) {
            document.getElementById('current-price').textContent = `${getTranslation("stats.buy")}: ` + buyPrice;
        } else {
            document.getElementById('current-price').innerHTML = 
                `${getTranslation("stats.buy")}: <span class="buy-price">${buyPrice}</span><br />` +
                `${getTranslation("stats.sell")}: <span class="sell-price">${sellPrice}</span>`;
        }
        
        document.getElementById('last-transaction').textContent = statsData.lastTimestamp ? 
            formatTimestamp(statsData.lastTimestamp) : '--';
    } else {
        document.getElementById('current-price').textContent = '--';
        document.getElementById('last-transaction').textContent = '--';
    }
    
    // Variation 24h (séparée pour achat et vente)
    if (chartData && chartData.length > 1) {
        const latestBuy = chartData[chartData.length - 1].closeBuy;
        const firstBuy = chartData[0].closeBuy;
        
        const latestSell = chartData[chartData.length - 1].closeSell;
        const firstSell = chartData[0].closeSell;
        
        // Calculer les variations (ne pas calculer si prix = -1)
        let buyChange = (firstBuy > 0 && latestBuy !== -1 && firstBuy !== -1) 
            ? ((latestBuy - firstBuy) / firstBuy * 100).toFixed(2) 
            : '--';
        
        let sellChange = (firstSell > 0 && latestSell !== -1 && firstSell !== -1) 
            ? ((latestSell - firstSell) / firstSell * 100).toFixed(2) 
            : '--';
        
        // Afficher les variations
        const element = document.getElementById('price-change');
        
        // Si les deux variations sont identiques ou si un prix n'est pas disponible
        // if (buyChange === sellChange || buyChange === '--' && sellChange === '--') {
        //     element.textContent = `${buyChange === '--' ? '--' : buyChange + '%'}`;
        //     element.style.color = buyChange >= 0 && buyChange !== '--' ? 'green' : 'red';
        // } else if (buyChange === '--' && sellChange !== '--') {
        if (buyChange === '--' && sellChange !== '--') {
            element.innerHTML = `${getTranslation("stats.sell")}: <span style="color:${sellChange >= 0 ? 'red' : 'green'}">${sellChange}%</span>`;
        } else if (buyChange !== '--' && sellChange === '--') {
            element.innerHTML = `${getTranslation("stats.buy")}: <span style="color:${buyChange >= 0 ? 'green' : 'red'}">${buyChange}%</span>`;
        } else {
            element.innerHTML = 
                `${getTranslation("stats.buy")}: <span style="color:${buyChange >= 0 ? 'green' : 'red'}">${buyChange}%</span><br />` +
                `${getTranslation("stats.sell")}: <span style="color:${sellChange >= 0 ? 'red' : 'green'}">${sellChange}%</span>`;
        }
    } else {
        document.getElementById('price-change').textContent = '--';
    }
    
    // Volume 24h
    if (chartData && Array.isArray(chartData)) {
        const volume24h = chartData.reduce((sum, point) => sum + (point.volume || 0), 0);
        document.getElementById('volume-24h').textContent = volume24h.toFixed(0);
    } else if (statsData && statsData.totalVolume) {
        document.getElementById('volume-24h').textContent = statsData.totalVolume.toFixed(0);
    } else {
        document.getElementById('volume-24h').textContent = '--';
    }
    
    // Ajouter des statistiques supplémentaires si disponibles
    if (statsData) {
        if (document.getElementById('total-points')) {
            document.getElementById('total-points').textContent = statsData.totalPoints || '--';
        }
        if (document.getElementById('time-span')) {
            document.getElementById('time-span').textContent = 
                statsData.timeSpanHours ? `${statsData.timeSpanHours} ${getTranslation("time.hours")}` : '--';
        }
    }
    
    // Afficher le stock si disponible (pour les types STOCK/STATIC_STOCK ou RECIPE avec ingrédients en STOCK)
    if (statsData && (
        (['STOCK', 'STATIC_STOCK'].includes(statsData.shopType)) || 
        statsData.isStockItem || statsData.isRecipeStock)) {
        
        if (statsData.currentStock !== undefined) {
            const currentStock = statsData.currentStock;
            const maxStock = statsData.maxStock || '--';
            
            // Déterminer la couleur du stock
            let colorClass = '';
            if (maxStock !== '--' && !isNaN(maxStock)) {
                const ratio = currentStock / maxStock;
                
                if (ratio < 0.10) {
                    colorClass = 'stock-critical'; // Rouge foncé pour stock critique
                } else if (ratio < 0.25) {
                    colorClass = 'stock-low'; // Rouge pour stock faible
                } else if (ratio < 0.50) {
                    colorClass = 'stock-medium'; // Jaune pour stock moyen
                } else {
                    colorClass = 'stock-high'; // Vert pour stock élevé
                }
            }
            
            document.getElementById('stock-info').innerHTML = `<span class="${colorClass}">${currentStock}</span><span class="separator">/</span><span>${maxStock}</span>`;
            document.getElementById('stock-container').style.display = 'block';
        } else {
            document.getElementById('stock-container').style.display = 'none';
        }
    } else {
        document.getElementById('stock-container').style.display = 'none';
    }
}

// Formatage de la date/heure
function formatTimestamp(timestamp) {
    if (!timestamp) return '--';
    
    // Créer un objet DateTime avec la langue actuelle
    const dt = luxon.DateTime.fromISO(timestamp);
    
    // Déterminer la locale à utiliser en fonction de la langue sélectionnée
    let locale = 'fr'; // Par défaut
    
    switch (currentLanguage) {
        case 'en':
            locale = 'en';
            break;
        case 'es':
            locale = 'es';
            break;
        case 'pt':
            locale = 'pt';
            break;
        case 'zh':
            locale = 'zh';
            break;
        case 'ar':
            locale = 'ar';
            break;
        case 'hi':
            locale = 'hi';
            break;
        default:
            locale = 'fr';
    }

    return luxon.DateTime.fromISO(timestamp).setLocale(locale).toLocaleString(luxon.DateTime.DATETIME_MED);
}

// Événement de changement de shop
function onShopChange(event) {
    const shopId = event.target.value;
    if (shopId) {
        currentShop = shopId;
        loadItems(shopId);
        
        // Mettre à jour l'URL
        updateUrlParams();
    } else {
        document.getElementById('item-select').disabled = true;
        document.getElementById('item-select').innerHTML = '<option value=""></option>';
    }
}

// Événement de changement d'item
function onItemChange(event) {
    const itemId = event.target.value;
    if (itemId && currentShop) {
        currentItem = itemId;
        loadPriceData(currentShop, itemId, currentPeriod);
        
        // Rafraîchir l'analyse de marché si elle est visible
        if (document.getElementById('market-analysis-container').style.display === 'block' && window.marketAnalytics) {
            window.marketAnalytics.displayMarketAnalysis(currentShop, itemId, currentPeriod);
        }
        
        // Mettre à jour l'URL
        updateUrlParams();
    }
}

// Événement de changement de période
function onPeriodChange(event) {
    const period = event.target.value;
    currentPeriod = period;
    
    if (currentShop && currentItem) {
        loadPriceData(currentShop, currentItem, period);
        
        // Rafraîchir l'analyse de marché si elle est visible
        if (document.getElementById('market-analysis-container').style.display === 'block' && window.marketAnalytics) {
            window.marketAnalytics.displayMarketAnalysis(currentShop, currentItem, currentPeriod);
        }
    }
}

// Mise à jour des paramètres dans l'URL
function updateUrlParams() {
    const url = new URL(window.location);
    
    if (currentShop) {
        url.searchParams.set('shop', currentShop);
    }
    
    if (currentItem) {
        url.searchParams.set('item', currentItem);
    }
    
    window.history.replaceState({}, '', url);
}

// Chargement des paramètres depuis l'URL
function loadParamsFromUrl() {
    const url = new URL(window.location);
    const shopId = url.searchParams.get('shop');
    const itemId = url.searchParams.get('item');
    
    if (shopId) {
        const shopSelect = document.getElementById('shop-select');
        shopSelect.value = shopId;
        currentShop = shopId;
        
        loadItems(shopId).then(() => {
            if (itemId) {
                const itemSelect = document.getElementById('item-select');
                itemSelect.value = itemId;
                currentItem = itemId;
                
                loadPriceData(shopId, itemId, currentPeriod);
            }
        });
    }
}





/**
 * Classe pour l'analyse avancée du marché
 */
class MarketAnalytics {
    constructor() {
        this.trendChart = null;
        this.rsiChart = null;
        this.macdChart = null;
        this.bollingerChart = null;
        this.forecaseChart = null;
        
        // Initialiser les couleurs en fonction du thème actuel
        this.updateColors();
        
        // Ajouter un écouteur pour les changements de thème
        document.addEventListener('theme-changed', () => {
            this.updateColors();
            this.refreshChartsIfVisible();
        });

        // Ajouter un écouteur pour les changements de langue
        document.addEventListener('language-changed', () => {
            // Rafraîchir l'analyse si elle est visible
            this.refreshChartsIfVisible();
        });
    }
    
    /**
     * Met à jour les couleurs en fonction du thème actuel
     */
    updateColors() {
        const isDarkMode = document.documentElement.getAttribute('data-theme') === 'dark';
        
        // Définir les couleurs en fonction du thème
        this.colors = {
            // Couleurs de tendance
            rising: isDarkMode ? 'rgba(76, 175, 80, 0.9)' : 'rgba(76, 175, 80, 0.8)',
            falling: isDarkMode ? 'rgba(244, 67, 54, 0.9)' : 'rgba(244, 67, 54, 0.8)',
            stable: isDarkMode ? 'rgba(33, 150, 243, 0.9)' : 'rgba(33, 150, 243, 0.8)',
            volatile: isDarkMode ? 'rgba(255, 152, 0, 0.9)' : 'rgba(255, 152, 0, 0.8)',
            spread_changing: isDarkMode ? 'rgba(156, 39, 176, 0.9)' : 'rgba(156, 39, 176, 0.8)',
            unknown: isDarkMode ? 'rgba(200, 200, 200, 0.9)' : 'rgba(158, 158, 158, 0.8)',
            
            // Couleurs pour les prévisions et les prix
            forecast: isDarkMode ? 'rgba(156, 39, 176, 0.4)' : 'rgba(156, 39, 176, 0.3)',
            buy: isDarkMode ? 'rgba(244, 67, 54, 0.9)' : 'rgba(244, 67, 54, 0.8)',
            sell: isDarkMode ? 'rgba(76, 175, 80, 0.9)' : 'rgba(76, 175, 80, 0.8)',
            
            // Couleurs pour les indicateurs techniques
            sma5: isDarkMode ? 'rgba(255, 193, 7, 1)' : 'rgba(255, 193, 7, 1)',
            sma20: isDarkMode ? 'rgba(33, 150, 243, 1)' : 'rgba(33, 150, 243, 1)',
            upper: isDarkMode ? 'rgba(76, 175, 80, 0.6)' : 'rgba(76, 175, 80, 0.5)',
            lower: isDarkMode ? 'rgba(244, 67, 54, 0.6)' : 'rgba(244, 67, 54, 0.5)',
            macd: isDarkMode ? 'rgba(33, 150, 243, 1)' : 'rgba(33, 150, 243, 1)',
            signal: isDarkMode ? 'rgba(255, 193, 7, 1)' : 'rgba(255, 193, 7, 1)',
            histogram_positive: isDarkMode ? 'rgba(76, 175, 80, 0.6)' : 'rgba(76, 175, 80, 0.5)',
            histogram_negative: isDarkMode ? 'rgba(244, 67, 54, 0.6)' : 'rgba(244, 67, 54, 0.5)'
        };
        
        // Définir les options Chart.js globales selon le thème
        Chart.defaults.color = isDarkMode ? '#f0f0f0' : '#333333';
        Chart.defaults.borderColor = isDarkMode ? 'rgba(255, 255, 255, 0.1)' : 'rgba(0, 0, 0, 0.1)';
        Chart.defaults.scale.grid.color = isDarkMode ? 'rgba(255, 255, 255, 0.05)' : 'rgba(0, 0, 0, 0.05)';
    }
    
    /**
     * Rafraîchit les graphiques si l'analyse est visible
     */
    refreshChartsIfVisible() {
        const container = document.getElementById('market-analysis-container');
        if (container && container.style.display === 'block' && this.hasChartsInitialized()) {
            const shopId = document.getElementById('shop-select').value;
            const itemId = document.getElementById('item-select').value;
            
            // Déterminer quelle méthode utiliser en fonction de l'élément select présent
            if (document.getElementById('trend-period-select')) {
                const trendPeriod = document.getElementById('trend-period-select').value;
                this.displayMarketAnalysisWithDays(shopId, itemId, parseInt(trendPeriod));
            } else {
                const period = document.getElementById('period-select').value || '1m';
                this.displayMarketAnalysis(shopId, itemId, period);
            }
        }
    }

    // Méthode pour vérifier si les graphiques existent déjà
    hasChartsInitialized() {
        return this.trendChart || this.rsiChart || this.macdChart || 
               this.bollingerChart || this.forecastChart;
    }
    
    // Méthode pour nettoyer les graphiques si nécessaire
    clearCharts() {
        if (this.trendChart) {
            this.trendChart.destroy();
            this.trendChart = null;
        }
        if (this.rsiChart) {
            this.rsiChart.destroy();
            this.rsiChart = null;
        }
        if (this.macdChart) {
            this.macdChart.destroy();
            this.macdChart = null;
        }
        if (this.bollingerChart) {
            this.bollingerChart.destroy();
            this.bollingerChart = null;
        }
        if (this.forecastChart) {
            this.forecastChart.destroy();
            this.forecastChart = null;
        }
    }

    /**
     * Charge et affiche l'analyse technique
     */
    async displayMarketAnalysis(shopId, itemId, period = '1m') {
        // Nettoyer les graphiques existants avant d'en créer de nouveaux
        this.clearCharts();

        try {
            // console.log(`Chargement de l'analyse pour ${shopId}:${itemId} (période: ${period})`);
            const response = await fetch(`/api/market-trends?shop=${encodeURIComponent(shopId)}&item=${encodeURIComponent(itemId)}&days=${this.getPeriodDays(period)}`);
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const data = await response.json();
            
            // Afficher les sections d'analyse
            document.getElementById('market-analysis-container').style.display = 'block';
            
            // Afficher les différentes analyses
            this.displayTrendSummary(data);
            this.displayTechnicalIndicators(data);
            this.displayPriceForecast(data);
            this.displayTradingSignals(data);
            this.displaySupportResistance(data);
            
        } catch (error) {
            console.error('Erreur lors du chargement de l\'analyse de marché:', error);
            document.getElementById('market-analysis-container').innerHTML = 
                `<div class="error-message">Impossible de charger l'analyse de marché: ${error.message}</div>`;
        }
    }

    /**
     * Charge et affiche l'analyse technique avec une période en jours directement
     */
    async displayMarketAnalysisWithDays(shopId, itemId, days = 7) {
        // Nettoyer les graphiques existants avant d'en créer de nouveaux
        this.clearCharts();
        
        try {
            // console.log(`Chargement de l'analyse pour ${shopId}:${itemId} (${days} jours)`);
            const response = await fetch(`/api/market-trends?shop=${encodeURIComponent(shopId)}&item=${encodeURIComponent(itemId)}&days=${days}`);
            
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            
            const data = await response.json();
            
            // Afficher les sections d'analyse
            document.getElementById('market-analysis-container').style.display = 'block';
            
            // Afficher les différentes analyses
            this.displayTrendSummary(data);
            this.displayTechnicalIndicators(data);
            this.displayPriceForecast(data);
            this.displayTradingSignals(data);
            this.displaySupportResistance(data);
            
        } catch (error) {
            console.error('Erreur lors du chargement de l\'analyse de marché:', error);
            document.getElementById('market-analysis-container').innerHTML = 
                `<div class="error-message">Impossible de charger l'analyse de marché: ${error.message}</div>`;
        }
    }
    
    /**
     * Affiche le résumé de la tendance
     */
    displayTrendSummary(data) {
        const container = document.getElementById('trend-summary');
        if (!container) return;
        
        const trendClass = data.trend.toLowerCase();
        const trendLabel = this.getTrendLabel(data.trend);
        
        // Construire le HTML pour le résumé
        let html = `
            <div class="trend-header">
                <h3>${getTranslation('trend.title')}</h3>
                <div class="trend-badge ${trendClass}">${trendLabel}</div>
            </div>
            
            <div class="trend-stats">
                <div class="stat-item">
                    <div class="stat-label">${getTranslation('trend.strength')}</div>
                    <div class="stat-value">${Math.round(data.strength * 100)}%</div>
                </div>
                <div class="stat-item">
                    <div class="stat-label">${getTranslation('trend.volatility')}</div>
                    <div class="stat-value">${Math.round(data.volatility * 100)}%</div>
                </div>
                <div class="stat-item">
                    <div class="stat-label">${getTranslation('trend.buyPrice')}</div>
                    <div class="stat-value ${this.getChangeBuyClass(data.buyPriceChange)}">${this.formatChange(data.buyPriceChange)}</div>
                </div>
                <div class="stat-item">
                    <div class="stat-label">${getTranslation('trend.sellPrice')}</div>
                    <div class="stat-value ${this.getChangeSellClass(data.sellPriceChange)}">${this.formatChange(data.sellPriceChange)}</div>
                </div>
                <div class="stat-item">
                    <div class="stat-label">${getTranslation('trend.volume')}</div>
                    <div class="stat-value ${this.getChangeClass(data.volumeChange)}">${this.formatChange(data.volumeChange)}</div>
                </div>
            </div>
        `;
        
        container.innerHTML = html;
    }
    
    /**
     * Affiche les indicateurs techniques
     */
    displayTechnicalIndicators(data) {
        this.displaySMAChart(data);
        this.displayRSIChart(data);
        this.displayMACDChart(data);
        this.displayBollingerBandsChart(data);
    }
    
    /**
     * Affiche le graphique des moyennes mobiles (SMA)
     */
    displaySMAChart(data) {
        const container = document.getElementById('sma-chart-container');
        if (!container) return;
        
        const buyAnalysis = data.buyAnalysis;
        if (!buyAnalysis || !buyAnalysis.sma5 || !buyAnalysis.sma20) {
            container.innerHTML = `<div class="no-data-message">${getTranslation('analytics.noData')}</div>`;
            return;
        }
        
        // Créer un canvas s'il n'existe pas
        if (!document.getElementById('sma-chart')) {
            container.innerHTML = '<canvas id="sma-chart"></canvas>';
        }
        
        const ctx = document.getElementById('sma-chart').getContext('2d');
        
        // Préparer les données
        const labels = Array.from({ length: buyAnalysis.sma20.length }, (_, i) => i);
        
        // Créer des datasets pour les prix récents et les SMAs
        const datasets = [];
        
        // Déterminer les données de prix récentes (mêmes nombres de points que SMA20)
        const startIndex = buyAnalysis.sma5.length - buyAnalysis.sma20.length;
        
        // Destruction du graphique existant si présent
        if (this.smaChart) {
            this.smaChart.destroy();
        }
        
        // Création du graphique
        this.smaChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: labels,
                datasets: [
                    {
                        label: 'SMA 5',
                        data: buyAnalysis.sma5.slice(startIndex),
                        borderColor: this.colors.sma5,
                        borderWidth: 2,
                        tension: 0.1,
                        fill: false
                    },
                    {
                        label: 'SMA 20',
                        data: buyAnalysis.sma20,
                        borderColor: this.colors.sma20,
                        borderWidth: 2,
                        tension: 0.1,
                        fill: false
                    }
                ]
            },
            options: {
                responsive: true,
                plugins: {
                    legend: {
                        position: 'top',
                    },
                    tooltip: {
                        mode: 'index',
                        intersect: false
                    },
                    title: {
                        display: true,
                        text: `${getTranslation('SMAChart.title')}`
                    }
                },
                scales: {
                    y: {
                        beginAtZero: false
                    }
                }
            }
        });
    }
    
    /**
     * Affiche le graphique RSI
     */
    displayRSIChart(data) {
        const container = document.getElementById('rsi-chart-container');
        if (!container) return;
        
        const buyAnalysis = data.buyAnalysis;
        if (!buyAnalysis || !buyAnalysis.rsi || buyAnalysis.rsi.length === 0) {
            container.innerHTML = `<div class="no-data-message">${getTranslation('RSIChart.noData')}</div>`;
            return;
        }
        
        // Créer un canvas s'il n'existe pas
        if (!document.getElementById('rsi-chart')) {
            container.innerHTML = '<canvas id="rsi-chart"></canvas>';
        }
        
        const ctx = document.getElementById('rsi-chart').getContext('2d');
        
        // Préparer les données
        const labels = Array.from({ length: buyAnalysis.rsi.length }, (_, i) => i);
        
        // Destruction du graphique existant si présent
        if (this.rsiChart) {
            this.rsiChart.destroy();
        }
        
        // Création du graphique
        this.rsiChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: labels,
                datasets: [
                    {
                        label: 'RSI',
                        data: buyAnalysis.rsi,
                        borderColor: 'rgba(255, 99, 132, 1)',
                        borderWidth: 2,
                        tension: 0.1,
                        fill: false
                    }
                ]
            },
            options: {
                responsive: true,
                plugins: {
                    legend: {
                        position: 'top',
                    },
                    tooltip: {
                        mode: 'index',
                        intersect: false
                    },
                    title: {
                        display: true,
                        text: `${getTranslation('RSIChart.title')}`
                    }
                },
                scales: {
                    y: {
                        min: 0,
                        max: 100,
                        grid: {
                            drawOnChartArea: true,
                            color: (context) => {
                                if (context.tick.value === 30 || context.tick.value === 70) {
                                    return 'rgba(255, 0, 0, 0.3)';
                                }
                                return 'rgba(0, 0, 0, 0.1)';
                            }
                        }
                    }
                }
            }
        });
    }
    
    /**
     * Affiche le graphique MACD
     */
    displayMACDChart(data) {
        const container = document.getElementById('macd-chart-container');
        if (!container) return;
        
        const buyAnalysis = data.buyAnalysis;
        if (!buyAnalysis || !buyAnalysis.macd || !buyAnalysis.macd.macd) {
            container.innerHTML = `<div class="no-data-message">${getTranslation('MACDChart.noData')}</div>`;
            return;
        }
        
        // Créer un canvas s'il n'existe pas
        if (!document.getElementById('macd-chart')) {
            container.innerHTML = '<canvas id="macd-chart"></canvas>';
        }
        
        const ctx = document.getElementById('macd-chart').getContext('2d');
        
        // Préparer les données
        const macdData = buyAnalysis.macd;
        const labels = Array.from({ length: macdData.macd.length }, (_, i) => i);
        
        // Préparer les données d'histogramme avec couleurs
        const histogramColors = macdData.histogram.map(val => 
            val >= 0 ? this.colors.histogram_positive : this.colors.histogram_negative
        );
        
        // Destruction du graphique existant si présent
        if (this.macdChart) {
            this.macdChart.destroy();
        }
        
        // Création du graphique
        this.macdChart = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [
                    {
                        label: `${getTranslation('MACDChart.histogram')}`,
                        data: macdData.histogram,
                        backgroundColor: histogramColors,
                        barPercentage: 0.8,
                        categoryPercentage: 0.9,
                        order: 3
                    },
                    {
                        label: 'MACD',
                        data: macdData.macd,
                        borderColor: this.colors.macd,
                        borderWidth: 2,
                        tension: 0.1,
                        fill: false,
                        type: 'line',
                        order: 1
                    },
                    {
                        label: `${getTranslation('MACDChart.signalLine')}`,
                        data: macdData.signal,
                        borderColor: this.colors.signal,
                        borderWidth: 2,
                        tension: 0.1,
                        fill: false,
                        type: 'line',
                        order: 2
                    }
                ]
            },
            options: {
                responsive: true,
                plugins: {
                    legend: {
                        position: 'top',
                    },
                    tooltip: {
                        mode: 'index',
                        intersect: false
                    },
                    title: {
                        display: true,
                        text: `${getTranslation('MACDChart.title')}`
                    }
                },
                scales: {
                    y: {
                        beginAtZero: true
                    }
                }
            }
        });
    }
    
    /**
     * Affiche le graphique des bandes de Bollinger
     */
    displayBollingerBandsChart(data) {
        const container = document.getElementById('bollinger-chart-container');
        if (!container) return;
        
        const buyAnalysis = data.buyAnalysis;
        if (!buyAnalysis || !buyAnalysis.bollingerBands || !buyAnalysis.bollingerBands.middle) {
            container.innerHTML = `<div class="no-data-message">${getTranslation('BollingerBandsChart.noData')}</div>`;
            return;
        }
        
        // Créer un canvas s'il n'existe pas
        if (!document.getElementById('bollinger-chart')) {
            container.innerHTML = '<canvas id="bollinger-chart"></canvas>';
        }
        
        const ctx = document.getElementById('bollinger-chart').getContext('2d');
        
        // Préparer les données
        const bands = buyAnalysis.bollingerBands;
        const labels = Array.from({ length: bands.middle.length }, (_, i) => i);
        
        // Destruction du graphique existant si présent
        if (this.bollingerChart) {
            this.bollingerChart.destroy();
        }
        
        // Création du graphique
        this.bollingerChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: labels,
                datasets: [
                    {
                        label: `${getTranslation('BollingerBandsChart.upperBand')}`,
                        data: bands.upper,
                        borderColor: this.colors.upper,
                        borderWidth: 1,
                        tension: 0.1,
                        fill: false
                    },
                    {
                        label: `${getTranslation('BollingerBandsChart.middleBand')}`,
                        data: bands.middle,
                        borderColor: this.colors.sma20,
                        borderWidth: 2,
                        tension: 0.1,
                        fill: false
                    },
                    {
                        label: `${getTranslation('BollingerBandsChart.lowerBand')}`,
                        data: bands.lower,
                        borderColor: this.colors.lower,
                        borderWidth: 1,
                        tension: 0.1,
                        fill: '-1',
                        backgroundColor: 'rgba(200, 200, 200, 0.1)'
                    }
                ]
            },
            options: {
                responsive: true,
                plugins: {
                    legend: {
                        position: 'top',
                    },
                    tooltip: {
                        mode: 'index',
                        intersect: false
                    },
                    title: {
                        display: true,
                        text: `${getTranslation('BollingerBandsChart.title')}`
                    }
                },
                scales: {
                    y: {
                        beginAtZero: false
                    }
                }
            }
        });
    }
    
    /**
     * Affiche les prévisions de prix
     */
    displayPriceForecast(data) {
        const container = document.getElementById('forecast-container');
        if (!container) return;
        
        const buyForecast = data.buyForecast;
        const sellForecast = data.sellForecast;
        
        if ((!buyForecast || buyForecast.length === 0) && (!sellForecast || sellForecast.length === 0)) {
            container.innerHTML = `<div class="no-data-message">${getTranslation("forecast.noData")}</div>`;
            return;
        }
        
        // Créer un canvas s'il n'existe pas
        // if (!document.getElementById('forecast-chart')) {
            container.innerHTML = `
                <div class="forecast-header">
                    <h3>${getTranslation("forecast.title")}</h3>
                    <div class="forecast-confidence">
                        ${getTranslation("forecast.confidence")}: <span id="forecast-confidence-value">--</span>
                    </div>
                </div>
                <canvas id="forecast-chart"></canvas>
                <div class="forecast-disclaimer">
                    ${getTranslation("forecast.disclaimer")}
                </div>
            `;
        // }
        
        const ctx = document.getElementById('forecast-chart').getContext('2d');
        
        // Préparer les données
        const labels = [];
        const buyPrices = [];
        const buyLow = [];
        const buyHigh = [];
        const sellPrices = [];
        const sellLow = [];
        const sellHigh = [];
        
        let confidenceSum = 0;
        let confidenceCount = 0;
        
        // Traiter les prévisions d'achat
        if (buyForecast && buyForecast.length > 0) {
            buyForecast.forEach(point => {
                if (!labels.includes(point.date)) {
                    labels.push(point.date);
                }
                buyPrices.push(point.price);
                buyLow.push(point.lowEstimate);
                buyHigh.push(point.highEstimate);
                
                if (point.confidence) {
                    confidenceSum += point.confidence;
                    confidenceCount++;
                }
            });
        }
        
        // Traiter les prévisions de vente
        if (sellForecast && sellForecast.length > 0) {
            sellForecast.forEach((point, i) => {
                if (i >= labels.length) {
                    labels.push(point.date);
                }
                sellPrices.push(point.price);
                sellLow.push(point.lowEstimate);
                sellHigh.push(point.highEstimate);
                
                if (point.confidence) {
                    confidenceSum += point.confidence;
                    confidenceCount++;
                }
            });
        }
        
        // Mettre à jour la valeur de confiance
        const avgConfidence = confidenceCount > 0 ? (confidenceSum / confidenceCount) : 0;
        document.getElementById('forecast-confidence-value').textContent = `${Math.round(avgConfidence * 100)}%`;
        
        // Destruction du graphique existant si présent
        if (this.forecastChart) {
            this.forecastChart.destroy();
        }

        // // Créer un plugin pour synchroniser la visibilité des datasets
        // const synchronizeVisibility = {
        //     id: 'synchronizeVisibility',
        //     beforeDraw: (chart) => {
        //         const datasets = chart.data.datasets;
                
        //         // Pour les datasets d'achat
        //         if (datasets.length > 2) {
        //             const meta0 = chart.getDatasetMeta(0); // Prévision achat
        //             const meta1 = chart.getDatasetMeta(1); // Max achat
        //             const meta2 = chart.getDatasetMeta(2); // Min achat
                    
        //             // Si la ligne principale est cachée, cacher les zones
        //             if (meta0.hidden) {
        //                 meta1.hidden = true;
        //                 meta2.hidden = true;
        //             } 
        //             // Si la ligne principale est visible, montrer les zones
        //             else {
        //                 meta1.hidden = false;
        //                 meta2.hidden = false;
        //             }
        //         }
                
        //         // Pour les datasets de vente
        //         if (datasets.length > 5) {
        //             const meta3 = chart.getDatasetMeta(3); // Prévision vente
        //             const meta4 = chart.getDatasetMeta(4); // Max vente
        //             const meta5 = chart.getDatasetMeta(5); // Min vente
                    
        //             // Si la ligne principale est cachée, cacher les zones
        //             if (meta3.hidden) {
        //                 meta4.hidden = true;
        //                 meta5.hidden = true;
        //             } 
        //             // Si la ligne principale est visible, montrer les zones
        //             else {
        //                 meta4.hidden = false;
        //                 meta5.hidden = false;
        //             }
        //         }
        //     }
        // };
        
        // Au lieu d'utiliser un plugin, utilisez l'API d'événements de Chart.js
        const legendClickHandler = {
            id: 'legendClickHandler',
            beforeInit(chart) {
                // Stocker le gestionnaire d'événements original
                const originalClick = chart.options.plugins.legend.onClick;
                
                // Remplacer par notre gestionnaire personnalisé
                chart.options.plugins.legend.onClick = function(e, legendItem, legend) {
                    // Appeler le gestionnaire d'origine pour basculer la visibilité
                    originalClick.call(this, e, legendItem, legend);
                    
                    // Déterminer quel groupe de datasets est affecté
                    const index = legendItem.datasetIndex;
                    const isPurchaseGroup = index === 0; // Prévision achat
                    const isSellGroup = index === 3; // Prévision vente (si présent)
                    
                    // Obtenir l'état de visibilité actuel après le clic
                    const meta = chart.getDatasetMeta(index);
                    const isVisible = !meta.hidden;
                    
                    // Synchroniser la visibilité des zones Min/Max
                    if (isPurchaseGroup) {
                        chart.getDatasetMeta(1).hidden = !isVisible; // Max achat
                        chart.getDatasetMeta(2).hidden = !isVisible; // Min achat
                    } else if (isSellGroup && chart.getDatasetMeta(4)) {
                        chart.getDatasetMeta(4).hidden = !isVisible; // Max vente
                        chart.getDatasetMeta(5).hidden = !isVisible; // Min vente
                    }
                    
                    // Forcer la mise à jour du graphique
                    chart.update();
                };
            }
        };
        
        // Création du graphique
        this.forecastChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: labels.map(date => luxon.DateTime.fromISO(date).toFormat('dd/MM')),
                datasets: [
                    // Données d'achat
                    ...(buyPrices.length > 0 ? [
                        {
                            label: `${getTranslation("forecast.predictedBuyPrice")}`,
                            data: buyPrices,
                            borderColor: this.colors.buy,
                            backgroundColor: 'transparent',
                            borderWidth: 2,
                            tension: 0.1,
                            pointStyle: 'circle',
                            pointRadius: 4
                        },
                        {
                            label: `${getTranslation("forecast.maxBuyPrice")}`,
                            data: buyHigh,
                            borderColor: 'transparent',
                            backgroundColor: 'rgba(244, 67, 54, 0.2)',
                            borderWidth: 0,
                            tension: 0.1,
                            pointStyle: 'circle',
                            pointRadius: 0,
                            fill: '+1'
                        },
                        {
                            label: `${getTranslation("forecast.minBuyPrice")}`,
                            data: buyLow,
                            borderColor: 'transparent',
                            backgroundColor: 'transparent',
                            borderWidth: 0,
                            tension: 0.1,
                            pointStyle: 'circle',
                            pointRadius: 0,
                            fill: false
                        }
                    ] : []),
                    
                    // Données de vente
                    ...(sellPrices.length > 0 ? [
                        {
                            label: `${getTranslation("forecast.predictedSellPrice")}`,
                            data: sellPrices,
                            borderColor: this.colors.sell,
                            backgroundColor: 'transparent',
                            borderWidth: 2,
                            tension: 0.1,
                            pointStyle: 'triangle',
                            pointRadius: 4
                        },
                        {
                            label: `${getTranslation("forecast.maxSellPrice")}`,
                            data: sellHigh,
                            borderColor: 'transparent',
                            backgroundColor: 'rgba(76, 175, 80, 0.2)',
                            borderWidth: 0,
                            tension: 0.1,
                            pointStyle: 'circle',
                            pointRadius: 0,
                            fill: '+1'
                        },
                        {
                            label: `${getTranslation("forecast.minSellPrice")}`,
                            data: sellLow,
                            borderColor: 'transparent',
                            backgroundColor: 'transparent',
                            borderWidth: 0,
                            tension: 0.1,
                            pointStyle: 'circle',
                            pointRadius: 0,
                            fill: false
                        }
                    ] : [])
                ]
            },
            options: {
                responsive: true,
                plugins: {
                    legend: {
                        position: 'top',
                        labels: {
                            filter: item => {
                                // N'afficher que les lignes principales dans la légende
                                return item.text.includes(`${getTranslation("forecast.predicted")}`);
                            }
                        }
                    },
                    tooltip: {
                        mode: 'index',
                        intersect: false
                    }
                },
                scales: {
                    y: {
                        beginAtZero: false
                    }
                }
            },
            plugins: [legendClickHandler]
        });
    }
    
    /**
     * Affiche les signaux de trading
     */
    displayTradingSignals(data) {
        const container = document.getElementById('signals-container');
        if (!container) return;
        
        const buyAnalysis = data.buyAnalysis;
        const sellAnalysis = data.sellAnalysis;
        
        // Vérifier si nous avons des signaux
        const buySignals = buyAnalysis && buyAnalysis.signals ? buyAnalysis.signals : [];
        const sellSignals = sellAnalysis && sellAnalysis.signals ? sellAnalysis.signals : [];
        
        if (buySignals.length === 0 && sellSignals.length === 0) {
            container.innerHTML = `<div class="no-data-message">${getTranslation("TradingSignals.noData")}</div>`;
            return;
        }
        
        // Construire le HTML pour les signaux
        let html = `<h3>${getTranslation("TradingSignals.title")}</h3>`;

        // Signaux d'achat
        if (buySignals.length > 0) {
            html += `<div class="signals-section buy-signals">`;
            html += `<h4>${getTranslation("TradingSignals.signals.buy")}</h4>`;
            html += `<ul class="signals-list">`;

            buySignals.forEach(signal => {
                const signalClass = this.getSignalClass(signal.type);
                html += `
                    <li class="signal-item ${signalClass}">
                        <div class="signal-icon"><i class="${this.getSignalIcon(signal.type)}"></i></div>
                        <div class="signal-content">
                            <div class="signal-title">${this.getSignalTitle(signal.type)}</div>
                            <div class="signal-description">${signal.description}</div>
                            <div class="signal-time">${luxon.DateTime.fromISO(signal.timestamp).toFormat('dd/MM/yyyy HH:mm')}</div>
                        </div>
                    </li>
                `;
            });
            
            html += '</ul></div>';
        }
        
        // Signaux de vente
        if (sellSignals.length > 0) {
            html += '<div class="signals-section sell-signals">';
            html += `<h4>${getTranslation("TradingSignals.signals.sell")}</h4>`;
            html += '<ul class="signals-list">';
            
            sellSignals.forEach(signal => {
                const signalClass = this.getSignalClass(signal.type);
                html += `
                    <li class="signal-item ${signalClass}">
                        <div class="signal-icon"><i class="${this.getSignalIcon(signal.type)}"></i></div>
                        <div class="signal-content">
                            <div class="signal-title">${this.getSignalTitle(signal.type)}</div>
                            <div class="signal-description">${signal.description}</div>
                            <div class="signal-time">${luxon.DateTime.fromISO(signal.timestamp).toFormat('dd/MM/yyyy HH:mm')}</div>
                        </div>
                    </li>
                `;
            });
            
            html += '</ul></div>';
        }
        
        container.innerHTML = html;
    }
    
    /**
     * Affiche les niveaux de support et résistance
     */
    displaySupportResistance(data) {
        const container = document.getElementById('support-resistance-container');
        if (!container) return;
        
        const buySupportLevels = data.buySupportLevels || [];
        const buyResistanceLevels = data.buyResistanceLevels || [];
        const sellSupportLevels = data.sellSupportLevels || [];
        const sellResistanceLevels = data.sellResistanceLevels || [];
        
        if (buySupportLevels.length === 0 && buyResistanceLevels.length === 0 && 
            sellSupportLevels.length === 0 && sellResistanceLevels.length === 0) {
            container.innerHTML = `<div class="no-data-message">${getTranslation("SupportResistance.noData")}</div>`;
            return;
        }
        
        // Construire le HTML pour les niveaux
        let html = `<h3>${getTranslation("SupportResistance.title")}</h3>`;
        
        // Niveaux pour les prix d'achat
        if (buySupportLevels.length > 0 || buyResistanceLevels.length > 0) {
            html += `<div class="levels-section buy-levels">`;
            html += `<h4>${getTranslation("SupportResistance.buy")}</h4>`;
            
            html += '<div class="levels-container">';
            
            // Résistances
            if (buyResistanceLevels.length > 0) {
                html += '<div class="resistance-levels">';
                html += `<h5>${getTranslation("SupportResistance.resistance")}</h5>`;
                html += '<ul>';
                
                buyResistanceLevels
                    .sort((a, b) => b - a) // Trier du plus haut au plus bas
                    .forEach(level => {
                        html += `<li>${level.toFixed(2)}</li>`;
                    });
                
                html += '</ul></div>';
            }
            
            // Supports
            if (buySupportLevels.length > 0) {
                html += '<div class="support-levels">';
                html += `<h5>${getTranslation("SupportResistance.support")}</h5>`;
                html += '<ul>';
                
                buySupportLevels
                    .sort((a, b) => b - a) // Trier du plus haut au plus bas
                    .forEach(level => {
                        html += `<li>${level.toFixed(2)}</li>`;
                    });
                
                html += '</ul></div>';
            }
            
            html += '</div></div>';
        }
        
        // Niveaux pour les prix de vente
        if (sellSupportLevels.length > 0 || sellResistanceLevels.length > 0) {
            html += '<div class="levels-section sell-levels">';
            html += `<h4>${getTranslation("SupportResistance.sell")}</h4>`;
            
            html += '<div class="levels-container">';
            
            // Résistances
            if (sellResistanceLevels.length > 0) {
                html += '<div class="resistance-levels">';
                html += `<h5>${getTranslation("SupportResistance.resistance")}</h5>`;
                html += '<ul>';
                
                sellResistanceLevels
                    .sort((a, b) => b - a) // Trier du plus haut au plus bas
                    .forEach(level => {
                        html += `<li>${level.toFixed(2)}</li>`;
                    });
                
                html += '</ul></div>';
            }
            
            // Supports
            if (sellSupportLevels.length > 0) {
                html += '<div class="support-levels">';
                html += `<h5>${getTranslation("SupportResistance.support")}</h5>`;
                html += '<ul>';
                
                sellSupportLevels
                    .sort((a, b) => b - a) // Trier du plus haut au plus bas
                    .forEach(level => {
                        html += `<li>${level.toFixed(2)}</li>`;
                    });
                
                html += '</ul></div>';
            }
            
            html += '</div></div>';
        }
        
        container.innerHTML = html;
    }
    
    // Méthodes utilitaires
    
    /**
     * Convertit une période en nombre de jours
     */
    getPeriodDays(period) {
        switch (period) {
            case '1h': return 1;
            case '6h': return 1;
            case '12h': return 1;
            case '1d': return 1;
            case '1w': return 7;
            case '1m': return 30;
            default: return 7;
        }
    }
    
    /**
     * Retourne une classe CSS pour un changement de valeur
     */
    getChangeClass(change) {
        if (change > 0) return 'positive';
        if (change < 0) return 'negative';
        return 'neutral';
    }
    getChangeBuyClass(change) {
        if (change > 0) return 'positive';
        if (change < 0) return 'negative';
        return 'neutral';
    }
    getChangeSellClass(change) {
        if (change > 0) return 'negative';
        if (change < 0) return 'positive';
        return 'neutral';
    }
    
    /**
     * Formate un pourcentage de changement
     */
    formatChange(change) {
        if (change === undefined || change === null) return '--';
        return `${change > 0 ? '+' : ''}${change.toFixed(2)}%`;
    }
    
    /**
     * Retourne le libellé d'une tendance
     */
    getTrendLabel(trend) {
        switch (trend) {
            case 'RISING': return getTranslation('forecast.uptrend');
            case 'FALLING': return getTranslation('forecast.downtrend');
            case 'STABLE': return getTranslation('forecast.stable');
            case 'VOLATILE': return getTranslation('forecast.volatile');
            case 'SPREAD_CHANGING': return getTranslation('forecast.spread_changing');
            default: return 'Unknown';
        }
    }
    
    /**
     * Retourne une classe CSS pour un type de signal
     */
    getSignalClass(signalType) {
        switch (signalType) {
            case 'GOLDEN_CROSS': return 'bullish strong';
            case 'DEATH_CROSS': return 'bearish strong';
            case 'OVERBOUGHT': return 'bearish';
            case 'OVERSOLD': return 'bullish';
            case 'MACD_BULLISH_CROSS': return 'bullish';
            case 'MACD_BEARISH_CROSS': return 'bearish';
            case 'PRICE_ABOVE_UPPER_BAND': return 'bearish';
            case 'PRICE_BELOW_LOWER_BAND': return 'bullish';
            default: return 'neutral';
        }
    }
    
    /**
     * Retourne une icône pour un type de signal
     */
    getSignalIcon(signalType) {
        switch (signalType) {
            case 'GOLDEN_CROSS': return 'fas fa-arrow-trend-up';
            case 'DEATH_CROSS': return 'fas fa-arrow-trend-down';
            case 'OVERBOUGHT': return 'fas fa-temperature-high';
            case 'OVERSOLD': return 'fas fa-temperature-low';
            case 'MACD_BULLISH_CROSS': return 'fas fa-chart-line';
            case 'MACD_BEARISH_CROSS': return 'fas fa-chart-line fa-flip-vertical';
            case 'PRICE_ABOVE_UPPER_BAND': return 'fas fa-arrow-up';
            case 'PRICE_BELOW_LOWER_BAND': return 'fas fa-arrow-down';
            default: return 'fas fa-question';
        }
    }
    
    /**
     * Retourne un titre pour un type de signal
     */
    getSignalTitle(signalType) {
        switch (signalType) {
            case 'GOLDEN_CROSS': return `${getTranslation('signal.bullish')} (Golden Cross)`;
            case 'DEATH_CROSS': return `${getTranslation('signal.bearish')} (Death Cross)`;
            case 'OVERBOUGHT': return `${getTranslation('signal.overbought')} (RSI > 70)`;
            case 'OVERSOLD': return `${getTranslation('signal.oversold')} (RSI < 30)`;
            case 'MACD_BULLISH_CROSS': return `${getTranslation('signal.bullish')} (MACD)`;
            case 'MACD_BEARISH_CROSS': return `${getTranslation('signal.bearish')} (MACD)`;
            case 'PRICE_ABOVE_UPPER_BAND': return `${getTranslation('signal.price_above_upper_band')}`;
            case 'PRICE_BELOW_LOWER_BAND': return `${getTranslation('signal.price_below_lower_band')}`;
            default: return `${getTranslation('signal.unknown_signal')}`;
        }
    }
}