package org.jetbrains.plugins.innerbuilder;

import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.swing.JCheckBox;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiFieldMember;

import com.intellij.ide.util.MemberChooser;
import com.intellij.ide.util.PropertiesComponent;

import com.intellij.lang.LanguageCodeInsightActionHandler;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;

import com.intellij.ui.NonFocusableCheckBox;

public class GenerateInnerBuilderHandler implements LanguageCodeInsightActionHandler {
    @NonNls
    private static final String BUILDER_CLASS_NAME = "Builder";

    @NonNls
    private static final String JAVA_DOT_LANG = "java.lang.";

    @NonNls
    private static final String PROP_FINALSETTERS = "GenerateInnerBuilder.finalSetters";

    @NonNls
    private static final String PROP_NEWBUILDERMETHOD = "GenerateInnerBuilder.newBuilderMethod";

    @NonNls
    private static final String PROP_COPYCONSTRUCTOR = "GenerateInnerBuilder.copyConstructor";

    @NonNls
    private static final String PROP_WITHNOTATION = "GenerateInnerBuilder.withNotation";

    @NonNls
    private static final String PROP_JSR305_ANNOTATIONS = "GenerateInnerBuilder.useJSR305Annotations";

    @NonNls
    private static final String PROP_FINAL_PARAMETERS = "GenerateInnerBuilder.finalParameters";

    @Override
    public boolean isValidFor(final Editor editor, final PsiFile file) {
        if (!(file instanceof PsiJavaFile)) {
            return false;
        }

        final Project project = editor.getProject();
        if (project == null) {
            return false;
        }

        return OverrideImplementUtil.getContextClass(project, editor, file, false) != null
                && isApplicable(file, editor);
    }

    @Override
    public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
        if (!CodeInsightUtilBase.prepareEditorForWrite(editor)) {
            return;
        }

        if (!FileDocumentManager.getInstance().requestWriting(editor.getDocument(), project)) {
            return;
        }

        PsiDocumentManager.getInstance(project).commitAllDocuments();

        final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        final List<PsiFieldMember> fieldMembers = chooseFields(file, editor, project, propertiesComponent);
        if (fieldMembers == null || fieldMembers.isEmpty()) {
            return;
        }

