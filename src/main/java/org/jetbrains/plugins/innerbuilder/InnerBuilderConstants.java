package org.jetbrains.plugins.innerbuilder;

import org.jetbrains.annotations.NonNls;

public class InnerBuilderConstants {
    @NonNls
    static final String BUILDER_CLASS_NAME = "Builder";
    @NonNls
    static final String JAVA_DOT_LANG = "java.lang.";
    @NonNls
    static final String PROP_FINALSETTERS = "GenerateInnerBuilder.finalSetters";
    @NonNls
    static final String PROP_NEWBUILDERMETHOD = "GenerateInnerBuilder.newBuilderMethod";
    @NonNls
    static final String PROP_COPYCONSTRUCTOR = "GenerateInnerBuilder.copyConstructor";
    @NonNls
    static final String PROP_WITHNOTATION = "GenerateInnerBuilder.withNotation";
    @NonNls
    static final String PROP_JSR305_ANNOTATIONS = "GenerateInnerBuilder.useJSR305Annotations";
    @NonNls
    static final String PROP_FINAL_PARAMETERS = "GenerateInnerBuilder.finalParameters";
}
