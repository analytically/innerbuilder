package org.jetbrains.plugins.innerbuilder;

import org.jetbrains.annotations.NotNull;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;

import com.intellij.psi.PsiFile;

/**
 * The IntelliJ IDEA action for this plugin, generates an inner builder class as described in Effective Java.
 *
 * @author  Mathias Bogaert
 */
public class InnerBuilderAction extends BaseCodeInsightAction {
    private final GenerateInnerBuilderHandler handler = new GenerateInnerBuilderHandler();

    @NotNull
    @Override
    protected CodeInsightActionHandler getHandler() {
        return handler;
    }

    @Override
    protected boolean isValidForFile(@NotNull final Project project, @NotNull final Editor editor,
            @NotNull final PsiFile file) {
        return handler.isValidFor(editor, file);
    }
}
