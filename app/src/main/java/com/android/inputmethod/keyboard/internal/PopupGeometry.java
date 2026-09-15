/*
 * Copyright (C) 2026 The Karuikey Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.keyboard.internal;

/** Small, allocation-free bounds calculation shared by keyboard popup surfaces. */
public final class PopupGeometry {
    private PopupGeometry() {}

    /** Returns a position for an object that keeps its whole rectangle in the available area. */
    public static int clampPosition(final int desired, final int size, final int available) {
        final int max = Math.max(0, available - size);
        return Math.max(0, Math.min(max, desired));
    }
}
