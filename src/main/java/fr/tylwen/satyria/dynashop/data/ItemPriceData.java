package fr.tylwen.satyria.dynashop.data;

import java.util.Optional;

public class ItemPriceData {
    public Optional<Double> buyPrice = Optional.empty();
    public Optional<Double> sellPrice = Optional.empty();

    public Optional<Double> minBuy = Optional.empty();
    public Optional<Double> maxBuy = Optional.empty();

    public Optional<Double> minSell = Optional.empty();
    public Optional<Double> maxSell = Optional.empty();

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
