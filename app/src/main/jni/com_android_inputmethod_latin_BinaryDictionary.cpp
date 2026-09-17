/*
 * Copyright (C) 2009 The Android Open Source Project
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

#define LOG_TAG "LatinIME: jni: BinaryDictionary"

// Karuikey modification: retain only the read-only dictionary query bridge.

#include "com_android_inputmethod_latin_BinaryDictionary.h"

#include "defines.h"
#include "dictionary/property/ngram_context.h"
#include "dictionary/structure/dictionary_structure_with_buffer_policy_factory.h"
#include "jni.h"
#include "jni_common.h"
#include "suggest/core/dictionary/dictionary.h"
#include "suggest/core/result/suggestion_results.h"
#include "suggest/core/suggest_options.h"
#include "utils/char_utils.h"
#include "utils/int_array_view.h"
#include "utils/jni_data_utils.h"
#include "utils/log_utils.h"
#include "utils/profiler.h"
#include "utils/time_keeper.h"

namespace latinime {

class ProximityInfo;

static jlong latinime_BinaryDictionary_open(JNIEnv *env, jclass clazz, jstring sourceDir,
        jlong dictOffset, jlong dictSize, jboolean isUpdatable) {
    PROF_INIT;
    PROF_TIMER_START(66);
    const jsize sourceDirUtf8Length = env->GetStringUTFLength(sourceDir);
    if (sourceDirUtf8Length <= 0) {
        AKLOGE("DICT: Can't get sourceDir string");
        return 0;
    }
    char sourceDirChars[sourceDirUtf8Length + 1];
    env->GetStringUTFRegion(sourceDir, 0, env->GetStringLength(sourceDir), sourceDirChars);
    sourceDirChars[sourceDirUtf8Length] = '\0';
    DictionaryStructureWithBufferPolicy::StructurePolicyPtr dictionaryStructureWithBufferPolicy(
            DictionaryStructureWithBufferPolicyFactory::newPolicyForExistingDictFile(
                    sourceDirChars, static_cast<int>(dictOffset), static_cast<int>(dictSize),
                    isUpdatable == JNI_TRUE));
    if (!dictionaryStructureWithBufferPolicy) {
        return 0;
    }

    Dictionary *const dictionary =
            new Dictionary(env, std::move(dictionaryStructureWithBufferPolicy));
    PROF_TIMER_END(66);
    return reinterpret_cast<jlong>(dictionary);
}

static void latinime_BinaryDictionary_close(JNIEnv *env, jclass clazz, jlong dict) {
    Dictionary *dictionary = reinterpret_cast<Dictionary *>(dict);
    if (!dictionary) return;
    delete dictionary;
}

static void latinime_BinaryDictionary_getSuggestions(JNIEnv *env, jclass clazz, jlong dict,
        jlong proximityInfo, jlong dicTraverseSession, jintArray xCoordinatesArray,
        jintArray yCoordinatesArray, jintArray timesArray, jintArray pointerIdsArray,
        jintArray inputCodePointsArray, jint inputSize, jintArray suggestOptions,
        jobjectArray prevWordCodePointArrays, jbooleanArray isBeginningOfSentenceArray,
        jint prevWordCount, jintArray outSuggestionCount, jintArray outCodePointsArray,
        jintArray outScoresArray, jintArray outSpaceIndicesArray, jintArray outTypesArray,
        jintArray outAutoCommitFirstWordConfidenceArray,
        jfloatArray inOutWeightOfLangModelVsSpatialModel) {
    Dictionary *dictionary = reinterpret_cast<Dictionary *>(dict);
    // Assign 0 to outSuggestionCount here in case of returning earlier in this method.
    JniDataUtils::putIntToArray(env, outSuggestionCount, 0 /* index */, 0);
    if (!dictionary) {
        return;
    }
    ProximityInfo *pInfo = reinterpret_cast<ProximityInfo *>(proximityInfo);
    DicTraverseSession *traverseSession =
            reinterpret_cast<DicTraverseSession *>(dicTraverseSession);
    if (!traverseSession) {
        return;
    }
    // Input values
    int xCoordinates[inputSize];
    int yCoordinates[inputSize];
    int times[inputSize];
    int pointerIds[inputSize];
    const jsize inputCodePointsLength = env->GetArrayLength(inputCodePointsArray);
    int inputCodePoints[inputCodePointsLength];
    env->GetIntArrayRegion(xCoordinatesArray, 0, inputSize, xCoordinates);
    env->GetIntArrayRegion(yCoordinatesArray, 0, inputSize, yCoordinates);
    env->GetIntArrayRegion(timesArray, 0, inputSize, times);
    env->GetIntArrayRegion(pointerIdsArray, 0, inputSize, pointerIds);
    env->GetIntArrayRegion(inputCodePointsArray, 0, inputCodePointsLength, inputCodePoints);

    const jsize numberOfOptions = env->GetArrayLength(suggestOptions);
    int options[numberOfOptions];
    env->GetIntArrayRegion(suggestOptions, 0, numberOfOptions, options);
    SuggestOptions givenSuggestOptions(options, numberOfOptions);

    // Output values
    /* By the way, let's check the output array length here to make sure */
    const jsize outputCodePointsLength = env->GetArrayLength(outCodePointsArray);
    if (outputCodePointsLength != (MAX_WORD_LENGTH * MAX_RESULTS)) {
        AKLOGE("Invalid outputCodePointsLength: %d", outputCodePointsLength);
        ASSERT(false);
        return;
    }
    const jsize scoresLength = env->GetArrayLength(outScoresArray);
    if (scoresLength != MAX_RESULTS) {
        AKLOGE("Invalid scoresLength: %d", scoresLength);
        ASSERT(false);
        return;
    }
    const jsize outputAutoCommitFirstWordConfidenceLength =
            env->GetArrayLength(outAutoCommitFirstWordConfidenceArray);
    ASSERT(outputAutoCommitFirstWordConfidenceLength == 1);
    if (outputAutoCommitFirstWordConfidenceLength != 1) {
        // We only use the first result, as obviously we will only ever autocommit the first one
        AKLOGE("Invalid outputAutoCommitFirstWordConfidenceLength: %d",
                outputAutoCommitFirstWordConfidenceLength);
        ASSERT(false);
        return;
    }
    float weightOfLangModelVsSpatialModel;
    env->GetFloatArrayRegion(inOutWeightOfLangModelVsSpatialModel, 0, 1 /* len */,
            &weightOfLangModelVsSpatialModel);
    SuggestionResults suggestionResults(MAX_RESULTS);
    const NgramContext ngramContext = JniDataUtils::constructNgramContext(env,
            prevWordCodePointArrays, isBeginningOfSentenceArray, prevWordCount);
    if (givenSuggestOptions.isGesture() || inputSize > 0) {
        // TODO: Use SuggestionResults to return suggestions.
        dictionary->getSuggestions(pInfo, traverseSession, xCoordinates, yCoordinates,
                times, pointerIds, inputCodePoints, inputSize, &ngramContext,
                &givenSuggestOptions, weightOfLangModelVsSpatialModel, &suggestionResults);
    } else {
        dictionary->getPredictions(&ngramContext, &suggestionResults);
    }
    if (DEBUG_DICT) {
        suggestionResults.dumpSuggestions();
    }
    suggestionResults.outputSuggestions(env, outSuggestionCount, outCodePointsArray,
            outScoresArray, outSpaceIndicesArray, outTypesArray,
            outAutoCommitFirstWordConfidenceArray, inOutWeightOfLangModelVsSpatialModel);
}

