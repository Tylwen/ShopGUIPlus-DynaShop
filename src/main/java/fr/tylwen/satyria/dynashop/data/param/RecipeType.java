package fr.tylwen.satyria.dynashop.data.param;

public enum RecipeType {
    NONE,           // Aucun type
    SHAPED,         // Recette avec une forme définie
    SHAPELESS,      // Recette sans forme définie
    FURNACE,        // Recette de four
    BLAST_FURNACE,  // Recette de haut fourneau
    SMOKER,         // Recette de fumoir
    BREWING,        // Recette d'alchimie
    STONECUTTER     // Recette de tailleur de pierre
;

    public static RecipeType fromString(String upperCase) {
        switch (upperCase) {
            case "NONE":
                return NONE;
            case "SHAPED":
                return SHAPED;
            case "SHAPELESS":
                return SHAPELESS;
            case "FURNACE":
                return FURNACE;
            case "BLAST_FURNACE":
                return BLAST_FURNACE;
            case "SMOKER":
                return SMOKER;
            case "BREWING":
                return BREWING;
            case "STONECUTTER":
                return STONECUTTER;
            default:
                throw new IllegalArgumentException("Type de recette inconnu: " + upperCase);
        }
    }
}