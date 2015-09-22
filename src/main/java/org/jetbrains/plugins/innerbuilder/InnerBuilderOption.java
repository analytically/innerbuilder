package org.jetbrains.plugins.innerbuilder;

public enum InnerBuilderOption {

    FINAL_SETTERS("finalSetters"),
    NEW_BUILDER_METHOD("newBuilderMethod"),
    COPY_CONSTRUCTOR("copyConstructor"),
    WITH_NOTATION("withNotation"),
    JSR305_ANNOTATIONS("useJSR305Annotations"),
    FINDBUGS_ANNOTATION("useFindbugsAnnotation"),
    WITH_JAVADOC("withJavadoc"),
    USE_VAL("useVal");

    private final String property;

    InnerBuilderOption(final String property) {
        this.property = String.format("GenerateInnerBuilder.%s", property);
    }

    public String getProperty() {
        return property;
    }
}
