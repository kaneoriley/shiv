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

import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.support.annotation.NonNull;
import android.support.v7.preference.PreferenceFragmentCompat;
import com.squareup.javapoet.*;
import me.oriley.shiv.BindPreference;
import me.oriley.shiv.ShivException;
import me.oriley.shiv.ShivProcessor;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

import static me.oriley.shiv.ProcessorUtils.isNullable;
import static me.oriley.shiv.ProcessorUtils.isSubtypeOfType;

final class PreferenceBindingHolder extends AbstractBindingHolder {

    private static final String BIND_PREFERENCES = "bindPreferences";
    private static final String UNBIND_PREFERENCES = "unbindPreferences";


    PreferenceBindingHolder(@NonNull TypeElement hostType) {
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

        // Create bindPreferences method
        MethodSpec bindMethod = MethodSpec.methodBuilder(BIND_PREFERENCES)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(param)
                .addCode(generateBindPreferencesMethod())
                .build();

        // Create unbindPreferences method
        MethodSpec unbindMethod = MethodSpec.methodBuilder(UNBIND_PREFERENCES)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(param)
                .addCode(generateUnbindPreferencesMethod())
                .build();

        typeSpecBuilder.addMethod(bindMethod).addMethod(unbindMethod);
    }

    @NonNull
    private CodeBlock generateBindPreferencesMethod() throws ShivException {
        CodeBlock.Builder builder = CodeBlock.builder()
                .add("$T $N = ($T) $N;\n", mHostType, FIELD_HOST, mHostType, OBJECT);

        if (!isSubtypeOfType(mHostType, PreferenceActivity.class) && !isSubtypeOfType(mHostType, PreferenceFragment.class) &&
                !isSubtypeOfType(mHostType, PreferenceFragmentCompat.class)) {
            throw new ShivException("Unsupported class: " + mHostType.getQualifiedName());
        }

        for (Element element : mElements) {
            builder.add("$N.$N = ($T) $N.findPreference($S);\n", FIELD_HOST, element.getSimpleName(), element.asType(),
                    FIELD_HOST, element.getAnnotation(BindPreference.class).value());
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
    private CodeBlock generateUnbindPreferencesMethod() throws ShivException {
        CodeBlock.Builder builder = CodeBlock.builder()
                .add("$T $N = ($T) $N;\n", mHostType, FIELD_HOST, mHostType, OBJECT);

        for (Element element : mElements) {
            builder.add("$N.$N = null;\n", FIELD_HOST, element.getSimpleName());
        }

        return builder.build();
    }
}
