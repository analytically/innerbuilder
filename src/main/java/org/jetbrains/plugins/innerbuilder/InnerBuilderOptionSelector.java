package org.jetbrains.plugins.innerbuilder;

import com.intellij.codeInsight.generation.PsiFieldMember;
import com.intellij.ide.util.MemberChooser;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.LabeledComponent;
import com.intellij.ui.NonFocusableCheckBox;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class InnerBuilderOptionSelector {
    private static final DropdownListCellRenderer RENDERER = new DropdownListCellRenderer();
    private static final List<SelectorOption> OPTIONS = createGeneratorOptions();

    private static List<SelectorOption> createGeneratorOptions() {
        final List<SelectorOption> options = new ArrayList<>();

        options.add(
            CheckboxSelectorOption.newBuilder()
                .withCaption("Generate builder methods for final fields")
                .withMnemonic('f')
                .withOption(InnerBuilderOption.FINAL_SETTERS)
                .build());
        options.add(
            CheckboxSelectorOption.newBuilder()
                .withCaption("Generate static builder method")
                .withMnemonic('n')
                .withOption(InnerBuilderOption.NEW_BUILDER_METHOD)
                .build());

        options.add(
            DropdownSelectorOption.newBuilder()
                .withOption(InnerBuilderOption.STATIC_BUILDER_DROPDOWN)
                .withCaption("Static builder naming")
                .withToolTip("Select what the static builder method should look like.")
                .withValues(List.of(
                        DropdownSelectorOptionValue.newBuilder()
                            .withCaption("newBuilder()")
                            .withOption(InnerBuilderOption.STATIC_BUILDER_NEW_BUILDER_NAME)
                            .build(),
                        DropdownSelectorOptionValue.newBuilder()
                            .withCaption("builder()")
                            .withOption(InnerBuilderOption.STATIC_BUILDER_BUILDER_NAME)
                            .build(),
                        DropdownSelectorOptionValue.newBuilder()
                            .withCaption("new[ClassName]()")
                            .withOption(InnerBuilderOption.STATIC_BUILDER_NEW_CLASS_NAME)
                            .build(),
                    DropdownSelectorOptionValue.newBuilder()
                        .withCaption("new[ClassName]Builder()")
                        .withOption(InnerBuilderOption.STATIC_BUILDER_NEW_CLASS_NAME_BUILDER)
                        .build()
                    )
                ).build());

        options.add(
            DropdownSelectorOption.newBuilder()
                .withOption(InnerBuilderOption.BUILDER_METHOD_LOCATION_DROPDOWN)
                .withCaption("Builder method location")
                .withToolTip("Select where the builder method should be located.")
                .withValues(List.of(
                        DropdownSelectorOptionValue.newBuilder()
                            .withCaption("Inside parent class")
                            .withOption(InnerBuilderOption.BUILDER_METHOD_IN_PARENT_CLASS)
                            .build(),
                        DropdownSelectorOptionValue.newBuilder()
                            .withCaption("Inside generated Builder class")
                            .withOption(InnerBuilderOption.BUILDER_METHOD_IN_BUILDER)
                            .build()
                    )
                ).build());

        options.add(
            CheckboxSelectorOption.newBuilder()
                .withCaption("Generate builder copy constructor")
                .withMnemonic('o')
                .withOption(InnerBuilderOption.COPY_CONSTRUCTOR)
                .build());
        options.add(
            CheckboxSelectorOption.newBuilder()
                .withCaption("Use 'with...' notation")
                .withMnemonic('w')
                .withToolTip(
                    "Generate builder methods that start with 'with', for example: "
                        + "builder.withName(String name)")
                .withOption(InnerBuilderOption.WITH_NOTATION)
                .build());
        options.add(
            CheckboxSelectorOption.newBuilder()
                .withCaption("Use 'set...' notation")
                .withMnemonic('t')
                .withToolTip(
                    "Generate builder methods that start with 'set', for example: "
                        + "builder.setName(String name)")
                .withOption(InnerBuilderOption.SET_NOTATION)
                .build());
        options.add(
            CheckboxSelectorOption.newBuilder()
                .withCaption("Add JSR-305 @Nonnull annotation")
                .withMnemonic('j')
                .withToolTip(
                    "Add @Nonnull annotations to generated methods and parameters, for example: "
                        + "@Nonnull public Builder withName(@Nonnull String name) { ... }")
                .withOption(InnerBuilderOption.JSR305_ANNOTATIONS)
                .build());
        options.add(
            CheckboxSelectorOption.newBuilder()
                .withCaption("Add @SuppressWarnings(\"PMD.AvoidFieldNameMatchingMethodName\") annotation")
                .withMnemonic('p')
                .withToolTip(
                    "Add @SuppressWarnings(\"PMD.AvoidFieldNameMatchingMethodName\") annotation to the generated Builder class")
                .withOption(InnerBuilderOption.PMD_AVOID_FIELD_NAME_MATCHING_METHOD_NAME_ANNOTATION)
                .build());
        options.add(
            CheckboxSelectorOption.newBuilder()
                .withCaption("Add Javadoc")
                .withMnemonic('c')
                .withToolTip("Add Javadoc to generated builder class and methods")
                .withOption(InnerBuilderOption.WITH_JAVADOC)
                .build());

        options.add(
            CheckboxSelectorOption.newBuilder()
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

        final JComponent[] optionCheckBoxes = buildOptions();

        final PsiFieldMember[] memberArray = members.toArray(new PsiFieldMember[0]);

        final MemberChooser<PsiFieldMember> chooser = new MemberChooser<>(memberArray,
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

    private static JComponent[] buildOptions() {
        final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        final int optionCount = OPTIONS.size();
        final JComponent[] checkBoxesArray = new JComponent[optionCount];
        for (int i = 0; i < optionCount; i++) {
            checkBoxesArray[i] = buildOptions(propertiesComponent, OPTIONS.get(i));
        }

        return checkBoxesArray;
    }

    private static JComponent buildOptions(final PropertiesComponent propertiesComponent,
                                           final SelectorOption selectorOption) {

        if (selectorOption instanceof CheckboxSelectorOption) {
            return buildCheckbox(propertiesComponent, (CheckboxSelectorOption) selectorOption);
        }

        return buildDropdown(propertiesComponent, (DropdownSelectorOption) selectorOption);
    }

    private static JComponent buildCheckbox(PropertiesComponent propertiesComponent, CheckboxSelectorOption selectorOption) {
        final JCheckBox optionCheckBox = new NonFocusableCheckBox(selectorOption.getCaption());
        optionCheckBox.setMnemonic(selectorOption.getMnemonic());
        optionCheckBox.setToolTipText(selectorOption.getToolTip());

        final String optionProperty = selectorOption.getOption().getProperty();
        optionCheckBox.setSelected(propertiesComponent.isTrueValue(optionProperty));
        optionCheckBox.addItemListener(event -> propertiesComponent.setValue(optionProperty, Boolean.toString(optionCheckBox.isSelected())));
        return optionCheckBox;
    }

    private static JComponent buildDropdown(PropertiesComponent propertiesComponent, DropdownSelectorOption selectorOption) {
        final ComboBox<DropdownSelectorOptionValue> comboBox = new ComboBox();
        comboBox.setEditable(false);
        comboBox.setRenderer(RENDERER);
        selectorOption.getValues().forEach(comboBox::addItem);

        comboBox.setSelectedItem(setSelectedComboBoxItem(propertiesComponent, selectorOption));
        comboBox.addItemListener(event -> setPropertiesComponentValue(propertiesComponent, selectorOption, event));

        LabeledComponent<ComboBox<DropdownSelectorOptionValue>> labeledComponent = LabeledComponent.create(comboBox, selectorOption.getCaption());
        labeledComponent.setToolTipText(selectorOption.getToolTip());

        return labeledComponent;
    }

    private static void setPropertiesComponentValue(PropertiesComponent propertiesComponent, DropdownSelectorOption selectorOption, ItemEvent itemEvent) {
        DropdownSelectorOptionValue value = (DropdownSelectorOptionValue) itemEvent.getItem();
        propertiesComponent.setValue(selectorOption.getOption().getProperty(), value.getOption().getProperty());
    }

    private static DropdownSelectorOptionValue setSelectedComboBoxItem(PropertiesComponent propertiesComponent, DropdownSelectorOption selectorOption) {
        String selectedValue = propertiesComponent.getValue(selectorOption.getOption().getProperty());
        return selectorOption.getValues()
            .stream()
            .filter(it -> Objects.equals(it.getOption().getProperty(), selectedValue))
            .findFirst()
            .orElse(selectorOption.getValues().get(0));
    }
}

