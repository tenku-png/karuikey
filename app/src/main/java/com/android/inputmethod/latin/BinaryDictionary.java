/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Karuikey adaptation: the AOSP read-only binary dictionary query path is kept; update,
 * migration, property and personalization JNI APIs are intentionally not part of this phase.
 */

package com.android.inputmethod.latin;

import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.keyboard.Key;
import com.android.inputmethod.latin.common.StringUtils;
import com.android.inputmethod.latin.utils.JniUtils;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;

public final class BinaryDictionary {
    private static final int MAX_WORD_LENGTH = 48;
    private static final int MAX_RESULTS = 18;
    private static final int MIN_CORRECTION_INPUT = 3;
    private static final int NOT_A_CODE = -1;

    static {
        JniUtils.loadNativeLibrary();
    }

    private static native long openNative(String sourceDir, long dictOffset, long dictSize,
            boolean isUpdatable);
    private static native void closeNative(long dict);
    private static native void getSuggestionsNative(long dict, long proximityInfo,
            long traverseSession, int[] xCoordinates, int[] yCoordinates, int[] times,
            int[] pointerIds, int[] inputCodePoints, int inputSize, int[] suggestOptions,
            int[][] prevWordCodePointArrays, boolean[] isBeginningOfSentenceArray,
            int prevWordCount, int[] outputSuggestionCount, int[] outputCodePoints,
            int[] outputScores, int[] outputIndices, int[] outputTypes,
            int[] outputAutoCommitFirstWordConfidence,
            float[] inOutWeightOfLangModelVsSpatialModel);
    private static native int getProbabilityNative(long dict, int[] word);
    private static native int getNextWordNative(long dict, int token, int[] outCodePoints,
            boolean[] outIsBeginningOfSentence);

    private final long mNativeDict;
    private final DicTraverseSession mTraverseSession;
    private final int[] mXCoordinates = new int[MAX_WORD_LENGTH];
    private final int[] mYCoordinates = new int[MAX_WORD_LENGTH];
    private final int[] mTimes = new int[MAX_WORD_LENGTH];
    private final int[] mPointerIds = new int[MAX_WORD_LENGTH];
    private final int[] mInputCodePoints = new int[MAX_WORD_LENGTH];
    private final int[] mOutputCodePoints = new int[MAX_WORD_LENGTH * MAX_RESULTS];
    private final int[] mOutputScores = new int[MAX_RESULTS];
    private final int[] mOutputIndices = new int[MAX_RESULTS];
    private final int[] mOutputTypes = new int[MAX_RESULTS];
    private final int[] mOrder = new int[MAX_RESULTS];
    private final int[] mOutputCount = new int[1];
    private final int[] mOutputConfidence = new int[1];
    private final float[] mWeight = new float[] {-1.0f};
    private final String[] mPreviousWords = new String[3];
    private String[] mTopWords;

    /** Kind flags reported by the native decoder alongside each candidate. */
    public static final int KIND_MASK_KIND = 0xFF;
    public static final int KIND_WHITELIST = 3;
    public static final int KIND_FLAG_APPROPRIATE_FOR_AUTOCORRECTION = 0x10000000;

    /** One scored decoder result; higher scores are better. */
    public static final class Candidate {
        public final String word;
        public final int score;
        public final int kindAndFlags;

        public Candidate(final String word, final int score, final int kindAndFlags) {
            this.word = word;
            this.score = score;
            this.kindAndFlags = kindAndFlags;
        }

        public boolean isWhitelisted() {
            return (kindAndFlags & KIND_MASK_KIND) == KIND_WHITELIST;
        }

        public boolean isAppropriateForAutoCorrection() {
            return (kindAndFlags & KIND_FLAG_APPROPRIATE_FOR_AUTOCORRECTION) != 0;
        }
    }

    public BinaryDictionary(final File file, final Locale locale) {
        final long nativeDict = openNative(file.getAbsolutePath(), 0, file.length(), false);
        if (nativeDict == 0) {
            throw new IllegalArgumentException("Unsupported dictionary");
        }
        try {
            mTraverseSession = new DicTraverseSession(locale, nativeDict, file.length());
            mNativeDict = nativeDict;
        } catch (RuntimeException exception) {
            closeNative(nativeDict);
            throw exception;
        }
    }

