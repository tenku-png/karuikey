# Copyright (C) 2011 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Standalone Gradle/ndk-build adaptation for Karuikey. The source list is
# deliberately limited to the AOSP dictionary/suggestion core and JNI bridge.

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_C_INCLUDES := $(LOCAL_PATH)/src
LOCAL_CFLAGS := -Wall -Wextra -Wno-unused-parameter -Wno-unused-function
LOCAL_SRC_FILES := \
    com_android_inputmethod_keyboard_ProximityInfo.cpp \
    com_android_inputmethod_latin_BinaryDictionary.cpp \
    com_android_inputmethod_latin_DicTraverseSession.cpp \
    jni_common.cpp \
    $(shell find $(LOCAL_PATH)/src -name '*.cpp' | sed 's#^$(LOCAL_PATH)/##')
LOCAL_MODULE := libjni_latinime_common
LOCAL_NDK_STL_VARIANT := c++_static
include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_STATIC_LIBRARIES := libjni_latinime_common
LOCAL_LDFLAGS := -Wl,--build-id=none -Wl,--undefined=JNI_OnLoad
LOCAL_LDLIBS := -llog -ldl
LOCAL_MODULE := libjni_latinime
LOCAL_NDK_STL_VARIANT := c++_static
include $(BUILD_SHARED_LIBRARY)
