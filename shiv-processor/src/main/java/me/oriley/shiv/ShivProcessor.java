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
import java.lang.annotation.Annotation;
import java.util.*;

public final class ShivProcessor extends BaseProcessor {

    private static final String CHAR_SEQUENCE_TYPE = "java.lang.CharSequence";
    private static final String SERIALIZABLE_TYPE = "java.io.Serializable";
    private static final String PARCELABLE_TYPE = "android.os.Parcelable";
    private static final String PREFERENCE_TYPE = "android.preference.Preference";
    private static final String SUPPORT_PREFERENCE_TYPE = "android.support.v7.preference.Preference";
    private static final String VIEW_TYPE = "android.view.View";
    private static final ClassName BUNDLE_CLASS = ClassName.get("android.os", "Bundle");
    private static final ClassName INTENT_CLASS = ClassName.get("android.content", "Intent");
    private static final ClassName VIEW_CLASS = ClassName.get("android.view", "View");
    private static final ClassName SHIV_CLASS = ClassName.get("me.oriley.shiv", "Shiv");

    private static final String ACTIVITY_TYPE = "android.app.Activity";
    private static final String FRAGMENT_TYPE = "android.app.Fragment";
    private static final String SUPPORT_FRAGMENT_TYPE = "android.support.v4.app.Fragment";

    private static final String PREFERENCE_ACTIVITY_TYPE = "android.preference.PreferenceActivity";
    private static final String PREFERENCE_FRAGMENT_TYPE = "android.preference.PreferenceFragment";
    private static final String SUPPORT_PREFERENCE_FRAGMENT_TYPE = "android.support.v7.preference.PreferenceFragmentCompat";

