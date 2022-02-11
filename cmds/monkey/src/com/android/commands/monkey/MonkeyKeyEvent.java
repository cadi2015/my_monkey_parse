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

package com.android.commands.monkey;

import android.app.IActivityManager;
import android.hardware.input.InputManager;
import android.os.SystemClock;
import android.view.IWindowManager;
import android.view.InputDevice;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
/**
 * monkey key event
 * 每个创建的镀锡表示系统事件,3个不同的构造方法，满足不同的需求
 */
public class MonkeyKeyEvent extends MonkeyEvent {
    private int mDeviceId;  //持有的设备id，描述是硬件设备，比如键盘
    private long mEventTime;
    private long mDownTime;
    private int mAction;
    private int mKeyCode;
    private int mScanCode;
    private int mMetaState;
    private int mRepeatCount;

    private KeyEvent mKeyEvent; //持有的KeyEvent对象

    /**
     *
     * @param action 表示系统事件动作整型值
     * @param keyCode 表示对应动作的整型值
     */
    public MonkeyKeyEvent(int action, int keyCode) {
        this(-1, -1, action, keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0);
    }

    /**
     *
     * @param downTime 表示按下时间
     * @param eventTime 表示发生时间
     * @param action 表示要做的动作
     * @param keyCode 表示整型keyCode值
     * @param repeatCount 表示重复数量
     * @param metaState 表示原始状态
     * @param device 表示设备
     * @param scanCode 表示扫描码?
     */
    public MonkeyKeyEvent(long downTime, long eventTime, int action,
            int keyCode, int repeatCount, int metaState,
            int device, int scanCode) {
        super(EVENT_TYPE_KEY);
        mDownTime = downTime;
        mEventTime = eventTime;
        mAction = action;
        mKeyCode = keyCode;
        mRepeatCount = repeatCount;
        mMetaState = metaState;
        mDeviceId = device;
        mScanCode = scanCode;
    }

    /**
     *
     * @param e 传入KeyEvent对象
     */
    public MonkeyKeyEvent(KeyEvent e) {
        super(EVENT_TYPE_KEY);
        mKeyEvent = e;
    }

    public int getKeyCode() {
        return mKeyEvent != null ? mKeyEvent.getKeyCode() : mKeyCode;
    }

    public int getAction() {
        return mKeyEvent != null ? mKeyEvent.getAction() : mAction;
    }

    public long getDownTime() {
        return mKeyEvent != null ? mKeyEvent.getDownTime() : mDownTime;
    }

    public long getEventTime() {
        return mKeyEvent != null ? mKeyEvent.getEventTime() : mEventTime;
    }

    public void setDownTime(long downTime) {
        if (mKeyEvent != null) {
            throw new IllegalStateException("Cannot modify down time of this key event.");
        }
        mDownTime = downTime;
    }

    public void setEventTime(long eventTime) {
        if (mKeyEvent != null) {
            throw new IllegalStateException("Cannot modify event time of this key event.");
        }
        mEventTime = eventTime;
    }

    /**
     * 判断是否支持间隔，判断动作，如果是ACTION_UP，就支持间隔
     * @return
     */
    @Override
    public boolean isThrottlable() {
        return (getAction() == KeyEvent.ACTION_UP);
    }

    /**
     *
     * @param iwm wires to current window manager ，WMS服务
     * @param iam wires to current activity manager AMS服务
     * @param verbose a log switch 打印log用的
     * @return 注入事件的成功与失败
     */
    @Override
    public int injectEvent(IWindowManager iwm, IActivityManager iam, int verbose) {
        if (verbose > 1) {
            String note;  //用于存储动作的字符串
            if (mAction == KeyEvent.ACTION_UP) {
                note = "ACTION_UP"; //存储的是，存储为ACTION_UP
            } else {
                note = "ACTION_DOWN"; //其他情况直接是ACTION_DOWN，这里怎么没有ACTION_MOVE
            }

            try {
                Logger.out.println(":Sending Key (" + note + "): "
                        + mKeyCode + "    // "
                        + MonkeySourceRandom.getKeyName(mKeyCode)); //向标准错误流输出日志，keycode
            } catch (ArrayIndexOutOfBoundsException e) {
                Logger.out.println(":Sending Key (" + note + "): "
                        + mKeyCode + "    // Unknown key event"); //大佬竟然也这么搞？超出数组下标的情况，直接捕获，然后不处理……
            }
        }

        KeyEvent keyEvent = mKeyEvent; //将需要执行的KeyEvent
        if (keyEvent == null) { //如果，创建MonkeyKeyEvent对象时，没有传入mKeyEvent，或者传入的就时一个空的
            long eventTime = mEventTime; //先获取事件发生时间
            if (eventTime <= 0) { //如果时间小于等于0……
                eventTime = SystemClock.uptimeMillis(); //获取当前开机距离到现在的时间（毫秒）
            }
            long downTime = mDownTime; //记录按下时间
            if (downTime <= 0) { //如果按下时间小于事件的发生时间
                downTime = eventTime; //那么按下时间就用最近的发生时间
            }
            keyEvent = new KeyEvent(downTime, eventTime, mAction, mKeyCode,
                    mRepeatCount, mMetaState, mDeviceId, mScanCode,
                    KeyEvent.FLAG_FROM_SYSTEM, InputDevice.SOURCE_KEYBOARD);
        }
        if (!InputManager.getInstance().injectInputEvent(keyEvent,
                InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT)) { //如果IMS注入事件失败，则返回INJECT_FAIL，表示注入事件失败
            return MonkeyEvent.INJECT_FAIL; //key事件，也是通过InputManagerService系统服务注入的……
        }
        return MonkeyEvent.INJECT_SUCCESS; //其他情况下返回注入事件成功
    }
}
