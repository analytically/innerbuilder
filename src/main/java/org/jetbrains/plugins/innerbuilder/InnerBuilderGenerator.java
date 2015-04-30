package org.jetbrains.plugins.innerbuilder;

import com.intellij.codeInsight.generation.PsiFieldMember;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static org.jetbrains.plugins.innerbuilder.InnerBuilderUtils.areTypesPresentableEqual;

public class InnerBuilderGenerator implements Runnable {

    @NonNls
    private static final String BUILDER_CLASS_NAME = "Builder";
    @NonNls
    private static final String JSR305_NONNULL = "javax.annotation.Nonnull";
    @NonNls
    private static final String FINDBUGS_NONNULL = "edu.umd.cs.findbugs.annotations.NonNull";

    private final Project project;
    private final PsiFile file;
    private final Editor editor;
    private final List<PsiFieldMember> selectedFields;
    private final PsiElementFactory psiElementFactory;

    public static void generate(final Project project, final Editor editor, final PsiFile file,
                                final List<PsiFieldMember> selectedFields) {
        final Runnable builderGenerator = new InnerBuilderGenerator(project, file, editor, selectedFields);
        ApplicationManager.getApplication().runWriteAction(builderGenerator);
    }

    private InnerBuilderGenerator(final Project project, final PsiFile file, final Editor editor,
                                  final List<PsiFieldMember> selectedFields) {
        this.project = project;
        this.file = file;
        this.editor = editor;
        this.selectedFields = selectedFields;
        psiElementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    }

    @Override
    public void run() {
        final PsiClass topLevelClass = InnerBuilderUtils.getTopLevelClass(project, file, editor);
        if (topLevelClass == null) {
            return;
        }
        final Set<InnerBuilderOption> options = currentOptions();
        final PsiClass builderClass = findOrCreateBuilderClass(topLevelClass);
        final PsiType builderType = psiElementFactory.createTypeFromText(BUILDER_CLASS_NAME, null);
        final PsiMethod constructor = generateConstructor(topLevelClass, builderType, options);

        addMethod(topLevelClass, null, constructor, true);
        final Collection<PsiFieldMember> finalFields = new ArrayList<PsiFieldMember>();
        final Collection<PsiFieldMember> nonFinalFields = new ArrayList<PsiFieldMember>();

        PsiElement lastAddedField = null;
        for (final PsiFieldMember fieldMember : selectedFields) {
            lastAddedField = findOrCreateField(builderClass, fieldMember, lastAddedField);
            if (fieldMember.getElement().hasModifierProperty(PsiModifier.FINAL)
                    && !options.contains(InnerBuilderOption.FINAL_SETTERS)) {
                finalFields.add(fieldMember);
                PsiUtil.setModifierProperty((PsiField) lastAddedField, PsiModifier.FINAL, true);
            } else {
                nonFinalFields.add(fieldMember);
            }
        }
        if (options.contains(InnerBuilderOption.NEW_BUILDER_METHOD)) {
            final PsiMethod newBuilderMethod = generateNewBuilderMethod(builderType, finalFields, options);
            addMethod(topLevelClass, null, newBuilderMethod, false);
        }

        // builder constructor, accepting the final fields
        final PsiMethod builderConstructorMethod = generateBuilderConstructor(builderClass, finalFields, options);
        addMethod(builderClass, null, builderConstructorMethod, false);

        // builder copy constructor or static copy method
        if (options.contains(InnerBuilderOption.COPY_CONSTRUCTOR)) {
            if (options.contains(InnerBuilderOption.NEW_BUILDER_METHOD)) {
                final PsiMethod copyBuilderMethod = generateCopyBuilderMethod(topLevelClass, builderType,
                        nonFinalFields, options);
                addMethod(topLevelClass, null, copyBuilderMethod, true);
            } else {
                final PsiMethod copyConstructorBuilderMethod = generateCopyConstructor(topLevelClass, builderType,
                        selectedFields, options);
                addMethod(builderClass, null, copyConstructorBuilderMethod, true);
            }
        }

        // builder methods
        PsiElement lastAddedElement = null;
        for (final PsiFieldMember member : nonFinalFields) {
            final PsiMethod setterMethod = generateBuilderSetter(builderType, member, options);
            lastAddedElement = addMethod(builderClass, lastAddedElement, setterMethod, false);
        }

        // builder.build() method
        final PsiMethod buildMethod = generateBuildMethod(topLevelClass, options);
        addMethod(builderClass, lastAddedElement, buildMethod, false);

        JavaCodeStyleManager.getInstance(project).shortenClassReferences(file);
        CodeStyleManager.getInstance(project).reformat(builderClass);
    }

