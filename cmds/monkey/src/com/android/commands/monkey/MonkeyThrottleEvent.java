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

package com.android.commands.monkey;

import java.util.List;

import android.app.IActivityManager;
import android.view.IWindowManager;


/**
 * monkey throttle event
 * 间隔事件（两个事件之间的停顿事件，也封装为一个事件，厉害）
 */
public class MonkeyThrottleEvent extends MonkeyEvent {
    private long mThrottle;  //MonkeyThrottleEvent对象持有的间隔时间

    /**
     * 创建对象时，需要传入间隔时间
     * @param throttle 间隔时间
     */
    public MonkeyThrottleEvent(long throttle) {
        super(MonkeyEvent.EVENT_TYPE_THROTTLE); //事件类型为EVENT_TYPE_THROTTLE
        mThrottle = throttle; //创建MonkeyThrottleEvent对象时，初始化间隔时间
    }

    /**
     *
     * @param iwm wires to current window manager WMS系统服务（这里未使用）
     * @param iam wires to current activity manager AMS系统服务 (这里未使用)
     * @param verbose a log switch log等级
     * @return 注入事件的结果，当线程被中断，则认为此事件注入失败，其他情况均为成功
     */
    @Override
    public int injectEvent(IWindowManager iwm, IActivityManager iam, int verbose) {

        if (verbose > 1) {
            Logger.out.println("Sleeping for " + mThrottle + " milliseconds");
        }
        try {
            Thread.sleep(mThrottle); //线程休眠指定的毫秒数……
        } catch (InterruptedException e1) { //如果发送线程中断
            Logger.out.println("** Monkey interrupted in sleep.");
            return MonkeyEvent.INJECT_FAIL;  //也算注入事件失败
        }
        
        return MonkeyEvent.INJECT_SUCCESS;//返回成功
    }
}
