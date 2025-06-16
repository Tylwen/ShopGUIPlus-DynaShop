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
package fr.tylwen.satyria.dynashop.data;

import java.util.Optional;

public class ItemPriceData {
    public Optional<Double> buyPrice = Optional.empty();
    public Optional<Double> sellPrice = Optional.empty();

    public Optional<Double> minBuy = Optional.empty();
    public Optional<Double> maxBuy = Optional.empty();
    public Optional<String> minBuyLink = Optional.empty();
    public Optional<String> maxBuyLink = Optional.empty();

    public Optional<Double> minSell = Optional.empty();
    public Optional<Double> maxSell = Optional.empty();
    public Optional<String> minSellLink = Optional.empty();
    public Optional<String> maxSellLink = Optional.empty();

    public Optional<Double> growthBuy = Optional.empty();
    public Optional<Double> decayBuy = Optional.empty();
    
    public Optional<Double> growthSell = Optional.empty();
    public Optional<Double> decaySell = Optional.empty();

    public Optional<Integer> stock = Optional.empty();
    public Optional<Integer> minStock = Optional.empty();
    public Optional<Integer> maxStock = Optional.empty();
    public Optional<Double> stockBuyModifier = Optional.empty();
    public Optional<Double> stockSellModifier = Optional.empty();
}
