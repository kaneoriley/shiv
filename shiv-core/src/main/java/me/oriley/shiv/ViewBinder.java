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

@SuppressWarnings({"WeakerAccess", "unused"})
public abstract class ViewBinder {

    static final String CLASS_SUFFIX = "$$ShivViewBinder";

    public abstract void bind(@NonNull final Object host);

    public abstract void unbind(@NonNull final Object host);
}