    private PsiMethod generateCopyBuilderMethod(final PsiClass topLevelClass, final PsiType builderType,
                                                final Collection<PsiFieldMember> fields,
                                                final Set<InnerBuilderOption> options) {
        final PsiMethod copyBuilderMethod = psiElementFactory.createMethod("newBuilder", builderType);
        PsiUtil.setModifierProperty(copyBuilderMethod, PsiModifier.STATIC, true);
        PsiUtil.setModifierProperty(copyBuilderMethod, PsiModifier.PUBLIC, true);

        final PsiType topLevelClassType = psiElementFactory.createType(topLevelClass);
        final PsiParameter parameter = psiElementFactory.createParameter("copy", topLevelClassType);
        final PsiModifierList parameterModifierList = parameter.getModifierList();

        if (parameterModifierList != null) {
            if (options.contains(InnerBuilderOption.JSR305_ANNOTATIONS)) {
                parameterModifierList.addAnnotation(JSR305_NONNULL);
            }
            if (options.contains(InnerBuilderOption.FINDBUGS_ANNOTATION)) {
                parameterModifierList.addAnnotation(FINDBUGS_NONNULL);
            }
        }
        copyBuilderMethod.getParameterList().add(parameter);
        final PsiCodeBlock copyBuilderBody = copyBuilderMethod.getBody();
        if (copyBuilderBody != null) {
            final StringBuilder copyBuilderParameters = new StringBuilder();
            for (final PsiFieldMember fieldMember : selectedFields) {
                if (fieldMember.getElement().hasModifierProperty(PsiModifier.FINAL)
                        && !options.contains(InnerBuilderOption.FINAL_SETTERS)) {

                    if (copyBuilderParameters.length() > 0) {
                        copyBuilderParameters.append(", ");
                    }

                    copyBuilderParameters.append(String.format("copy.%s", fieldMember.getElement().getName()));
                }
            }
            if (options.contains(InnerBuilderOption.NEW_BUILDER_METHOD)) {
                final PsiStatement newBuilderStatement = psiElementFactory.createStatementFromText(String.format(
                                "%s builder = new %s(%s);", builderType.getPresentableText(),
                                builderType.getPresentableText(), copyBuilderParameters.toString()),
                        copyBuilderMethod);
                copyBuilderBody.add(newBuilderStatement);

                addCopyBody(fields, copyBuilderMethod, "builder.", options);
                copyBuilderBody.add(psiElementFactory.createStatementFromText("return builder;", copyBuilderMethod));
            } else {
                final PsiStatement newBuilderStatement = psiElementFactory.createStatementFromText(String.format(
                                "return new %s(%s);", builderType.getPresentableText(),
                                copyBuilderParameters.toString()),
                        copyBuilderMethod);
                copyBuilderBody.add(newBuilderStatement);
            }
        }
        return copyBuilderMethod;
    }

