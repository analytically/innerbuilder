package org.jetbrains.plugins.innerbuilder;

import com.intellij.codeInsight.generation.PsiFieldMember;
import com.intellij.ide.util.MemberChooser;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.NonFocusableCheckBox;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;

public final class InnerBuilderOptionSelector {
    private static final List<SelectorOption> OPTIONS = createGeneratorOptions();

    private static List<SelectorOption> createGeneratorOptions() {
        final List<SelectorOption> options = new ArrayList<SelectorOption>(8);

        options.add(
                SelectorOption.newBuilder()
                        .withCaption("Generate builder methods for final fields")
                        .withMnemonic('f')
                        .withOption(InnerBuilderOption.FINAL_SETTERS)
                        .build());
        options.add(
                SelectorOption.newBuilder()
                        .withCaption("Generate static newBuilder() method")
                        .withMnemonic('n')
                        .withOption(InnerBuilderOption.NEW_BUILDER_METHOD)
                        .build());
        options.add(
                SelectorOption.newBuilder()
                        .withCaption("Generate builder copy constructor")
                        .withMnemonic('o')
                        .withOption(InnerBuilderOption.COPY_CONSTRUCTOR)
                        .build());
        options.add(
                SelectorOption.newBuilder()
                        .withCaption("Use 'with...' notation")
                        .withMnemonic('w')
                        .withToolTip(
                                "Generate builder methods that start with 'with', for example: "
                                        + "builder.withName(String name)")
                        .withOption(InnerBuilderOption.WITH_NOTATION)
                        .build());
        options.add(
                SelectorOption.newBuilder()
                        .withCaption("Use 'set...' notation")
                        .withMnemonic('t')
                        .withToolTip(
                                "Generate builder methods that start with 'set', for example: "
                                        + "builder.setName(String name)")
                        .withOption(InnerBuilderOption.SET_NOTATION)
                        .build());
        options.add(
                SelectorOption.newBuilder()
                        .withCaption("Add JSR-305 @Nonnull annotation")
                        .withMnemonic('j')
                        .withToolTip(
                                "Add @Nonnull annotations to generated methods and parameters, for example: "
                                        + "@Nonnull public Builder withName(@Nonnull String name) { ... }")
                        .withOption(InnerBuilderOption.JSR305_ANNOTATIONS)
                        .build());
        options.add(
                SelectorOption.newBuilder()
                        .withCaption("Add @SuppressWarnings(\"PMD.AvoidFieldNameMatchingMethodName\") annotation")
                        .withMnemonic('p')
                        .withToolTip(
                                "Add @SuppressWarnings(\"PMD.AvoidFieldNameMatchingMethodName\") annotation to the generated Builder class")
                        .withOption(InnerBuilderOption.PMD_AVOID_FIELD_NAME_MATCHING_METHOD_NAME_ANNOTATION)
                        .build());
        options.add(
                SelectorOption.newBuilder()
                        .withCaption("Add Findbugs @NonNull annotation")
                        .withMnemonic('b')
                        .withToolTip(
                                "Add @NonNull annotations to generated methods and parameters, for example: "
                                        + "@NonNull public Builder withName(@NonNull String name) { ... }")
                        .withOption(InnerBuilderOption.FINDBUGS_ANNOTATION)
                        .build());
        options.add(
                SelectorOption.newBuilder()
                        .withCaption("Add Javadoc")
                        .withMnemonic('c')
                        .withToolTip("Add Javadoc to generated builder class and methods")
                        .withOption(InnerBuilderOption.WITH_JAVADOC)
                        .build());

	options.add(
		SelectorOption.newBuilder()
			.withCaption("Use field names in setter")
			.withMnemonic('s')
			.withToolTip(
				"Generate builder methods that has the same parameter names in setter methods as field names, for example: "
				+ "builder.withName(String fieldName)")
			.withOption(InnerBuilderOption.FIELD_NAMES)
			.build());

        return options;
    }

    private InnerBuilderOptionSelector() {
    }

    @Nullable
    public static List<PsiFieldMember> selectFieldsAndOptions(final List<PsiFieldMember> members,
                                                              final Project project) {
        if (members == null || members.isEmpty()) {
            return null;
        }

        if (ApplicationManager.getApplication().isUnitTestMode()) {
            return members;
        }

        final JCheckBox[] optionCheckBoxes = buildOptionCheckBoxes();

        final PsiFieldMember[] memberArray = members.toArray(new PsiFieldMember[members.size()]);

        final MemberChooser<PsiFieldMember> chooser = new MemberChooser<PsiFieldMember>(memberArray,
                false, // allowEmptySelection
                true,  // allowMultiSelection
                project, null, optionCheckBoxes);

        chooser.setTitle("Select Fields and Options for the Builder");
        chooser.selectElements(memberArray);
        if (chooser.showAndGet()) {
            return chooser.getSelectedElements();
        }

        return null;
    }

    private static JCheckBox[] buildOptionCheckBoxes() {
        final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        final int optionCount = OPTIONS.size();
        final JCheckBox[] checkBoxesArray = new JCheckBox[optionCount];
        for (int i = 0; i < optionCount; i++) {
            checkBoxesArray[i] = buildOptionCheckBox(propertiesComponent, OPTIONS.get(i));
        }

        return checkBoxesArray;
    }

    private static JCheckBox buildOptionCheckBox(final PropertiesComponent propertiesComponent,
                                                 final SelectorOption selectorOption) {
        final InnerBuilderOption option = selectorOption.getOption();

        final JCheckBox optionCheckBox = new NonFocusableCheckBox(selectorOption.getCaption());
        optionCheckBox.setMnemonic(selectorOption.getMnemonic());
        optionCheckBox.setToolTipText(selectorOption.getToolTip());

        final String optionProperty = option.getProperty();
        optionCheckBox.setSelected(propertiesComponent.isTrueValue(optionProperty));
        optionCheckBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(final ItemEvent event) {
                propertiesComponent.setValue(optionProperty, Boolean.toString(optionCheckBox.isSelected()));
            }
        });
        return optionCheckBox;
    }
}

