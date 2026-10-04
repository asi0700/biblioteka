package ru.library.model;

public enum CopyCondition {
    USABLE("usable"), REPAIR("repair"), RETIRED("retired");
    private final String code;
    CopyCondition(String code) { this.code = code; }
    public String code() { return code; }
    public static CopyCondition fromCode(String code) {
        for (CopyCondition value : values()) if (value.code.equals(code)) return value;
        throw new IllegalArgumentException("Неизвестное состояние экземпляра.");
    }
}
