/*
 * Copyright (C) 2012 Google Inc.
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
import android.os.RemoteException;
import android.view.IWindowManager;
/**
 * monkey screen rotation event
 * 用于调整屏幕的对象
 * 也称屏幕角度事件
 */
public class MonkeyRotationEvent extends MonkeyEvent {

    private final int mRotationDegree; //MonkeyRotationEvent对象持有的角度值
    private final boolean mPersist; //MonkeyRotationEvent对象持有的

    /**
     * Construct a rotation Event.
     *
     * @param degree Possible rotation degrees, see constants in
     * anroid.view.Suface. 角度值，建议看Suface类中的常量表示屏幕角度
     * @param persist Should we keep the rotation lock after the orientation
     * change. 表示角度改变后，是否应该锁定角度，true表示锁定
     */
    public MonkeyRotationEvent(int degree, boolean persist) {
        super(EVENT_TYPE_ROTATION); //向父类中定义的事件类型赋值，事件为EVENT_TYPE_ROTATION
        mRotationDegree = degree; //初始化角度值
        mPersist = persist; //记录是否锁定
    }

    /**
     * 改变屏幕角度的事件
     * @param iwm wires to current window manager WMS服务对象
     * @param iam wires to current activity manager AMS服务对象
     * @param verbose a log switch log的开关
     * @return 注入事件的结果
     */
    @Override
    public int injectEvent(IWindowManager iwm, IActivityManager iam, int verbose) {
        if (verbose > 0) {
            Logger.out.println(":Sending rotation degree=" + mRotationDegree +
                               ", persist=" + mPersist);
        }

        // inject rotation event
        try {
            iwm.freezeRotation(mRotationDegree); //通过WMS系统服务改变屏幕角度，这个方法是同步的？还是异步的呢？显然肯定是同步方法
            if (!mPersist) { //如果不需要锁定屏幕，走这里
                iwm.thawRotation();
            }
            return MonkeyEvent.INJECT_SUCCESS; //没有抛出远程服务的异常，代表注入事件成功，向调用者返回车弄个
        } catch (RemoteException ex) {
            return MonkeyEvent.INJECT_ERROR_REMOTE_EXCEPTION;
        }
    }
}
