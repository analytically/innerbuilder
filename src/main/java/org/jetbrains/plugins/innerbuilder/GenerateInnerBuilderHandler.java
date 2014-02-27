package org.jetbrains.plugins.innerbuilder;

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
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.ui.NonFocusableCheckBox;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GenerateInnerBuilderHandler implements LanguageCodeInsightActionHandler {
    @NonNls
    private static final String BUILDER_CLASS_NAME = "Builder";

    @NonNls
    private static final String JAVA_DOT_LANG = "java.lang.";

    @NonNls
    private static final String PROP_NEWBUILDERMETHOD = "GenerateInnerBuilder.";

    @NonNls
    private static final String PROP_USEWITHNOTATION = "GenerateInnerBuilder.useWithNotation";

    @NonNls
    private static final String PROP_COPYCONSTRUCTOR = "GenerateInnerBuilder.copyConstructor";

    @Override
    public boolean isValidFor(Editor editor, PsiFile file) {
        if (!(file instanceof PsiJavaFile)) return false;
        return OverrideImplementUtil.getContextClass(editor.getProject(), editor, file, false) != null && isApplicable(file, editor);
    }

    @Override
    public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
        if (!CodeInsightUtilBase.prepareEditorForWrite(editor)) return;
        if (!FileDocumentManager.getInstance().requestWriting(editor.getDocument(), project)) {
            return;
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments();

        final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        final List<PsiFieldMember> fieldMembers = chooseFields(file, editor, project, propertiesComponent);
        if (fieldMembers == null || fieldMembers.isEmpty()) return;

        ApplicationManager.getApplication().runWriteAction(new Runnable() {
            CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
            PsiElementFactory psiElementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

            @Override
            public void run() {
                PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
                PsiClass clazz = PsiTreeUtil.getParentOfType(element, PsiClass.class);

                PsiClass builderClass = clazz.findInnerClassByName(BUILDER_CLASS_NAME, false);
                if (builderClass == null) {
                    builderClass = (PsiClass) clazz.add(psiElementFactory.createClass(BUILDER_CLASS_NAME));

                    // builder classes are static and final
                    builderClass.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
                    builderClass.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
                }

                StringBuilder constructorTakingBuilder = new StringBuilder();
                constructorTakingBuilder.append("private ").append(clazz.getName()).append("(").append(builderClass.getName()).append(" builder) {");

                for (PsiFieldMember member : fieldMembers) {
                    PsiField field = member.getElement();

                    final PsiMethod setterPrototype = PropertyUtil.generateSetterPrototype(field);
                    final PsiMethod setter = clazz.findMethodBySignature(setterPrototype, true);

                    if (setter == null || field.getModifierList().hasModifierProperty(PsiModifier.FINAL)) {
                        constructorTakingBuilder.append(field.getName()).append("= builder.").append(field.getName()).append(";");
                    } else {
                        constructorTakingBuilder.append(setter.getName()).append("(builder.").append(field.getName()).append(");");
                    }
                }
                constructorTakingBuilder.append("}");
                addMethod(clazz, null, constructorTakingBuilder.toString(), true);

                StringBuilder typedFinalFields = new StringBuilder();
                StringBuilder untypedFinalFields = new StringBuilder();
                StringBuilder untypedFinalFieldsCopy = new StringBuilder();
                List<PsiFieldMember> finalFields = new ArrayList<PsiFieldMember>();
                List<PsiFieldMember> nonFinalFields = new ArrayList<PsiFieldMember>();

                PsiElement addedField = null;
                for (Iterator<PsiFieldMember> iterator = fieldMembers.iterator(); iterator.hasNext(); ) {
                    PsiFieldMember member = iterator.next();
                    PsiField field = member.getElement();

                    addedField = addField(builderClass, addedField, field.getName(), field.getType());

                    if (field.hasModifierProperty(PsiModifier.FINAL)) {
                        if (!finalFields.isEmpty()) {
                            typedFinalFields.append(", ");
                            untypedFinalFields.append(", ");
                            untypedFinalFieldsCopy.append(", ");
                        }

                        finalFields.add(member);
                        ((PsiField) addedField).getModifierList().setModifierProperty(PsiModifier.FINAL, true);

                        typedFinalFields.append(member.getElement().getType().getCanonicalText()).append(" ").append(member.getElement().getName());
                        untypedFinalFields.append(member.getElement().getName());
                        untypedFinalFieldsCopy.append("copy.").append(member.getElement().getName());
                    } else {
                        nonFinalFields.add(member);
                    }
                }

                boolean newBuilderMethod = propertiesComponent.getBoolean(PROP_NEWBUILDERMETHOD, false);
                boolean copyConstructor = propertiesComponent.getBoolean(PROP_COPYCONSTRUCTOR, false);
                boolean withNotation = propertiesComponent.getBoolean(PROP_USEWITHNOTATION, false);

                // builder constructor, accepting the final fields
                StringBuilder builderConstructorText = new StringBuilder();
                String builderConstructorContructorAccess = newBuilderMethod ? "private" : "public";
                builderConstructorText.append(builderConstructorContructorAccess + " " + builderClass.getName() + "(").append(typedFinalFields).append(") {");
                for (PsiFieldMember field : finalFields) {
                    builderConstructorText.append("this.").append(field.getElement().getName()).append("=").append(field.getElement().getName()).append(";");
                }
                builderConstructorText.append("}");
                addMethod(builderClass, null, builderConstructorText.toString());

                if (newBuilderMethod) {
                    StringBuilder newBuilderText = new StringBuilder();
                    newBuilderText.append("public static " + builderClass.getName() + " newBuilder(").append(typedFinalFields).append(") {");
                    newBuilderText.append("return new ").append(builderClass.getName()).append("(").append(untypedFinalFields).append(");");
                    newBuilderText.append("}");
                    addMethod(clazz, null, newBuilderText.toString());
                }

                // COPY CONSTRUCTOR
                if (copyConstructor) {
                    StringBuilder copyConstructorText = new StringBuilder();

                    if (newBuilderMethod) {
                        copyConstructorText.append("public static ").append(builderClass.getName()).append(" newBuilder(");
                        copyConstructorText.append(clazz.getQualifiedName()).append(" copy) { ").append(builderClass.getName()).append(" builder = new ")
                                .append(builderClass.getName()).append("(" + untypedFinalFieldsCopy + ");");
                    } else {
                        copyConstructorText.append("public ").append(builderClass.getName()).append("(");
                        copyConstructorText.append(clazz.getQualifiedName()).append(" copy) {");
                    }

                    for (PsiFieldMember member : fieldMembers) {
                        if (member.getElement().getModifierList().hasModifierProperty(PsiModifier.FINAL) && newBuilderMethod) continue;
                        if (newBuilderMethod) copyConstructorText.append("builder.");
                        copyConstructorText.append(member.getElement().getName()).append("= copy.").append(member.getElement().getName()).append(";");
                    }
                    if (newBuilderMethod) copyConstructorText.append("return builder;");
                    copyConstructorText.append("}");
                    addMethod(newBuilderMethod ? clazz : builderClass, null, copyConstructorText.toString(), true);
                }

                PsiElement added = null;

                // builder methods
                for (PsiFieldMember member : nonFinalFields) {
                    PsiField field = member.getElement();

                    String methodName = withNotation ? "with" + capitalize(field.getName()) : field.getName();
                    String builderMethod = new StringBuilder().append("public Builder ")
                            .append(methodName).append("(").append(field.getType().getCanonicalText()).append(" ")
                            .append(field.getName()).append("){").append("this.").append(field.getName()).append("=")
                            .append(field.getName()).append(";").append("return this;").append("}").toString();

                    added = addMethod(builderClass, added, builderMethod);
                }

                // builder.build() method
                addMethod(builderClass, null, "public " + clazz.getQualifiedName() + " build() { return new " + clazz.getQualifiedName() + "(this);}");
                codeStyleManager.reformat(builderClass);
            }

            PsiElement addField(PsiClass target, PsiElement after, String name, PsiType type) {
                PsiField existingField = target.findFieldByName(name, false);

                if (existingField == null || !areTypesPresentableEqual(existingField.getType(), type)) {
                    if (existingField != null) existingField.delete();
                    PsiField newField = psiElementFactory.createField(name, type);
                    if (after != null) {
                        return target.addAfter(newField, after);
                    } else {
                        return target.add(newField);
                    }
                } else return existingField;
            }

            PsiElement addMethod(PsiClass target, PsiElement after, String methodText) {
                return addMethod(target, after, methodText, false);
            }

            PsiElement addMethod(PsiClass target, PsiElement after, String methodText, boolean replace) {
                PsiMethod newMethod = psiElementFactory.createMethodFromText(methodText, null);
                PsiMethod existingMethod = target.findMethodBySignature(newMethod, false);

                if (existingMethod == null && newMethod.isConstructor()) {
                    for (PsiMethod constructor : target.getConstructors()) {
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
                } else if (replace) existingMethod.replace(newMethod);
                return existingMethod;
            }

            boolean areParameterListsEqual(PsiParameterList paramList1, PsiParameterList paramList2) {
                if (paramList1.getParametersCount() != paramList2.getParametersCount()) return false;

                PsiParameter[] param1Params = paramList1.getParameters();
                PsiParameter[] param2Params = paramList2.getParameters();
                for (int i = 0; i < param1Params.length; i++) {
                    PsiParameter param1Param = param1Params[i];
                    PsiParameter param2Param = param2Params[i];

                    if (!areTypesPresentableEqual(param1Param.getType(), param2Param.getType())) {
                        return false;
                    }
                }
                return true;
            }

            boolean areTypesPresentableEqual(PsiType type1, PsiType type2) {
                if (type1 != null && type2 != null) {
                    String type1Canonical = stripJavaLang(type1.getPresentableText());
                    String type2Canonical = stripJavaLang(type2.getPresentableText());
                    return type1Canonical.equals(type2Canonical);
                }
                return false;
            }

            private String stripJavaLang(String typeString) {
                return typeString.startsWith(JAVA_DOT_LANG) ? typeString.substring(JAVA_DOT_LANG.length()) : typeString;
            }
        });
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    public static boolean isApplicable(PsiFile file, Editor editor) {
        List<PsiFieldMember> targetElements = getFields(file, editor);
        return targetElements != null && targetElements.size() > 0;
    }

    @Nullable
    private static List<PsiFieldMember> chooseFields(PsiFile file, Editor editor, Project project, final PropertiesComponent propertiesComponent) {
        List<PsiFieldMember> members = getFields(file, editor);
        if (members == null || members.size() == 0) return null;
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
            final JCheckBox newBuilderMethodCheckbox = new NonFocusableCheckBox("Generate newBuilder() method");
            newBuilderMethodCheckbox.setMnemonic('n');
            newBuilderMethodCheckbox.setToolTipText("Generate a method to construct the builder (no builder constructor)");
            newBuilderMethodCheckbox.setSelected(propertiesComponent.isTrueValue(PROP_NEWBUILDERMETHOD));
            newBuilderMethodCheckbox.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    propertiesComponent.setValue(PROP_NEWBUILDERMETHOD, Boolean.toString(newBuilderMethodCheckbox.isSelected()));
                }
            });

            final JCheckBox copyConstructorCheckbox = new NonFocusableCheckBox("Generate copy constructor");
            copyConstructorCheckbox.setMnemonic('o');
            newBuilderMethodCheckbox.setToolTipText("Generate a copy constructor or copy method");
            copyConstructorCheckbox.setSelected(propertiesComponent.isTrueValue(PROP_COPYCONSTRUCTOR));
            copyConstructorCheckbox.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    propertiesComponent.setValue(PROP_COPYCONSTRUCTOR, Boolean.toString(copyConstructorCheckbox.isSelected()));
                }
            });

            final JCheckBox withNotationCheckbox = new NonFocusableCheckBox("Use 'with...' notation");
            withNotationCheckbox.setMnemonic('w');
            withNotationCheckbox.setToolTipText("Generate builder methods that start with 'with', eg. builder.withName(String name)");
            withNotationCheckbox.setSelected(propertiesComponent.isTrueValue(PROP_USEWITHNOTATION));
            withNotationCheckbox.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    propertiesComponent.setValue(PROP_USEWITHNOTATION, Boolean.toString(withNotationCheckbox.isSelected()));
                }
            });

            PsiFieldMember[] memberArray = members.toArray(new PsiFieldMember[members.size()]);

            MemberChooser<PsiFieldMember> chooser = new MemberChooser<PsiFieldMember>(memberArray, false, true, project, null,
                    new JCheckBox[]{newBuilderMethodCheckbox, copyConstructorCheckbox, withNotationCheckbox});

            chooser.setTitle("Select Fields to Include in Builder");
            chooser.selectElements(memberArray);
            chooser.show();

            if (chooser.getExitCode() != DialogWrapper.OK_EXIT_CODE) return null;

            return chooser.getSelectedElements();
        } else {
            return members;
        }
    }

    @Nullable
    private static List<PsiFieldMember> getFields(PsiFile file, Editor editor) {
        int offset = editor.getCaretModel().getOffset();
        PsiElement element = file.findElementAt(offset);
        if (element == null) return null;
        PsiClass clazz = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (clazz == null || clazz.hasModifierProperty(PsiModifier.ABSTRACT)) return null;

        List<PsiFieldMember> result = new ArrayList<PsiFieldMember>();

        PsiClass classToExtractFieldsFrom = clazz;
        while (classToExtractFieldsFrom != null) {
            collectFieldsInClass(element, clazz, classToExtractFieldsFrom, result);
            if (classToExtractFieldsFrom.hasModifierProperty(PsiModifier.STATIC)) break;
            classToExtractFieldsFrom = classToExtractFieldsFrom.getSuperClass();
        }

        return result;
    }

    private static void collectFieldsInClass(PsiElement element, PsiClass accessObjectClass, PsiClass clazz, List<PsiFieldMember> result) {
        List<PsiFieldMember> classFieldMembers = new ArrayList<PsiFieldMember>();
        PsiResolveHelper helper = JavaPsiFacade.getInstance(clazz.getProject()).getResolveHelper();
        for (PsiField field : clazz.getFields()) {
            // check access to the field from the builder container class (eg. private superclass fields)
            if (helper.isAccessible(field, accessObjectClass, clazz) && !PsiTreeUtil.isAncestor(field, element, false)) {
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
                        || "org.jboss.logging.Logger".equals(field.getType().getCanonicalText())) {
                    continue;
                }

                if (field.hasModifierProperty(PsiModifier.FINAL)) {
                    if (field.getInitializer() != null)
                        continue; // remove final fields that are assigned in the declaration
                    if (!accessObjectClass.isEquivalentTo(clazz)) continue; // remove final superclass fields
                }

                PsiClass containingClass = field.getContainingClass();
                classFieldMembers.add(new PsiFieldMember(field, TypeConversionUtil.getSuperClassSubstitutor(containingClass, clazz, PsiSubstitutor.EMPTY)));
            }
        }

        result.addAll(0, classFieldMembers);
    }

    /**
     * Capitalize the first letter of a string.
     *
     * @param s the string to capitalize
     * @return the capitalized string
     */
    private static String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Does the string have a lowercase character?
     *
     * @param s the string to test.
     * @return true if the string has a lowercase character, false if not.
     */
    private static boolean hasLowerCaseChar(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLowerCase(s.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
