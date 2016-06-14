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

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.*;

@SuppressWarnings({"unused", "WeakerAccess"})
public final class Shiv {

    private static final String TAG = Shiv.class.getSimpleName();
    private static final boolean DEBUG = false;

    @NonNull
    private static final Map<Class<?>, Set<Class<?>>> sFlattenHierarchyCache = new HashMap<>();

    @NonNull
    private static final Map<Class<?>, Binder> sBinderCache = new HashMap<>();


    @SuppressWarnings("unused")
    public static void bindViews(@NonNull Object object) {
        Set<Class<?>> registerTypes = flattenHierarchy(object.getClass());
        for (Class<?> type : registerTypes) {
            Binder binder = findBinderForClass(type);
            if (binder != null) {
                binder.bindViews(object);
            }
        }
    }

    @SuppressWarnings("unused")
    public static void unbindViews(@NonNull Object object) {
        Set<Class<?>> registerTypes = flattenHierarchy(object.getClass());
        for (Class<?> type : registerTypes) {
            Binder binder = findBinderForClass(type);
            if (binder != null) {
                binder.unbindViews(object);
            }
        }
    }

    @SuppressWarnings("unused")
    public static void bindPreferences(@NonNull Object object) {
        Set<Class<?>> registerTypes = flattenHierarchy(object.getClass());
        for (Class<?> type : registerTypes) {
            Binder binder = findBinderForClass(type);
            if (binder != null) {
                binder.bindPreferences(object);
            }
        }
    }

    @SuppressWarnings("unused")
    public static void unbindPreferences(@NonNull Object object) {
        Set<Class<?>> registerTypes = flattenHierarchy(object.getClass());
        for (Class<?> type : registerTypes) {
            Binder binder = findBinderForClass(type);
            if (binder != null) {
                binder.unbindPreferences(object);
            }
        }
    }

    @SuppressWarnings("unused")
    public static void bindExtras(@NonNull Object object) {
        Set<Class<?>> registerTypes = flattenHierarchy(object.getClass());
        for (Class<?> type : registerTypes) {
            Binder binder = findBinderForClass(type);
            if (binder != null) {
                binder.bindExtras(object);
            }
        }
    }

    @SuppressWarnings("unused")
    public static void saveInstance(@NonNull Object object, @Nullable Bundle bundle) {
        Set<Class<?>> registerTypes = flattenHierarchy(object.getClass());
        for (Class<?> type : registerTypes) {
            Binder binder = findBinderForClass(type);
            if (binder != null) {
                binder.saveInstance(object, bundle);
            }
        }
    }

    @SuppressWarnings("unused")
    public static void restoreInstance(@NonNull Object object, @Nullable Bundle bundle) {
        Set<Class<?>> registerTypes = flattenHierarchy(object.getClass());
        for (Class<?> type : registerTypes) {
            Binder binder = findBinderForClass(type);
            if (binder != null) {
                binder.restoreInstance(object, bundle);
            }
        }
    }

    @NonNull
    private static Set<Class<?>> flattenHierarchy(@NonNull Class<?> concreteClass) {
        Set<Class<?>> classes = sFlattenHierarchyCache.get(concreteClass);
        if (classes == null) {
            classes = getClassesFor(concreteClass);
            sFlattenHierarchyCache.put(concreteClass, classes);
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

            Binder binder = findBinderForClass(clazz);
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
    private static Binder findBinderForClass(@NonNull Class<?> cls) {
        Binder binder = sBinderCache.get(cls);
        if (binder != null) {
            log("Found cached Binder for %s.", cls);
            return binder;
        }

        try {
            Class<?> binderClass = Class.forName(cls.getName() + Binder.CLASS_SUFFIX);
            //noinspection unchecked
            binder = (Binder) binderClass.newInstance();
            log("Created Binder for %s.", cls);
        } catch (Exception e) {
            log("Binder not found for %s.", cls);
            binder = null;
        }

        if (binder != null) {
            sBinderCache.put(cls, binder);
        }
        return binder;
    }

    private static void log(@NonNull String message, @Nullable Object... args) {
        if (DEBUG) {
            if (args != null) {
                message = String.format(message, args);
            }
            Log.d(TAG, message);
        }
    }

    @NonNull
    public static FluentInterface with(@NonNull Object host) {
        return new FluentInterface(host);
    }

    public static final class FluentInterface {

        @NonNull
        private final Object mHost;


        FluentInterface(@NonNull Object host) {
            mHost = host;
        }


        @NonNull
        public FluentInterface bindViews() {
            Shiv.bindViews(mHost);
            return this;
        }

        @NonNull
        public FluentInterface unbindViews() {
            Shiv.unbindViews(mHost);
            return this;
        }

        @NonNull
        public FluentInterface bindExtras() {
            Shiv.bindExtras(mHost);
            return this;
        }

        @NonNull
        public FluentInterface bindPreferences() {
            Shiv.bindPreferences(mHost);
            return this;
        }

        @NonNull
        public FluentInterface unbindPreferences() {
            Shiv.unbindPreferences(mHost);
            return this;
        }

        @NonNull
        public FluentInterface saveInstance(@Nullable Bundle bundle) {
            Shiv.saveInstance(mHost, bundle);
            return this;
        }

        @NonNull
        public FluentInterface restoreInstance(@Nullable Bundle bundle) {
            Shiv.restoreInstance(mHost, bundle);
            return this;
        }
    }
}