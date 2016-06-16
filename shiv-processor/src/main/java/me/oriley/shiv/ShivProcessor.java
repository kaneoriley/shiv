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
import android.os.Parcelable;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.support.annotation.NonNull;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import me.oriley.shiv.holders.*;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static me.oriley.shiv.ProcessorUtils.*;

public final class ShivProcessor extends BaseProcessor {

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
        return new Class[]{BindView.class, BindExtra.class, BindPreference.class, BindInstance.class,
                BindNonConfigurationInstance.class, BindService.class};
    }

    @Override
    public boolean process(@NonNull Set<? extends TypeElement> annotations, @NonNull RoundEnvironment env) {
        if (env.processingOver()) {
            return true;
        }

        try {
            final Map<TypeElement, BindingManager> bindings = new HashMap<>();
            collectBindings(env, bindings, BindView.class);
            collectBindings(env, bindings, BindExtra.class);
            collectBindings(env, bindings, BindPreference.class);
            collectBindings(env, bindings, BindInstance.class);
            collectBindings(env, bindings, BindNonConfigurationInstance.class);
            collectBindings(env, bindings, BindService.class);

            for (BindingManager manager : bindings.values()) {
                String packageName = getPackageName(manager.hostType);
                writeToFile(packageName, manager.createBinder(this));
            }
        } catch (ShivException e) {
            error(e.getMessage());
            return true;
        }

        return false;
    }

    private void collectBindings(@NonNull RoundEnvironment env,
                                 @NonNull Map<TypeElement, BindingManager> bindings,
                                 @NonNull Class<? extends Annotation> annotation) throws ShivException {
        for (Element e : env.getElementsAnnotatedWith(annotation)) {
            if (e.getKind() != ElementKind.FIELD) {
                throw new ShivException(e.getSimpleName() + " is annotated with @" + annotation.getName() +
                        " but is not a field");
            }

            TypeMirror fieldType = e.asType();
            if (isPrivate(e)) {
                throw new ShivException("Field must not be private: " + e.getSimpleName());
            } else if (isStatic(e)) {
                throw new ShivException("Field must not be static: " + e.getSimpleName());
            }

            final TypeElement type = findEnclosingElement(e);
            // class should exist
            if (type == null) {
                throw new ShivException("Could not find a class for " + e.getSimpleName());
            }
            // and it should be public
            if (isPrivate(type)) {
                throw new ShivException("Class is private: " + type);
            }
            // as well as all parent classes
            TypeElement parentType = findEnclosingElement(type);
            while (parentType != null) {
                if (isPrivate(parentType)) {
                    throw new ShivException("Parent class is private: " + parentType);
                }
                parentType = findEnclosingElement(parentType);
            }

            if (annotation == BindView.class) {
                if (!isSubtypeOfType(type, Activity.class) && !isSubtypeOfType(type, Fragment.class) &&
                        !isSubtypeOfType(type, android.support.v4.app.Fragment.class) && !isSubtypeOfType(type, ViewGroup.class)) {
                    throw new ShivException("Invalid view binding class: " + type.getSimpleName());
                } else if (!isSubtypeOfType(fieldType, View.class)) {
                    throw new ShivException("Field must inherit from View type: " + e.getSimpleName());
                }
            } else if (annotation == BindExtra.class) {
                if (!isSubtypeOfType(type, Activity.class) && !isSubtypeOfType(type, Fragment.class) &&
                        !isSubtypeOfType(type, android.support.v4.app.Fragment.class)) {
                    throw new ShivException("Invalid extra binding class: " + type.getSimpleName());
                } else if (!isValidBundleEntry(fieldType)) {
                    throw new ShivException("Extra field not suitable for bundle: " + e.getSimpleName());
                }
            } else if (annotation == BindPreference.class) {
                if (isSubtypeOfType(type, PreferenceFragment.class) || isSubtypeOfType(type, PreferenceActivity.class)) {
                    if (!isSubtypeOfType(fieldType, Preference.class)) {
                        throw new ShivException("Preferences in " + type.getQualifiedName() +
                                " must inherit from " + Preference.class + ": " + e.getSimpleName());
                    }
                } else if (isSubtypeOfType(type, PreferenceFragmentCompat.class)) {
                    if (!isSubtypeOfType(fieldType, android.support.v7.preference.Preference.class)) {
                        throw new ShivException("Preferences in " + PreferenceFragmentCompat.class +
                                " must inherit from " + android.support.v7.preference.Preference.class +
                                ": " + e.getSimpleName());
                    }
                } else {
                    throw new ShivException("Invalid preference binding class: " + type.getSimpleName());
                }
            } else if (annotation == BindInstance.class) {
                if (!isSubtypeOfType(type, Activity.class) && !isSubtypeOfType(type, Fragment.class) &&
                        !isSubtypeOfType(type, android.support.v4.app.Fragment.class)) {
                    throw new ShivException("Invalid instance binding class: " + type.getSimpleName());
                } else if (!isValidBundleEntry(fieldType)) {
                    throw new ShivException("Instance field not suitable for bundle: " + e.getSimpleName());
                }
            } else if (annotation == BindNonConfigurationInstance.class) {
                if (!isSubtypeOfType(type, Activity.class)) {
                    throw new ShivException("Invalid non-configuration instance binding class: " + type.getSimpleName());
                }
            } else if (annotation == BindService.class) {
                if (!isSubtypeOfType(type, Activity.class) && !isSubtypeOfType(type, Fragment.class) &&
                        !isSubtypeOfType(type, android.support.v4.app.Fragment.class) &&
                        !isSubtypeOfType(type, View.class)) {
                    throw new ShivException("Invalid service binding class: " + type.getSimpleName());
                }
            } else {
                throw new ShivException("Unrecognised annotation: " + annotation);
            }

            BindingManager manager = bindings.get(type);
            if (manager == null) {
                manager = new BindingManager(this, type);
                bindings.put(type, manager);
            }

            manager.addBinding(annotation, e);
        }
    }

    private boolean isValidBundleEntry(@NonNull TypeMirror fieldType) throws ShivException {
        return isAssignable(fieldType, CharSequence.class) || isAssignable(fieldType, Serializable.class) ||
                isAssignable(fieldType, Parcelable.class) || SparseArray.class.getCanonicalName().equals(erasedType(fieldType)) ||
                ArrayList.class.getCanonicalName().equals(erasedType(fieldType));
    }

    @NonNull
    private JavaFile writeToFile(@NonNull String packageName, @NonNull TypeSpec spec) throws ShivException {
        final JavaFile file = JavaFile.builder(packageName, spec)
                .addFileComment("Generated by ShivProcessor, do not edit manually!")
                .indent("    ").build();
        try {
            file.writeTo(mFiler);
        } catch (IOException e) {
            throw new ShivException(e);
        }
        return file;
    }
}