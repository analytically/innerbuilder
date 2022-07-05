package org.jetbrains.plugins.innerbuilder;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public enum InnerBuilderOption {

    FINAL_SETTERS("finalSetters"),
    NEW_BUILDER_METHOD("newBuilderMethod"),

    STATIC_BUILDER_DROPDOWN("staticBuilderDropdown", false),
    STATIC_BUILDER_NEW_BUILDER_NAME("staticBuilderNewBuilderName", false),
    STATIC_BUILDER_BUILDER_NAME("staticBuilderBuilderName", false),
    STATIC_BUILDER_NEW_CLASS_NAME("staticBuilderNewClassName", false),
    STATIC_BUILDER_NEW_CLASS_NAME_BUILDER("staticBuilderNewClassNameBuilder", false),

    BUILDER_METHOD_LOCATION_DROPDOWN("builderMethodDropdownLocation", false),
    BUILDER_METHOD_IN_PARENT_CLASS("builderMethodInParentClass", false),
    BUILDER_METHOD_IN_BUILDER("builderMethodInBuilder", false),

    COPY_CONSTRUCTOR("copyConstructor"),
    WITH_NOTATION("withNotation"),
    SET_NOTATION("setNotation"),
    JSR305_ANNOTATIONS("useJSR305Annotations"),
    PMD_AVOID_FIELD_NAME_MATCHING_METHOD_NAME_ANNOTATION("suppressAvoidFieldNameMatchingMethodName"),
    WITH_JAVADOC("withJavadoc"),
    FIELD_NAMES("fieldNames");

    private final String property;
    private final Boolean booleanProperty;

    InnerBuilderOption(final String property) {
        this(property, true);
    }

    InnerBuilderOption(final String property, final Boolean booleanProperty) {
        this.property = String.format("GenerateInnerBuilder.%s", property);
        this.booleanProperty = booleanProperty;
    }

    public String getProperty() {
        return property;
    }

    public Boolean isBooleanProperty() {
        return booleanProperty;
    }

    public static Optional<InnerBuilderOption> findValue(String value) {
        return Arrays.stream(values())
            .filter(it -> Objects.equals(it.getProperty(), value))
            .findFirst();
    }
}