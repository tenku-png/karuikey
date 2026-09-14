/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.inputmethod.keyboard;
// Karuikey adaptation: reduced from AOSP LatinIME for the standalone keyboard core.

import android.content.Context;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.event.Event;
import com.android.inputmethod.keyboard.internal.KeyboardState;
import com.android.inputmethod.latin.RichInputMethodSubtype;
import com.android.inputmethod.latin.utils.RecapitalizeStatus;

import androidx.annotation.NonNull;

/** Coordinates AOSP keyboard layouts, state transitions, and the main keyboard view. */
public final class KeyboardSwitcher implements KeyboardState.SwitchActions {
    private final Context mContext;
    private final MainKeyboardView mKeyboardView;
    private KeyboardState mState;
    private KeyboardLayoutSet mKeyboardLayoutSet;

    public KeyboardSwitcher(@NonNull final Context context,
            @NonNull final MainKeyboardView keyboardView) {
        mContext = context;
        mKeyboardView = keyboardView;
        mState = new KeyboardState(this);
    }

    public void loadKeyboard(@NonNull final EditorInfo editorInfo,
            @NonNull final InputMethodSubtype subtype, final int width, final int height,
            final int autoCapsFlags) {
        final RichInputMethodSubtype richSubtype = new RichInputMethodSubtype(subtype);
        final KeyboardLayoutSet.Builder builder = new KeyboardLayoutSet.Builder(
                mContext, editorInfo);
        builder.setKeyboardGeometry(width, height)
                .setSubtype(richSubtype)
                .setVoiceInputKeyEnabled(false)
                .setLanguageSwitchKeyEnabled(false);
        mKeyboardLayoutSet = builder.build();
        mState.onLoadKeyboard(autoCapsFlags,
                RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE);
    }

    public void resetForNewInput() {
        mState = new KeyboardState(this);
        mKeyboardLayoutSet = null;
    }

    public void closing() {
        mKeyboardView.closing();
    }

    public void onHideWindow() {
        mKeyboardView.onHideWindow();
    }

    public void deallocateMemory() {
        mKeyboardView.deallocateMemory();
    }

    public Keyboard getKeyboard() {
        return mKeyboardView.getKeyboard();
    }

    public void onPressKey(final int code, final boolean isSinglePointer,
            final int autoCapsFlags) {
        mState.onPressKey(code, isSinglePointer, autoCapsFlags,
                RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE);
    }

    public void onReleaseKey(final int code, final boolean withSliding,
            final int autoCapsFlags) {
        mState.onReleaseKey(code, withSliding, autoCapsFlags,
                RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE);
    }

    public void onEvent(@NonNull final Event event, final int autoCapsFlags) {
        mState.onEvent(event, autoCapsFlags, RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE);
    }

    public void onFinishSlidingInput(final int autoCapsFlags) {
        mState.onFinishSlidingInput(autoCapsFlags,
                RecapitalizeStatus.NOT_A_RECAPITALIZE_MODE);
    }

    private void setKeyboard(final int elementId) {
        if (mKeyboardLayoutSet == null) {
            return;
        }
        mKeyboardView.setKeyboard(mKeyboardLayoutSet.getKeyboard(elementId));
    }

    @Override
    public void setAlphabetKeyboard() {
        setKeyboard(KeyboardId.ELEMENT_ALPHABET);
    }

    @Override
    public void setAlphabetManualShiftedKeyboard() {
        setKeyboard(KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED);
    }

    @Override
    public void setAlphabetAutomaticShiftedKeyboard() {
        setKeyboard(KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED);
    }

    @Override
    public void setAlphabetShiftLockedKeyboard() {
        setKeyboard(KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED);
    }

    @Override
    public void setAlphabetShiftLockShiftedKeyboard() {
        setKeyboard(KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED);
    }

    @Override
    public void setSymbolsKeyboard() {
        setKeyboard(KeyboardId.ELEMENT_SYMBOLS);
    }

    @Override
    public void setSymbolsShiftedKeyboard() {
        setKeyboard(KeyboardId.ELEMENT_SYMBOLS_SHIFTED);
    }

    @Override
    public void setEmojiKeyboard() {
        // Emoji is deliberately omitted from this phase; keep the state machine safe if an
        // inherited more-key path ever requests it.
        setAlphabetKeyboard();
    }

    @Override
    public void requestUpdatingShiftState(final int autoCapsFlags,
            final int recapitalizeMode) {
        mState.onUpdateShiftState(autoCapsFlags, recapitalizeMode);
    }

    @Override
    public void startDoubleTapShiftKeyTimer() {
        mKeyboardView.startDoubleTapShiftKeyTimer();
    }

    @Override
    public boolean isInDoubleTapShiftKeyTimeout() {
        return mKeyboardView.isInDoubleTapShiftKeyTimeout();
    }

    @Override
    public void cancelDoubleTapShiftKeyTimer() {
        mKeyboardView.cancelDoubleTapShiftKeyTimer();
    }
}
