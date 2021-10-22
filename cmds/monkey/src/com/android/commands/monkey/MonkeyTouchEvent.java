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
 * monkey touch event
 * 触摸事件，同样继承于MonkeyMotionEvent。
 * MonkeyTouchEvent is a MonkeyMotionEvent
 */
public class MonkeyTouchEvent extends MonkeyMotionEvent {
    /**
     *
     * @param action 表示动作,创建MonkeyTouchEvent对象，必须指定一个action
     */
    public MonkeyTouchEvent(int action) {
        super(MonkeyEvent.EVENT_TYPE_TOUCH, InputDevice.SOURCE_TOUCHSCREEN, action); //传入参数为事件类型、事件来源、需要执行的事件动作
    }

    @Override
    protected String getTypeLabel() {
        return "Touch";
    } //事件标签为Touch
}