    private PsiMethod generateCopyConstructor(final PsiClass topLevelClass, final PsiType builderType,
                                              final Collection<PsiFieldMember> nonFinalFields,
                                              final Set<InnerBuilderOption> options) {

        final PsiMethod copyConstructor = psiElementFactory.createConstructor(builderType.getPresentableText());
        PsiUtil.setModifierProperty(copyConstructor, PsiModifier.PUBLIC, true);

        final PsiType topLevelClassType = psiElementFactory.createType(topLevelClass);
        final PsiParameter constructorParameter = psiElementFactory.createParameter("copy", topLevelClassType);
        final PsiModifierList parameterModifierList = constructorParameter.getModifierList();

        if (parameterModifierList != null) {
            if (options.contains(InnerBuilderOption.JSR305_ANNOTATIONS))
                parameterModifierList.addAnnotation(JSR305_NONNULL);
            if (options.contains(InnerBuilderOption.FINDBUGS_ANNOTATION))
                parameterModifierList.addAnnotation(FINDBUGS_NONNULL);
        }
        copyConstructor.getParameterList().add(constructorParameter);
        addCopyBody(nonFinalFields, copyConstructor, "this.", options);
        return copyConstructor;
    }

    private void addCopyBody(final Collection<PsiFieldMember> fields, final PsiMethod method, final String qName,
                             final Set<InnerBuilderOption> options) {
        final PsiCodeBlock methodBody = method.getBody();
        if (methodBody == null) {
            return;
        }
        for (final PsiFieldMember member : fields) {
            final PsiField field = member.getElement();
            final PsiStatement assignStatement = psiElementFactory.createStatementFromText(String.format(
                    "%s%2$s = copy.%2$s;", qName, field.getName()), method);
            methodBody.add(assignStatement);
        }
    }

    private PsiMethod generateBuilderConstructor(final PsiClass builderClass,
                                                 final Collection<PsiFieldMember> finalFields,
                                                 final Set<InnerBuilderOption> options) {

        final PsiMethod builderConstructor = psiElementFactory.createConstructor(builderClass.getName());
        if (options.contains(InnerBuilderOption.NEW_BUILDER_METHOD)) {
            PsiUtil.setModifierProperty(builderConstructor, PsiModifier.PRIVATE, true);
        } else {
            PsiUtil.setModifierProperty(builderConstructor, PsiModifier.PUBLIC, true);
        }
        final PsiCodeBlock builderConstructorBody = builderConstructor.getBody();
        if (builderConstructorBody != null) {
            for (final PsiFieldMember member : finalFields) {
                final PsiField field = member.getElement();
                final PsiType fieldType = field.getType();
                final String fieldName = field.getName();

                final PsiParameter parameter = psiElementFactory.createParameter(fieldName, fieldType);
                final PsiModifierList parameterModifierList = parameter.getModifierList();
                final boolean useJsr305 = options.contains(InnerBuilderOption.JSR305_ANNOTATIONS);
                final boolean useFindbugs = options.contains(InnerBuilderOption.FINDBUGS_ANNOTATION);

                if (!InnerBuilderUtils.isPrimitive(field) && parameterModifierList != null) {
                    if (useJsr305) parameterModifierList.addAnnotation(JSR305_NONNULL);
                    if (useFindbugs) parameterModifierList.addAnnotation(FINDBUGS_NONNULL);
                }

                builderConstructor.getParameterList().add(parameter);
                final PsiStatement assignStatement = psiElementFactory.createStatementFromText(String.format(
                        "this.%1$s = %1$s;", fieldName), builderConstructor);
                builderConstructorBody.add(assignStatement);
            }
        }

        return builderConstructor;
    }


