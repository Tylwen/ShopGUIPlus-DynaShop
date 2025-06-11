// Variables globales
let priceChart = null;
let volumeChart = null;
let candlestickChart = null;
let currentShop = null;
let currentItem = null;
let currentPeriod = '1m';
let currentTheme = localStorage.getItem('theme') || 'light';
let autoRefreshInterval = localStorage.getItem('refreshInterval') || 'disabled';
let refreshTimerId = null;

// Initialisation de la page
document.addEventListener('DOMContentLoaded', function() {
    // Mettre à jour l'heure
    updateCurrentTime();
    // Mettre à jour toutes les 60 secondes
    // setInterval(updateCurrentTime, 60000); // Mettre à jour toutes les 60 secondes
    setInterval(updateCurrentTime, 1000); // Mettre à jour toutes les secondes

    // Charger les shops
    loadShops();
    
    // Initialiser le thème
    initTheme();
    
    // Configurer les événements
    document.getElementById('shop-select').addEventListener('change', onShopChange);
    document.getElementById('item-select').addEventListener('change', onItemChange);
    document.getElementById('period-select').addEventListener('change', onPeriodChange);
    document.getElementById('theme-switch').addEventListener('click', toggleTheme);
    window.addEventListener('beforeunload', () => {stopAutoRefresh();});

    // Charger les paramètres de l'URL si présents
    loadParamsFromUrl();

    // Configurer l'actualisation automatique
    const refreshSelect = document.getElementById('refresh-interval');
    if (refreshSelect) {
        refreshSelect.value = autoRefreshInterval;
        refreshSelect.addEventListener('change', (e) => {
            changeRefreshInterval(e.target.value);
        });
    }

    // Démarrer l'actualisation automatique
    startAutoRefresh();
});

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
        document.getElementById('theme-switch').innerHTML = '<i class="fas fa-sun"></i><span>Mode clair</span>';
    } else {
        document.documentElement.setAttribute('data-theme', 'light');
        document.getElementById('theme-switch').innerHTML = '<i class="fas fa-moon"></i><span>Mode sombre</span>';
    }
    
    // Mettre à jour les graphiques avec le thème actuel
    updateChartsTheme();
}

