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

import java.util.LinkedList;
import java.util.Random;

/**
 * class for keeping a monkey event queue
 * 双向链表，存储的元素是MonkeyEvent
 */
@SuppressWarnings("serial")
public class MonkeyEventQueue extends LinkedList<MonkeyEvent> {

    private Random mRandom; //持有Random对象
    private long mThrottle; //持有间隔时间
    private boolean mRandomizeThrottle; //持有随机间隔

    public MonkeyEventQueue(Random random, long throttle, boolean randomizeThrottle) {
        super();
        mRandom = random;
        mThrottle = throttle;
        mRandomizeThrottle = randomizeThrottle;
    }

    @Override
    public void addLast(MonkeyEvent e) {
        super.add(e);
        if (e.isThrottlable()) { //事件是否支持间隔时间
            long throttle = mThrottle; //固定间隔值
            if (mRandomizeThrottle && (mThrottle > 0)) { //如果支持随机间隔，重新计算间隔时间
                throttle = mRandom.nextLong();
                if (throttle < 0) {
                    throttle = -throttle;
                }
                throttle %= mThrottle;
                ++throttle;
            }
            super.add(new MonkeyThrottleEvent(throttle));  //向当前事件的后面直接添加一个间隔事件
        }
    }
}
