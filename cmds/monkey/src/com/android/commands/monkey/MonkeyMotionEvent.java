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
    private long mDownTime; //持有的用于记录按下的时间点
    private long mEventTime; //持有的用于记录事件发生时的时间点
    private int mAction; //持有的动作
    private SparseArray<MotionEvent.PointerCoords> mPointers; //MonkeyMotionEvent持有一个集合对象（SparseArray），key只能为整型、value则为MotionEvent.PointerCoords对象，草，想在内存中干嘛？
    private int mMetaState;
    private float mXPrecision;
    private float mYPrecision;
    private int mDeviceId;
    private int mSource;
    private int mFlags;
    private int mEdgeFlags;

    //If true, this is an intermediate step (more verbose logging, only)
    private boolean mIntermediateNote; //标志位，用于标记是否为过渡事件

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

    /**
     * 用于将坐标点的信息添加到一个map中
     * @param id 用于在集合中记录key值
     * @param x x轴坐标
     * @param y y轴坐标
     * @return 当前对象
     */
    public MonkeyMotionEvent addPointer(int id, float x, float y) {
        return addPointer(id, x, y, 0, 0);
    }

    /**
     *
     * @param id 表示在集合中存储时用的Key
     * @param x
     * @param y
     * @param pressure
     * @param size
     * @return
     */
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
        int pointerCount = mPointers.size(); //检查触摸点的数量，如果点事件，这里其实值为1
        int[] pointerIds = new int[pointerCount]; //创建数组对象，数组容量为触摸点的数量
        MotionEvent.PointerCoords[] pointerCoords = new MotionEvent.PointerCoords[pointerCount]; //创建PointerCoords数组对象，容量也是触摸点的数量
        for (int i = 0; i < pointerCount; i++) { //遍历所有触摸点
            pointerIds[i] = mPointers.keyAt(i); //把SparseArray中的key，取出来，存放到临时数组中（典型的将所有key转化为一个list）
            pointerCoords[i] = mPointers.valueAt(i); //把SparseArray中的value，取出来，存放到临时数组中（典型的将所有value转化为一个list）
        }

        MotionEvent ev = MotionEvent.obtain(mDownTime,
                mEventTime < 0 ? SystemClock.uptimeMillis() : mEventTime,
                mAction, pointerCount, pointerIds, pointerCoords,
                mMetaState, mXPrecision, mYPrecision, mDeviceId, mEdgeFlags, mSource, mFlags); //通过MotionEvent的obtain方法，获取到在内存中缓存的一个MotionEvent对象，它不一定是一个点哦
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
        MotionEvent me = getEvent(); //获取到封装好的MotionEvent对象（可能是一个点，也可能是多个点）
        if ((verbose > 0 && !mIntermediateNote) || verbose > 1) { //这个verbose这牛逼？原来这里只是为了向标准输出流输出日志
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
            if (!InputManager.getInstance().injectInputEvent(me,  //走到这里才是真的向手机注入事件，通过InputManager的injectInputEvent注入事件，
                    InputManager.INJECT_INPUT_EVENT_MODE_WAIT_FOR_RESULT)) { //依赖InputManagerService系统服务注入事件
                return MonkeyEvent.INJECT_FAIL; //只要IMS返回的是失败，则证明注入失败，看来这里也是同步方法，Monkey主线程会等待执行完……
            }
        } finally {
            me.recycle(); //将缓存的MotionEvent对象回收掉，牛逼！
        }
        return MonkeyEvent.INJECT_SUCCESS; //走到这里说明注入事件成功，系统封装好了，靠你来执行了
    }

    protected abstract String getTypeLabel();
}