        ApplicationManager.getApplication().runWriteAction(new Runnable() {
                CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
                PsiElementFactory psiElementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

                @Override
                public void run() {
                    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
                    final PsiClass clazz = PsiTreeUtil.getParentOfType(element, PsiClass.class);

                    PsiClass builderClass = clazz.findInnerClassByName(BUILDER_CLASS_NAME, false);
                    if (builderClass == null) {
                        builderClass = (PsiClass) clazz.add(psiElementFactory.createClass(BUILDER_CLASS_NAME));

                        // builder classes are static and final
                        builderClass.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
                        builderClass.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
                    }

                    final boolean finalSetters = propertiesComponent.getBoolean(PROP_FINALSETTERS, false);
                    final boolean newBuilderMethod = propertiesComponent.getBoolean(PROP_NEWBUILDERMETHOD, false);
                    final boolean copyConstructor = propertiesComponent.getBoolean(PROP_COPYCONSTRUCTOR, false);
                    final boolean withNotation = propertiesComponent.getBoolean(PROP_WITHNOTATION, false);
                    final boolean useJsr305 = propertiesComponent.getBoolean(PROP_JSR305_ANNOTATIONS, false);
                    final boolean finalParameters = propertiesComponent.getBoolean(PROP_FINAL_PARAMETERS, false);

                    final StringBuilder constructorTakingBuilder = new StringBuilder();
                    constructorTakingBuilder.append("private ").append(clazz.getName()).append('(')
                                            .append(builderClass.getName()).append(" builder) {");

                    for (final PsiFieldMember member : fieldMembers) {
                        final PsiField field = member.getElement();

                        final PsiMethod setterPrototype = PropertyUtil.generateSetterPrototype(field);
                        final PsiMethod setter = clazz.findMethodBySignature(setterPrototype, true);

                        if (setter == null || field.getModifierList().hasModifierProperty(PsiModifier.FINAL)) {
                            constructorTakingBuilder.append(field.getName()).append("= builder.")
                                                    .append(field.getName()).append(';');
                        } else {
                            constructorTakingBuilder.append(setter.getName()).append("(builder.")
                                                    .append(field.getName()).append(");");
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
                    for (final Iterator<PsiFieldMember> iterator = fieldMembers.iterator(); iterator.hasNext();) {
                        final PsiFieldMember member = iterator.next();
                        final PsiField field = member.getElement();

                        addedField = addField(builderClass, addedField, field.getName(), field.getType());

                        if (field.hasModifierProperty(PsiModifier.FINAL) && !finalSetters) {
                            if (!finalFields.isEmpty()) {
                                typedFinalFields.append(", ");
                                untypedFinalFields.append(", ");
                                untypedFinalFieldsCopy.append(", ");
                            }

                            finalFields.add(member);
                            ((PsiField) addedField).getModifierList().setModifierProperty(PsiModifier.FINAL, true);

                            typedFinalFields.append(member.getElement().getType().getCanonicalText()).append(' ')
                                            .append(member.getElement().getName());
                            untypedFinalFields.append(member.getElement().getName());
                            untypedFinalFieldsCopy.append("copy.").append(member.getElement().getName());
                        } else {
                            nonFinalFields.add(member);
                        }
                    }

                    // builder constructor, accepting the final fields
                    final StringBuilder builderConstructorText = new StringBuilder();
                    final String builderConstructorContructorAccess = newBuilderMethod ? "private" : "public";
                    builderConstructorText.append(
                                              String.format("%s %s(%s", builderConstructorContructorAccess,
                                                  builderClass.getName(), (finalParameters ? "final " : "")))
                                          .append(typedFinalFields).append(") {");
                    for (final PsiFieldMember field : finalFields) {
                        builderConstructorText.append("this.").append(field.getElement().getName()).append('=')
                                              .append(field.getElement().getName()).append(';');
                    }

                    builderConstructorText.append('}');
                    addMethod(builderClass, null, builderConstructorText.toString());

                    if (newBuilderMethod) {
                        final StringBuilder newBuilderText = new StringBuilder();
                        newBuilderText.append(String.format("public static %s newBuilder(", builderClass.getName()))
                                      .append(typedFinalFields).append(") {");
                        newBuilderText.append("return new ").append(builderClass.getName()).append('(')
                                      .append(untypedFinalFields).append(");");
                        newBuilderText.append('}');
                        addMethod(clazz, null, newBuilderText.toString());
                    }

                    // COPY CONSTRUCTOR
                    if (copyConstructor) {
                        final StringBuilder copyConstructorText = new StringBuilder();

                        if (newBuilderMethod) {
                            copyConstructorText.append("public static ").append(builderClass.getName()).append(
                                " newBuilder(");
                            copyConstructorText.append(clazz.getQualifiedName()).append(" copy) { ")
                                               .append(builderClass.getName()).append(" builder = new ")
                                               .append(builderClass.getName()).append('(')
                                               .append(untypedFinalFieldsCopy).append(");");
                        } else {
                            copyConstructorText.append("public ").append(builderClass.getName()).append('(');
                            copyConstructorText.append(clazz.getQualifiedName()).append(" copy) {");
                        }

                        for (final PsiFieldMember member : fieldMembers) {
                            if (member.getElement().getModifierList().hasModifierProperty(PsiModifier.FINAL)
                                    && !finalSetters) {
                                continue;
                            }

                            if (newBuilderMethod) {
                                copyConstructorText.append("builder.");
                            }

                            copyConstructorText.append(member.getElement().getName()).append("= copy.")
                                               .append(member.getElement().getName()).append(';');
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
                    addMethod(builderClass, null,
                        (useJsr305 ? "@Nonnull " : "") + "public " + clazz.getQualifiedName() + " build() { return new "
                            + clazz.getQualifiedName() + "(this);}");
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
                    return typeString.startsWith(JAVA_DOT_LANG) ? typeString.substring(JAVA_DOT_LANG.length())
                                                                : typeString;
                }
            });
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    public static boolean isApplicable(final PsiFile file, final Editor editor) {
        final List<PsiFieldMember> targetElements = getFields(file, editor);
        return targetElements != null && !targetElements.isEmpty();
    }

    @Nullable
    private static List<PsiFieldMember> chooseFields(final PsiFile file, final Editor editor, final Project project,
            final PropertiesComponent propertiesComponent) {
        final List<PsiFieldMember> members = getFields(file, editor);
        if (members == null || members.isEmpty()) {
            return null;
        }

        if (!ApplicationManager.getApplication().isUnitTestMode()) {

            final JCheckBox finalSettersCheckbox = new NonFocusableCheckBox(
                    "Generate builder methods for final fields");
            finalSettersCheckbox.setMnemonic('f');
            finalSettersCheckbox.setSelected(propertiesComponent.isTrueValue(PROP_FINALSETTERS));
            finalSettersCheckbox.addItemListener(new ItemListener() {
                    public void itemStateChanged(final ItemEvent e) {
                        propertiesComponent.setValue(PROP_FINALSETTERS,
                            Boolean.toString(finalSettersCheckbox.isSelected()));
                    }
                });

            final JCheckBox newBuilderMethodCheckbox = new NonFocusableCheckBox("Generate static newBuilder() method");
            newBuilderMethodCheckbox.setMnemonic('n');
            newBuilderMethodCheckbox.setSelected(propertiesComponent.isTrueValue(PROP_NEWBUILDERMETHOD));
            newBuilderMethodCheckbox.addItemListener(new ItemListener() {
                    public void itemStateChanged(final ItemEvent e) {
                        propertiesComponent.setValue(PROP_NEWBUILDERMETHOD,
                            Boolean.toString(newBuilderMethodCheckbox.isSelected()));
                    }
                });

            final JCheckBox copyConstructorCheckbox = new NonFocusableCheckBox("Generate builder copy constructor");
            copyConstructorCheckbox.setMnemonic('o');
            copyConstructorCheckbox.setSelected(propertiesComponent.isTrueValue(PROP_COPYCONSTRUCTOR));
            copyConstructorCheckbox.addItemListener(new ItemListener() {
                    public void itemStateChanged(final ItemEvent e) {
                        propertiesComponent.setValue(PROP_COPYCONSTRUCTOR,
                            Boolean.toString(copyConstructorCheckbox.isSelected()));
                    }
                });

            final JCheckBox withNotationCheckbox = new NonFocusableCheckBox("Use 'with...' notation");
            withNotationCheckbox.setMnemonic('w');
            withNotationCheckbox.setToolTipText(
                "Generate builder methods that start with 'with', for example: builder.withName(String name)");
            withNotationCheckbox.setSelected(propertiesComponent.isTrueValue(PROP_WITHNOTATION));
            withNotationCheckbox.addItemListener(new ItemListener() {
                    public void itemStateChanged(final ItemEvent e) {
                        propertiesComponent.setValue(PROP_WITHNOTATION,
                            Boolean.toString(withNotationCheckbox.isSelected()));
                    }
                });

            final JCheckBox useJSR305Checkbox = new NonFocusableCheckBox("Use JSR-305 @Nonnull annotations");
            useJSR305Checkbox.setMnemonic('j');
            useJSR305Checkbox.setToolTipText(
                "Add @Nonnull annotations to generated methods and parameters, for example: @Nonnull public Builder withName(@Nonnull String name) { ... }");
            useJSR305Checkbox.setSelected(propertiesComponent.isTrueValue(PROP_JSR305_ANNOTATIONS));
            useJSR305Checkbox.addItemListener(new ItemListener() {
                    public void itemStateChanged(final ItemEvent e) {
                        propertiesComponent.setValue(PROP_JSR305_ANNOTATIONS,
                            Boolean.toString(useJSR305Checkbox.isSelected()));
                    }
                });

            final JCheckBox finalParametersCheckbox = new NonFocusableCheckBox("Make all parameters final");
            finalParametersCheckbox.setMnemonic('f');
            finalParametersCheckbox.setToolTipText(
                "Add the 'final' modifier to all the method parameters, for example: @Nonnull public Builder withName(@Nonnull final String name) { ... }");
            finalParametersCheckbox.setSelected(propertiesComponent.isTrueValue(PROP_FINAL_PARAMETERS));
            finalParametersCheckbox.addItemListener(new ItemListener() {
                    public void itemStateChanged(final ItemEvent e) {
                        propertiesComponent.setValue(PROP_FINAL_PARAMETERS,
                            Boolean.toString(finalParametersCheckbox.isSelected()));
                    }
                });

            final PsiFieldMember[] memberArray = members.toArray(new PsiFieldMember[members.size()]);

            final MemberChooser<PsiFieldMember> chooser = new MemberChooser<PsiFieldMember>(memberArray, false, true,
                    project, null,
                    new JCheckBox[] {
                        finalSettersCheckbox, newBuilderMethodCheckbox, copyConstructorCheckbox, withNotationCheckbox,
                        useJSR305Checkbox, finalParametersCheckbox
                    });

            chooser.setTitle("Select options to include in the Builder");
            chooser.selectElements(memberArray);
            chooser.show();

            if (chooser.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
                return null;
            }

            return chooser.getSelectedElements();
        } else {
            return members;
        }
    }

    @Nullable
    private static List<PsiFieldMember> getFields(final PsiFile file, final Editor editor) {
        final int offset = editor.getCaretModel().getOffset();
        final PsiElement element = file.findElementAt(offset);
        if (element == null) {
            return null;
        }

        final PsiClass clazz = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (clazz == null || clazz.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return null;
        }

        final List<PsiFieldMember> result = new ArrayList<PsiFieldMember>();

        PsiClass classToExtractFieldsFrom = clazz;
        while (classToExtractFieldsFrom != null) {
            collectFieldsInClass(element, clazz, classToExtractFieldsFrom, result);
            if (classToExtractFieldsFrom.hasModifierProperty(PsiModifier.STATIC)) {
                break;
            }

            classToExtractFieldsFrom = classToExtractFieldsFrom.getSuperClass();
        }

        return result;
    }

    private static void collectFieldsInClass(final PsiElement element, final PsiClass accessObjectClass,
            final PsiClass clazz, final List<PsiFieldMember> result) {
        final List<PsiFieldMember> classFieldMembers = new ArrayList<PsiFieldMember>();
        final PsiResolveHelper helper = JavaPsiFacade.getInstance(clazz.getProject()).getResolveHelper();
        for (final PsiField field : clazz.getFields()) {

            // check access to the field from the builder container class (eg. private superclass fields)
            if (helper.isAccessible(field, accessObjectClass, clazz)
                    && !PsiTreeUtil.isAncestor(field, element, false)) {

                // remove static fields
                if (field.hasModifierProperty(PsiModifier.STATIC)) {
                    continue;
                }

                // remove any uppercase fields
                if (!hasLowerCaseChar(field.getName())) {
                    continue;
                }

                // remove any logging fields
                if ("org.apache.log4j.Logger".equals(field.getType().getCanonicalText())
                        || "org.apache.logging.log4j.Logger".equals(field.getType().getCanonicalText())
                        || "java.util.logging.Logger".equals(field.getType().getCanonicalText())
                        || "org.slf4j.Logger".equals(field.getType().getCanonicalText())
                        || "ch.qos.logback.classic.Logger".equals(field.getType().getCanonicalText())
                        || "net.sf.microlog.core.Logger".equals(field.getType().getCanonicalText())
                        || "org.apache.commons.logging.Log".equals(field.getType().getCanonicalText())
                        || "org.pmw.tinylog.Logger".equals(field.getType().getCanonicalText())
                        || "org.jboss.logging.Logger".equals(field.getType().getCanonicalText())
                        || "jodd.log.Logger".equals(field.getType().getCanonicalText())) {
                    continue;
                }

                if (field.hasModifierProperty(PsiModifier.FINAL)) {
                    if (field.getInitializer() != null) {
                        continue; // remove final fields that are assigned in the declaration
                    }

                    if (!accessObjectClass.isEquivalentTo(clazz)) {
                        continue; // remove final superclass fields
                    }
                }

                final PsiClass containingClass = field.getContainingClass();
                classFieldMembers.add(new PsiFieldMember(field,
                        TypeConversionUtil.getSuperClassSubstitutor(containingClass, clazz, PsiSubstitutor.EMPTY)));
            }
        }

        result.addAll(0, classFieldMembers);
    }

    /**
     * Capitalize the first letter of a string.
     *
     * @param   s  the string to capitalize
     *
     * @return  the capitalized string
     */
    private static String capitalize(final String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Does the string have a lowercase character?
     *
     * @param   s  the string to test.
     *
     * @return  true if the string has a lowercase character, false if not.
     */
    private static boolean hasLowerCaseChar(final String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLowerCase(s.charAt(i))) {
                return true;
            }
        }

        return false;
    }
}