    private PsiMethod generateNewBuilderMethod(final PsiType builderType, final Collection<PsiFieldMember> finalFields,
                                               final Set<InnerBuilderOption> options) {
        final PsiMethod newBuilderMethod = psiElementFactory.createMethod("newBuilder", builderType);
        PsiUtil.setModifierProperty(newBuilderMethod, PsiModifier.STATIC, true);
        PsiUtil.setModifierProperty(newBuilderMethod, PsiModifier.PUBLIC, true);

        final StringBuilder fieldList = new StringBuilder();
        if (!finalFields.isEmpty()) {
            for (final PsiFieldMember member : finalFields) {
                final PsiField field = member.getElement();
                final PsiType fieldType = field.getType();
                final String fieldName = field.getName();

                final PsiParameter parameter = psiElementFactory.createParameter(fieldName, fieldType);
                final PsiModifierList parameterModifierList = parameter.getModifierList();
                if (parameterModifierList != null) {

                    if (!InnerBuilderUtils.isPrimitive(field)) {
                        if (options.contains(InnerBuilderOption.JSR305_ANNOTATIONS))
                            parameterModifierList.addAnnotation(JSR305_NONNULL);
                        if (options.contains(InnerBuilderOption.FINDBUGS_ANNOTATION))
                            parameterModifierList.addAnnotation(FINDBUGS_NONNULL);
                    }
                }
                newBuilderMethod.getParameterList().add(parameter);
                if (fieldList.length() > 0) {
                    fieldList.append(", ");
                }
                fieldList.append(fieldName);
            }
        }
        final PsiCodeBlock newBuilderMethodBody = newBuilderMethod.getBody();
        if (newBuilderMethodBody != null) {
            final PsiStatement newStatement = psiElementFactory.createStatementFromText(String.format(
                            "return new %s(%s);", builderType.getPresentableText(), fieldList.toString()),
                    newBuilderMethod);
            newBuilderMethodBody.add(newStatement);
        }
        return newBuilderMethod;
    }

    private PsiMethod generateBuilderSetter(final PsiType builderType, final PsiFieldMember member,
                                            final Set<InnerBuilderOption> options) {

        final PsiField field = member.getElement();
        final PsiType fieldType = field.getType();
        final String fieldName = field.getName();

        final String methodName;
        if (options.contains(InnerBuilderOption.WITH_NOTATION)) {
            methodName = String.format("with%s", InnerBuilderUtils.capitalize(fieldName));
        } else {
            methodName = fieldName;
        }
        final PsiMethod setterMethod = psiElementFactory.createMethod(methodName, builderType);
        final boolean useJsr305 = options.contains(InnerBuilderOption.JSR305_ANNOTATIONS);
        final boolean useFindbugs = options.contains(InnerBuilderOption.FINDBUGS_ANNOTATION);

        if (useJsr305) setterMethod.getModifierList().addAnnotation(JSR305_NONNULL);
        if (useFindbugs) setterMethod.getModifierList().addAnnotation(FINDBUGS_NONNULL);

        setterMethod.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);
        final PsiParameter setterParameter = psiElementFactory.createParameter(fieldName, fieldType);