// Changement de thème
function toggleTheme() {
    if (currentTheme === 'light') {
        currentTheme = 'dark';
        document.documentElement.setAttribute('data-theme', 'dark');
        document.getElementById('theme-switch').innerHTML = '<i class="fas fa-sun"></i><span>Mode clair</span>';
    } else {
        currentTheme = 'light';
        document.documentElement.setAttribute('data-theme', 'light');
        document.getElementById('theme-switch').innerHTML = '<i class="fas fa-moon"></i><span>Mode sombre</span>';
    }
    
    // Sauvegarder la préférence
    localStorage.setItem('theme', currentTheme);
    
    // Mettre à jour les graphiques
    updateChartsTheme();
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
    
    // if (candlestickChart) {
    //     // Et pour le graphique en chandelier
    //     candlestickChart.options.scales.x.grid.color = gridColor;
    //     candlestickChart.options.scales['y-acheter'].grid.color = gridColor;
    //     candlestickChart.options.scales['y-vendre'].grid.color = gridColor;
        
    //     candlestickChart.options.scales.x.ticks.color = textColor;
    //     candlestickChart.options.scales['y-acheter'].ticks.color = textColor;
    //     candlestickChart.options.scales['y-vendre'].ticks.color = textColor;
        
    //     candlestickChart.options.scales.x.title.color = textColor;
    //     candlestickChart.options.scales['y-acheter'].title.color = textColor;
    //     candlestickChart.options.scales['y-vendre'].title.color = textColor;
        
    //     // Mettre à jour les couleurs de légende
    //     if (candlestickChart.options.plugins && candlestickChart.options.plugins.legend) {
    //         candlestickChart.options.plugins.legend.labels.color = textColor;
    //     }
        
    //     candlestickChart.update();
    // }
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
            shopSelect.innerHTML = '<option value="">Choisir une boutique</option>';
            
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
            enhanceSelectWithSearch('shop-select', "Rechercher une boutique...");
            
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
        itemSelect.innerHTML = '<option value="">Choisir un item</option>';
        itemSelect.disabled = false;
        
        items.forEach(item => {
            const option = document.createElement('option');
            option.value = item.id;
            option.textContent = item.name || item.id;
            itemSelect.appendChild(option);
        });
        
        // Ajouter le champ de recherche pour ce select
        enhanceSelectWithSearch('item-select', "Rechercher un item...");
        
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
        const dataResponse = await fetch(
            `/api/prices?shop=${encodeURIComponent(shopId)}&item=${encodeURIComponent(itemId)}` +
            `&period=${period}&granularity=${granularity}&maxPoints=${maxPoints}`
        );
        
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
        switch(type) {
            case 'DYNAMIC': return 'Dynamique';
            case 'STOCK': return 'Stock (prix évolutif)';
            case 'STATIC_STOCK': return 'Stock (prix fixe)';
            case 'RECIPE': return 'Recette';
            case 'LINK': return 'Lié à un autre item';
            case 'NONE': return 'Statique';
            default: return type || 'Unknown';
        }
    };
    
    // Déterminer si des liens sont présents
    const hasLinks = typeData.buy === 'LINK' || typeData.sell === 'LINK' || typeData.general === 'LINK';
    
    // Formatage avec couleurs selon le type
    let html = '';
    
    // Affichage différent si les types d'achat et vente sont différents
    if (typeData.buy !== typeData.sell) {
        html = `<span class="type-buy">Achat: ${getTypeDescription(typeData.buy)}</span>`;
        
        // Ajouter le type réel si différent et si c'est un LINK
        if (typeData.buy === 'LINK' && typeData.realBuy && typeData.realBuy !== 'LINK') {
            html += ` <small>(réellement ${getTypeDescription(typeData.realBuy)})</small>`;
        }
        
        html += `<br><span class="type-sell">Vente: ${getTypeDescription(typeData.sell)}</span>`;
        
        // Ajouter le type réel si différent et si c'est un LINK
        if (typeData.sell === 'LINK' && typeData.realSell && typeData.realSell !== 'LINK') {
            html += ` <small>(réellement ${getTypeDescription(typeData.realSell)})</small>`;
        }
    } else {
        html = getTypeDescription(typeData.general);
        
        // Ajouter le type réel si c'est un LINK
        if (typeData.general === 'LINK' && typeData.realBuy && typeData.realSell) {
            if (typeData.realBuy === typeData.realSell) {
                html += ` <small>(réellement ${getTypeDescription(typeData.realBuy)})</small>`;
            } else {
                html += `<br><small>(réellement Achat: ${getTypeDescription(typeData.realBuy)}, 
                       Vente: ${getTypeDescription(typeData.realSell)})</small>`;
            }
        }
    }
    
    // Enlever l'indication générique pour les LINK puisque nous affichons maintenant le type réel
    shopTypeElement.innerHTML = html;
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
    
    // Extraire et formater les données
    const chartData = data.map(point => ({
        x: luxon.DateTime.fromISO(point.timestamp),
        buy: point.closeBuy,
        sell: point.closeSell
    }));
    // const chartData = aggregatedPriceData(data); // Utiliser la fonction d'agrégation
    
    // Détruire le graphique existant s'il existe
    if (priceChart) {
        priceChart.destroy();
    }
    
    // Créer un nouveau graphique
    priceChart = new Chart(ctx, {
        type: 'line',
        data: {
            datasets: [
                {
                    label: 'Prix d\'achat',
                    data: chartData.map(item => ({ x: item.x, y: item.buy })),
                    borderColor: 'rgba(255, 99, 132, 1)',
                    backgroundColor: 'rgba(255, 99, 132, 0.2)',
                    borderWidth: 2,
                    tension: 0.1
                },
                {
                    label: 'Prix de vente',
                    data: chartData.map(item => ({ x: item.x, y: item.sell })),
                    borderColor: 'rgba(54, 162, 235, 1)',
                    backgroundColor: 'rgba(54, 162, 235, 0.2)',
                    borderWidth: 2,
                    tension: 0.1
                }
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
                        // unit: determineTimeUnit(data.length),
                        unit: 'hour',
                        tooltipFormat: 'dd/MM/yyyy HH:mm',
                        // displayFormats: {
                        //     hour: 'HH:mm',
                        //     day: 'dd/MM'
                        // }
                    },
                    title: {
                        display: true,
                        text: 'Date/Heure'
                    }
                    // type: 'category',
                    // title: {
                    //     display: true,
                    //     text: 'Date/Heure'
                    // },
                },
                y: {
                    title: {
                        display: true,
                        text: 'Prix'
                    },
                    beginAtZero: false
                }
            }
        }
    });
}

