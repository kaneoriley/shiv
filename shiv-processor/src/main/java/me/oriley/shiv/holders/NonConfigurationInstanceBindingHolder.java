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
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import com.squareup.javapoet.*;
import me.oriley.shiv.ShivException;
import me.oriley.shiv.ShivProcessor;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.Map;

import static me.oriley.shiv.ProcessorUtils.isSubtypeOfType;

final class NonConfigurationInstanceBindingHolder extends AbstractBindingHolder {

    private static final ParameterizedTypeName NON_CONFIGURATION_MAP_TYPE =
            ParameterizedTypeName.get(Map.class, String.class, Object.class);

    private static final String SAVE_NON_CONFIG_INSTANCE = "saveNonConfigurationInstance";
    private static final String RESTORE_NON_CONFIG_INSTANCE = "restoreNonConfigurationInstance";


    NonConfigurationInstanceBindingHolder(@NonNull TypeElement hostType) {
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

        ParameterSpec mapParam = ParameterSpec.builder(NON_CONFIGURATION_MAP_TYPE.annotated(AnnotationSpec
                .builder(NonNull.class).build()), MAP, Modifier.FINAL)
                .build();

        // Create restoreNonConfigInstance method
        MethodSpec restoreMethod = MethodSpec.methodBuilder(RESTORE_NON_CONFIG_INSTANCE)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(param)
                .addCode(generateRestoreNonConfigInstanceMethod(typeSpecBuilder))
                .build();

        // Create saveNonConfigInstance method
        MethodSpec saveMethod = MethodSpec.methodBuilder(SAVE_NON_CONFIG_INSTANCE)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(param)
                .addParameter(mapParam)
                .addCode(generateSaveNonConfigInstanceMethod())
                .build();

        typeSpecBuilder.addMethod(restoreMethod).addMethod(saveMethod);
    }

    @NonNull
    private CodeBlock generateSaveNonConfigInstanceMethod() throws ShivException {
        CodeBlock.Builder builder = CodeBlock.builder()
                .add("$T $N = ($T) $N;\n", mHostType, FIELD_HOST, mHostType, OBJECT);

        for (Element element : mElements) {
            String keyName = (KEY_INSTANCE_PREFIX + element.getSimpleName()).toUpperCase();
            builder.beginControlFlow("if ($N.$N != null)", FIELD_HOST, element.getSimpleName())
                    .add("$N.put($N, $N.$N);\n", MAP, keyName, FIELD_HOST, element.getSimpleName())
                    .endControlFlow();
        }

        return builder.build();
    }

    @NonNull
    private CodeBlock generateRestoreNonConfigInstanceMethod(@NonNull TypeSpec.Builder hostBuilder) throws ShivException {
        CodeBlock.Builder builder = CodeBlock.builder()
                .add("$T $N = ($T) $N;\n", mHostType, FIELD_HOST, mHostType, OBJECT);

        String methodName;
        if (isSubtypeOfType(mHostType, FragmentActivity.class)) {
            methodName = "getLastCustomNonConfigurationInstance";
        } else if (isSubtypeOfType(mHostType, Activity.class)) {
            methodName = "getLastNonConfigurationInstance";
        } else {
            throw new ShivException("Unsupported class: " + mHostType.getQualifiedName());
        }
        builder.add("$T $N = $N.$N();\n", Object.class, EXTRA, FIELD_HOST, methodName)
                .beginControlFlow("if ($N == null)", EXTRA)
                .add("return;\n")
                .endControlFlow();
        builder.add("$T $N = ($T) $N;\n", NON_CONFIGURATION_MAP_TYPE, MAP, NON_CONFIGURATION_MAP_TYPE, EXTRA);

        for (Element element : mElements) {
            String keyName = (KEY_INSTANCE_PREFIX + element.getSimpleName()).toUpperCase();
            hostBuilder.addField(FieldSpec.builder(String.class, keyName, Modifier.FINAL, Modifier.STATIC,
                    Modifier.PRIVATE).initializer("\"$N.$N\"", mHostType.getQualifiedName(), element.getSimpleName()).build());

            builder.add("$N = $N.get($N);\n", EXTRA, MAP, keyName)
                    .add("if ($N != null) {\n", EXTRA)
                    .add("    $N.$N = ($T) $N;\n", FIELD_HOST, element.getSimpleName(), element.asType(), EXTRA)
                    .add("}\n");
        }

        return builder.build();
    }
}
