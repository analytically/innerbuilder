package org.jetbrains.plugins.innerbuilder;

public enum InnerBuilderOption {
    FINAL_SETTERS("finalSetters"),
    NEW_BUILDER_METHOD("newBuilderMethod"),
    COPY_CONSTRUCTOR("copyConstructor"),
    WITH_NOTATION("withNotation"),
    JSR305_ANNOTATIONS("useJSR305Annotations"),
    FINAL_PARAMETERS("finalParameters");

    private final String property;

    private InnerBuilderOption(final String property) {
        this.property = String.format("GenerateInnerBuilder.%s", property);
    }

    public String getProperty() {
        return property;
    }
}
