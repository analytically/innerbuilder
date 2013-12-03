package org.jetbrains.plugins.innerbuilder;

import com.intellij.codeInsight.generation.PsiFieldMember;
import com.intellij.ide.util.MemberChooser;
import com.intellij.ide.util.MemberChooserBuilder;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Mathias Bogaert
 */
public class GenerateInnerBuilderActionHandlerImpl extends EditorWriteActionHandler implements GenerateInnerBuilderActionHandler {

    public void executeWriteAction(Editor editor, DataContext dataContext) {
        final Project project = editor.getProject();
        assert project != null;

        PsiClass clazz = getSubjectClass(editor, dataContext);
        assert clazz != null;

        doExecuteAction(project, clazz, editor);
    }

    private void doExecuteAction(@NotNull final Project project, @NotNull final PsiClass clazz, final Editor editor) {
        final MemberChooserBuilder<PsiFieldMember> builder = new MemberChooserBuilder<PsiFieldMember>(project);
        builder.overrideAnnotationVisible(false);
        builder.setTitle("Choose fields to be included in Builder");

        PsiFieldMember[] dialogMembers = buildFieldsToShow(clazz);
        final MemberChooser<PsiFieldMember> dialog = builder.createBuilder(dialogMembers);
        dialog.setCopyJavadocVisible(false);
        dialog.selectElements(filterDeprecated(dialogMembers));

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                dialog.show();

                if (MemberChooser.OK_EXIT_CODE == dialog.getExitCode()) {
                    if (dialog.getSelectedElements() != null && !dialog.getSelectedElements().isEmpty()) {
                        List<PsiField> selectedFields = new ArrayList<PsiField>();
                        for (PsiFieldMember fieldMember : dialog.getSelectedElements()) {
                            selectedFields.add(fieldMember.getElement());
                        }

                        GenerateInnerBuilderWorker.executeGenerateActionLater(clazz, editor, selectedFields);
                    }
                }
            }
        });
    }

    private PsiFieldMember[] buildFieldsToShow(PsiClass clazz) {
        PsiField[] filteredFields = filterAvailableFields(clazz);

        PsiFieldMember[] members = new PsiFieldMember[filteredFields.length];
        for (int i = 0; i < filteredFields.length; i++) {
            members[i] = new PsiFieldMember(filteredFields[i]);
        }
        return members;
    }

    private PsiFieldMember[] filterDeprecated(PsiFieldMember[] fields) {
        PsiFieldMember[] filteredFields = new PsiFieldMember[fields.length];
        for (int i = 0; i < fields.length; i++) {
            if (!fields[i].getElement().isDeprecated()) {
                filteredFields[i] = fields[i];
            }
        }
        return filteredFields;
    }

    @Override
    public boolean isEnabled(Editor editor, DataContext dataContext) {
        return getSubjectClass(editor, dataContext) != null;
    }

    @Nullable
    private PsiClass getSubjectClass(Editor editor, DataContext dataContext) {
        PsiFile file = LangDataKeys.PSI_FILE.getData(dataContext);
        if (file == null) return null;

        int offset = editor.getCaretModel().getOffset();
        PsiElement context = file.findElementAt(offset);

        if (context == null) return null;

        PsiClass clazz = PsiTreeUtil.getParentOfType(context, PsiClass.class, false);
        if (clazz == null) {
            return null;
        }

        // must not be an interface
        return clazz.isInterface() ? null : clazz;
    }

    public PsiField[] filterAvailableFields(PsiClass clazz) {
        List<PsiField> availableFields = new ArrayList<PsiField>();

        // filter out constant, static and logger fields
        PsiField[] fields = clazz.getFields();
        for (PsiField field : fields) {
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

            // remove final fields that are assigned in the declaration
            if(list.hasModifierProperty(PsiModifier.FINAL) && field.getInitializer() != null) {
                continue;
            }

            availableFields.add(field);
        }

        return availableFields.toArray(new PsiField[availableFields.size()]);
    }

    /**
     * Does the string have a lowercase character?
     *
     * @param s the string to test.
     * @return true if the string has a lowercase character, false if not.
     */
    private boolean hasLowerCaseChar(String s) {
        char[] chars = s.toCharArray();
        for (char c : chars) {
            if (Character.isLowerCase(c)) {
                return true;
            }
        }
        return false;
    }
}
