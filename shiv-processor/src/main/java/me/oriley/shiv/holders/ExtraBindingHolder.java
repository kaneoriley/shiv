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
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import com.squareup.javapoet.*;
import me.oriley.shiv.*;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

import static me.oriley.shiv.ProcessorUtils.isNullable;

final class ExtraBindingHolder extends AbstractBindingHolder {

    private static final String BIND_EXTRAS = "bindExtras";
    private static final String INTENT = "intent";

    private boolean mHasNonOptionalExtra;


    ExtraBindingHolder(@NonNull ShivProcessor processor, @NonNull TypeElement hostType) {
        super(processor, hostType);
    }


    @Override
    void addElement(@NonNull Element element) {
        super.addElement(element);
        if (!element.getAnnotation(BindExtra.class).optional()) {
            mHasNonOptionalExtra = true;
        }
    }

    @Override
    void addBindingsToClass(@NonNull TypeSpec.Builder typeSpecBuilder) throws ShivException {
        if (mElements.isEmpty()) {
            // Nothing to bind
            return;
        }

        // Type parameter
        ParameterSpec param = ParameterSpec.builder(TypeName.get(Object.class)
                .annotated(AnnotationSpec.builder(NonNull.class).build()), OBJECT, Modifier.FINAL)
                .build();

        // Create bindExtras method
        MethodSpec bindMethod = MethodSpec.methodBuilder(BIND_EXTRAS)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(param)
                .addCode(generateBindExtrasMethod())
                .build();

        typeSpecBuilder.addMethod(bindMethod);
    }

    @NonNull
    private CodeBlock generateBindExtrasMethod() throws ShivException {
        CodeBlock.Builder builder = CodeBlock.builder()
                .add("$T $N = ($T) $N;\n", mHostType, FIELD_HOST, mHostType, OBJECT)
                .add("$T $N;\n", Object.class, EXTRA);

        if (ProcessorUtils.isSubtypeOfType(mHostType, Activity.class)) {
            builder.add("$T $N = $N.getIntent();\n", Intent.class, INTENT, FIELD_HOST);
            builder.add("$T $N = $N != null ? $N.getExtras() : null;\n", Bundle.class, BUNDLE, INTENT, INTENT);
        } else if (ProcessorUtils.isSubtypeOfType(mHostType, Fragment.class) ||
                ProcessorUtils.isSubtypeOfType(mHostType, android.support.v4.app.Fragment.class)) {
            builder.add("$T $N = $N.getArguments();\n", Bundle.class, BUNDLE, FIELD_HOST);
        } else {
            throw new ShivException("Unsupported class: " + mHostType.getQualifiedName());
        }

        builder.beginControlFlow("if ($N == null)", BUNDLE);
        if (mHasNonOptionalExtra) {
            builder.add("throw new $T(\"$T contains non-optional extra and bundle was null\");\n", IllegalStateException.class,
                    mHostType);
        } else {
            builder.add("return;\n");
        }
        builder.endControlFlow();

        for (Element element : mElements) {
            BindExtra bindExtra = element.getAnnotation(BindExtra.class);

            builder.add("$N = $N.get($S);\n", EXTRA, BUNDLE, bindExtra.value());
            if (isNullable(element) || bindExtra.optional()) {
                builder.add("if ($N != null) {\n", EXTRA)
                        .add("    $N.$N = ($T) $N;\n", FIELD_HOST, element.getSimpleName(), element.asType(), EXTRA)
                        .add("}\n");
            } else {
                builder.add("if ($N == null) {\n", EXTRA)
                        .add("    throw new $T(\"Non-optional extra for $T.$N was not found\");\n", NullPointerException.class,
                                mHostType, element.getSimpleName())
                        .add("}\n")
                        .add("$N.$N = ($T) $N;\n", FIELD_HOST, element.getSimpleName(), element.asType(), EXTRA);
            }
        }

        return builder.build();
    }
}
