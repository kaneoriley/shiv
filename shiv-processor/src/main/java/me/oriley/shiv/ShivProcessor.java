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

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.support.annotation.NonNull;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import com.squareup.javapoet.*;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.util.*;

public final class ShivProcessor extends BaseProcessor {

    private static final String SUPPORT_PREFERENCE_TYPE = "android.support.v7.preference.Preference";
    private static final String SUPPORT_FRAGMENT_TYPE = "android.support.v4.app.Fragment";
    private static final String SUPPORT_PREFERENCE_FRAGMENT_TYPE = "android.support.v7.preference.PreferenceFragmentCompat";

    private static final String BIND_VIEWS = "bindViews";
    private static final String UNBIND_VIEWS = "unbindViews";
    private static final String BIND_EXTRAS = "bindExtras";
    private static final String BIND_PREFERENCES = "bindPreferences";
    private static final String UNBIND_PREFERENCES = "unbindPreferences";
    private static final String SAVE_INSTANCE = "saveInstance";
    private static final String RESTORE_INSTANCE = "restoreInstance";
    private static final String OBJECT = "object";
    private static final String INTENT = "intent";
    private static final String BUNDLE = "bundle";
    private static final String EXTRA = "extra";
    private static final String BOUND = "bound";
    private static final String VIEW = "view";
    private static final String VIEW_GROUP = "viewGroup";
    private static final String FIELD_HOST = "fieldHost";
    private static final String KEY_INSTANCE_PREFIX = "SHIV_KEY_INSTANCE_";
    private static final String VIEW_COUNT = "VIEW_COUNT";

