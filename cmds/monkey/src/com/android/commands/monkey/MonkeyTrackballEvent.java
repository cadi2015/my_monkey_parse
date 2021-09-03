/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.commands.monkey;

import android.view.InputDevice;

/**
 * monkey trackball event
 * 轨迹球事件，继承MonkeyMotionEvent
 */
public class MonkeyTrackballEvent extends MonkeyMotionEvent {
    /**
     *
     * @param action 表示具体的轨迹球动作
     */
    public MonkeyTrackballEvent(int action) {
        super(MonkeyEvent.EVENT_TYPE_TRACKBALL, InputDevice.SOURCE_TRACKBALL, action); //传入参数，事件类型为EVENT_TYPE_TRACKBALL，事件来源为SOURCE_TRACKBALL
    }

    @Override
    protected String getTypeLabel() {
        return "Trackball";
    } //事件标签用于打印日志，标记为Trackball
}
