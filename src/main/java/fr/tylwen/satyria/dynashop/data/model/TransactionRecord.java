/*
 * ShopGUI+ DynaShop - Dynamic Economy Addon for Minecraft
 * Copyright (C) 2025 Tylwen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package fr.tylwen.satyria.dynashop.data.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Classe qui représente une transaction d'achat ou de vente
 * Utilisée par tous les systèmes de stockage et de limite
 */
public class TransactionRecord {
    private UUID playerUuid;
    private String playerName;
    private String shopId;
    private String itemId;
    private String itemName;
    private boolean isBuy;
    private int quantity;
    private double unitPrice;
    private double totalPrice;
    private LocalDateTime timestamp;
    private long id;

    /**
     * Constructeur pour les nouvelles transactions
     */
    public TransactionRecord(UUID playerUuid, String shopId, String itemId, 
                           boolean isBuy, int quantity, LocalDateTime timestamp) {
        this.playerUuid = playerUuid;
        this.shopId = shopId;
        this.itemId = itemId;
        this.isBuy = isBuy;
        this.quantity = quantity;
        this.timestamp = timestamp;
    }
    
    /**
     * Constructeur pour les transactions avec plus de détails
     */
    public TransactionRecord(UUID playerUuid, String playerName, String shopId, String itemId, String itemName,
                           boolean isBuy, int quantity, double unitPrice, LocalDateTime timestamp) {
        this(playerUuid, shopId, itemId, isBuy, quantity, timestamp);
        this.playerName = playerName;
        this.itemName = itemName;
        this.unitPrice = unitPrice;
        this.totalPrice = unitPrice * quantity;
    }
    
    /**
     * Constructeur par défaut pour la désérialisation
     */
    public TransactionRecord() {
        // Nécessaire pour la désérialisation JSON avec Gson
    }

    // Getters et setters
    
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public void setPlayerUuid(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }
    
    public String getPlayerName() {
        return playerName;
    }
    
    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public String getShopId() {
        return shopId;
    }

    public void setShopId(String shopId) {
        this.shopId = shopId;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }
    
    public String getItemName() {
        return itemName;
    }
    
    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public boolean isBuy() {
        return isBuy;
    }

    public void setBuy(boolean buy) {
        isBuy = buy;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
        if (unitPrice > 0) {
            this.totalPrice = unitPrice * quantity;
        }
    }
    
    public double getUnitPrice() {
        return unitPrice;
    }
    
    public void setUnitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
        this.totalPrice = unitPrice * quantity;
    }
    
    public double getTotalPrice() {
        return totalPrice;
    }
    
    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public long getId() {
        return id;
    }
    
    public void setId(long id) {
        this.id = id;
    }
    
    @Override
    public String toString() {
        return "TransactionRecord{" +
                "player=" + (playerName != null ? playerName : playerUuid) +
                ", shop='" + shopId + '\'' +
                ", item='" + (itemName != null ? itemName : itemId) + '\'' +
                ", " + (isBuy ? "achat" : "vente") +
                ", quantité=" + quantity +
                ", prix=" + (unitPrice > 0 ? unitPrice : "N/A") +
                ", date=" + timestamp +
                '}';
    }
}