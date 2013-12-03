package org.jetbrains.plugins.innerbuilder;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.generation.*;
import com.intellij.ide.util.MemberChooser;
import com.intellij.lang.LanguageCodeInsightActionHandler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GenerateInnerBuilderHandler implements LanguageCodeInsightActionHandler {
    public static String BUILDER_CLASS_NAME = "Builder";
    private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.innerbuilder.GenerateInnerBuilderWorker");

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

        final List<PsiElementClassMember> members = chooseFields(file, editor, project);
        if (members == null || members.isEmpty()) return;

        ApplicationManager.getApplication().runWriteAction(new Runnable() {
            CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
            PsiElementFactory psiElementFactory = JavaPsiFacade.getInstance(project).getElementFactory();

            @Override
            public void run() {
                int offset = editor.getCaretModel().getOffset();
                PsiElement context = file.findElementAt(offset);
                if (context == null) return;
                PsiClass clazz = PsiTreeUtil.getParentOfType(context, PsiClass.class, false);
                if (clazz == null || clazz.isInterface()) return;

                PsiClass builderClass = clazz.findInnerClassByName(BUILDER_CLASS_NAME, false);
                if (builderClass == null) {
                    builderClass = (PsiClass) clazz.add(psiElementFactory.createClass(BUILDER_CLASS_NAME));
                    // builder classes are static and final
                    builderClass.getModifierList().setModifierProperty(PsiModifier.STATIC, true);
                    builderClass.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
                }

                StringBuilder constructorTakingBuilder = new StringBuilder();
                constructorTakingBuilder.append("private ");
                constructorTakingBuilder.append(clazz.getName()).append("(Builder builder) {");

                for (PsiElementClassMember member : members) {
                    PsiField field = ((PsiFieldMember) member).getElement();

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
                for (PsiElementClassMember member : members) {
                    PsiField field = ((PsiFieldMember) member).getElement();

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
                for (PsiElementClassMember member : members) {
                    PsiField field = ((PsiFieldMember) member).getElement();

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
                StringBuilder buildMethod = new StringBuilder("public ")
                        .append(clazz.getName())
                        .append(" build() { return new ")
                        .append(clazz.getName())
                        .append("(this);}");

                addOrReplaceMethod(builderClass, buildMethod.toString());

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

    private PsiGenerationInfo<PsiMethod> generateDelegatePrototype(PsiMethodMember methodCandidate, PsiElement target) throws IncorrectOperationException {
        PsiMethod method = GenerateMembersUtil.substituteGenericMethod(methodCandidate.getElement(), methodCandidate.getSubstitutor());
        clearMethod(method);

        clearModifiers(method);

        @NonNls StringBuffer call = new StringBuffer();

        PsiModifierList modifierList = null;

        if (method.getReturnType() != PsiType.VOID) {
            call.append("return ");
        }

        boolean isMethodStatic = methodCandidate.getElement().hasModifierProperty(PsiModifier.STATIC);
        if (target instanceof PsiField) {
            PsiField field = (PsiField) target;
            modifierList = field.getModifierList();
            final String name = field.getName();

            final PsiParameter[] parameters = method.getParameterList().getParameters();
            for (PsiParameter parameter : parameters) {
                if (name.equals(parameter.getName())) {
                    call.append("this.");
                    break;
                }

                call.append(name);
            }
            call.append(".");
        } else if (target instanceof PsiMethod) {
            PsiMethod m = (PsiMethod) target;
            modifierList = m.getModifierList();
            call.append(m.getName());
            call.append("().");
        }

        call.append(method.getName());
        call.append("(");
        final PsiParameter[] parameters = method.getParameterList().getParameters();
        for (int j = 0; j < parameters.length; j++) {
            PsiParameter parameter = parameters[j];
            if (j > 0) call.append(",");
            call.append(parameter.getName());
        }
        call.append(");");

        final PsiManager psiManager = method.getManager();
        PsiStatement stmt = JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory().createStatementFromText(call.toString(), method);
        stmt = (PsiStatement) CodeStyleManager.getInstance(psiManager.getProject()).reformat(stmt);
        method.getBody().add(stmt);

        for (PsiAnnotation annotation : methodCandidate.getElement().getModifierList().getAnnotations()) {
            method.getModifierList().add(annotation.copy());
        }

        if (isMethodStatic || modifierList != null && modifierList.hasModifierProperty(PsiModifier.STATIC)) {
            PsiUtil.setModifierProperty(method, PsiModifier.STATIC, true);
        }

        PsiUtil.setModifierProperty(method, PsiModifier.PUBLIC, true);

        final PsiClass targetClass = ((PsiMember) target).getContainingClass();
        LOG.assertTrue(targetClass != null);
        PsiMethod overridden = targetClass.findMethodBySignature(method, true);
        if (overridden != null && overridden.getContainingClass() != targetClass) {
            OverrideImplementUtil.annotateOnOverrideImplement(method, targetClass, overridden);
        }

        return new PsiGenerationInfo<PsiMethod>(method);
    }

    private void clearMethod(PsiMethod method) throws IncorrectOperationException {
        LOG.assertTrue(!method.isPhysical());
        PsiCodeBlock codeBlock = JavaPsiFacade.getInstance(method.getProject()).getElementFactory().createCodeBlock();
        if (method.getBody() != null) {
            method.getBody().replace(codeBlock);
        } else {
            method.add(codeBlock);
        }
    }

    private static void clearModifiers(PsiMethod method) throws IncorrectOperationException {
        final PsiElement[] children = method.getModifierList().getChildren();
        for (PsiElement child : children) {
            if (child instanceof PsiKeyword) child.delete();
        }
    }

    public static boolean isApplicable(PsiFile file, Editor editor) {
        List<PsiElementClassMember> targetElements = getTargetElements(file, editor);
        return targetElements != null && targetElements.size() > 0;
    }

    @Nullable
    private static List<PsiElementClassMember> chooseFields(PsiFile file, Editor editor, Project project) {
        final List<PsiElementClassMember> targetElements = getTargetElements(file, editor);
        if (targetElements == null || targetElements.size() == 0) return null;
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
            MemberChooser<PsiElementClassMember> chooser = new MemberChooser<PsiElementClassMember>(targetElements.toArray(new PsiElementClassMember[targetElements.size()]), false, true, project);
            chooser.setTitle("Choose fields to be included in Builder");
            chooser.setCopyJavadocVisible(false);
            chooser.show();

            if (chooser.getExitCode() != DialogWrapper.OK_EXIT_CODE) return null;

            return chooser.getSelectedElements();
        } else {
            return targetElements;
        }
    }

    @Nullable
    private static List<PsiElementClassMember> getTargetElements(PsiFile file, Editor editor) {
        int offset = editor.getCaretModel().getOffset();
        PsiElement element = file.findElementAt(offset);
        if (element == null) return null;
        PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (aClass == null) return null;

        List<PsiElementClassMember> result = new ArrayList<PsiElementClassMember>();

        while (aClass != null) {
            collectTargetsInClass(element, aClass, result);
            if (aClass.hasModifierProperty(PsiModifier.STATIC)) break;
            aClass = PsiTreeUtil.getParentOfType(aClass, PsiClass.class, true);
        }

        return result;
    }

    private static void collectTargetsInClass(PsiElement element, final PsiClass aClass, List<PsiElementClassMember> result) {
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
                result.add(new PsiFieldMember(field, TypeConversionUtil.getSuperClassSubstitutor(containingClass, aClass, PsiSubstitutor.EMPTY)));
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