    @NonNull
    private Filer mFiler;

    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        mFiler = env.getFiler();
        setTag(ShivProcessor.class.getSimpleName());
    }

    @NonNull
    @Override
    protected Class[] getSupportedAnnotationClasses() {
        return new Class[]{BindView.class, BindExtra.class, BindPreference.class, BindInstance.class};
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
            collectBindings(env, bindings, BindInstance.class);

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
            ParameterSpec viewGroupParam = ParameterSpec.builder(ClassName.get(ViewGroup.class)
                    .annotated(AnnotationSpec.builder(NonNull.class).build()), VIEW_GROUP, Modifier.FINAL)
                    .build();

            // Add count to final field for early exit strategy
            typeSpecBuilder.addField(FieldSpec.builder(int.class, VIEW_COUNT, Modifier.FINAL, Modifier.STATIC,
                    Modifier.PRIVATE).initializer("$L", holder.viewBindings.size()).build());

            // Create bindViews method
            MethodSpec publicBindMethod = MethodSpec.methodBuilder(BIND_VIEWS)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .addParameter(param)
                    .addCode(generatePublicBindViewsMethod(typeElement, holder.viewBindings))
                    .build();

            // Create bindViews method
            MethodSpec iterativeBindMethod = MethodSpec.methodBuilder(BIND_VIEWS)
                    .addModifiers(Modifier.PRIVATE)
                    .addParameter(param)
                    .addParameter(viewGroupParam)
                    .returns(int.class)
                    .addCode(generateProtectedBindViewsMethod(typeElement, holder.viewBindings))
                    .build();

            // Create unbindViews method
            MethodSpec unbindMethod = MethodSpec.methodBuilder(UNBIND_VIEWS)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .addParameter(param)
                    .addCode(generateUnbindViewsMethod(typeElement, holder.viewBindings))
                    .build();

            typeSpecBuilder.addMethod(publicBindMethod).addMethod(iterativeBindMethod).addMethod(unbindMethod);
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

        if (!holder.instanceBindings.isEmpty()) {
            ParameterSpec bundleParam = ParameterSpec.builder(ClassName.get(Bundle.class)
                    .annotated(AnnotationSpec.builder(NonNull.class).build()), BUNDLE, Modifier.FINAL)
                    .build();

            // Create restoreInstance method
            MethodSpec restoreMethod = MethodSpec.methodBuilder(RESTORE_INSTANCE)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .addParameter(param)
                    .addParameter(bundleParam)
                    .addCode(generateRestoreInstanceMethod(typeSpecBuilder, typeElement, holder.instanceBindings))
                    .build();

            // Create saveInstance method
            MethodSpec saveMethod = MethodSpec.methodBuilder(SAVE_INSTANCE)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(Override.class)
                    .addParameter(param)
                    .addParameter(bundleParam)
                    .addCode(generateSaveInstanceMethod(typeElement, holder.instanceBindings))
                    .build();

            typeSpecBuilder.addMethod(restoreMethod).addMethod(saveMethod);
        }

        return typeSpecBuilder.build();
    }

    @NonNull
    private CodeBlock generatePublicBindViewsMethod(@NonNull TypeElement hostType,
                                                    @NonNull List<Element> binderFields) throws ShivProcessorException {
        CodeBlock.Builder builder = CodeBlock.builder()
                .add("$T $N = ($T) $N;\n", hostType, FIELD_HOST, hostType, OBJECT);

        String getViewGroup;
        if (isSubtypeOfType(hostType, Activity.class)) {
            getViewGroup = ".getWindow().getDecorView().getRootView()";
        } else if (isSubtypeOfType(hostType, ViewGroup.class)) {
            getViewGroup = "";
        } else if (isSubtypeOfType(hostType, Fragment.class) || isSubtypeOfType(hostType, SUPPORT_FRAGMENT_TYPE)) {
            getViewGroup = ".getView().getRootView()";
        } else {
            throw new ShivProcessorException("Unsupported class: " + hostType.getQualifiedName());
        }
        builder.add("$T $N = ($T) $N$L;\n", ViewGroup.class, VIEW_GROUP, ViewGroup.class, FIELD_HOST, getViewGroup)
                .add("$L($N, $N);\n", BIND_VIEWS, OBJECT, VIEW_GROUP);

        for (Element element : binderFields) {
            if (!isNullable(element)) {
                builder.add("if ($N.$N == null) {\n", FIELD_HOST, element.getSimpleName())
                        .add("    throw new $T(\"Non-optional field $T.$N was not found\");\n", NullPointerException.class,
                                hostType, element.getSimpleName())
                        .add("}\n");
            }
        }

        return builder.build();
    }

    @NonNull
    private CodeBlock generateProtectedBindViewsMethod(@NonNull TypeElement hostType,
                                                       @NonNull List<Element> binderFields) throws ShivProcessorException {
        CodeBlock.Builder builder = CodeBlock.builder()
                .add("$T $N = ($T) $N;\n", hostType, FIELD_HOST, hostType, OBJECT)
                .add("int size = $N.getChildCount();\n", VIEW_GROUP)
                .add("int $N = 0;\n", BOUND)
                .beginControlFlow("for (int i = 0; i < size; i++)")
                .add("$T $N = $N.getChildAt(i);\n", View.class, VIEW, VIEW_GROUP)
                .beginControlFlow("if ($N instanceof $T)", VIEW, ViewGroup.class)
                .add("$N += $N($N, ($T) $N);\n", BOUND, BIND_VIEWS, OBJECT, ViewGroup.class, VIEW)
                .endControlFlow()
                .beginControlFlow("switch ($N.getId())", VIEW);

        for (Element element : binderFields) {
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

        if (!isSubtypeOfType(hostType, PreferenceActivity.class) && !isSubtypeOfType(hostType, PreferenceFragment.class) &&
                !isSubtypeOfType(hostType, SUPPORT_PREFERENCE_FRAGMENT_TYPE)) {
            throw new ShivProcessorException("Unsupported class: " + hostType.getQualifiedName());
        }

        for (Element element : binderFields) {
            builder.add("$N.$N = ($T) $N.findPreference($S);\n", FIELD_HOST, element.getSimpleName(), element.asType(),
                    FIELD_HOST, element.getAnnotation(BindPreference.class).value());
            if (!isNullable(element)) {
                builder.add("if ($N.$N == null) {\n", FIELD_HOST, element.getSimpleName())
                        .add("    throw new $T(\"Non-optional field $T.$N was not found\");\n", NullPointerException.class,
                                hostType, element.getSimpleName())
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

        if (isSubtypeOfType(hostType, Activity.class)) {
            builder.add("$T $N = $N.getIntent();\n", Intent.class, INTENT, FIELD_HOST);
            builder.add("$T $N = $N != null ? $N.getExtras() : null;\n", Bundle.class, BUNDLE, INTENT, INTENT);
        } else if (isSubtypeOfType(hostType, Fragment.class) || isSubtypeOfType(hostType, SUPPORT_FRAGMENT_TYPE)) {
            builder.add("$T $N = $N.getArguments();\n", Bundle.class, BUNDLE, FIELD_HOST);
        } else {
            throw new ShivProcessorException("Unsupported class: " + hostType.getQualifiedName());
        }

        for (Element element : binderFields) {
            BindExtra bindExtra = element.getAnnotation(BindExtra.class);

            builder.add("$N = $T.get($N, $S);\n", EXTRA, BinderUtils.class, BUNDLE, bindExtra.value());
            if (isNullable(element) || bindExtra.optional()) {
                builder.add("if ($N != null) {\n", EXTRA)
                        .add("    $N.$N = ($T) $N;\n", FIELD_HOST, element.getSimpleName(), element.asType(), EXTRA)
                        .add("}\n");
            } else {
                builder.add("if ($N == null) {\n", EXTRA)
                        .add("    throw new $T(\"Non-optional extra for $T.$N was not found\");\n", NullPointerException.class,
                                hostType, element.getSimpleName())
                        .add("}\n")
                        .add("$N.$N = ($T) $N;\n", FIELD_HOST, element.getSimpleName(), element.asType(), EXTRA);
            }
        }

        return builder.build();
    }

    @NonNull
    private CodeBlock generateSaveInstanceMethod(@NonNull TypeElement hostType,
                                                 @NonNull List<Element> binderFields) throws ShivProcessorException {
        CodeBlock.Builder builder = CodeBlock.builder()
                .add("if ($N == null) return;\n", BUNDLE)
                .add("$T $N = ($T) $N;\n", hostType, FIELD_HOST, hostType, OBJECT);

        for (Element element : binderFields) {
            String keyName = (KEY_INSTANCE_PREFIX + element.getSimpleName()).toUpperCase();
            builder.add("$T.$N($N, $N, $N.$N);\n", BinderUtils.class, getPutMethodName(element), BUNDLE, keyName,
                    FIELD_HOST, element.getSimpleName());
        }

        return builder.build();
    }

    @NonNull
    private CodeBlock generateRestoreInstanceMethod(@NonNull TypeSpec.Builder hostBuilder,
                                                    @NonNull TypeElement hostType,
                                                    @NonNull List<Element> binderFields) throws ShivProcessorException {
        CodeBlock.Builder builder = CodeBlock.builder()
                .add("if ($N == null) return;\n", BUNDLE)
                .add("$T $N = ($T) $N;\n", hostType, FIELD_HOST, hostType, OBJECT)
                .add("$T $N;\n", Object.class, EXTRA);

        for (Element element : binderFields) {
            String keyName = (KEY_INSTANCE_PREFIX + element.getSimpleName()).toUpperCase();
            hostBuilder.addField(FieldSpec.builder(String.class, keyName, Modifier.FINAL, Modifier.STATIC,
                    Modifier.PRIVATE).initializer("\"$N.$N\"", hostType.getQualifiedName(), element.getSimpleName()).build());

            builder.add("$N = $T.get($N, $N);\n", EXTRA, BinderUtils.class, BUNDLE, keyName)
                    .add("if ($N != null) {\n", EXTRA)
                    .add("    $N.$N = ($T) $N;\n", FIELD_HOST, element.getSimpleName(), element.asType(), EXTRA)
                    .add("}\n");
        }

        return builder.build();
    }

    @NonNull
    private String getPutMethodName(@NonNull Element element) throws ShivProcessorException {
        String type = erasedType(element.asType());
        if (ArrayList.class.getCanonicalName().equals(type)) {
            DeclaredType declaredType = (DeclaredType) element.asType();
            List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
            if (typeArguments.size() != 1) {
                throw new ShivProcessorException("Generic type not specified for list: " + element);
            }
            TypeMirror listType = typeArguments.get(0);

            if (isSubtypeOfType(listType, Parcelable.class)) {
                return "putParcelableArrayList";
            } else if (isSubtypeOfType(listType, String.class)) {
                return "putStringArrayList";
            } else if (isSubtypeOfType(listType, CharSequence.class)) {
                return "putCharSequenceArrayList";
            } else if (isSubtypeOfType(listType, Integer.class)) {
                return "putIntegerArrayList";
            } else {
                throw new ShivProcessorException("Invalid array list type: " + listType);
            }
        } else if (SparseArray.class.getCanonicalName().equals(type)) {
            DeclaredType declaredType = (DeclaredType) element.asType();
            List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
            if (typeArguments.size() != 1) {
                throw new ShivProcessorException("Generic type not specified for sparse array: " + element);
            }
            TypeMirror sparseArrayType = typeArguments.get(0);

            if (isSubtypeOfType(sparseArrayType, Parcelable.class)) {
                return "putSparseParcelableArray";
            } else {
                throw new ShivProcessorException("Invalid sparse array type: " + sparseArrayType);
            }
        } else {
            return "put";
        }
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
                if (isPrivate(parentType)) {
                    throw new ShivProcessorException("Parent class is private: " + parentType);
                }
                parentType = findEnclosingElement(parentType);
            }

            if (annotation == BindView.class) {
                if (!isSubtypeOfType(type, Activity.class) && !isSubtypeOfType(type, Fragment.class) &&
                        !isSubtypeOfType(type, SUPPORT_FRAGMENT_TYPE) && !isSubtypeOfType(type, ViewGroup.class)) {
                    throw new ShivProcessorException("Invalid view binding class: " + type.getSimpleName());
                } else if (!isSubtypeOfType(fieldType, View.class)) {
                    throw new ShivProcessorException("Field must inherit from View type: " + e.getSimpleName());
                }
            } else if (annotation == BindExtra.class) {
                if (!isSubtypeOfType(type, Activity.class) && !isSubtypeOfType(type, Fragment.class) &&
                        !isSubtypeOfType(type, SUPPORT_FRAGMENT_TYPE)) {
                    throw new ShivProcessorException("Invalid extra binding class: " + type.getSimpleName());
                } else if (!isValidBundleEntry(fieldType)) {
                    throw new ShivProcessorException("Extra field not suitable for bundle: " + e.getSimpleName());
                }
            } else if (annotation == BindPreference.class) {
                if (isSubtypeOfType(type, PreferenceFragment.class) || isSubtypeOfType(type, PreferenceActivity.class)) {
                    if (!isSubtypeOfType(fieldType, Preference.class)) {
                        throw new ShivProcessorException("Preferences in " + type.getQualifiedName() +
                                " must inherit from " + Preference.class + ": " + e.getSimpleName());
                    }
                } else if (isSubtypeOfType(type, SUPPORT_PREFERENCE_FRAGMENT_TYPE)) {
                    if (!isSubtypeOfType(fieldType, SUPPORT_PREFERENCE_TYPE)) {
                        throw new ShivProcessorException("Preferences in " + SUPPORT_PREFERENCE_FRAGMENT_TYPE +
                                " must inherit from " + SUPPORT_PREFERENCE_TYPE + ": " + e.getSimpleName());
                    }
                } else {
                    throw new ShivProcessorException("Invalid preference binding class: " + type.getSimpleName());
                }
            } else if (annotation == BindInstance.class) {
                if (!isSubtypeOfType(type, Activity.class) && !isSubtypeOfType(type, Fragment.class) &&
                        !isSubtypeOfType(type, SUPPORT_FRAGMENT_TYPE)) {
                    throw new ShivProcessorException("Invalid instance binding class: " + type.getSimpleName());
                } else if (!isValidBundleEntry(fieldType)) {
                    throw new ShivProcessorException("Instance field not suitable for bundle: " + e.getSimpleName());
                }
            } else {
                throw new ShivProcessorException("Unrecognised annotation: " + annotation);
            }

            BindingHolder bindingHolder = bindings.get(type);
            if (bindingHolder == null) {
                bindingHolder = new BindingHolder();
                bindings.put(type, bindingHolder);
            }

            bindingHolder.addBinding(annotation, e);
        }
    }

    private boolean isValidBundleEntry(@NonNull TypeMirror fieldType) {
        return isAssignable(fieldType, CharSequence.class) || isAssignable(fieldType, Serializable.class) ||
                isAssignable(fieldType, Parcelable.class) || SparseArray.class.getCanonicalName().equals(erasedType(fieldType)) ||
                ArrayList.class.getCanonicalName().equals(erasedType(fieldType));
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
    static final class BindingHolder {

        @NonNull
        final List<Element> viewBindings = new ArrayList<>();

        @NonNull
        final List<Element> extraBindings = new ArrayList<>();

        @NonNull
        final List<Element> preferenceBindings = new ArrayList<>();

        @NonNull
        final List<Element> instanceBindings = new ArrayList<>();

        void addBinding(@NonNull Class<? extends Annotation> annotation, @NonNull Element element) throws ShivProcessorException {
            if (annotation == BindView.class) {
                viewBindings.add(element);
            } else if (annotation == BindExtra.class) {
                extraBindings.add(element);
            } else if (annotation == BindPreference.class) {
                preferenceBindings.add(element);
            } else if (annotation == BindInstance.class) {
                instanceBindings.add(element);
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