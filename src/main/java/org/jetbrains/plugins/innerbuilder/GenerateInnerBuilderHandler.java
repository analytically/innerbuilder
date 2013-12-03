package org.jetbrains.plugins.innerbuilder;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiFieldMember;
import com.intellij.ide.util.MemberChooser;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GenerateInnerBuilderHandler implements LanguageCodeInsightActionHandler {
    public static String BUILDER_CLASS_NAME = "Builder";

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

        final List<PsiFieldMember> fieldMembers = chooseFields(file, editor, project);
        if (fieldMembers == null || fieldMembers.isEmpty()) return;

        ApplicationManager.getApplication().runWriteAction(new Runnable() {
            CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
            PsiElementFactory psiElementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

            @Override
            public void run() {
                int offset = editor.getCaretModel().getOffset();
                PsiElement element = file.findElementAt(offset);
                PsiClass clazz = PsiTreeUtil.getParentOfType(element, PsiClass.class);

                PsiClass builderClass = clazz.findInnerClassByName(BUILDER_CLASS_NAME, false);
                if (builderClass == null) {
                    builderClass = (PsiClass) clazz.add(psiElementFactory.createClass(BUILDER_CLASS_NAME));

                    // builder classes are static and final
                    builderClass.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
                    builderClass.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
                }

                StringBuilder constructorTakingBuilder = new StringBuilder();
                constructorTakingBuilder.append("private ").append(clazz.getName()).append("(Builder builder) {");

                for (PsiFieldMember member : fieldMembers) {
                    PsiField field = member.getElement();

                    final PsiMethod setterPrototype = PropertyUtil.generateSetterPrototype(field);
                    final PsiMethod setter = clazz.findMethodBySignature(setterPrototype, true);

                    if (setter == null) {
                        constructorTakingBuilder.append(field.getName()).append("= builder.").append(field.getName()).append(";");
                    } else {
                        constructorTakingBuilder.append(setter.getName()).append("(builder.").append(field.getName()).append(");");
                    }
                }
                constructorTakingBuilder.append("}");
                codeStyleManager.reformat(addOrReplaceMethod(clazz, constructorTakingBuilder.toString()));

                // final fields become constructor fields in the builder
                List<PsiField> finalFields = new ArrayList<PsiField>();
                for (PsiFieldMember member : fieldMembers) {
                    PsiField field = member.getElement();

                    if (field.hasModifierProperty(PsiModifier.FINAL)) {
                        finalFields.add(field);
                    }
                }

                // add all final fields to the builder
                for (PsiField field : finalFields) {
                    if (!hasField((builderClass), field)) {
                        PsiField builderField = psiElementFactory.createField(field.getName(), field.getType());
                        builderField.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
                        builderClass.add(builderField);
                    }
                }

                // add all non-final fields to the builder
                List<PsiField> nonFinalFields = new ArrayList<PsiField>();
                for (PsiFieldMember member : fieldMembers) {
                    PsiField field = member.getElement();

                    if (!hasField((builderClass), field) && !field.hasModifierProperty(PsiModifier.FINAL)) {
                        nonFinalFields.add(field);

                        PsiField builderField = psiElementFactory.createField(field.getName(), field.getType());
                        builderClass.add(builderField);
                    }
                }

                // builder constructor, accepting the final fields
                StringBuilder constructor = new StringBuilder();
                constructor.append("public Builder(");

                for (Iterator<PsiField> iterator = finalFields.iterator(); iterator.hasNext(); ) {
                    PsiField field = iterator.next();
                    constructor.append(field.getTypeElement().getType().getCanonicalText()).append(" ").append(field.getName());

                    if (iterator.hasNext()) {
                        constructor.append(",");
                    }
                }
                constructor.append(") {");
                for (PsiField field : finalFields) {
                    constructor.append("this.").append(field.getName()).append("=").append(field.getName()).append(";");
                }
                constructor.append("}");
                addOrReplaceMethod(builderClass, constructor.toString());

                // copy builder constructor, accepting a clazz instance
                StringBuilder copyConstructor = new StringBuilder();
                copyConstructor.append("public Builder(").append(clazz.getName()).append(" copy) {");
                for (PsiField field : finalFields) {
                    copyConstructor.append(field.getName()).append("= copy.").append(field.getName()).append(";");
                }
                for (PsiField field : nonFinalFields) {
                    copyConstructor.append(field.getName()).append("= copy.").append(field.getName()).append(";");
                }
                copyConstructor.append("}");
                addOrReplaceMethod(builderClass, copyConstructor.toString());

                // builder methods
                for (PsiField field : nonFinalFields) {
                    String setMethodText = "public "
                            + "Builder" + " " + field.getName() + "(" + field.getTypeElement().getType().getCanonicalText()
                            + " " + field.getName() + "){"
                            + "this." + field.getName() + "=" + field.getName() + ";"
                            + "return this;"
                            + "}";
                    addOrReplaceMethod(builderClass, setMethodText);
                }

                // builder.build() method
                addOrReplaceMethod(builderClass, "public " + clazz.getName() + " build() { return new " + clazz.getName() + "(this);}");
                codeStyleManager.reformat(builderClass);
            }

            boolean hasField(PsiClass clazz, PsiField field) {
                return clazz.findFieldByName(field.getName(), false) != null;
            }

            PsiMethod addOrReplaceMethod(PsiClass target, String methodText) {
                PsiMethod newMethod = psiElementFactory.createMethodFromText(methodText, null);
                PsiMethod existingMethod = target.findMethodBySignature(newMethod, false);

                if (existingMethod != null) {
                    existingMethod.replace(newMethod);
                } else {
                    target.add(newMethod);
                }
                return newMethod;
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
    private static List<PsiFieldMember> chooseFields(PsiFile file, Editor editor, Project project) {
        final List<PsiFieldMember> targetElements = getFields(file, editor);
        if (targetElements == null || targetElements.size() == 0) return null;
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
            MemberChooser<PsiFieldMember> chooser = new MemberChooser<PsiFieldMember>(targetElements.toArray(new PsiFieldMember[targetElements.size()]), false, true, project);
            chooser.setTitle("Choose fields to be included in Builder");
            chooser.setCopyJavadocVisible(false);
            chooser.selectElements(targetElements.toArray(new PsiFieldMember[targetElements.size()]));
            chooser.show();

            if (chooser.getExitCode() != DialogWrapper.OK_EXIT_CODE) return null;

            return chooser.getSelectedElements();
        } else {
            return targetElements;
        }
    }

    @Nullable
    private static List<PsiFieldMember> getFields(PsiFile file, Editor editor) {
        int offset = editor.getCaretModel().getOffset();
        PsiElement element = file.findElementAt(offset);
        if (element == null) return null;
        PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (aClass == null) return null;

        List<PsiFieldMember> result = new ArrayList<PsiFieldMember>();

        while (aClass != null) {
            collectFieldsInClass(element, aClass, result);
            if (aClass.hasModifierProperty(PsiModifier.STATIC)) break;
            aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class, true);
        }

        return result;
    }

    private static void collectFieldsInClass(PsiElement element, final PsiClass aClass, List<PsiFieldMember> result) {
        final PsiField[] fields = aClass.getAllFields();
        PsiResolveHelper helper = JavaPsiFacade.getInstance(aClass.getProject()).getResolveHelper();
        for (PsiField field : fields) {
            final PsiType type = field.getType();
            if (helper.isAccessible(field, aClass, aClass) && type instanceof PsiClassType && !PsiTreeUtil.isAncestor(field, element, false)) {
                PsiModifierList list = field.getModifierList();
                // remove any fields without modifiers
                if (list == null) {
                    continue;
                }

                // remove static fields
                if (list.hasModifierProperty(PsiModifier.STATIC)) {
                    continue;
                }

                // remove final fields that are assigned in the declaration
                if (list.hasModifierProperty(PsiModifier.FINAL) && field.getInitializer() != null) {
                    continue;
                }

                // remove any uppercase fields
                if (!hasLowerCaseChar(field.getName())) {
                    continue;
                }

                // remove any logging fields
                if ("org.apache.log4j.Logger".equals(field.getType().getCanonicalText())
                        || "java.util.logging.Logger".equals(field.getType().getCanonicalText())
                        || "org.slf4j.Logger".equals(field.getType().getCanonicalText())
                        || "net.sf.microlog.core.Logger".equals(field.getType().getCanonicalText())
                        || "org.apache.commons.logging.Log".equals(field.getType().getCanonicalText())) {
                    continue;
                }

                final PsiClass containingClass = field.getContainingClass();
                result.add(0, new PsiFieldMember(field, TypeConversionUtil.getSuperClassSubstitutor(containingClass, aClass, PsiSubstitutor.EMPTY)));
            }
        }
    }

    /**
     * Does the string have a lowercase character?
     *
     * @param s the string to test.
     * @return true if the string has a lowercase character, false if not.
     */
    private static boolean hasLowerCaseChar(String s) {
        char[] chars = s.toCharArray();
        for (char c : chars) {
            if (Character.isLowerCase(c)) {
                return true;
            }
        }
        return false;
    }
}