static jint latinime_BinaryDictionary_getProbability(JNIEnv *env, jclass clazz, jlong dict,
        jintArray word) {
    Dictionary *dictionary = reinterpret_cast<Dictionary *>(dict);
    if (!dictionary) return NOT_A_PROBABILITY;
    const jsize codePointCount = env->GetArrayLength(word);
    int codePoints[codePointCount];
    env->GetIntArrayRegion(word, 0, codePointCount, codePoints);
    return dictionary->getProbability(CodePointArrayView(codePoints, codePointCount));
}

// If token is 0, this method newly starts iterating the dictionary. This method returns 0 when
// the dictionary does not have a next word.
static jint latinime_BinaryDictionary_getNextWord(JNIEnv *env, jclass clazz,
        jlong dict, jint token, jintArray outCodePoints, jbooleanArray outIsBeginningOfSentence) {
    Dictionary *dictionary = reinterpret_cast<Dictionary *>(dict);
    if (!dictionary) return 0;
    const jsize codePointBufSize = env->GetArrayLength(outCodePoints);
    if (codePointBufSize != MAX_WORD_LENGTH) {
        AKLOGE("Invalid outCodePointsLength: %d", codePointBufSize);
        ASSERT(false);
        return 0;
    }
    int wordCodePoints[codePointBufSize];
    int wordCodePointCount = 0;
    const int nextToken = dictionary->getNextWordAndNextToken(token, wordCodePoints,
            &wordCodePointCount);
    JniDataUtils::outputCodePoints(env, outCodePoints, 0 /* start */,
            MAX_WORD_LENGTH /* maxLength */, wordCodePoints, wordCodePointCount,
            false /* needsNullTermination */);
    bool isBeginningOfSentence = false;
    if (wordCodePointCount > 0 && wordCodePoints[0] == CODE_POINT_BEGINNING_OF_SENTENCE) {
        isBeginningOfSentence = true;
    }
    JniDataUtils::putBooleanToArray(env, outIsBeginningOfSentence, 0 /* index */,
            isBeginningOfSentence);
    return nextToken;
}

static const JNINativeMethod sMethods[] = {
    {
        const_cast<char *>("openNative"),
        const_cast<char *>("(Ljava/lang/String;JJZ)J"),
        reinterpret_cast<void *>(latinime_BinaryDictionary_open)
    },
    {
        const_cast<char *>("closeNative"),
        const_cast<char *>("(J)V"),
        reinterpret_cast<void *>(latinime_BinaryDictionary_close)
    },
    {
        const_cast<char *>("getSuggestionsNative"),
        const_cast<char *>("(JJJ[I[I[I[I[II[I[[I[ZI[I[I[I[I[I[I[F)V"),
        reinterpret_cast<void *>(latinime_BinaryDictionary_getSuggestions)
    },
    {
        const_cast<char *>("getProbabilityNative"),
        const_cast<char *>("(J[I)I"),
        reinterpret_cast<void *>(latinime_BinaryDictionary_getProbability)
    },
    {
        const_cast<char *>("getNextWordNative"),
        const_cast<char *>("(JI[I[Z)I"),
        reinterpret_cast<void *>(latinime_BinaryDictionary_getNextWord)
    },
};

int register_BinaryDictionary(JNIEnv *env) {
    const char *const kClassPathName = "com/android/inputmethod/latin/BinaryDictionary";
    return registerNativeMethods(env, kClassPathName, sMethods, NELEMS(sMethods));
}
} // namespace latinime
