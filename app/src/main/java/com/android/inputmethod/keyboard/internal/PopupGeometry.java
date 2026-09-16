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

import android.graphics.Paint;

/** Small, allocation-free bounds calculation shared by keyboard popup surfaces. */
public final class PopupGeometry {
    private static final float PREVIEW_HEIGHT_RATIO = 1.15f;
    private static final float PREVIEW_MIN_WIDTH_RATIO = 1.0f;
    private static final float PREVIEW_WIDTH_RATIO = 1.25f;
    private static final float PREVIEW_TEXT_MIN_RATIO = 1.20f;
    private static final float PREVIEW_TEXT_RATIO = 1.28f;
    private static final float PREVIEW_TEXT_MAX_RATIO = 1.35f;

    private PopupGeometry() {}

    /** Returns a position for an object that keeps its whole rectangle in the available area. */
    public static int clampPosition(final int desired, final int size, final int available) {
        final int max = Math.max(0, available - size);
        return Math.max(0, Math.min(max, desired));
    }

    /** Returns the full width occupied by a row of popup keys and dividers. */
    public static int getPopupWidth(final int columnCount, final int keyWidth,
            final int dividerWidth) {
        if (columnCount <= 0) {
            return 0;
        }
        return columnCount * Math.max(1, keyWidth)
                + Math.max(0, columnCount - 1) * Math.max(0, dividerWidth);
    }

    /** Reduces the requested columns only when even one-pixel keys could not fit. */
    public static int fitPopupColumnCount(final int keyCount, final int requestedColumns,
            final int dividerWidth, final int availableWidth) {
        if (keyCount <= 0) {
            return 0;
        }
        int columns = Math.max(1, Math.min(keyCount, requestedColumns));
        while (columns > 1 && getPopupWidth(columns, 1, dividerWidth) > availableWidth) {
            columns--;
        }
        return columns;
    }

    /** Keeps dividers while guaranteeing room for at least one pixel per column. */
    public static int fitPopupDividerWidth(final int columnCount, final int dividerWidth,
            final int availableWidth) {
        if (columnCount <= 1 || availableWidth <= columnCount) {
            return 0;
        }
        final int maximum = (availableWidth - columnCount) / (columnCount - 1);
        return Math.min(Math.max(0, dividerWidth), maximum);
    }

    /** Fits equal-width popup keys into the available content width. */
    public static int fitPopupKeyWidth(final int requestedKeyWidth, final int columnCount,
            final int dividerWidth, final int availableWidth) {
        if (columnCount <= 0 || availableWidth <= 0) {
            return 1;
        }
        final int dividerTotal = Math.max(0, columnCount - 1) * Math.max(0, dividerWidth);
        final int maximum = Math.max(1, (availableWidth - dividerTotal) / columnCount);
        return Math.min(Math.max(1, requestedKeyWidth), maximum);
    }

    /** Calculates the outer popup origin; drawing and touch translation share this origin. */
    public static int getPanelLeft(final int touchX, final int defaultCoordX,
            final int panelWidth, final int availableWidth, final int containerPaddingLeft) {
        return clampPosition(touchX - defaultCoordX - containerPaddingLeft,
                panelWidth, availableWidth);
    }

    /** Returns a compact preview height derived from the visible keycap height. */
    public static int getPreviewHeight(final int keyHeight, final int maxPreviewHeight) {
        if (keyHeight <= 0 || maxPreviewHeight <= 0) {
            return 0;
        }
        return Math.min(maxPreviewHeight, Math.max(1, Math.round(keyHeight * PREVIEW_HEIGHT_RATIO)));
    }

    /** Keeps a one-character preview close to its key while allowing room for its label. */
    public static int clampPreviewWidth(final int measuredWidth, final int keyWidth) {
        if (measuredWidth <= 0 || keyWidth <= 0) {
            return Math.max(0, keyWidth > 0 ? Math.round(keyWidth * PREVIEW_MIN_WIDTH_RATIO)
                    : measuredWidth);
        }
        final int minimum = getMinimumPreviewWidth(keyWidth);
        final int maximum = getMaximumPreviewWidth(keyWidth);
        return Math.min(maximum, Math.max(minimum, measuredWidth));
    }

    public static int getMinimumPreviewWidth(final int keyWidth) {
        return keyWidth <= 0 ? 0 : Math.round(keyWidth * PREVIEW_MIN_WIDTH_RATIO);
    }

    public static int getMaximumPreviewWidth(final int keyWidth) {
        return keyWidth <= 0 ? 0 : Math.round(keyWidth * PREVIEW_WIDTH_RATIO);
    }

    /** Returns a bounded preview label size relative to the normal key label size. */
    public static int getPreviewTextSize(final int keyLabelSize) {
        if (keyLabelSize <= 0) {
            return 0;
        }
        final float min = keyLabelSize * PREVIEW_TEXT_MIN_RATIO;
        final float requested = keyLabelSize * PREVIEW_TEXT_RATIO;
        final float max = keyLabelSize * PREVIEW_TEXT_MAX_RATIO;
        return Math.round(Math.max(min, Math.min(max, requested)));
    }

    /**
     * Fits a preview label using the same Paint that will draw it. The line metrics are used for
     * the vertical bound because TextView centers its baseline from the font metrics.
     */
    public static float getFittedPreviewTextSize(final Paint paint, final CharSequence text,
            final float desiredSize, final int contentWidth, final int contentHeight) {
        if (paint == null || text == null || text.length() == 0 || desiredSize <= 0
                || contentWidth <= 0 || contentHeight <= 0) {
            return Math.max(1.0f, desiredSize);
        }
        paint.setTextSize(desiredSize);
        final float measuredWidth = paint.measureText(text, 0, text.length());
        final float fontHeight = paint.descent() - paint.ascent();
        float scale = 1.0f;
        if (measuredWidth > 0) {
            scale = Math.min(scale, contentWidth / measuredWidth);
        }
        if (fontHeight > 0) {
            scale = Math.min(scale, contentHeight / fontHeight);
        }
        return Math.max(1.0f, desiredSize * Math.min(1.0f, scale));
    }
}
