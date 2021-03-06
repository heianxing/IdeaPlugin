package org.axonframework.intellij.ide.plugin.handler;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiImmediateClassType;

import static org.axonframework.intellij.ide.plugin.handler.InternalEventTypes.ABSTRACT_ANNOTATED_AGGREGATE_ROOT;
import static org.axonframework.intellij.ide.plugin.handler.InternalEventTypes.ABSTRACT_ANNOTATED_ENTITY;

class DefaultEventHandler implements Handler {

    private static final String EVENT_HANDLER_ARGUMENT = "eventType";
    private static final String AlTERNATIVE_EVENT_HANDLER_ARGUMENT = "payloadType";

    private final PsiType[] annotationOrMethodArguments;
    private final PsiMethod method;


    private DefaultEventHandler(PsiMethod method) {
        this.method = method;
        this.annotationOrMethodArguments = getMethodArguments(method);
    }

    private DefaultEventHandler(PsiAnnotationMemberValue eventType, PsiMethod method) {
        this.method = method;
        this.annotationOrMethodArguments = getAnnotationArguments(eventType);
    }

    @Override
    public PsiType getHandledType() {
        if (annotationOrMethodArguments == null || annotationOrMethodArguments.length == 0) {
            return null;
        }
        return annotationOrMethodArguments[0];
    }


    @Override
    public PsiElement getElementForAnnotation() {
        return method.getNameIdentifier();
    }

    @Override
    public boolean canHandle(PsiType eventType) {
        PsiType handledType = getHandledType();

        if (eventType == null || handledType == null) {
            return false;
        }
        return handledType.isAssignableFrom(eventType) || isAssignableFromFirstParameter(eventType, handledType);
    }

    private boolean isAssignableFromFirstParameter(PsiType eventType, PsiType handledType) {
        return (eventType instanceof PsiImmediateClassType)
                && ((PsiImmediateClassType) eventType).getParameters().length > 0
                && handledType.isAssignableFrom(((PsiImmediateClassType) eventType).getParameters()[0]);
    }

    @Override
    public boolean isValid() {
        return !(method == null || getHandledType() == null) && method.isValid() && getHandledType().isValid();
    }

    @Override
    public boolean isInternalEvent() {
        if (method == null) {
            return false;
        }
        if (method.getContainingClass() == null) {
            return false;
        }
        if (method.getContainingClass().getQualifiedName() == null) {
            return false;
        }

        PsiClassType[] superTypes = method.getContainingClass().getSuperTypes();
        for (PsiClassType superType : superTypes) {
            if (superType.getCanonicalText().startsWith(ABSTRACT_ANNOTATED_AGGREGATE_ROOT.getFullyQualifiedName())
                    || superType.getCanonicalText().startsWith(ABSTRACT_ANNOTATED_ENTITY.getFullyQualifiedName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isSagaEvent() {
        PsiAnnotation[] annotations = method.getModifierList().getAnnotations();
        if (annotations.length < 1) {
            return false;
        }
        for (PsiAnnotation annotation : annotations) {
            if (AnnotationTypes.SAGA_EVENT_HANDLER.getFullyQualifiedName().equals(annotation.getQualifiedName())) {
                return true;
            }
        }
        return false;
    }

    private PsiType[] getMethodArguments(PsiMethod method) {
        PsiParameterList list = method.getParameterList();
        PsiType[] argument = new PsiType[list.getParametersCount()];
        for (int i = 0; i < list.getParameters().length; i++) {
            PsiParameter psiParameter = list.getParameters()[i];
            argument[i] = psiParameter.getType();
        }
        return argument;
    }

    private PsiType[] getAnnotationArguments(PsiAnnotationMemberValue eventType) {
        if (eventType.getChildren().length > 0 && eventType.getFirstChild().getChildren().length > 0) {
            if (eventType instanceof PsiExpression) {
                PsiType typeOfArgument = ((PsiExpression) eventType).getType();
                if (typeOfArgument instanceof PsiClassType
                        && ((PsiClassType) typeOfArgument).getParameterCount() > 0) {
                    return new PsiType[]{((PsiClassType) typeOfArgument).getParameters()[0]};
                }
            }
        }
        return new PsiType[]{};
    }

    public static Handler createEventHandler(PsiMethod method, PsiAnnotation annotation) {
        PsiAnnotationMemberValue eventType = annotation.findAttributeValue(DefaultEventHandler.EVENT_HANDLER_ARGUMENT);
        if (eventType == null) {
            eventType = annotation.findAttributeValue(AlTERNATIVE_EVENT_HANDLER_ARGUMENT);
        }
        if (annotationHasEventTypeArgument(eventType) && hasChildren(eventType)) {
            return new DefaultEventHandler(eventType, method);
        }
        return new DefaultEventHandler(method);
    }

    private static boolean annotationHasEventTypeArgument(PsiAnnotationMemberValue eventType) {
        if (eventType == null) {
            return false;
        }

        PsiType type = ((PsiExpression) eventType).getType();
        return type != null && !type.getCanonicalText().equals("java.lang.Class<java.lang.Void>");
    }

    private static boolean hasChildren(PsiAnnotationMemberValue eventType) {
        return eventType.getChildren().length > 0 && eventType.getFirstChild().getChildren().length > 0;
    }
}
