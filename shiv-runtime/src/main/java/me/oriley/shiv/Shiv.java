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

import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.*;

@SuppressWarnings("unused")
public final class Shiv {

    private static final boolean DEBUG = false;

    @NonNull
    private static final Map<Class<?>, Set<Class<?>>> mFlattenHierarchyCache = new HashMap<>();

    @NonNull
    private static final Map<Class<?>, ViewBinder> mViewBinderCache = new HashMap<>();


    @SuppressWarnings("unused")
    public static void bind(@NonNull Object object) {
        enforceMainThread();

        Set<Class<?>> registerTypes = flattenHierarchy(object.getClass());
        for (Class<?> type : registerTypes) {
            bind(object, type);
        }
    }

    private static void bind(@NonNull Object object, @NonNull Class objectClass) {
        ViewBinder viewBinder = findViewBinderForClass(objectClass);
        if (viewBinder != null) {
            viewBinder.bind(object);
        }
    }

    @SuppressWarnings("unused")
    public static void unbind(@NonNull Object object) {
        enforceMainThread();

        Set<Class<?>> registerTypes = flattenHierarchy(object.getClass());
        for (Class<?> type : registerTypes) {
            unbind(object, type);
        }
    }

    private static void unbind(@NonNull Object object, @NonNull Class objectClass) {
        ViewBinder viewBinder = findViewBinderForClass(objectClass);
        if (viewBinder != null) {
            viewBinder.unbind(object);
        }
    }

    private static void enforceMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("View binding must occur on the main thread " + Looper.myLooper());
        }
    }

    @NonNull
    private static Set<Class<?>> flattenHierarchy(@NonNull Class<?> concreteClass) {
        Set<Class<?>> classes = mFlattenHierarchyCache.get(concreteClass);
        if (classes == null) {
            classes = getClassesFor(concreteClass);
            mFlattenHierarchyCache.put(concreteClass, classes);
        }
        return classes;
    }

    @NonNull
    private static Set<Class<?>> getClassesFor(@NonNull Class<?> concreteClass) {
        List<Class<?>> parents = new LinkedList<>();
        Set<Class<?>> classes = new HashSet<>();

        parents.add(concreteClass);

        while (!parents.isEmpty()) {
            Class<?> clazz = parents.remove(0);

            ViewBinder binder = findViewBinderForClass(clazz);
            if (binder != null) {
                classes.add(clazz);
            }

            Class<?> parent = clazz.getSuperclass();
            if (parent != null) {
                String name = parent.getName();
                if (!name.startsWith("java.") && !name.startsWith("android.")) {
                    parents.add(parent);
                }
            }
        }

        return classes;
    }

    @Nullable
    private static ViewBinder findViewBinderForClass(@NonNull Class<?> cls) {
        ViewBinder binder = mViewBinderCache.get(cls);
        if (binder != null) {
            log("Found cached ViewBinder for %s.", cls);
            return binder;
        }

        try {
            Class<?> binderClass = Class.forName(cls.getName() + ViewBinder.CLASS_SUFFIX);
            //noinspection unchecked
            binder = (ViewBinder) binderClass.newInstance();
            log("Created ViewBinder for %s.", cls);
        } catch (Exception e) {
            log("ViewBinder not found for %s.", cls);
            e.printStackTrace();
            binder = null;
        }

        if (binder != null) {
            mViewBinderCache.put(cls, binder);
        }
        return binder;
    }

    private static void log(@NonNull String message, @NonNull Object... args) {
        if (DEBUG) {
            System.out.println("SHIV -- " + String.format(message, args));
        }
    }
}