// Variables globales
let priceChart = null;
let volumeChart = null;
let candlestickChart = null;
let currentShop = null;
let currentItem = null;
let currentPeriod = '1d';

// Initialisation de la page
document.addEventListener('DOMContentLoaded', function() {
    // Mettre à jour l'heure
    updateCurrentTime();
    // Mettre à jour toutes les 60 secondes
    setInterval(updateCurrentTime, 60000); // Mettre à jour toutes les 60 secondes

    // Charger les shops
    loadShops();
    
    // Configurer les événements
    document.getElementById('shop-select').addEventListener('change', onShopChange);
    document.getElementById('item-select').addEventListener('change', onItemChange);
    document.getElementById('period-select').addEventListener('change', onPeriodChange);
    
    // Charger les paramètres de l'URL si présents
    loadParamsFromUrl();
});

// Mise à jour de l'heure
function updateCurrentTime() {
    const now = new Date();
    document.getElementById('current-time').textContent = now.toLocaleString();
}

// Chargement des shops
// async function loadShops() {
//     try {
//         const response = await fetch('/api/shops');
//         const shops = await response.json();
        
//         const shopSelect = document.getElementById('shop-select');
//         shopSelect.innerHTML = '<option value="">Choisir une boutique</option>';
        
//         shops.forEach(shop => {
//             const option = document.createElement('option');
//             option.value = shop;
//             option.textContent = shop;
//             shopSelect.appendChild(option);
//         });
//     } catch (error) {
//         console.error("Erreur lors du chargement des shops:", error);
//     }
// }
async function loadShops() {
    fetch('/api/shops')
        .then(response => response.json())
        .then(shops => {
            const shopSelect = document.getElementById('shop-select');
            
            // Vider la liste actuelle (sauf la première option)
            while (shopSelect.options.length > 1) {
                shopSelect.remove(1);
            }
            
            // Trier les boutiques par nom
            shops.sort((a, b) => a.name.localeCompare(b.name));
            
            // Ajouter les options de boutiques
            shops.forEach(shop => {
                const option = document.createElement('option');
                option.value = shop.id;  // L'ID comme valeur
                option.textContent = shop.name;  // Le nom comme texte affiché
                shopSelect.appendChild(option);
            });
            
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

// Chargement des items d'un shop
async function loadItems(shopId) {
    try {
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
    } catch (error) {
        console.error("Erreur lors du chargement des items:", error);
    }
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
    
    // Mettre à jour les graphiques
    updatePriceChart(processedData);
    updateVolumeChart(processedData);
    updateCandlestickChart(processedData);
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
                        unit: determineTimeUnit(data.length),
                        tooltipFormat: 'dd/MM/yyyy HH:mm',
                        displayFormats: {
                            hour: 'HH:mm',
                            day: 'dd/MM'
                        }
                    },
                    title: {
                        display: true,
                        text: 'Date/Heure'
                    }
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

// function updateCandlestickChart(data) {
//     const ctx = document.getElementById('candlestick-chart').getContext('2d');
    
//     // Convertir les données OHLC en format simplifié
//     const highData = data.map(point => ({
//         x: luxon.DateTime.fromISO(point.timestamp),
//         y: point.highBuy
//     }));
    
//     const lowData = data.map(point => ({
//         x: luxon.DateTime.fromISO(point.timestamp),
//         y: point.lowBuy
//     }));

//     // Détruire le graphique existant s'il existe
//     if (candlestickChart) {
//         candlestickChart.destroy();
//     }
    
//     // Utiliser un graphique en ligne à la place
//     candlestickChart = new Chart(ctx, {
//         type: 'line',
//         data: {
//             datasets: [
//                 {
//                     label: 'Prix haut',
//                     data: highData,
//                     borderColor: 'rgba(75, 192, 75, 1)',
//                     backgroundColor: 'rgba(75, 192, 75, 0.2)',
//                     borderWidth: 1,
//                     fill: false
//                 },
//                 {
//                     label: 'Prix bas',
//                     data: lowData,
//                     borderColor: 'rgba(255, 99, 132, 1)',
//                     backgroundColor: 'rgba(255, 99, 132, 0.2)',
//                     borderWidth: 1,
//                     fill: false
//                 }
//             ]
//         },
//         options: {
//             responsive: true,
//             scales: {
//                 x: {
//                     type: 'time',
//                     time: {
//                         unit: determineTimeUnit(data.length)
//                     },
//                     title: {
//                         display: true,
//                         text: 'Date/Heure'
//                     }
//                 },
//                 y: {
//                     title: {
//                         display: true,
//                         text: 'Prix'
//                     },
//                     beginAtZero: false
//                 }
//             }
//         }
//     });
// }

// Mise à jour du graphique de volume
function updateVolumeChart(data) {
    const ctx = document.getElementById('volume-chart').getContext('2d');
    
    // Extraire et formater les données
    const volumeData = data.map(point => ({
        x: luxon.DateTime.fromISO(point.timestamp),
        y: point.volume
    }));
    
    // Détruire le graphique existant s'il existe
    if (volumeChart) {
        volumeChart.destroy();
    }
    
    // Créer un nouveau graphique
    volumeChart = new Chart(ctx, {
        type: 'bar',
        data: {
            datasets: [
                {
                    label: 'Volume',
                    data: volumeData,
                    backgroundColor: 'rgba(75, 192, 192, 0.6)',
                    borderColor: 'rgba(75, 192, 192, 1)',
                    borderWidth: 1
                }
            ]
        },
        options: {
            responsive: true,
            scales: {
                x: {
                    type: 'time',
                    time: {
                        unit: determineTimeUnit(data.length),
                        tooltipFormat: 'dd/MM/yyyy HH:mm',
                        displayFormats: {
                            hour: 'HH:mm',
                            day: 'dd/MM'
                        }
                    },
                    title: {
                        display: true,
                        text: 'Date/Heure'
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

// Mise à jour du graphique en chandeliers
function updateCandlestickChart(data) {
    const ctx = document.getElementById('candlestick-chart').getContext('2d');
    
    // Créer des structures de données pour les chandeliers d'achat (buy)
    const ohlcBuy = data.map(point => ({
        x: luxon.DateTime.fromISO(point.timestamp),
        o: point.openBuy,
        h: point.highBuy,
        l: point.lowBuy,
        c: point.closeBuy
    }));
    
    // Créer des structures de données pour les chandeliers de vente (sell)
    const ohlcSell = data.map(point => ({
        x: luxon.DateTime.fromISO(point.timestamp),
        o: point.openSell,
        h: point.highSell,
        l: point.lowSell,
        c: point.closeSell
    }));
    
    // Détruire le graphique existant s'il existe
    if (candlestickChart) {
        candlestickChart.destroy();
    }
    
    // Créer un nouveau graphique en chandeliers avec deux datasets
    candlestickChart = new Chart(ctx, {
        type: 'candlestick',
        data: {
            datasets: [
                {
                    label: 'Prix d\'achat (OHLC)',
                    data: ohlcBuy,
                    color: {
                        up: 'rgba(75, 192, 75, 1)',    // vert pour hausse
                        down: 'rgba(255, 99, 132, 1)',  // rouge pour baisse
                        unchanged: 'rgba(90, 90, 90, 1)', // gris pour stable
                    },
                    borderColor: 'rgba(0, 0, 0, 0.1)',
                    yAxisID: 'y-acheter'
                },
                {
                    label: 'Prix de vente (OHLC)',
                    data: ohlcSell,
                    color: {
                        up: 'rgba(54, 162, 235, 0.8)',    // bleu pour hausse
                        down: 'rgba(255, 159, 64, 0.8)',  // orange pour baisse
                        unchanged: 'rgba(153, 102, 255, 0.8)', // violet pour stable
                    },
                    borderColor: 'rgba(0, 0, 0, 0.1)',
                    yAxisID: 'y-vendre'
                }
            ]
        },
        options: {
            responsive: true,
            interaction: {
                mode: 'index',
                intersect: false
            },
            scales: {
                x: {
                    type: 'time',
                    time: {
                        unit: determineTimeUnit(data.length),
                        tooltipFormat: 'dd/MM/yyyy HH:mm',
                        displayFormats: {
                            hour: 'HH:mm',
                            day: 'dd/MM'
                        }
                    },
                    title: {
                        display: true,
                        text: 'Date/Heure'
                    }
                },
                'y-acheter': {
                    position: 'left',
                    title: {
                        display: true,
                        text: 'Prix d\'achat'
                    },
                    beginAtZero: false
                },
                'y-vendre': {
                    position: 'right',
                    title: {
                        display: true,
                        text: 'Prix de vente'
                    },
                    beginAtZero: false,
                    grid: {
                        drawOnChartArea: false // uniquement les lignes de grille pour l'axe principal
                    }
                }
            },
            plugins: {
                legend: {
                    labels: {
                        usePointStyle: true,
                        generateLabels: function(chart) {
                            const datasets = chart.data.datasets;
                            return datasets.map((dataset, i) => ({
                                text: dataset.label,
                                fillStyle: i === 0 ? 'rgba(75, 192, 75, 1)' : 'rgba(54, 162, 235, 0.8)',
                                hidden: !chart.isDatasetVisible(i),
                                datasetIndex: i
                            }));
                        }
                    }
                },
                tooltip: {
                    callbacks: {
                        label: function(context) {
                            const point = context.raw;
                            const datasetLabel = context.dataset.label || '';
                            return [
                                `${datasetLabel}:`,
                                `Ouverture: ${point.o?.toFixed(2) || 'N/A'}`,
                                `Haut: ${point.h?.toFixed(2) || 'N/A'}`,
                                `Bas: ${point.l?.toFixed(2) || 'N/A'}`,
                                `Fermeture: ${point.c?.toFixed(2) || 'N/A'}`
                            ];
                        }
                    }
                }
            }
        }
    });
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