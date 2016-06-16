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
import me.oriley.shiv.ShivException;
import me.oriley.shiv.ShivProcessor;

import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.List;

abstract class AbstractBindingHolder {

    static final String OBJECT = "object";
    static final String BUNDLE = "bundle";
    static final String EXTRA = "extra";
    static final String FIELD_HOST = "fieldHost";
    static final String KEY_INSTANCE_PREFIX = "SHIV_KEY_INSTANCE_";

    @NonNull
    final List<Element> mElements = new ArrayList<>();

    @NonNull
    final TypeElement mHostType;


    AbstractBindingHolder(@NonNull TypeElement hostType) {
        mHostType = hostType;
    }


    void addElement(@NonNull Element element) {
        mElements.add(element);
    }

    abstract void addBindingsToClass(@NonNull ShivProcessor processor, @NonNull TypeSpec.Builder typeSpecBuilder) throws ShivException;
}