    public synchronized void getSuggestions(final CharSequence prefix, final Keyboard keyboard,
            final String[] previousWords, final int previousWordCount,
            final java.util.List<String> out) {
        out.clear();
        if (mNativeDict == 0 || mTraverseSession.getSession() == 0 ||
                Character.codePointCount(prefix, 0, prefix.length()) > MAX_WORD_LENGTH) {
            return;
        }
        final int inputSize = StringUtils.copyCodePointsAndReturnCodePointCount(
                mInputCodePoints, prefix, 0, prefix.length(), false);
        if (inputSize < 0) return;
        if (inputSize > 0 && keyboard == null) return;
        Arrays.fill(mInputCodePoints, inputSize, mInputCodePoints.length, NOT_A_CODE);
        for (int i = 0; i < inputSize; i++) {
            final Key key = keyboard == null ? null : keyboard.getKey(mInputCodePoints[i]);
            if (key == null) {
                mXCoordinates[i] = 0;
                mYCoordinates[i] = 0;
            } else {
                mXCoordinates[i] = key.getX() + key.getWidth() / 2;
                mYCoordinates[i] = key.getY() + key.getHeight() / 2;
            }
            mTimes[i] = i;
            mPointerIds[i] = 0;
        }
        final int contextCount = Math.min(previousWordCount, previousWords == null ? 0 :
                previousWords.length);
        for (int i = 0; i < contextCount; i++) {
            mTraverseSession.mPrevWordCodePointArrays[i] = StringUtils.toCodePointArray(
                    previousWords[i]);
            mTraverseSession.mIsBeginningOfSentenceArray[i] = false;
        }
        final int count = runNative(inputSize, keyboard, contextCount);
        final String lowerPrefix = prefix.toString().toLowerCase(Locale.ROOT);
        String correction = null;
        for (int rank = 0; rank < count; rank++) {
            final int start = mOrder[rank] * MAX_WORD_LENGTH;
            int length = 0;
            while (length < MAX_WORD_LENGTH && mOutputCodePoints[start + length] != 0) length++;
            if (length == 0) continue;
            final String candidate = new String(mOutputCodePoints, start, length);
            if ((inputSize == 0 || candidate.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) &&
                    !out.contains(candidate)) {
                out.add(candidate);
            } else if (inputSize >= MIN_CORRECTION_INPUT && correction == null && out.isEmpty()
                    && candidate.indexOf(' ') < 0) {
                // A typo fix only counts when it outscores every completion of what was typed.
                correction = candidate;
            }
        }
        if (correction != null && !out.contains(correction)) out.add(0, correction);
        if (out.isEmpty() && inputSize == 0) {
            final String[] topWords = topWords();
            for (final String word : topWords) {
                if (out.size() == 3) break;
                out.add(word);
            }
        }
    }

    /**
     * Scored corrections and completions for typed code points. Coordinates are keyboard-relative
     * touch points; a negative coordinate falls back to the center of the code point's key.
     */
    public synchronized void getCandidates(final int[] codePoints, final int[] xCoordinates,
            final int[] yCoordinates, final int inputSize, final Keyboard keyboard,
            final String[] previousWords, final int previousWordCount,
            final boolean beginningOfSentence, final java.util.List<Candidate> out) {
        out.clear();
        if (mNativeDict == 0 || mTraverseSession.getSession() == 0 ||
                inputSize > MAX_WORD_LENGTH || (inputSize > 0 && keyboard == null)) {
            return;
        }
        System.arraycopy(codePoints, 0, mInputCodePoints, 0, inputSize);
        Arrays.fill(mInputCodePoints, inputSize, mInputCodePoints.length, NOT_A_CODE);
        for (int i = 0; i < inputSize; i++) {
            int x = xCoordinates[i];
            int y = yCoordinates[i];
            if (x < 0 || y < 0) {
                Key key = keyboard.getKey(codePoints[i]);
                if (key == null) key = keyboard.getKey(Character.toLowerCase(codePoints[i]));
                x = key == null ? 0 : key.getX() + key.getWidth() / 2;
                y = key == null ? 0 : key.getY() + key.getHeight() / 2;
            }
            mXCoordinates[i] = x;
            mYCoordinates[i] = y;
            mTimes[i] = i;
            mPointerIds[i] = 0;
        }
        int contextCount = Math.min(previousWordCount, previousWords == null ? 0 :
                previousWords.length);
        for (int i = 0; i < contextCount; i++) {
            mTraverseSession.mPrevWordCodePointArrays[i] = StringUtils.toCodePointArray(
                    previousWords[i]);
            mTraverseSession.mIsBeginningOfSentenceArray[i] = false;
        }
        if (contextCount == 0 && beginningOfSentence) {
            // AOSP NgramContext encodes a sentence start as an empty word flagged as such.
            mTraverseSession.mPrevWordCodePointArrays[0] = new int[0];
            mTraverseSession.mIsBeginningOfSentenceArray[0] = true;
            contextCount = 1;
        }
        final int count = runNative(inputSize, keyboard, contextCount);
        for (int rank = 0; rank < count; rank++) {
            final int index = mOrder[rank];
            final int start = index * MAX_WORD_LENGTH;
            int length = 0;
            while (length < MAX_WORD_LENGTH && mOutputCodePoints[start + length] != 0) length++;
            if (length == 0) continue;
            out.add(new Candidate(new String(mOutputCodePoints, start, length),
                    mOutputScores[index], mOutputTypes[index]));
        }
    }

