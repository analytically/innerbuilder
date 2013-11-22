package org.jetbrains.plugins.innerbuilder;

import com.intellij.openapi.editor.actionSystem.EditorAction;

/**
 * The IDEA action for this plugin, handles the generation of an inner <code>Builder</code> class.
 *
 * @author Mathias Bogaert
 */
public class GenerateInnerBuilderAction extends EditorAction {
    public GenerateInnerBuilderAction() {
        super(new GenerateInnerBuilderActionHandlerImpl()); // register our action handler
    }
}
