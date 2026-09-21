package com.terraceweather.scoring;

/** Ordered from best to worst so the worst verdict of a window is the max ordinal. */
public enum TerraceVerdict {
    OPEN(100),
    CAUTION(50),
    CLOSED(0);

    private final int capacityPercent;

    TerraceVerdict(int capacityPercent) {
        this.capacityPercent = capacityPercent;
    }

    /** Suggested share of terrace tables to offer for reservations. */
    public int capacityPercent() {
        return capacityPercent;
    }
}