    private static final String BIND_VIEWS = "bindViews";
    private static final String UNBIND_VIEWS = "unbindViews";
    private static final String BIND_EXTRAS = "bindExtras";
    private static final String BIND_PREFERENCES = "bindPreferences";
    private static final String UNBIND_PREFERENCES = "unbindPreferences";
    private static final String OBJECT = "object";
    private static final String INTENT = "intent";
    private static final String BUNDLE = "bundle";
    private static final String EXTRA = "extra";
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
        Collections.addAll(types, BindView.class.getCanonicalName(), BindExtra.class.getCanonicalName(),
                BindPreference.class.getCanonicalName());
        return types;
    }

    @Override
    public boolean process(@NonNull Set<? extends TypeElement> annotations, @NonNull RoundEnvironment env) {
        if (env.processingOver()) {
            return true;
        }

        try {
            final Map<TypeElement, BindingHolder> bindings = new HashMap<>();
            collectBindings(env, bindings, BindView.class);
            collectBindings(env, bindings, BindExtra.class);
            collectBindings(env, bindings, BindPreference.class);

            for (TypeElement typeElement : bindings.keySet()) {
                String packageName = getPackageName(typeElement);
                writeToFile(packageName, generateBinder(typeElement, bindings.get(typeElement)));
            }
        } catch (ShivProcessorException e) {
            error(e.getMessage());
            return true;
        }

        return false;
    }

    @NonNull
    private TypeSpec generateBinder(@NonNull TypeElement typeElement,
                                    @NonNull BindingHolder holder) throws ShivProcessorException {
        // Class type
        String packageName = getPackageName(typeElement);
        String className = getClassName(typeElement, packageName);

        // Class builder
        TypeSpec.Builder typeSpecBuilder = TypeSpec.classBuilder(className + Binder.CLASS_SUFFIX)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .superclass(Binder.class);

        // Type parameter
        ParameterSpec param = ParameterSpec.builder(TypeName.get(Object.class)
                .annotated(AnnotationSpec.builder(NonNull.class).build()), OBJECT, Modifier.FINAL)
                .build();

        if (!holder.viewBindings.isEmpty()) {
            // Create bindViews method
            MethodSpec bindMethod = MethodSpec.methodBuilder(BIND_VIEWS)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .addParameter(param)
                    .addCode(generateBindViewsMethod(typeElement, holder.viewBindings))
                    .build();

            // Create unbindViews method
            MethodSpec unbindMethod = MethodSpec.methodBuilder(UNBIND_VIEWS)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .addParameter(param)
                    .addCode(generateUnbindViewsMethod(typeElement, holder.viewBindings))
                    .build();

            typeSpecBuilder.addMethod(bindMethod).addMethod(unbindMethod);
        }

        if (!holder.extraBindings.isEmpty()) {
            // Create bindExtras method
            MethodSpec bindMethod = MethodSpec.methodBuilder(BIND_EXTRAS)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .addParameter(param)
                    .addCode(generateBindExtrasMethod(typeElement, holder.extraBindings))
                    .build();

            typeSpecBuilder.addMethod(bindMethod);
        }

        if (!holder.preferenceBindings.isEmpty()) {
            // Create bindPreferences method
            MethodSpec bindMethod = MethodSpec.methodBuilder(BIND_PREFERENCES)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .addParameter(param)
                    .addCode(generateBindPreferencesMethod(typeElement, holder.preferenceBindings))
                    .build();

            // Create unbindPreferences method
            MethodSpec unbindMethod = MethodSpec.methodBuilder(UNBIND_PREFERENCES)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .addParameter(param)
                    .addCode(generateUnbindPreferencesMethod(typeElement, holder.preferenceBindings))
                    .build();

            typeSpecBuilder.addMethod(bindMethod).addMethod(unbindMethod);
        }

        return typeSpecBuilder.build();
    }

    @NonNull
    private CodeBlock generateBindViewsMethod(@NonNull TypeElement hostType,
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
    private CodeBlock generateUnbindViewsMethod(@NonNull TypeElement hostType,
                                                @NonNull List<Element> binderFields) throws ShivProcessorException {
        CodeBlock.Builder builder = CodeBlock.builder()
                .add("$T $N = ($T) $N;\n", hostType, FIELD_HOST, hostType, OBJECT);

        for (Element element : binderFields) {
            builder.add("$N.$N = null;\n", FIELD_HOST, element.getSimpleName());
        }

        return builder.build();
    }

    @NonNull
    private CodeBlock generateBindPreferencesMethod(@NonNull TypeElement hostType,
                                                    @NonNull List<Element> binderFields) throws ShivProcessorException {
        CodeBlock.Builder builder = CodeBlock.builder()
                .add("$T $N = ($T) $N;\n", hostType, FIELD_HOST, hostType, OBJECT);

        if (!isSubtypeOfType(hostType, PREFERENCE_ACTIVITY_TYPE) && !isSubtypeOfType(hostType, PREFERENCE_FRAGMENT_TYPE) &&
                !isSubtypeOfType(hostType, SUPPORT_PREFERENCE_FRAGMENT_TYPE)) {
            throw new ShivProcessorException("Unsupported class: " + hostType.getQualifiedName());
        }

        for (Element element : binderFields) {
            builder.add("$N.$N = ($T) $N.findPreference($S);\n", FIELD_HOST, element.getSimpleName(), element.asType(),
                    FIELD_HOST, element.getAnnotation(BindPreference.class).value());
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
    private CodeBlock generateUnbindPreferencesMethod(@NonNull TypeElement hostType,
                                                      @NonNull List<Element> binderFields) throws ShivProcessorException {
        CodeBlock.Builder builder = CodeBlock.builder()
                .add("$T $N = ($T) $N;\n", hostType, FIELD_HOST, hostType, OBJECT);

        for (Element element : binderFields) {
            builder.add("$N.$N = null;\n", FIELD_HOST, element.getSimpleName());
        }

        return builder.build();
    }

    @NonNull
    private CodeBlock generateBindExtrasMethod(@NonNull TypeElement hostType,
                                               @NonNull List<Element> binderFields) throws ShivProcessorException {
        CodeBlock.Builder builder = CodeBlock.builder()
                .add("$T $N = ($T) $N;\n", hostType, FIELD_HOST, hostType, OBJECT)
                .add("$T $N;\n", Object.class, EXTRA);

        if (isSubtypeOfType(hostType, ACTIVITY_TYPE)) {
            builder.add("$T $N = $N.getIntent();\n", INTENT_CLASS, INTENT, FIELD_HOST);
            builder.add("$T $N = $N != null ? $N.getExtras() : null;\n", BUNDLE_CLASS, BUNDLE, INTENT, INTENT);
        } else if (isSubtypeOfType(hostType, FRAGMENT_TYPE) || isSubtypeOfType(hostType, SUPPORT_FRAGMENT_TYPE)) {
            builder.add("$T $N = $N.getArguments();\n", BUNDLE_CLASS, BUNDLE, FIELD_HOST);
        } else {
            throw new ShivProcessorException("Unsupported class: " + hostType.getQualifiedName());
        }

        for (Element element : binderFields) {
            BindExtra bindExtra = element.getAnnotation(BindExtra.class);

            builder.add("$N = $T.getExtra($N, $S);\n", EXTRA, SHIV_CLASS, BUNDLE, bindExtra.value());
            if (isNullable(element) || bindExtra.optional()) {
                builder.add("if ($N != null) {\n", EXTRA)
                        .add("    $N.$N = ($T) $N;\n", FIELD_HOST, element.getSimpleName(), element.asType(), EXTRA)
                        .add("}\n");
            } else {
                builder.add("if ($N == null) {\n", EXTRA)
                        .add("    throw new $T(\"Non-optional extra for $N.$N was not found\");\n", NullPointerException.class,
                                FIELD_HOST, element.getSimpleName())
                        .add("}\n")
                        .add("$N.$N = ($T) $N;\n", FIELD_HOST, element.getSimpleName(), element.asType(), EXTRA);
            }
        }

        return builder.build();
    }

    private void collectBindings(@NonNull RoundEnvironment env,
                                 @NonNull Map<TypeElement, BindingHolder> bindings,
                                 @NonNull Class<? extends Annotation> annotation) throws ShivProcessorException {
        for (Element e : env.getElementsAnnotatedWith(annotation)) {
            if (e.getKind() != ElementKind.FIELD) {
                throw new ShivProcessorException(e.getSimpleName() + " is annotated with @" + annotation.getName() +
                        " but is not a field");
            }

            TypeMirror fieldType = e.asType();
            if (isPrivate(e)) {
                throw new ShivProcessorException("Field must not be private: " + e.getSimpleName());
            } else if (isStatic(e)) {
                throw new ShivProcessorException("Field must not be static: " + e.getSimpleName());
            }

            if (annotation == BindView.class && !isSubtypeOfType(fieldType, VIEW_TYPE)) {
                throw new ShivProcessorException("Field must inherit from View type: " + e.getSimpleName());
            } else if (annotation == BindExtra.class && !isValidExtraField(fieldType)) {
                throw new ShivProcessorException("Field must be Serializable or Parcelable: " + e.getSimpleName());
            } else if (annotation == BindPreference.class && !isValidPreferenceField(fieldType)) {
                throw new ShivProcessorException("Field must inherit from " + PREFERENCE_TYPE + " or " +
                        SUPPORT_PREFERENCE_TYPE + " type: " + e.getSimpleName());
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

            BindingHolder bindingHolder = bindings.get(type);
            if (bindingHolder == null) {
                bindingHolder = new BindingHolder();
                bindings.put(type, bindingHolder);
            }

            bindingHolder.addBinding(annotation, e);
        }
    }

    private boolean isValidExtraField(@NonNull TypeMirror fieldType) {
        return isAssignable(fieldType, CHAR_SEQUENCE_TYPE) || isAssignable(fieldType, SERIALIZABLE_TYPE) ||
                isAssignable(fieldType, PARCELABLE_TYPE);
    }

    private boolean isValidPreferenceField(@NonNull TypeMirror fieldType) {
        return isSubtypeOfType(fieldType, PREFERENCE_TYPE) || isSubtypeOfType(fieldType, SUPPORT_PREFERENCE_TYPE);
    }

    @NonNull
    private JavaFile writeToFile(@NonNull String packageName, @NonNull TypeSpec spec) throws ShivProcessorException {
        final JavaFile file = JavaFile.builder(packageName, spec)
                .addFileComment("Generated by ShivProcessor, do not edit manually!")
                .indent("    ").build();
        try {
            file.writeTo(mFiler);
        } catch (IOException e) {
            throw new ShivProcessorException(e);
        }
        return file;
    }

    @SuppressWarnings("WeakerAccess")
    static final class BindingHolder{

        @NonNull
        final List<Element> viewBindings = new ArrayList<>();

        @NonNull
        final List<Element> extraBindings = new ArrayList<>();

        @NonNull
        final List<Element> preferenceBindings = new ArrayList<>();

        void addBinding(@NonNull Class<? extends Annotation> annotation, @NonNull Element element) throws ShivProcessorException {
            if (annotation == BindView.class) {
                viewBindings.add(element);
            } else if (annotation == BindExtra.class) {
                extraBindings.add(element);
            } else if (annotation == BindPreference.class) {
                preferenceBindings.add(element);
            } else {
                throw new ShivProcessorException("Invalid annotation: " + annotation);
            }
        }
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