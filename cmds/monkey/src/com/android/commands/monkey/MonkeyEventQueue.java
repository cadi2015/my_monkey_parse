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
 * 双向链表，存储的元素对象是MonkeyEvent
 */
@SuppressWarnings("serial")
public class MonkeyEventQueue extends LinkedList<MonkeyEvent> {

    private Random mRandom; //MonkeyEventQueue对象持有的Random对象，用于生成随机间隔时间
    private long mThrottle; //MonkeyEventQueue对象持有的事件停留间隔时间
    private boolean mRandomizeThrottle; //MonkeyEventQueue对象持有的是否需要随机间隔

    /**
     * 创建双向链表对象的构造方法
     * @param random 指定Random对象
     * @param throttle 指定间隔时间
     * @param randomizeThrottle 指定是否开启随机间隔
     */
    public MonkeyEventQueue(Random random, long throttle, boolean randomizeThrottle) {
        super();
        mRandom = random;
        mThrottle = throttle;
        mRandomizeThrottle = randomizeThrottle;
    }

    /**
     * 用于将事件添加到双向链表尾部的方法
     * 不过我们新加了一个功能，要不要在它的尾部立即插入一个MonkeyThrottleEvent事件，它表示间隔时间
     * @param e 表示事件对象（具体是由子类对象传递进来的）
     */
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
