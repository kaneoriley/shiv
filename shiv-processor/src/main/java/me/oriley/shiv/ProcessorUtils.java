/*
 * Copyright (C) 2016 Kane O'Riley
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.oriley.shiv;

import android.support.annotation.NonNull;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.Set;

@SuppressWarnings({"WeakerAccess", "unused"})
public final class ProcessorUtils {

    private static final String NULLABLE = "Nullable";


    private ProcessorUtils() {
        throw new IllegalAccessError("no instances");
    }


    // region Properties

    static boolean hasAnnotationWithName(@NonNull Element element, @NonNull String simpleName) {
        for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
            String annotationName = mirror.getAnnotationType().asElement().getSimpleName().toString();
            if (simpleName.equals(annotationName)) {
                return true;
            }
        }
        return false;
    }

    static boolean isPrivate(@NonNull Element element) {
        Set<Modifier> modifiers = element.getModifiers();
        return modifiers.contains(Modifier.PRIVATE);
    }

    static boolean isStatic(@NonNull Element element) {
        Set<Modifier> modifiers = element.getModifiers();
        return modifiers.contains(Modifier.STATIC);
    }

    public static boolean isNullable(@NonNull Element element) {
        return hasAnnotationWithName(element, NULLABLE);
    }

    public static boolean isSubtypeOfType(@NonNull TypeElement element, @NonNull Class c) {
        return isSubtypeOfType(element.asType(), c.getCanonicalName());
    }

    public static boolean isSubtypeOfType(@NonNull TypeMirror typeMirror, @NonNull Class c) {
        return isSubtypeOfType(typeMirror, c.getCanonicalName());
    }

    public static boolean isSubtypeOfType(@NonNull TypeElement element, @NonNull String type) {
        return isSubtypeOfType(element.asType(), type);
    }

    public static boolean isSubtypeOfType(@NonNull TypeMirror typeMirror, @NonNull String type) {
        if (type.equals(typeMirror.toString())) {
            return true;
        }
        if (typeMirror.getKind() != TypeKind.DECLARED) {
            return false;
        }
        DeclaredType declaredType = (DeclaredType) typeMirror;
        List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
        if (typeArguments.size() > 0) {
            StringBuilder typeString = new StringBuilder(declaredType.asElement().toString());
            typeString.append('<');
            for (int i = 0; i < typeArguments.size(); i++) {
                if (i > 0) {
                    typeString.append(',');
                }
                typeString.append('?');
            }
            typeString.append('>');
            if (typeString.toString().equals(type)) {
                return true;
            }
        }
        Element element = declaredType.asElement();
        if (!(element instanceof TypeElement)) {
            return false;
        }
        TypeElement typeElement = (TypeElement) element;
        TypeMirror superType = typeElement.getSuperclass();
        if (isSubtypeOfType(superType, type)) {
            return true;
        }
        for (TypeMirror interfaceType : typeElement.getInterfaces()) {
            if (isSubtypeOfType(interfaceType, type)) {
                return true;
            }
        }
        return false;
    }

    // endregion Properties
}
