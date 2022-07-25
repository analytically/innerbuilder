package org.jetbrains.plugins.innerbuilder;


public class DropdownSelectorOptionValue {
    InnerBuilderOption option;
    String caption;

    private DropdownSelectorOptionValue(Builder builder) {
        option = builder.option;
        caption = builder.caption;
    }

    public InnerBuilderOption getOption() {
        return option;
    }

    public String getCaption() {
        return caption;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {
        private InnerBuilderOption option;
        private String caption;

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

        public DropdownSelectorOptionValue build() {
            return new DropdownSelectorOptionValue(this);
        }
    }
}