    /** Whether the dictionary knows the word itself, not only as a correction target. */
    public synchronized boolean isValidWord(final String word) {
        if (mNativeDict == 0 || word.isEmpty()) return false;
        return getProbabilityNative(mNativeDict, StringUtils.toCodePointArray(word)) >= 0 ||
                getProbabilityNative(mNativeDict, StringUtils.toCodePointArray(
                        word.toLowerCase(Locale.ROOT))) >= 0;
    }

    /** Runs the decoder over the prepared input and returns results ordered best first. */
    private int runNative(final int inputSize, final Keyboard keyboard, final int contextCount) {
        mTraverseSession.mNativeSuggestOptions.setIsGesture(false);
        mTraverseSession.mNativeSuggestOptions.setUseFullEditDistance(false);
        mTraverseSession.mNativeSuggestOptions.setBlockOffensiveWords(true);
        mTraverseSession.mNativeSuggestOptions.setWeightForLocale(1.0f);
        Arrays.fill(mOutputCodePoints, 0);
        mOutputCount[0] = 0;
        mWeight[0] = -1.0f;
        getSuggestionsNative(mNativeDict, keyboard == null ? 0 :
                        keyboard.getProximityInfo().getNativeProximityInfo(),
                mTraverseSession.getSession(), mXCoordinates, mYCoordinates, mTimes, mPointerIds,
                mInputCodePoints, inputSize, mTraverseSession.mNativeSuggestOptions.getOptions(),
                mTraverseSession.mPrevWordCodePointArrays,
                mTraverseSession.mIsBeginningOfSentenceArray, contextCount, mOutputCount,
                mOutputCodePoints, mOutputScores, mOutputIndices, mOutputTypes,
                mOutputConfidence, mWeight);
        // The native queue pops its lowest score first; AOSP re-sorted results on the Java side.
        final int count = Math.min(mOutputCount[0], MAX_RESULTS);
        for (int i = 0; i < count; i++) mOrder[i] = i;
        for (int i = 1; i < count; i++) {
            final int index = mOrder[i];
            int j = i - 1;
            while (j >= 0 && mOutputScores[mOrder[j]] < mOutputScores[index]) {
                mOrder[j + 1] = mOrder[j];
                j--;
            }
            mOrder[j + 1] = index;
        }
        return count;
    }

    private String[] topWords() {
        if (mTopWords != null) return mTopWords;
        final String[] bestWords = new String[3];
        final int[] bestScores = new int[] {-1, -1, -1};
        final int[] word = new int[MAX_WORD_LENGTH];
        final boolean[] beginningOfSentence = new boolean[1];
        int token = 0;
        while (true) {
            Arrays.fill(word, 0);
            token = getNextWordNative(mNativeDict, token, word, beginningOfSentence);
            if (token == 0) break;
            int wordLength = 0;
            while (wordLength < MAX_WORD_LENGTH && word[wordLength] != 0) wordLength++;
            if (wordLength == 0 || beginningOfSentence[0]) continue;
            final int probability = getProbabilityNative(
                    mNativeDict, Arrays.copyOf(word, wordLength));
            for (int i = 0; i < bestScores.length; i++) {
                if (probability <= bestScores[i]) continue;
                for (int j = bestScores.length - 1; j > i; j--) {
                    bestScores[j] = bestScores[j - 1];
                    bestWords[j] = bestWords[j - 1];
                }
                bestScores[i] = probability;
                bestWords[i] = new String(word, 0, wordLength);
                break;
            }
        }
        int count = 0;
        for (final String bestWord : bestWords) if (bestWord != null) count++;
        mTopWords = new String[count];
        int output = 0;
        for (final String bestWord : bestWords) {
            if (bestWord != null) mTopWords[output++] = bestWord;
        }
        return mTopWords;
    }

    public void getSuggestions(final CharSequence prefix, final Keyboard keyboard,
            final String previousWord, final String secondPreviousWord,
            final String thirdPreviousWord, final java.util.List<String> out) {
        final int previousWordCount = thirdPreviousWord != null ? 3 :
                secondPreviousWord != null ? 2 : previousWord != null ? 1 : 0;
        mPreviousWords[0] = previousWord;
        mPreviousWords[1] = secondPreviousWord;
        mPreviousWords[2] = thirdPreviousWord;
        getSuggestions(prefix, keyboard, mPreviousWords, previousWordCount, out);
        mPreviousWords[0] = null;
        mPreviousWords[1] = null;
        mPreviousWords[2] = null;
    }

    public void close() {
        mTraverseSession.close();
        if (mNativeDict != 0) closeNative(mNativeDict);
    }
}
