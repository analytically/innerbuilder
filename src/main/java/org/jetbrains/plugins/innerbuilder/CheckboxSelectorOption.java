package org.jetbrains.plugins.innerbuilder;


public class CheckboxSelectorOption implements SelectorOption {
    private final InnerBuilderOption option;
    private final String caption;
    private final char mnemonic;
    private String toolTip;

    public CheckboxSelectorOption(InnerBuilderOption option, String caption, char mnemonic) {
        this.option = option;
        this.caption = caption;
        this.mnemonic = mnemonic;
    }

    public CheckboxSelectorOption(InnerBuilderOption option, String caption, char mnemonic, String toolTip) {
        this.option = option;
        this.caption = caption;
        this.mnemonic = mnemonic;
        this.toolTip = toolTip;
    }

    @Override
    public InnerBuilderOption getOption() {
        return option;
    }

    @Override
    public String getCaption() {
        return caption;
    }

    public char getMnemonic() {
        return mnemonic;
    }

    @Override
    public String getToolTip() {
        return toolTip;
    }
}
