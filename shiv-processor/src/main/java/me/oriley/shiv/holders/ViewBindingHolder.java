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

import android.app.Activity;
import android.app.Fragment;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.ViewGroup;
import com.squareup.javapoet.*;
import me.oriley.shiv.BindView;
import me.oriley.shiv.ShivException;
import me.oriley.shiv.ShivProcessor;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

import static me.oriley.shiv.ProcessorUtils.isNullable;
import static me.oriley.shiv.ProcessorUtils.isSubtypeOfType;

final class ViewBindingHolder extends AbstractBindingHolder {

    private static final String BIND_VIEWS = "bindViews";
    private static final String UNBIND_VIEWS = "unbindViews";
    private static final String BOUND = "bound";
    private static final String VIEW = "view";
    private static final String VIEW_COUNT = "VIEW_COUNT";


    ViewBindingHolder(@NonNull TypeElement hostType) {
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

        ParameterSpec viewGroupParam = ParameterSpec.builder(ClassName.get(ViewGroup.class)
                .annotated(AnnotationSpec.builder(NonNull.class).build()), VIEW_GROUP, Modifier.FINAL)
                .build();

        // Add count to final field for early exit strategy
        typeSpecBuilder.addField(FieldSpec.builder(int.class, VIEW_COUNT, Modifier.FINAL, Modifier.STATIC,
                Modifier.PRIVATE).initializer("$L", mElements.size()).build());

        // Create bindViews method
        MethodSpec publicBindMethod = MethodSpec.methodBuilder(BIND_VIEWS)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(param)
                .addCode(generatePublicBindViewsMethod())
                .build();

        // Create bindViews method
        MethodSpec iterativeBindMethod = MethodSpec.methodBuilder(BIND_VIEWS)
                .addModifiers(Modifier.PRIVATE)
                .addParameter(param)
                .addParameter(viewGroupParam)
                .returns(int.class)
                .addCode(generateProtectedBindViewsMethod())
                .build();

        // Create unbindViews method
        MethodSpec unbindMethod = MethodSpec.methodBuilder(UNBIND_VIEWS)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(param)
                .addCode(generateUnbindViewsMethod())
                .build();

        typeSpecBuilder.addMethod(publicBindMethod).addMethod(iterativeBindMethod).addMethod(unbindMethod);
    }

    @NonNull
    private CodeBlock generatePublicBindViewsMethod() throws ShivException {
        CodeBlock.Builder builder = CodeBlock.builder()
                .add("$T $N = ($T) $N;\n", mHostType, FIELD_HOST, mHostType, OBJECT);

        String getViewGroup;
        if (isSubtypeOfType(mHostType, Activity.class)) {
            getViewGroup = ".getWindow().getDecorView().getRootView()";
        } else if (isSubtypeOfType(mHostType, ViewGroup.class)) {
            getViewGroup = "";
        } else if (isSubtypeOfType(mHostType, Fragment.class) || isSubtypeOfType(mHostType, android.support.v4.app.Fragment.class)) {
            getViewGroup = ".getView().getRootView()";
        } else {
            throw new ShivException("Unsupported class: " + mHostType.getQualifiedName());
        }
        builder.add("$T $N = ($T) $N$L;\n", ViewGroup.class, VIEW_GROUP, ViewGroup.class, FIELD_HOST, getViewGroup)
                .add("$L($N, $N);\n", BIND_VIEWS, OBJECT, VIEW_GROUP);

        for (Element element : mElements) {
            if (!isNullable(element)) {
                builder.add("if ($N.$N == null) {\n", FIELD_HOST, element.getSimpleName())
                        .add("    throw new $T(\"Non-optional field $T.$N was not found\");\n", NullPointerException.class,
                                mHostType, element.getSimpleName())
                        .add("}\n");
            }
        }

        return builder.build();
    }

    @NonNull
    private CodeBlock generateProtectedBindViewsMethod() throws ShivException {
        CodeBlock.Builder builder = CodeBlock.builder()
                .add("$T $N = ($T) $N;\n", mHostType, FIELD_HOST, mHostType, OBJECT)
                .add("int size = $N.getChildCount();\n", VIEW_GROUP)
                .add("int $N = 0;\n", BOUND)
                .beginControlFlow("for (int i = 0; i < size; i++)")
                .add("$T $N = $N.getChildAt(i);\n", View.class, VIEW, VIEW_GROUP)
                .beginControlFlow("if ($N instanceof $T)", VIEW, ViewGroup.class)
                .add("$N += $N($N, ($T) $N);\n", BOUND, BIND_VIEWS, OBJECT, ViewGroup.class, VIEW)
                .endControlFlow()
                .beginControlFlow("switch ($N.getId())", VIEW);

        for (Element element : mElements) {
            builder.add("case $L:\n", element.getAnnotation(BindView.class).value())
                    .add("    $N.$N = ($T) $N;\n", FIELD_HOST, element.getSimpleName(), element.asType(), VIEW)
                    .add("    $N++;\n", BOUND)
                    .add("    break;\n");
        }

        builder.endControlFlow()
                .beginControlFlow("if ($N >= $N)", BOUND, VIEW_COUNT)
                .add("break;\n")
                .endControlFlow()
                .endControlFlow()
                .add("return $N;\n", BOUND);

        return builder.build();
    }

    @NonNull
    private CodeBlock generateUnbindViewsMethod() throws ShivException {
        CodeBlock.Builder builder = CodeBlock.builder()
                .add("$T $N = ($T) $N;\n", mHostType, FIELD_HOST, mHostType, OBJECT);

        for (Element element : mElements) {
            builder.add("$N.$N = null;\n", FIELD_HOST, element.getSimpleName());
        }

        return builder.build();
    }
}
