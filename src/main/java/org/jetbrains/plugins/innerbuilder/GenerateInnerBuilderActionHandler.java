package org.jetbrains.plugins.innerbuilder;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;

/**
 * Main interface for the plugin.
 * <p/>
 * This handler is the entry point to execute the action from different situations.
 *
 * @author Mathias Bogaert
 */
public interface GenerateInnerBuilderActionHandler {
    /**
     * The action that does the actual generation of the code.
     * <p/>
     * This is called automatically by IDEA when user invokes the builderPlugin from the builderPlugin menu.
     *
     * @param editor      the current editor.
     * @param dataContext the current data context.
     */
    void executeWriteAction(Editor editor, DataContext dataContext);
}
