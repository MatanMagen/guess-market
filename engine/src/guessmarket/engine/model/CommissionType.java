package guessmarket.engine.model;

/** When an event collects its commission. The XML spelling lives next to the constant. */
public enum CommissionType {

    ON_PURCHASE("on-purchase"),
    ON_CLOSE("on-close");

    private final String xmlValue;

    CommissionType(String xmlValue) {
        this.xmlValue = xmlValue;
    }

    public String xmlValue() {
        return xmlValue;
    }

    /** @return the matching type, or null when the file holds something unrecognised. */
    public static CommissionType fromXmlValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        for (CommissionType type : values()) {
            if (type.xmlValue.equalsIgnoreCase(normalized)) {
                return type;
            }
        }
        return null;
    }
}
