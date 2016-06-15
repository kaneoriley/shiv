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

import android.support.annotation.NonNull;
import com.squareup.javapoet.TypeSpec;
import me.oriley.shiv.*;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.lang.annotation.Annotation;

public final class BindingManager {

    @NonNull
    private final ViewBindingHolder mViewBindingHolder;

    @NonNull
    private final PreferenceBindingHolder mPreferenceBindingHolder;

    @NonNull
    private final ExtraBindingHolder mExtraBindingHolder;

    @NonNull
    private final InstanceBindingHolder mInstanceBindingHolder;

    @NonNull
    private final NonConfigurationInstanceBindingHolder mNonConfigurationInstanceBindingHolder;

    @NonNull
    private final ServiceBindingHolder mServiceBindingHolder;

    @NonNull
    public final TypeElement hostType;


    public BindingManager(@NonNull TypeElement hostType) {
        this.hostType = hostType;
        mViewBindingHolder = new ViewBindingHolder(hostType);
        mPreferenceBindingHolder = new PreferenceBindingHolder(hostType);
        mExtraBindingHolder = new ExtraBindingHolder(hostType);
        mInstanceBindingHolder = new InstanceBindingHolder(hostType);
        mNonConfigurationInstanceBindingHolder = new NonConfigurationInstanceBindingHolder(hostType);
        mServiceBindingHolder = new ServiceBindingHolder(hostType);
    }


    public void addBinding(@NonNull Class<? extends Annotation> annotation, @NonNull Element element) throws ShivException {
        if (annotation == BindView.class) {
            mViewBindingHolder.addElement(element);
        } else if (annotation == BindExtra.class) {
            mExtraBindingHolder.addElement(element);
        } else if (annotation == BindPreference.class) {
            mPreferenceBindingHolder.addElement(element);
        } else if (annotation == BindInstance.class) {
            mInstanceBindingHolder.addElement(element);
        } else if (annotation == BindNonConfigurationInstance.class) {
            mNonConfigurationInstanceBindingHolder.addElement(element);
        } else if (annotation == BindService.class) {
            mServiceBindingHolder.addElement(element);
        } else {
            throw new ShivException("Invalid annotation: " + annotation);
        }
    }

    @NonNull
    public TypeSpec createBinder(@NonNull ShivProcessor processor) throws ShivException {

        // Class type
        String packageName = processor.getPackageName(hostType);
        String className = processor.getClassName(hostType, packageName);

        // Class builder
        TypeSpec.Builder typeSpecBuilder = TypeSpec.classBuilder(className + Binder.CLASS_SUFFIX)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .superclass(Binder.class);

        mViewBindingHolder.addBindingsToClass(processor, typeSpecBuilder);
        mPreferenceBindingHolder.addBindingsToClass(processor, typeSpecBuilder);
        mExtraBindingHolder.addBindingsToClass(processor, typeSpecBuilder);
        mInstanceBindingHolder.addBindingsToClass(processor, typeSpecBuilder);
        mNonConfigurationInstanceBindingHolder.addBindingsToClass(processor, typeSpecBuilder);
        mServiceBindingHolder.addBindingsToClass(processor, typeSpecBuilder);

        return typeSpecBuilder.build();
    }
}
