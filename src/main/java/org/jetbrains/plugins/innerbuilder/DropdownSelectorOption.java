package org.jetbrains.plugins.innerbuilder;

import java.util.List;

public class DropdownSelectorOption implements SelectorOption {

    private final InnerBuilderOption option;
    private final String caption;
    private final String toolTip;
    private final List<DropdownSelectorOptionValue> values;

    private DropdownSelectorOption(Builder builder) {
        option = builder.option;
        caption = builder.caption;
        toolTip = builder.toolTip;
        values = builder.values;
    }

    public static Builder newBuilder() {
        return new Builder();
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

    public static final class Builder {
        private InnerBuilderOption option;
        private String caption;
        private String toolTip;
        private List<DropdownSelectorOptionValue> values;

        private Builder() {
        }

        public Builder withOption(InnerBuilderOption val) {
            option = val;
            return this;
        }

        public Builder withCaption(String val) {
            caption = val;
            return this;
        }

        public Builder withToolTip(String val) {
            toolTip = val;
            return this;
        }

        public Builder withValues(List<DropdownSelectorOptionValue> val) {
            values = val;
            return this;
        }

        public DropdownSelectorOption build() {
            return new DropdownSelectorOption(this);
        }
    }
}
