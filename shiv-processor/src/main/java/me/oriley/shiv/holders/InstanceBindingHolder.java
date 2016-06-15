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

package me.oriley.shiv.holders;

import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.util.SparseArray;
import com.squareup.javapoet.*;
import me.oriley.shiv.BundleUtils;
import me.oriley.shiv.ProcessorUtils;
import me.oriley.shiv.ShivException;
import me.oriley.shiv.ShivProcessor;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;

final class InstanceBindingHolder extends AbstractBindingHolder {

    private static final String SAVE_INSTANCE = "saveInstance";
    private static final String RESTORE_INSTANCE = "restoreInstance";


    InstanceBindingHolder(@NonNull TypeElement hostType) {
        super(hostType);
    }


    @Override
    void addBindingsToClass(@NonNull ShivProcessor processor, @NonNull TypeSpec.Builder typeSpecBuilder) throws ShivException {
        if (mElements.isEmpty()) {
            // Nothing to bind
            return;
        }

        // Type parameter
        ParameterSpec param = ParameterSpec.builder(TypeName.get(Object.class)
                .annotated(AnnotationSpec.builder(NonNull.class).build()), OBJECT, Modifier.FINAL)
                .build();

        ParameterSpec bundleParam = ParameterSpec.builder(ClassName.get(Bundle.class)
                .annotated(AnnotationSpec.builder(NonNull.class).build()), BUNDLE, Modifier.FINAL)
                .build();

        // Create restoreInstance method
        MethodSpec restoreMethod = MethodSpec.methodBuilder(RESTORE_INSTANCE)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(param)
                .addParameter(bundleParam)
                .addCode(generateRestoreInstanceMethod(typeSpecBuilder))
                .build();

        // Create saveInstance method
        MethodSpec saveMethod = MethodSpec.methodBuilder(SAVE_INSTANCE)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(param)
                .addParameter(bundleParam)
                .addCode(generateSaveInstanceMethod(processor))
                .build();

        typeSpecBuilder.addMethod(restoreMethod).addMethod(saveMethod);
    }

    @NonNull
    private CodeBlock generateSaveInstanceMethod(@NonNull ShivProcessor processor) throws ShivException {
        CodeBlock.Builder builder = CodeBlock.builder()
                .add("if ($N == null) return;\n", BUNDLE)
                .add("$T $N = ($T) $N;\n", mHostType, FIELD_HOST, mHostType, OBJECT);

        for (Element element : mElements) {
            String keyName = (KEY_INSTANCE_PREFIX + element.getSimpleName()).toUpperCase();
            builder.add("$T.$N($N, $N, $N.$N);\n", BundleUtils.class,
                    getPutMethodName(processor.erasedType(element.asType()), element), BUNDLE, keyName,
                    FIELD_HOST, element.getSimpleName());
        }

        return builder.build();
    }

    @NonNull
    private CodeBlock generateRestoreInstanceMethod(@NonNull TypeSpec.Builder typeSpecBuilder) throws ShivException {
        CodeBlock.Builder builder = CodeBlock.builder()
                .add("if ($N == null) return;\n", BUNDLE)
                .add("$T $N = ($T) $N;\n", mHostType, FIELD_HOST, mHostType, OBJECT)
                .add("$T $N;\n", Object.class, EXTRA);

        for (Element element : mElements) {
            String keyName = (KEY_INSTANCE_PREFIX + element.getSimpleName()).toUpperCase();
            typeSpecBuilder.addField(FieldSpec.builder(String.class, keyName, Modifier.FINAL, Modifier.STATIC,
                    Modifier.PRIVATE).initializer("\"$N.$N\"", mHostType.getQualifiedName(), element.getSimpleName()).build());

            builder.add("$N = $T.get($N, $N);\n", EXTRA, BundleUtils.class, BUNDLE, keyName)
                    .add("if ($N != null) {\n", EXTRA)
                    .add("    $N.$N = ($T) $N;\n", FIELD_HOST, element.getSimpleName(), element.asType(), EXTRA)
                    .add("}\n");
        }

        return builder.build();
    }

    @NonNull
    private String getPutMethodName(@NonNull String erasedType, @NonNull Element element) throws ShivException {
        if (ArrayList.class.getCanonicalName().equals(erasedType)) {
            DeclaredType declaredType = (DeclaredType) element.asType();
            List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
            if (typeArguments.size() != 1) {
                throw new ShivException("Generic type not specified for list: " + element);
            }
            TypeMirror listType = typeArguments.get(0);

            if (ProcessorUtils.isSubtypeOfType(listType, Parcelable.class)) {
                return "putParcelableArrayList";
            } else if (ProcessorUtils.isSubtypeOfType(listType, String.class)) {
                return "putStringArrayList";
            } else if (ProcessorUtils.isSubtypeOfType(listType, CharSequence.class)) {
                return "putCharSequenceArrayList";
            } else if (ProcessorUtils.isSubtypeOfType(listType, Integer.class)) {
                return "putIntegerArrayList";
            } else {
                throw new ShivException("Invalid array list type: " + listType);
            }
        } else if (SparseArray.class.getCanonicalName().equals(erasedType)) {
            DeclaredType declaredType = (DeclaredType) element.asType();
            List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
            if (typeArguments.size() != 1) {
                throw new ShivException("Generic type not specified for sparse array: " + element);
            }
            TypeMirror sparseArrayType = typeArguments.get(0);

            if (ProcessorUtils.isSubtypeOfType(sparseArrayType, Parcelable.class)) {
                return "putSparseParcelableArray";
            } else {
                throw new ShivException("Invalid sparse array type: " + sparseArrayType);
            }
        } else {
            return "put";
        }
    }
}
