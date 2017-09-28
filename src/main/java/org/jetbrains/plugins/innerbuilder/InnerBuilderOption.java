package org.jetbrains.plugins.innerbuilder;

public enum InnerBuilderOption {

    FINAL_SETTERS("finalSetters"),
    NEW_BUILDER_METHOD("newBuilderMethod"),
    COPY_CONSTRUCTOR("copyConstructor"),
    WITH_NOTATION("withNotation"),
    SET_NOTATION("setNotation"),
    JSR305_ANNOTATIONS("useJSR305Annotations"),
    FINDBUGS_ANNOTATION("useFindbugsAnnotation"),
    PMD_AVOID_FIELD_NAME_MATCHING_METHOD_NAME_ANNOTATION("suppressAvoidFieldNameMatchingMethodName"),
    WITH_JAVADOC("withJavadoc"),
    FIELD_NAMES("fieldNames");

    private final String property;

    private InnerBuilderOption(final String property) {
        this.property = String.format("GenerateInnerBuilder.%s", property);
    }

    public String getProperty() {
        return property;
    }
}
