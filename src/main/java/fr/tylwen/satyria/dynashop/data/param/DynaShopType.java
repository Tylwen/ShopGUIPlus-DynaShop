package fr.tylwen.satyria.dynashop.data.param;

public enum DynaShopType {
    NONE,       // Aucun type
    DYNAMIC,    // Dynamique (prix évolutif)
    RECIPE,     // Basé sur une recette
    STOCK       // Basé sur le stock
;

    public DynaShopType orElse(DynaShopType other) {
        return this == NONE ? other : this;
    }
}