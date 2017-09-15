package org.jetbrains.plugins.innerbuilder;

import static org.jetbrains.plugins.innerbuilder.InnerBuilderUtils.hasLowerCaseChar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import org.jetbrains.annotations.Nullable;

import com.intellij.codeInsight.generation.PsiFieldMember;

import com.intellij.openapi.editor.Editor;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiResolveHelper;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;

public final class InnerBuilderCollector {
    private InnerBuilderCollector() { }

    @Nullable
    public static List<PsiFieldMember> collectFields(final PsiFile file, final Editor editor) {
        final int offset = editor.getCaretModel().getOffset();
        final PsiElement element = file.findElementAt(offset);
        if (element == null) {
            return null;
        }

        final PsiClass clazz = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        if (clazz == null || clazz.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return null;
        }

        final List<PsiFieldMember> allFields = new ArrayList<PsiFieldMember>();

        PsiClass classToExtractFieldsFrom = clazz;
        while (classToExtractFieldsFrom != null) {
            final List<PsiFieldMember> classFieldMembers = collectFieldsInClass(element, clazz,
                    classToExtractFieldsFrom);
            allFields.addAll(0, classFieldMembers);

            classToExtractFieldsFrom = classToExtractFieldsFrom.getSuperClass();
        }

        return allFields;
    }

    private static List<PsiFieldMember> collectFieldsInClass(final PsiElement element, final PsiClass accessObjectClass,
            final PsiClass clazz) {
        final List<PsiFieldMember> classFieldMembers = new ArrayList<PsiFieldMember>();
        final PsiResolveHelper helper = JavaPsiFacade.getInstance(clazz.getProject()).getResolveHelper();

        for (final PsiField field : clazz.getFields()) {

            // check access to the field from the builder container class (eg. private superclass fields)
            if ((helper.isAccessible(field, clazz, accessObjectClass) || hasSetter(clazz, field.getName()))
                    && !PsiTreeUtil.isAncestor(field, element, false)) {

                // skip static fields
                if (field.hasModifierProperty(PsiModifier.STATIC)) {
                    continue;
                }

                // skip any uppercase fields
                if (!hasLowerCaseChar(field.getName())) {
                    continue;
                }

                // skip eventual logging fields
                final String fieldType = field.getType().getCanonicalText();
                if ("org.apache.log4j.Logger".equals(fieldType) || "org.apache.logging.log4j.Logger".equals(fieldType)
                        || "java.util.logging.Logger".equals(fieldType) || "org.slf4j.Logger".equals(fieldType)
                        || "ch.qos.logback.classic.Logger".equals(fieldType)
                        || "net.sf.microlog.core.Logger".equals(fieldType)
                        || "org.apache.commons.logging.Log".equals(fieldType)
                        || "org.pmw.tinylog.Logger".equals(fieldType) || "org.jboss.logging.Logger".equals(fieldType)
                        || "jodd.log.Logger".equals(fieldType)) {
                    continue;
                }

                if (field.hasModifierProperty(PsiModifier.FINAL)) {
                    if (field.getInitializer() != null) {
                        continue; // skip final fields that are assigned in the declaration
                    }

                    if (!accessObjectClass.isEquivalentTo(clazz)) {
                        continue; // skip final superclass fields
                    }
                }

                final PsiClass containingClass = field.getContainingClass();
                if (containingClass != null) {
                    classFieldMembers.add(buildFieldMember(field, containingClass, clazz));
                }
            }
        }

        return classFieldMembers;
    }

    private static boolean hasSetter(PsiClass clazz, String name)
    {
        for(int i = 0; i < clazz.getAllMethods().length; i++)
        {
            if(clazz.getAllMethods()[i].getName().equals(String.format("set%s", InnerBuilderUtils.capitalize(name))))
            {
                return true;
            }
        }

        return false;
    }

    private static PsiFieldMember buildFieldMember(final PsiField field, final PsiClass containingClass,
            final PsiClass clazz) {
        return new PsiFieldMember(field,
                TypeConversionUtil.getSuperClassSubstitutor(containingClass, clazz, PsiSubstitutor.EMPTY));
    }
}
