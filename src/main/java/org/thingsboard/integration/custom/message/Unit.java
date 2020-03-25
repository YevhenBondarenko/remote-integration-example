package org.thingsboard.integration.custom.message;

public enum Unit {
    PRESSURE_MPA("1"), PRESSURE_BAR("2"), PRESSURE_KPA("3"), TEMPERATURE("4"), LEVEL_M("5"), FLOW("6"), ANGLE("7"), FLOAT("8");

    private String value;

    Unit(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static Unit of(String value) {
        for (Unit u : Unit.values()) {
            if (u.getValue().equals(value)) {
                return u;
            }
        }
        throw new IllegalArgumentException("Invalid value: " + value);
    }
}
