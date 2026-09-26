package gg.drak.restored.data;

/** Comparison used by a CompactConfiguration against the selected item amount. */
public enum QuantityOperand {
    EQUAL_TO,
    MORE_THAN,
    LESS_THAN,
    MORE_THAN_OR_EQUAL_TO,
    LESS_THAN_OR_EQUAL_TO,
    NOT_EQUAL_TO;

    public QuantityOperand next() {
        QuantityOperand[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public boolean test(long actual, long expected) {
        return switch (this) {
            case EQUAL_TO -> actual == expected;
            case MORE_THAN -> actual > expected;
            case LESS_THAN -> actual < expected;
            case MORE_THAN_OR_EQUAL_TO -> actual >= expected;
            case LESS_THAN_OR_EQUAL_TO -> actual <= expected;
            case NOT_EQUAL_TO -> actual != expected;
        };
    }

    public String display() {
        return switch (this) {
            case EQUAL_TO -> "Equal To";
            case MORE_THAN -> "More Than";
            case LESS_THAN -> "Less Than";
            case MORE_THAN_OR_EQUAL_TO -> "More Than or Equal To";
            case LESS_THAN_OR_EQUAL_TO -> "Less Than or Equal To";
            case NOT_EQUAL_TO -> "Not Equal To";
        };
    }
}
