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
import android.util.SparseArray;
import android.view.IWindowManager;
import android.view.MotionEvent;


/**
 * monkey motion event
 */
public abstract class MonkeyMotionEvent extends MonkeyEvent {
    private long mDownTime; //持有的按下时间
    private long mEventTime; //持有的点击时间
    private int mAction; //持有的动作
    private SparseArray<MotionEvent.PointerCoords> mPointers; //持有的触摸点数量
    private int mMetaState;
    private float mXPrecision;
    private float mYPrecision;
    private int mDeviceId;
    private int mSource;
    private int mFlags;
    private int mEdgeFlags;

    //If true, this is an intermediate step (more verbose logging, only)
    private boolean mIntermediateNote;

    protected MonkeyMotionEvent(int type, int source, int action) {
        super(type);
        mSource = source;
        mDownTime = -1;
        mEventTime = -1;
        mAction = action;
        mPointers = new SparseArray<MotionEvent.PointerCoords>();
        mXPrecision = 1;
        mYPrecision = 1;
    }

    public MonkeyMotionEvent addPointer(int id, float x, float y) {
        return addPointer(id, x, y, 0, 0);
    }

    public MonkeyMotionEvent addPointer(int id, float x, float y,
            float pressure, float size) {
        MotionEvent.PointerCoords c = new MotionEvent.PointerCoords();
        c.x = x;
        c.y = y;
        c.pressure = pressure;
        c.size = size;
        mPointers.append(id, c);
        return this;
    }

    public MonkeyMotionEvent setIntermediateNote(boolean b) {
        mIntermediateNote = b;
        return this;
    }

    public boolean getIntermediateNote() {
        return mIntermediateNote;
    }

    public int getAction() {
        return mAction;
    }

    public long getDownTime() {
        return mDownTime;
    }

    public long getEventTime() {
        return mEventTime;
    }

    public MonkeyMotionEvent setDownTime(long downTime) {
        mDownTime = downTime;
        return this;
    }

    public MonkeyMotionEvent setEventTime(long eventTime) {
        mEventTime = eventTime;
        return this;
    }

    public MonkeyMotionEvent setMetaState(int metaState) {
        mMetaState = metaState;
        return this;
    }

    public MonkeyMotionEvent setPrecision(float xPrecision, float yPrecision) {
        mXPrecision = xPrecision;
        mYPrecision = yPrecision;
        return this;
    }

    public MonkeyMotionEvent setDeviceId(int deviceId) {
        mDeviceId = deviceId;
        return this;
    }

    public MonkeyMotionEvent setEdgeFlags(int edgeFlags) {
        mEdgeFlags = edgeFlags;
        return this;
    }

    /**
     * 
     * @return instance of a motion event 返回一个MotionEvent对象
     */
    private MotionEvent getEvent() {
        int pointerCount = mPointers.size(); //检查触摸点的数量
        int[] pointerIds = new int[pointerCount]; //创建数组对象，数组容量为触摸点的数量
        MotionEvent.PointerCoords[] pointerCoords = new MotionEvent.PointerCoords[pointerCount]; //创建PointerCoords数组对象，容量也是触摸点的数量
        for (int i = 0; i < pointerCount; i++) {
            pointerIds[i] = mPointers.keyAt(i); //把SparseArray中的key，取出来，存放到临时数组中（典型的将所有key转化为一个list）
            pointerCoords[i] = mPointers.valueAt(i); //把SparseArray中的value，取出来，存放到临时数组中（典型的将所有value转化为一个list）
        }

        MotionEvent ev = MotionEvent.obtain(mDownTime,
                mEventTime < 0 ? SystemClock.uptimeMillis() : mEventTime,
                mAction, pointerCount, pointerIds, pointerCoords,
                mMetaState, mXPrecision, mYPrecision, mDeviceId, mEdgeFlags, mSource, mFlags); //通过MotionEvent的obtain方法，获取到缓存的一个MotionEvent对象
        //传入参数为按下的时间、点击的时间（做了保护，如果小于0，则直接使用当前系统开机至今的时间）、传入的动作
        return ev; //使用的是MotionEvent对象
    }

    @Override
    public boolean isThrottlable() {
        return (getAction() == MotionEvent.ACTION_UP);
    }

    /**
     *
     * @param iwm wires to current window manager WMS服务binder对象
     * @param iam wires to current activity manager ams服务binder对象
     * @param verbose a log switch log开关
     * @return  注入事件的结果
     */
    @Override
    public int injectEvent(IWindowManager iwm, IActivityManager iam, int verbose) {
        MotionEvent me = getEvent(); //用于获取表示事件的MotionEvent对象
        if ((verbose > 0 && !mIntermediateNote) || verbose > 1) {
            StringBuilder msg = new StringBuilder(":Sending "); //用于保存日志的StringBuilder对象
            msg.append(getTypeLabel()).append(" ("); //添加事件类型和一个（
            switch (me.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    msg.append("ACTION_DOWN");
                    break;
                case MotionEvent.ACTION_MOVE:
                    msg.append("ACTION_MOVE");
                    break;
                case MotionEvent.ACTION_UP:
                    msg.append("ACTION_UP");
                    break;
                case MotionEvent.ACTION_CANCEL:
                    msg.append("ACTION_CANCEL");
                    break;
                case MotionEvent.ACTION_POINTER_DOWN:
                    msg.append("ACTION_POINTER_DOWN ").append(me.getPointerId(me.getActionIndex()));
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    msg.append("ACTION_POINTER_UP ").append(me.getPointerId(me.getActionIndex()));
                    break;
                default:
                    msg.append(me.getAction());
                    break;
            }
            msg.append("):");

            int pointerCount = me.getPointerCount();
            for (int i = 0; i < pointerCount; i++) {
                msg.append(" ").append(me.getPointerId(i));
                msg.append(":(").append(me.getX(i)).append(",").append(me.getY(i)).append(")");
            }
            Logger.out.println(msg.toString());
        }
        try {
            if (!InputManager.getInstance().injectInputEvent(me,
                    InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT)) { //依赖InputManagerService注入事件
                return MonkeyEvent.INJECT_FAIL;
            }
        } finally {
            me.recycle();
        }
        return MonkeyEvent.INJECT_SUCCESS;
    }

    protected abstract String getTypeLabel();
}