function normalizeVolume(volume) {
    if (!volume || volume <= 0) return 0;
    // Pour rendre les petites valeurs plus visibles
    return Math.max(0.5, volume);
}

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
                label: 'Volume',
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
                    type: 'category',
                    title: {
                        display: true,
                        text: 'Date/Heure'
                    },
                    ticks: {
                        maxTicksLimit: 10,
                        autoSkip: true
                    }
                },
                y: {
                    title: {
                        display: true,
                        text: 'Volume'
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

// Mise à jour des statistiques
function updateStats(chartData, statsData) {
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
            document.getElementById('current-price').textContent = 'Vente: ' + sellPrice;
        } else if (rawBuyPrice !== -1 && rawSellPrice === -1) {
            document.getElementById('current-price').textContent = 'Achat: ' + buyPrice;
        } else {
            document.getElementById('current-price').innerHTML = 
                `Achat: <span class="buy-price">${buyPrice}</span><br />` +
                `Vente: <span class="sell-price">${sellPrice}</span>`;
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
            document.getElementById('current-price').textContent = 'Vente: ' + sellPrice;
        } else if (rawBuyPrice !== -1 && rawSellPrice === -1) {
            document.getElementById('current-price').textContent = 'Achat: ' + buyPrice;
        } else {
            document.getElementById('current-price').innerHTML = 
                `Achat: <span class="buy-price">${buyPrice}</span><br />` +
                `Vente: <span class="sell-price">${sellPrice}</span>`;
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
        if (buyChange === sellChange || buyChange === '--' && sellChange === '--') {
            element.textContent = `${buyChange === '--' ? '--' : buyChange + '%'}`;
            element.style.color = buyChange >= 0 && buyChange !== '--' ? 'green' : 'red';
        } else if (buyChange === '--' && sellChange !== '--') {
            element.innerHTML = `Vente: <span style="color:${sellChange >= 0 ? 'green' : 'red'}">${sellChange}%</span>`;
        } else if (buyChange !== '--' && sellChange === '--') {
            element.innerHTML = `Achat: <span style="color:${buyChange >= 0 ? 'green' : 'red'}">${buyChange}%</span>`;
        } else {
            element.innerHTML = 
                `Achat: <span style="color:${buyChange >= 0 ? 'green' : 'red'}">${buyChange}%</span><br />` +
                `Vente: <span style="color:${sellChange >= 0 ? 'green' : 'red'}">${sellChange}%</span>`;
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
                statsData.timeSpanHours ? `${statsData.timeSpanHours} heures` : '--';
        }
    }
}

// Formatage de la date/heure
function formatTimestamp(timestamp) {
    return luxon.DateTime.fromISO(timestamp).toLocaleString(luxon.DateTime.DATETIME_MED);
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
        document.getElementById('item-select').innerHTML = '<option value="">Choisir un item</option>';
    }
}

// Événement de changement d'item
function onItemChange(event) {
    const itemId = event.target.value;
    if (itemId && currentShop) {
        currentItem = itemId;
        loadPriceData(currentShop, itemId, currentPeriod);
        
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