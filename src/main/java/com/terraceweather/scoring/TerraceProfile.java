package com.terraceweather.scoring;

/** Ready-made tolerance presets by climate, so callers don't have to tune every threshold. */
public enum TerraceProfile {

    /** Default: mild climates. */
    TEMPERATE(new TerraceRules(40, 0.5, 35, 14, 34)),
    /** Hot, humid and rainy: showers are routine and shade/fans make heat bearable. */
    TROPICAL(new TerraceRules(70, 1.5, 40, 18, 38)),
    /** Dry and hot summers: rain is rare and unwelcome, heat is tolerated. */
    MEDITERRANEAN(new TerraceRules(30, 0.3, 35, 15, 38)),
    /** Cool climates: tolerant of cold and drizzle, wind still matters. */
    NORDIC(new TerraceRules(50, 0.8, 35, 8, 30));

    private final TerraceRules rules;

    TerraceProfile(TerraceRules rules) {
        this.rules = rules;
    }

    public TerraceRules rules() {
        return rules;
    }
}
