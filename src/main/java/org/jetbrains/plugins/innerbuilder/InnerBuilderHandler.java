package org.jetbrains.plugins.innerbuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiFieldMember;

import com.intellij.ide.util.PropertiesComponent;

import com.intellij.lang.LanguageCodeInsightActionHandler;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;

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

public class InnerBuilderHandler implements LanguageCodeInsightActionHandler {

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

        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
        // psiDocumentManager.commitAllDocuments();
        final Document currentDocument = psiDocumentManager.getDocument(file);
        if (currentDocument == null) {
            return;
        }
        psiDocumentManager.commitDocument(currentDocument);

        final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        final List<PsiFieldMember> existingFields = collectFields(file, editor);
        final List<PsiFieldMember> selectedFields = InnerBuilderOptionsDialog.selectFieldsAndOptions(existingFields, project, propertiesComponent);
        
        if (selectedFields == null || selectedFields.isEmpty()) {
            return;
        }

        ApplicationManager.getApplication().runWriteAction(new InnerBuilderGenerator(project, file, editor, selectedFields));
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    public static boolean isApplicable(final PsiFile file, final Editor editor) {
        final List<PsiFieldMember> targetElements = collectFields(file, editor);
        return targetElements != null && !targetElements.isEmpty();
    }

    @Nullable
    private static List<PsiFieldMember> collectFields(final PsiFile file, final Editor editor) {
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
                if(containingClass != null) {
                    classFieldMembers.add(new PsiFieldMember(field,
                            TypeConversionUtil.getSuperClassSubstitutor(containingClass, clazz, PsiSubstitutor.EMPTY)));
                }
            }
        }

        result.addAll(0, classFieldMembers);
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
