package org.jetbrains.plugins.innerbuilder;

import com.intellij.codeInsight.generation.PsiFieldMember;
import com.intellij.ide.util.MemberChooser;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiFile;
import com.intellij.ui.NonFocusableCheckBox;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

import static org.jetbrains.plugins.innerbuilder.InnerBuilderConstants.PROP_COPYCONSTRUCTOR;
import static org.jetbrains.plugins.innerbuilder.InnerBuilderConstants.PROP_FINALSETTERS;
import static org.jetbrains.plugins.innerbuilder.InnerBuilderConstants.PROP_FINAL_PARAMETERS;
import static org.jetbrains.plugins.innerbuilder.InnerBuilderConstants.PROP_JSR305_ANNOTATIONS;
import static org.jetbrains.plugins.innerbuilder.InnerBuilderConstants.PROP_NEWBUILDERMETHOD;
import static org.jetbrains.plugins.innerbuilder.InnerBuilderConstants.PROP_WITHNOTATION;

public class InnerBuilderOptionsDialog {
    @Nullable
    public static List<PsiFieldMember> selectFieldsAndOptions(final List<PsiFieldMember> members, final Project project,
                                                              final PropertiesComponent propertiesComponent) {
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
            finalParametersCheckbox.setMnemonic('p');
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

}
