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
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.SparseArray;

import java.io.Serializable;
import java.util.ArrayList;

@SuppressWarnings({"WeakerAccess", "unused"})
public final class BinderUtils {

    private BinderUtils() {
        throw new IllegalAccessError("no instances");
    }


    @Nullable
    public static Object get(@Nullable Bundle bundle, @NonNull String key) {
        return bundle != null ? bundle.get(key) : null;
    }

    // region Standard

    public static void put(@NonNull Bundle bundle, @NonNull String key, boolean value) {
        bundle.putBoolean(key, value);
    }

    public static void put(@NonNull Bundle bundle, @NonNull String key, @Nullable boolean[] value) {
        bundle.putBooleanArray(key, value);
    }

    public static void put(@NonNull Bundle bundle, @NonNull String key, int value) {
        bundle.putInt(key, value);
    }

    public static void put(@NonNull Bundle bundle, @NonNull String key, @Nullable int[] value) {
        bundle.putIntArray(key, value);
    }

    public static void put(@NonNull Bundle bundle, @NonNull String key, @Nullable Bundle value) {
        bundle.putBundle(key, value);
    }

    public static void put(@NonNull Bundle bundle, @NonNull String key, byte value) {
        bundle.putByte(key, value);
    }

    public static void put(@NonNull Bundle bundle, @NonNull String key, @Nullable byte[] value) {
        bundle.putByteArray(key, value);
    }

    public static void put(@NonNull Bundle bundle, @NonNull String key, @Nullable String value) {
        bundle.putString(key, value);
    }

    public static void put(@NonNull Bundle bundle, @NonNull String key, @Nullable String[] value) {
        bundle.putStringArray(key, value);
    }

    public static void put(@NonNull Bundle bundle, @NonNull String key, long value) {
        bundle.putLong(key, value);
    }

    public static void put(@NonNull Bundle bundle, @NonNull String key, @Nullable long[] value) {
        bundle.putLongArray(key, value);
    }

    public static void put(@NonNull Bundle bundle, @NonNull String key, float value) {
        bundle.putFloat(key, value);
    }

    public static void put(@NonNull Bundle bundle, @NonNull String key, @Nullable float[] value) {
        bundle.putFloatArray(key, value);
    }

    public static void put(@NonNull Bundle bundle, @NonNull String key, char value) {
        bundle.putChar(key, value);
    }

    public static void put(@NonNull Bundle bundle, @NonNull String key, @Nullable char[] value) {
        bundle.putCharArray(key, value);
    }

    public static void put(@NonNull Bundle bundle, @NonNull String key, @Nullable CharSequence value) {
        bundle.putCharSequence(key, value);
    }

    public static void put(@NonNull Bundle bundle, @NonNull String key, @Nullable CharSequence[] value) {
        bundle.putCharSequenceArray(key, value);
    }

    public static void put(@NonNull Bundle bundle, @NonNull String key, double value) {
        bundle.putDouble(key, value);
    }

    public static void put(@NonNull Bundle bundle, @NonNull String key, @Nullable double[] value) {
        bundle.putDoubleArray(key, value);
    }

    public static void put(@NonNull Bundle bundle, @NonNull String key, @Nullable Parcelable value) {
        bundle.putParcelable(key, value);
    }

    public static void put(@NonNull Bundle bundle, @NonNull String key, @Nullable Parcelable[] value) {
        bundle.putParcelableArray(key, value);
    }

    public static void put(@NonNull Bundle bundle, @NonNull String key, @Nullable ArrayList<Parcelable> value) {
        bundle.putParcelableArrayList(key, value);
    }

    public static void put(@NonNull Bundle bundle, @NonNull String key, short value) {
        bundle.putShort(key, value);
    }

    public static void put(@NonNull Bundle bundle, @NonNull String key, @Nullable short[] value) {
        bundle.putShortArray(key, value);
    }

    public static void put(@NonNull Bundle bundle, @NonNull String key, @Nullable Serializable value) {
        bundle.putSerializable(key, value);
    }

    // endregion Standard

    // region ErasedTypes

    public static void putParcelableArrayList(@NonNull Bundle bundle, @NonNull String key, @Nullable ArrayList<Parcelable> value) {
        bundle.putParcelableArrayList(key, value);
    }

    public static void putIntegerArrayList(@NonNull Bundle bundle, @NonNull String key, @Nullable ArrayList<Integer> value) {
        bundle.putIntegerArrayList(key, value);
    }

    public static void putStringArrayList(@NonNull Bundle bundle, @NonNull String key, @Nullable ArrayList<String> value) {
        bundle.putStringArrayList(key, value);
    }

    public static void putCharSequenceArrayList(@NonNull Bundle bundle, @NonNull String key, @Nullable ArrayList<CharSequence> value) {
        bundle.putCharSequenceArrayList(key, value);
    }

    public static void putSparseParcelableArray(@NonNull Bundle bundle, @NonNull String key, @Nullable SparseArray<? extends Parcelable> value) {
        bundle.putSparseParcelableArray(key, value);
    }

    // endregion ErasedTypes
}