        if (!(fieldType instanceof PsiPrimitiveType)) {
            final PsiModifierList setterParameterModifierList = setterParameter.getModifierList();
            if (setterParameterModifierList != null) {
                if (useJsr305) setterParameterModifierList.addAnnotation(JSR305_NONNULL);
                if (useFindbugs) setterParameterModifierList.addAnnotation(FINDBUGS_NONNULL);
            }
        }
        setterMethod.getParameterList().add(setterParameter);
        final PsiCodeBlock setterMethodBody = setterMethod.getBody();
        if (setterMethodBody != null) {
            final PsiStatement assignStatement = psiElementFactory.createStatementFromText(String.format(
                    "this.%s = %s;", fieldName, fieldName), setterMethod);
            setterMethodBody.add(assignStatement);
            setterMethodBody.add(InnerBuilderUtils.createReturnThis(psiElementFactory, setterMethod));
        }
        setSetterComment(setterMethod, fieldName);
        return setterMethod;
    }


    private PsiMethod generateConstructor(final PsiClass topLevelClass, final PsiType builderType,
                                          final Set<InnerBuilderOption> options) {
        final PsiMethod constructor = psiElementFactory.createConstructor(topLevelClass.getName());
        constructor.getModifierList().setModifierProperty(PsiModifier.PRIVATE, true);

        final PsiParameter builderParameter = psiElementFactory.createParameter("builder", builderType);
        constructor.getParameterList().add(builderParameter);

        final PsiCodeBlock constructorBody = constructor.getBody();
        if (constructorBody != null) {
            for (final PsiFieldMember member : selectedFields) {
                final PsiField field = member.getElement();

                final PsiMethod setterPrototype = PropertyUtil.generateSetterPrototype(field);
                final PsiMethod setter = topLevelClass.findMethodBySignature(setterPrototype, true);

                final String fieldName = field.getName();
                boolean isFinal = false;
                final PsiModifierList modifierList = field.getModifierList();
                if (modifierList != null) {
                    isFinal = modifierList.hasModifierProperty(PsiModifier.FINAL);
                }

                final String assignText;
                if (setter == null || isFinal) {
                    assignText = String.format("%1$s = builder.%1$s;", fieldName);
                } else {
                    assignText = String.format("%s(builder.%s);", setter.getName(), fieldName);
                }

                final PsiStatement assignStatement = psiElementFactory.createStatementFromText(assignText, null);
                constructorBody.add(assignStatement);
            }
        }

        return constructor;
    }

    private PsiMethod generateBuildMethod(final PsiClass topLevelClass, final Set<InnerBuilderOption> options) {
        final PsiType topLevelClassType = psiElementFactory.createType(topLevelClass);
        final PsiMethod buildMethod = psiElementFactory.createMethod("build", topLevelClassType);

        final boolean useJsr305 = options.contains(InnerBuilderOption.JSR305_ANNOTATIONS);
        final boolean useFindbugs = options.contains(InnerBuilderOption.FINDBUGS_ANNOTATION);
        if (useJsr305)
            buildMethod.getModifierList().addAnnotation(JSR305_NONNULL);
        if (useFindbugs)
            buildMethod.getModifierList().addAnnotation(FINDBUGS_NONNULL);

        buildMethod.getModifierList().setModifierProperty(PsiModifier.PUBLIC, true);

        final PsiCodeBlock buildMethodBody = buildMethod.getBody();
        if (buildMethodBody != null) {
            final PsiStatement returnStatement = psiElementFactory.createStatementFromText(String.format(
                    "return new %s(this);", topLevelClass.getName()), buildMethod);
            buildMethodBody.add(returnStatement);
        }
        setBuildMethodComment(buildMethod, topLevelClass);
        return buildMethod;
    }

    @NotNull
    private PsiClass findOrCreateBuilderClass(final PsiClass topLevelClass) {
        final PsiClass builderClass = topLevelClass.findInnerClassByName(BUILDER_CLASS_NAME, false);
        if (builderClass == null) {
            return createBuilderClass(topLevelClass);
        }

        return builderClass;
    }

    @NotNull
    private PsiClass createBuilderClass(final PsiClass topLevelClass) {
        final PsiClass builderClass = (PsiClass) topLevelClass.add(psiElementFactory.createClass(BUILDER_CLASS_NAME));
        PsiUtil.setModifierProperty(builderClass, PsiModifier.STATIC, true);
        PsiUtil.setModifierProperty(builderClass, PsiModifier.FINAL, true);
        setBuilderComment(builderClass, topLevelClass);
        return builderClass;
    }

    private PsiElement findOrCreateField(final PsiClass builderClass, final PsiFieldMember member,
                                         @Nullable final PsiElement last) {
        final PsiField field = member.getElement();
        final String fieldName = field.getName();
        final PsiType fieldType = field.getType();
        final PsiField existingField = builderClass.findFieldByName(fieldName, false);
        if (existingField == null || !areTypesPresentableEqual(existingField.getType(), fieldType)) {
            if (existingField != null) {
                existingField.delete();
            }
            final PsiField newField = psiElementFactory.createField(fieldName, fieldType);
            if (last != null) {
                return builderClass.addAfter(newField, last);
            } else {
                return builderClass.add(newField);
            }
        }
        return existingField;
    }

    private PsiElement addMethod(final PsiClass target, @Nullable final PsiElement after, final String methodText) {
        return addMethod(target, after, methodText, false);
    }

    private PsiElement addMethod(final PsiClass target, @Nullable final PsiElement after, final String methodText,
                                 final boolean replace) {
        final PsiMethod newMethod = psiElementFactory.createMethodFromText(methodText, null);
        return addMethod(target, after, newMethod, replace);
    }

    private PsiElement addMethod(@NotNull final PsiClass target, @Nullable final PsiElement after,
                                 @NotNull final PsiMethod newMethod, final boolean replace) {
        PsiMethod existingMethod = target.findMethodBySignature(newMethod, false);
        if (existingMethod == null && newMethod.isConstructor()) {
            for (final PsiMethod constructor : target.getConstructors()) {
                if (InnerBuilderUtils.areParameterListsEqual(constructor.getParameterList(),
                        newMethod.getParameterList())) {
                    existingMethod = constructor;
                    break;
                }
            }
        }
        if (existingMethod == null) {
            if (after != null) {
                return target.addAfter(newMethod, after);
            } else {
                return target.add(newMethod);
            }
        } else if (replace) {
            existingMethod.replace(newMethod);
        }
        return existingMethod;
    }

    private static EnumSet<InnerBuilderOption> currentOptions() {
        final EnumSet<InnerBuilderOption> options = EnumSet.noneOf(InnerBuilderOption.class);
        final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        for (final InnerBuilderOption option : InnerBuilderOption.values()) {
            final boolean currentSetting = propertiesComponent.getBoolean(option.getProperty(), false);
            if (currentSetting) {
                options.add(option);
            }
        }
        return options;
    }

    private void setBuilderComment(final PsiClass clazz, final PsiClass topLevelClass) {
        if (currentOptions().contains(InnerBuilderOption.WITH_JAVADOC)) {
            StringBuilder str = new StringBuilder("/**\n").append("* {@code ");
            str.append(topLevelClass.getName()).append("} builder static inner class.\n");
            str.append("*/");
            setStringComment(clazz, str.toString());
        }
    }

    private void setSetterComment(final PsiMethod method, final String fieldName) {
        if (currentOptions().contains(InnerBuilderOption.WITH_JAVADOC)) {
            StringBuilder str = new StringBuilder("/**\n").append("* Sets the {@code ").append(fieldName);
            str.append("} and returns a reference to this Builder so that the methods can be chained together.\n");
            str.append("* @param ").append(fieldName).append(" the {@code ");
            str.append(fieldName).append("} to set\n");
            str.append("* @return a reference to this Builder\n*/");
            setStringComment(method, str.toString());
        }
    }

    private void setBuildMethodComment(final PsiMethod method, final PsiClass topLevelClass) {
        if (currentOptions().contains(InnerBuilderOption.WITH_JAVADOC)) {
            StringBuilder str = new StringBuilder("/**\n");
            str.append("* Returns a {@code ").append(topLevelClass.getName()).append("} built ");
            str.append("from the parameters previously set.\n*\n");
            str.append("* @return a {@code ").append(topLevelClass.getName()).append("} ");
            str.append("built with parameters of this {@code ").append(topLevelClass.getName()).append(".Builder}\n*/");
            setStringComment(method, str.toString());
        }
    }

    private void setStringComment(final PsiMethod method, final String strComment) {
        PsiComment comment = psiElementFactory.createCommentFromText(strComment, null);
        PsiDocComment doc = method.getDocComment();
        if (doc != null) {
            doc.replace(comment);
        } else {
            method.addBefore(comment, method.getFirstChild());
        }
    }

    private void setStringComment(final PsiClass clazz, final String strComment) {
        PsiComment comment = psiElementFactory.createCommentFromText(strComment, null);
        PsiDocComment doc = clazz.getDocComment();
        if (doc != null) {
            doc.replace(comment);
        } else {
            clazz.addBefore(comment, clazz.getFirstChild());
        }
    }
}
