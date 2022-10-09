package org.jetbrains.plugins.innerbuilder;

import java.util.List;

public class DropdownSelectorOption implements SelectorOption {
    private final InnerBuilderOption option;
    private final String caption;
    private final String toolTip;
    private final List<DropdownSelectorOptionValue> values;

    public DropdownSelectorOption(InnerBuilderOption option, String caption, String toolTip, List<DropdownSelectorOptionValue> values) {
        this.option = option;
        this.caption = caption;
        this.toolTip = toolTip;
        this.values = values;
    }

    @Override
    public InnerBuilderOption getOption() {
        return option;
    }

    @Override
    public String getCaption() {
        return caption;
    }

    @Override
    public String getToolTip() {
        return toolTip;
    }

    public List<DropdownSelectorOptionValue> getValues() {
        return values;
    }

}
