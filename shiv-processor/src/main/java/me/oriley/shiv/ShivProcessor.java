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
import com.squareup.javapoet.*;

import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.util.*;

public final class ShivProcessor extends BaseProcessor {

    private static final String INTEGER_TYPE = Integer.class.getCanonicalName();
    private static final String STRING_TYPE = String.class.getCanonicalName();
    private static final String CHAR_SEQUENCE_TYPE = CharSequence.class.getCanonicalName();
    private static final String SERIALIZABLE_TYPE = Serializable.class.getCanonicalName();
    private static final String PARCELABLE_TYPE = Parcelable.class.getCanonicalName();
    private static final String SPARSE_ARRAY_TYPE = SparseArray.class.getCanonicalName();
    private static final String ARRAY_LIST_TYPE = ArrayList.class.getCanonicalName();
    private static final String PREFERENCE_TYPE = Preference.class.getCanonicalName();
    private static final String SUPPORT_PREFERENCE_TYPE = "android.support.v7.preference.Preference";
    private static final String VIEW_TYPE = View.class.getCanonicalName();

    private static final String ACTIVITY_TYPE = Activity.class.getCanonicalName();
    private static final String FRAGMENT_TYPE = Fragment.class.getCanonicalName();
    private static final String SUPPORT_FRAGMENT_TYPE = "android.support.v4.app.Fragment";
    private static final ClassName BUNDLE_CLASS = ClassName.get(Bundle.class);
    private static final ClassName INTENT_CLASS = ClassName.get(Intent.class);
    private static final ClassName VIEW_CLASS = ClassName.get(View.class);

    private static final String PREFERENCE_ACTIVITY_TYPE = PreferenceActivity.class.getCanonicalName();
    private static final String PREFERENCE_FRAGMENT_TYPE = PreferenceFragment.class.getCanonicalName();
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
    private static final String FIELD_HOST = "fieldHost";
    private static final String VIEW_HOST = "viewHost";
    private static final String KEY_INSTANCE_PREFIX = "SHIV_KEY_INSTANCE_";

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
        return new Class[] { BindView.class, BindExtra.class, BindPreference.class, BindInstance.class };
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

        if (!holder.instanceBindings.isEmpty()) {
            ParameterSpec bundleParam = ParameterSpec.builder(BUNDLE_CLASS
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

            builder.add("$N = $T.get($N, $S);\n", EXTRA, BinderUtils.class, BUNDLE, bindExtra.value());
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
        if (ARRAY_LIST_TYPE.equals(type)) {
            DeclaredType declaredType = (DeclaredType) element.asType();
            List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
            if (typeArguments.size() != 1) {
                throw new ShivProcessorException("Generic type not specified for list: " + element);
            }
            TypeMirror listType = typeArguments.get(0);

            if (isSubtypeOfType(listType, PARCELABLE_TYPE)) {
                return "putParcelableArrayList";
            } else if (isSubtypeOfType(listType, STRING_TYPE)) {
                return "putStringArrayList";
            } else if (isSubtypeOfType(listType, CHAR_SEQUENCE_TYPE)) {
                return "putCharSequenceArrayList";
            } else if (isSubtypeOfType(listType, INTEGER_TYPE)) {
                return "putIntegerArrayList";
            } else {
                throw new ShivProcessorException("Invalid array list type: " + listType);
            }
        } else if (SPARSE_ARRAY_TYPE.equals(type)) {
            DeclaredType declaredType = (DeclaredType) element.asType();
            List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
            if (typeArguments.size() != 1) {
                throw new ShivProcessorException("Generic type not specified for sparse array: " + element);
            }
            TypeMirror sparseArrayType = typeArguments.get(0);

            if (isSubtypeOfType(sparseArrayType, PARCELABLE_TYPE)) {
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
                if (!isPrivate(parentType)) {
                    throw new ShivProcessorException("Parent class is private: " + parentType);
                }
                parentType = findEnclosingElement(parentType);
            }

            if (annotation == BindView.class) {
                if (!isSubtypeOfType(fieldType, VIEW_TYPE)) {
                    throw new ShivProcessorException("Field must inherit from View type: " + e.getSimpleName());
                }
            } else if (annotation == BindExtra.class) {
                if (!isSubtypeOfType(type, ACTIVITY_TYPE) && !isSubtypeOfType(type, FRAGMENT_TYPE) &&
                        !isSubtypeOfType(type, SUPPORT_FRAGMENT_TYPE)) {
                    throw new ShivProcessorException("Extra binding must take place inside " +
                            FRAGMENT_TYPE + ", " + ACTIVITY_TYPE + ", or " + SUPPORT_FRAGMENT_TYPE + " type: " + e.getSimpleName());
                } else if (!isValidBundleEntry(fieldType)) {
                    throw new ShivProcessorException("Extra field not suitable for bundle: " + e.getSimpleName());
                }
            } else if (annotation == BindPreference.class) {
                if (isSubtypeOfType(type, PREFERENCE_FRAGMENT_TYPE) || isSubtypeOfType(type, PREFERENCE_ACTIVITY_TYPE)) {
                    if (!isSubtypeOfType(fieldType, PREFERENCE_TYPE)) {
                        throw new ShivProcessorException("Preferences in " + type.getQualifiedName() +
                                " must inherit from " + PREFERENCE_TYPE + ": " + e.getSimpleName());
                    }
                } else if (isSubtypeOfType(type, SUPPORT_PREFERENCE_FRAGMENT_TYPE)) {
                    if (!isSubtypeOfType(fieldType, SUPPORT_PREFERENCE_TYPE)) {
                        throw new ShivProcessorException("Preferences in " + SUPPORT_PREFERENCE_FRAGMENT_TYPE +
                                " must inherit from " + SUPPORT_PREFERENCE_TYPE + ": " + e.getSimpleName());
                    }
                } else {
                    throw new ShivProcessorException("Preference binding must take place inside " +
                            PREFERENCE_FRAGMENT_TYPE + ", " + PREFERENCE_ACTIVITY_TYPE + ", or " +
                            SUPPORT_PREFERENCE_TYPE + " type: " + e.getSimpleName());
                }
            } else if (annotation == BindInstance.class) {
                if (!isSubtypeOfType(type, ACTIVITY_TYPE) && !isSubtypeOfType(type, FRAGMENT_TYPE) &&
                        !isSubtypeOfType(type, SUPPORT_FRAGMENT_TYPE)) {
                    throw new ShivProcessorException("Instance binding must take place inside " +
                            FRAGMENT_TYPE + ", " + ACTIVITY_TYPE + ", or " + SUPPORT_FRAGMENT_TYPE + " type: " + e.getSimpleName());
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
        return isAssignable(fieldType, CHAR_SEQUENCE_TYPE) || isAssignable(fieldType, SERIALIZABLE_TYPE) ||
                isAssignable(fieldType, PARCELABLE_TYPE) || SPARSE_ARRAY_TYPE.equals(erasedType(fieldType)) ||
                ARRAY_LIST_TYPE.equals(erasedType(fieldType));
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