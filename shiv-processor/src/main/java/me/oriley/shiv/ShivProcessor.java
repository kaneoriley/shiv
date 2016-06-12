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
import com.squareup.javapoet.*;

import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.util.*;

public final class ShivProcessor extends BaseProcessor {

    private static final String VIEW_TYPE = "android.view.View";
    private static final ClassName VIEW_CLASS = ClassName.get("android.view", "View");

    private static final String ACTIVITY_TYPE = "android.app.Activity";
    private static final String FRAGMENT_TYPE = "android.app.Fragment";
    private static final String SUPPORT_FRAGMENT_TYPE = "android.support.v4.app.Fragment";

    private static final String BIND = "bind";
    private static final String UNBIND = "unbind";
    private static final String OBJECT = "object";
    private static final String FIELD_HOST = "fieldHost";
    private static final String VIEW_HOST = "viewHost";

    @NonNull
    private Filer mFiler;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        mFiler = env.getFiler();
        setTag(ShivProcessor.class.getSimpleName());
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> types = new LinkedHashSet<>();
        types.add(BindView.class.getCanonicalName());
        return types;
    }

    @Override
    public boolean process(@NonNull Set<? extends TypeElement> annotations, @NonNull RoundEnvironment env) {
        if (env.processingOver()) {
            return true;
        }

        try {
            final Map<TypeElement, List<Element>> fieldBindings = collectFields(env);

            for (TypeElement typeElement : fieldBindings.keySet()) {
                String packageName = getPackageName(typeElement);
                writeToFile(packageName, generateViewBinderFactory(typeElement, fieldBindings.get(typeElement)));
            }
        } catch (ShivProcessorException e) {
            error(e.getMessage());
            return true;
        }

        return false;
    }

    @NonNull
    private TypeSpec generateViewBinderFactory(@NonNull TypeElement typeElement,
                                               @NonNull List<Element> binderFields) throws ShivProcessorException {
        // Class type
        String packageName = getPackageName(typeElement);
        String className = getClassName(typeElement, packageName);

        // Class builder
        TypeSpec.Builder typeSpecBuilder = TypeSpec.classBuilder(className + ViewBinder.CLASS_SUFFIX)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .superclass(ViewBinder.class);

        // Type parameter
        ParameterSpec param = ParameterSpec.builder(TypeName.get(Object.class)
                .annotated(AnnotationSpec.builder(NonNull.class).build()), OBJECT, Modifier.FINAL)
                .build();

        // Create bind method
        MethodSpec bindMethod = MethodSpec.methodBuilder(BIND)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(param)
                .addCode(generateBindMethod(typeElement, binderFields))
                .build();

        // Create unbind method
        MethodSpec unbindMethod = MethodSpec.methodBuilder(UNBIND)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .addParameter(param)
                .addCode(generateUnbindMethod(typeElement, binderFields))
                .build();

        return typeSpecBuilder.addMethod(bindMethod).addMethod(unbindMethod).build();
    }

    @NonNull
    private CodeBlock generateBindMethod(@NonNull TypeElement hostType,
                                         @NonNull List<Element> binderFields) throws ShivProcessorException {
        CodeBlock.Builder builder = CodeBlock.builder()
                .add("$T $N = ($T) $N;\n", hostType, FIELD_HOST, hostType, OBJECT);

        String viewHost;
        if (isSubtypeOfType(hostType, ACTIVITY_TYPE) || isSubtypeOfType(hostType, VIEW_TYPE)) {
            viewHost = FIELD_HOST;
        } else if (isSubtypeOfType(hostType, FRAGMENT_TYPE) || isSubtypeOfType(hostType, SUPPORT_FRAGMENT_TYPE)) {
            builder.add("$T $N = $N.getView();\n", VIEW_CLASS, VIEW_HOST, FIELD_HOST);
            viewHost = VIEW_HOST;
        } else {
            throw new ShivProcessorException("Unsupported class: " + hostType.getQualifiedName());
        }

        for (Element element : binderFields) {
            builder.add("$N.$N = ($T) $N.findViewById($L);\n", FIELD_HOST, element.getSimpleName(), element.asType(),
                    viewHost, element.getAnnotation(BindView.class).value());
            if (!isNullable(element)) {
                builder.add("if ($N.$N == null) {\n", FIELD_HOST, element.getSimpleName())
                .add("    throw new $T(\"Non-optional field $N.$N was not found\");\n", NullPointerException.class,
                        FIELD_HOST, element.getSimpleName())
                .add("}\n");
            }
        }

        return builder.build();
    }

    @NonNull
    private CodeBlock generateUnbindMethod(@NonNull TypeElement hostType,
                                           @NonNull List<Element> binderFields) throws ShivProcessorException {
        CodeBlock.Builder builder = CodeBlock.builder()
                .add("$T $N = ($T) $N;\n", hostType, FIELD_HOST, hostType, OBJECT);

        for (Element element : binderFields) {
            builder.add("$N.$N = null;\n", FIELD_HOST, element.getSimpleName());
        }

        return builder.build();
    }

    @NonNull
    private Map<TypeElement, List<Element>> collectFields(@NonNull RoundEnvironment env) throws ShivProcessorException {
        final Map<TypeElement, List<Element>> fieldsByClass = new HashMap<>();

        for (Element e : env.getElementsAnnotatedWith(BindView.class)) {
            if (e.getKind() != ElementKind.FIELD) {
                throw new ShivProcessorException(e.getSimpleName() + " is annotated with @" +
                        BindView.class.getName() + " but is not a field");
            }

            TypeMirror fieldType = e.asType();
            if (isPrivate(e)) {
                throw new ShivProcessorException("Field must not be private: " + e.getSimpleName());
            }

            if (!isSubtypeOfType(fieldType, VIEW_TYPE)) {
                throw new ShivProcessorException("Field must inherit from View type: " + e.getSimpleName());
            }

            final TypeElement type = findEnclosingElement(e);
            // class should exist
            if (type == null) {
                throw new ShivProcessorException("Could not find a class for " + e.getSimpleName());
            }
            // and it should be public
            if (isPrivate(type)) {
                throw new ShivProcessorException("Class is private: " + type);
            }
            // as well as all parent classes
            TypeElement parentType = findEnclosingElement(type);
            while (parentType != null) {
                if (!isPrivate(parentType)) {
                    throw new ShivProcessorException("Parent class is private: " + parentType);
                }
                parentType = findEnclosingElement(parentType);
            }

            List<Element> fieldsInClass = fieldsByClass.get(type);
            if (fieldsInClass == null) {
                fieldsInClass = new ArrayList<>();
                fieldsByClass.put(type, fieldsInClass);
            }

            fieldsInClass.add(e);
        }

        return fieldsByClass;
    }

    @NonNull
    private JavaFile writeToFile(@NonNull String packageName, @NonNull TypeSpec spec) throws ShivProcessorException {
        final JavaFile file = JavaFile.builder(packageName, spec).indent("    ").build();
        try {
            file.writeTo(mFiler);
        } catch (IOException e) {
            throw new ShivProcessorException(e);
        }
        return file;
    }

    private static final class ShivProcessorException extends Exception {

        ShivProcessorException(String message) {
            super(message);
        }

        ShivProcessorException(Throwable cause) {
            super(cause);
        }
    }
}