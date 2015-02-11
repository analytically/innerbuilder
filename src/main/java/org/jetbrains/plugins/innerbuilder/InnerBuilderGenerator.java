package org.jetbrains.plugins.innerbuilder;

import com.intellij.codeInsight.generation.PsiFieldMember;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class InnerBuilderGenerator implements Runnable {
    private final Project project;
    private final PsiFile file;
    private final Editor editor;
    private final List<PsiFieldMember> selectedFields;
    private final CodeStyleManager codeStyleManager;
    private final PsiElementFactory psiElementFactory;

    public InnerBuilderGenerator(final Project project, final PsiFile file, final Editor editor, final List<PsiFieldMember> selectedFields) {
        this.project = project;
        this.file = file;
        this.editor = editor;
        this.selectedFields = selectedFields;
        codeStyleManager = CodeStyleManager.getInstance(project);
        psiElementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    }

    /**
     * Capitalize the first letter of a string.
     *
     * @param   s  the string to capitalize
     *
     * @return  the capitalized string
     */
    static String capitalize(final String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    public void run() {
        final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
        final PsiClass clazz = PsiTreeUtil.getParentOfType(element, PsiClass.class);

        PsiClass builderClass = clazz.findInnerClassByName(InnerBuilderConstants.BUILDER_CLASS_NAME, false);
        if (builderClass == null) {
            builderClass = (PsiClass) clazz.add(psiElementFactory.createClass(InnerBuilderConstants.BUILDER_CLASS_NAME));

            // builder classes are static and final
            builderClass.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
            builderClass.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
        }

        final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        
        final boolean finalSetters = propertiesComponent.getBoolean(InnerBuilderConstants.PROP_FINALSETTERS, false);
        final boolean newBuilderMethod = propertiesComponent.getBoolean(InnerBuilderConstants.PROP_NEWBUILDERMETHOD, false);
        final boolean copyConstructor = propertiesComponent.getBoolean(InnerBuilderConstants.PROP_COPYCONSTRUCTOR, false);
        final boolean withNotation = propertiesComponent.getBoolean(InnerBuilderConstants.PROP_WITHNOTATION, false);
        final boolean useJsr305 = propertiesComponent.getBoolean(InnerBuilderConstants.PROP_JSR305_ANNOTATIONS, false);
        final boolean finalParameters = propertiesComponent.getBoolean(InnerBuilderConstants.PROP_FINAL_PARAMETERS, false);

        final String builderClassName = builderClass.getName();

        final StringBuilder constructorTakingBuilder = new StringBuilder();
        constructorTakingBuilder.append("private ").append(clazz.getName()).append('(')
                .append(builderClassName).append(" builder) {");

        for (final PsiFieldMember member : selectedFields) {
            final PsiField field = member.getElement();

            final PsiMethod setterPrototype = PropertyUtil.generateSetterPrototype(field);
            final PsiMethod setter = clazz.findMethodBySignature(setterPrototype, true);

            final String fieldName = field.getName();
            if (setter == null || field.getModifierList().hasModifierProperty(PsiModifier.FINAL)) {
                constructorTakingBuilder.append(fieldName).append("= builder.")
                        .append(fieldName).append(';');
            } else {
                constructorTakingBuilder.append(setter.getName()).append("(builder.")
                        .append(fieldName).append(");");
            }
        }

        constructorTakingBuilder.append('}');
        addMethod(clazz, null, constructorTakingBuilder.toString(), true);

        final StringBuilder typedFinalFields = new StringBuilder();
        final StringBuilder untypedFinalFields = new StringBuilder();
        final StringBuilder untypedFinalFieldsCopy = new StringBuilder();
        final Collection<PsiFieldMember> finalFields = new ArrayList<PsiFieldMember>();
        final Collection<PsiFieldMember> nonFinalFields = new ArrayList<PsiFieldMember>();

        PsiElement addedField = null;
        for (final PsiFieldMember member : selectedFields) {
            final PsiField field = member.getElement();

            final String fieldName = field.getName();
            final PsiType fieldType = field.getType();
            addedField = addField(builderClass, addedField, fieldName, fieldType);

            if (field.hasModifierProperty(PsiModifier.FINAL) && !finalSetters) {
                if (!finalFields.isEmpty()) {
                    typedFinalFields.append(", ");
                    untypedFinalFields.append(", ");
                    untypedFinalFieldsCopy.append(", ");
                }

                finalFields.add(member);
                ((PsiField) addedField).getModifierList().setModifierProperty(PsiModifier.FINAL, true);

                if (finalParameters) {
                    typedFinalFields.append("final ");
                }
                typedFinalFields.append(fieldType.getCanonicalText()).append(' ').append(fieldName);
                
                untypedFinalFields.append(fieldName);
                untypedFinalFieldsCopy.append("copy.").append(fieldName);
            } else {
                nonFinalFields.add(member);
            }
        }

        // builder constructor, accepting the final fields
        final StringBuilder builderConstructorText = new StringBuilder();
        final String builderConstructorContructorAccess = newBuilderMethod ? "private" : "public";
        builderConstructorText.append(
                String.format("%s %s(", builderConstructorContructorAccess, builderClassName))
                .append(typedFinalFields).append(") {");
        for (final PsiFieldMember field : finalFields) {
            builderConstructorText.append("this.").append(field.getElement().getName()).append('=')
                    .append(field.getElement().getName()).append(';');
        }

        builderConstructorText.append('}');
        addMethod(builderClass, null, builderConstructorText.toString());

        if (newBuilderMethod) {
            final StringBuilder newBuilderText = new StringBuilder();
            newBuilderText.append(String.format("public static %s newBuilder(", builderClassName))
                    .append(typedFinalFields).append(") {");
            newBuilderText.append("return new ").append(builderClassName).append('(')
                    .append(untypedFinalFields).append(");");
            newBuilderText.append('}');
            addMethod(clazz, null, newBuilderText.toString());
        }

        // COPY CONSTRUCTOR
        final String qualifiedName = clazz.getQualifiedName();

        if (copyConstructor) {
            final StringBuilder copyConstructorText = new StringBuilder();

            if (newBuilderMethod) {
                copyConstructorText.append("public static ").append(builderClassName).append(
                        " newBuilder(");
                copyConstructorText.append(qualifiedName).append(" copy) { ")
                        .append(builderClassName).append(" builder = new ")
                        .append(builderClassName).append('(')
                        .append(untypedFinalFieldsCopy).append(");");
            } else {
                copyConstructorText.append("public ").append(builderClassName).append('(');
                copyConstructorText.append(qualifiedName).append(" copy) {");
            }

            for (final PsiFieldMember member : selectedFields) {
                final PsiField field = member.getElement();
                if (field.getModifierList().hasModifierProperty(PsiModifier.FINAL)
                        && !finalSetters) {
                    continue;
                }

                if (newBuilderMethod) {
                    copyConstructorText.append("builder.");
                }

                String fieldName = field.getName();
                copyConstructorText.append(fieldName).append("= copy.")
                        .append(fieldName).append(';');
            }

            if (newBuilderMethod) {
                copyConstructorText.append("return builder;");
            }

            copyConstructorText.append('}');
            addMethod(newBuilderMethod ? clazz : builderClass, null, copyConstructorText.toString(), true);
        }

        PsiElement added = null;

        // builder methods
        for (final PsiFieldMember member : nonFinalFields) {
            final PsiField field = member.getElement();

            final String fieldName = field.getName();
            final String methodName = withNotation ? String.format("with%s", capitalize(fieldName))
                    : fieldName;
            final StringBuilder methodBuilder = new StringBuilder();
            if (useJsr305) {
                methodBuilder.append("@Nonnull ");
            }

            methodBuilder.append("public Builder ").append(methodName).append('(');

            if (useJsr305) {
                methodBuilder.append("@Nonnull ");
            }

            if (finalParameters) {
                methodBuilder.append("final ");
            }

            methodBuilder.append(field.getType().getCanonicalText()).append(' ').append(fieldName);
            methodBuilder.append("){");
            methodBuilder.append("this.").append(fieldName).append('=');
            if (useJsr305) {
                methodBuilder.append("Preconditions.checkNotNull(");
            }

            methodBuilder.append(fieldName);
            if (useJsr305) {
                methodBuilder.append(String.format(", \"%s parameter can't be null\")", fieldName));
            }

            methodBuilder.append(';');
            methodBuilder.append("return this;");
            methodBuilder.append('}');

            final String builderMethod = methodBuilder.toString();

            added = addMethod(builderClass, added, builderMethod);
        }

        // builder.build() method
        addMethod(builderClass, added,
                (useJsr305 ? "@Nonnull " : "") + "public " + qualifiedName + " build() { return new "
                        + qualifiedName + "(this);}");
        codeStyleManager.reformat(builderClass);
    }

    PsiElement addField(final PsiClass target, final PsiElement after, final String name,
                        final PsiType type) {
        final PsiField existingField = target.findFieldByName(name, false);

        if (existingField == null || !areTypesPresentableEqual(existingField.getType(), type)) {
            if (existingField != null) {
                existingField.delete();
            }

            final PsiField newField = psiElementFactory.createField(name, type);
            if (after != null) {
                return target.addAfter(newField, after);
            } else {
                return target.add(newField);
            }
        } else {
            return existingField;
        }
    }

    PsiElement addMethod(final PsiClass target, final PsiElement after, final String methodText) {
        return addMethod(target, after, methodText, false);
    }

    PsiElement addMethod(final PsiClass target, final PsiElement after, final String methodText,
                         final boolean replace) {
        final PsiMethod newMethod = psiElementFactory.createMethodFromText(methodText, null);
        PsiMethod existingMethod = target.findMethodBySignature(newMethod, false);

        if (existingMethod == null && newMethod.isConstructor()) {
            for (final PsiMethod constructor : target.getConstructors()) {
                if (areParameterListsEqual(constructor.getParameterList(), newMethod.getParameterList())) {
                    existingMethod = constructor;
                    break;
                }
            }
        }

        if (existingMethod == null) {
            if (after != null) {
                return target.addAfter(newMethod, after);
            } else {
                return target.add(newMethod);
            }
        } else if (replace) {
            existingMethod.replace(newMethod);
        }

        return existingMethod;
    }

    boolean areParameterListsEqual(final PsiParameterList paramList1, final PsiParameterList paramList2) {
        if (paramList1.getParametersCount() != paramList2.getParametersCount()) {
            return false;
        }

        final PsiParameter[] param1Params = paramList1.getParameters();
        final PsiParameter[] param2Params = paramList2.getParameters();
        for (int i = 0; i < param1Params.length; i++) {
            final PsiParameter param1Param = param1Params[i];
            final PsiParameter param2Param = param2Params[i];

            if (!areTypesPresentableEqual(param1Param.getType(), param2Param.getType())) {
                return false;
            }
        }

        return true;
    }

    boolean areTypesPresentableEqual(final PsiType type1, final PsiType type2) {
        if (type1 != null && type2 != null) {
            final String type1Canonical = stripJavaLang(type1.getPresentableText());
            final String type2Canonical = stripJavaLang(type2.getPresentableText());
            return type1Canonical.equals(type2Canonical);
        }

        return false;
    }

    private String stripJavaLang(final String typeString) {
        return typeString.startsWith(InnerBuilderConstants.JAVA_DOT_LANG) ? typeString.substring(InnerBuilderConstants.JAVA_DOT_LANG.length())
                : typeString;
    }
}
