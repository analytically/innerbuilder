package org.jetbrains.plugins.innerbuilder;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Mathias Bogaert
 */
public class GenerateInnerBuilderWorker {
    private static final Logger logger = Logger.getInstance(GenerateInnerBuilderWorker.class);

    private final PsiElementFactory psiElementFactory;
    private final CodeStyleManager codeStyleManager;
    private final PsiClass clazz;

    public GenerateInnerBuilderWorker(PsiClass clazz, Editor editor) {
        this.clazz = clazz;
        this.psiElementFactory = JavaPsiFacade.getInstance(clazz.getProject()).getElementFactory();
        this.codeStyleManager = CodeStyleManager.getInstance(clazz.getProject());
    }

    public void execute(Iterable<PsiField> fields) throws IncorrectOperationException {
        PsiElement builderClass = clazz.findInnerClassByName("Builder", false);

        if (builderClass == null) {
            builderClass = clazz.add(psiElementFactory.createClass("Builder"));

            // builder classes are static final
            ((PsiClass) builderClass).getModifierList().setModifierProperty(PsiModifier.STATIC, true);
            ((PsiClass) builderClass).getModifierList().setModifierProperty(PsiModifier.FINAL, true);
        }

        // the owning class should have a private constructor accepting a builder
        StringBuilder constructorTakingBuilderBuilder = new StringBuilder();
        constructorTakingBuilderBuilder.append("private ").append(clazz.getName()).append("(Builder builder) {");
        for (PsiField field : fields) {
            final PsiMethod setterPrototype = PropertyUtil.generateSetterPrototype(field);
            final PsiMethod setter = clazz.findMethodBySignature(setterPrototype, true);

            if (setter == null) {
                constructorTakingBuilderBuilder.append(field.getName()).append("= builder.")
                        .append(field.getName()).append(";");
            } else {
                constructorTakingBuilderBuilder.append(setter.getName()).append("(builder.")
                        .append(field.getName()).append(");");
            }
        }
        constructorTakingBuilderBuilder.append("}");
        psiElementFactory.createConstructor();
        codeStyleManager.reformat(addOrReplaceMethod(clazz, constructorTakingBuilderBuilder.toString()));

        // final fields become constructor fields in the builder
        List<PsiField> finalFields = new ArrayList<PsiField>();
        for (PsiField field : fields) {
            if (field.hasModifierProperty(PsiModifier.FINAL)) {
                finalFields.add(field);
            }
        }

        // add all final fields to the builder
        for (PsiField field : finalFields) {
            if (!hasField(((PsiClass) builderClass), field)) {
                PsiField builderField = psiElementFactory.createField(field.getName(), field.getType());
                builderField.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
                builderClass.add(builderField);
            }
        }

        // add all non-final fields to the builder
        List<PsiField> nonFinalFields = new ArrayList<PsiField>();
        for (PsiField field : fields) {
            if (!hasField(((PsiClass) builderClass), field) && !field.hasModifierProperty(PsiModifier.FINAL)) {
                nonFinalFields.add(field);

                PsiField builderField = psiElementFactory.createField(field.getName(), field.getType());
                builderClass.add(builderField);
            }
        }

        // builder constructor, accepting the final fields
        StringBuilder constructor = new StringBuilder();
        constructor.append("public Builder(");
        for (Iterator<PsiField> iterator = finalFields.iterator(); iterator.hasNext(); ) {
            PsiField field = iterator.next();
            constructor.append(field.getTypeElement().getType().getCanonicalText()).append(" ").append(field.getName());

            if (iterator.hasNext()) {
                constructor.append(",");
            }
        }
        constructor.append(") {");
        for (PsiField field : finalFields) {
            constructor.append("this.");
            constructor.append(field.getName());
            constructor.append("=");
            constructor.append(field.getName());
            constructor.append(";");
        }
        constructor.append("}");
        addOrReplaceMethod((PsiClass) builderClass, constructor.toString());

        // builder methods
        for (PsiField field : nonFinalFields) {
            String setMethodText = "public "
                    + "Builder" + " " + field.getName() + "(" + field.getTypeElement().getType().getCanonicalText()
                    + " " + field.getName() + "){"
                    + "this." + field.getName() + "=" + field.getName() + ";"
                    + "return this;"
                    + "}";
            addOrReplaceMethod((PsiClass) builderClass, setMethodText);
        }

        // builder.build() method
        StringBuilder buildMethod = new StringBuilder();
        buildMethod.append("public ")
                .append(clazz.getName())
                .append(" build() { return new ")
                .append(clazz.getName())
                .append("(this);}");

        addOrReplaceMethod((PsiClass) builderClass, buildMethod.toString());
        codeStyleManager.reformat(builderClass);
    }

    protected boolean hasField(PsiClass clazz, PsiField field) {
        return (clazz.findFieldByName(field.getName(), false) != null);
    }

    protected PsiMethod addOrReplaceMethod(PsiClass target, String methodText) {
        PsiMethod newMethod = psiElementFactory.createMethodFromText(methodText, null);
        PsiMethod existingMethod = target.findMethodBySignature(newMethod, false);
        if (existingMethod != null) {
            existingMethod.replace(newMethod);
        } else {
            target.add(newMethod);
        }
        return newMethod;
    }

    /**
     * Generates the toString() code for the specified class and selected
     * fields, doing the work through a WriteAction ran by a CommandProcessor.
     */
    public static void executeGenerateActionLater(final PsiClass clazz,
                                                  final Editor editor,
                                                  final Iterable<PsiField> selectedFields) {
        Runnable writeCommand = new Runnable() {
            public void run() {
                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                    public void run() {
                        try {
                            new GenerateInnerBuilderWorker(clazz, editor).execute(selectedFields);
                        } catch (Exception e) {
                            handleExeption(clazz.getProject(), e);
                        }
                    }
                });
            }
        };

        CommandProcessor.getInstance().executeCommand(clazz.getProject(), writeCommand, "GenerateBuilder", null);
    }

    /**
     * Handles any exception during the executing on this plugin.
     *
     * @param project PSI project
     * @param e       the caused exception.
     * @throws RuntimeException is thrown for severe exceptions
     */
    public static void handleExeption(Project project, Exception e) throws RuntimeException {
        e.printStackTrace(); // must print stacktrace to see caused in IDEA log / console
        logger.error(e);

        if (e instanceof PluginException) {
            // plugin related error - could be recoverable.
            Messages.showMessageDialog(project, "A PluginException was thrown while performing the action - see IDEA log for details (stacktrace should be in idea.log):\n" + e.getMessage(), "Warning", Messages.getWarningIcon());
        } else if (e instanceof RuntimeException) {
            // unknown error (such as NPE) - not recoverable
            Messages.showMessageDialog(project, "An unrecoverable exception was thrown while performing the action - see IDEA log for details (stacktrace should be in idea.log):\n" + e.getMessage(), "Error", Messages.getErrorIcon());
            throw (RuntimeException) e; // throw to make IDEA alert user
        } else {
            // unknown error (such as NPE) - not recoverable
            Messages.showMessageDialog(project, "An unrecoverable exception was thrown while performing the action - see IDEA log for details (stacktrace should be in idea.log):\n" + e.getMessage(), "Error", Messages.getErrorIcon());
            throw new RuntimeException(e); // rethrow as runtime to make IDEA alert user
        }
    }
}
