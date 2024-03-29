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
import android.view.IWindowManager;

/**
 * abstract class for monkey event
 * 用于描述作为Monkey事件的抽象类，子类产生的对象，表示一个具体的事件
 * 程序中描述了9个事件，不过对于用户来说是不一样的……
 * 规范了作为事件应该提供的功能
 * 规范了事件分类的常量（有哪些事件）
 * 规范了事件结果的标志值（成功与失败）
 * 规范了事件结果的异常值（远程服务出错，安全异常错误）
 * 抽象类不能产生对象
 */
public abstract class MonkeyEvent {
    protected int eventType; //MonkeyEvent对象持有的事件类型，用于标记当前事件是哪个事件类型，所有子类复用
    public static final int EVENT_TYPE_KEY = 0; //此常量值表示KEY事件，第1个
    public static final int EVENT_TYPE_TOUCH = 1; //表示TOUCH事件，第2个
    public static final int EVENT_TYPE_TRACKBALL = 2; //TRACKBALL，第3个
    public static final int EVENT_TYPE_ROTATION = 3;  // Screen rotation，第4个
    public static final int EVENT_TYPE_ACTIVITY = 4; //第5个
    public static final int EVENT_TYPE_FLIP = 5; // Keyboard flip 第6个
    public static final int EVENT_TYPE_THROTTLE = 6; //延迟也算一个事件，线程停顿也算事件，有道理 第7个
    public static final int EVENT_TYPE_PERMISSION = 7; //第8个
    public static final int EVENT_TYPE_NOOP = 8; //第9个

    public static final int INJECT_SUCCESS = 1; //表示注入事件成功的提示码，常量值为1
    public static final int INJECT_FAIL = 0; //表示注入事件失败的提示码，常量值为0

    // error code for remote exception during injection
    public static final int INJECT_ERROR_REMOTE_EXCEPTION = -1; //表示远程服务出错的错误码，父类中定义（所有事件通用）
    // error code for security exception during injection
    public static final int INJECT_ERROR_SECURITY_EXCEPTION = -2; //表示注入事件时出现安全异常的错误码，同样父类中定义

    /**
     * 创建MonkeyEvent对象，必须调用的构造方法，必须指定事件类型
     * @param type 表示事件的分类
     */
    public MonkeyEvent(int type) {
        eventType = type;
    }

    /**事件类型
     * @return event type 用于返回事件类型
     */
    public int getEventType() {
        return eventType;
    }

    /** 是否支持延迟
     * @return true if it is safe to throttle after this event, and false otherwise.
     * 不是所有事件都支持在中间插入一个停留的间隔时间！所以才有这个方法
     * 不支持的事件，可以重写此方法，用于添加事件时使用
     */
    public boolean isThrottlable() {
        return true;
    }


    /**实际注入事件
     * a method for injecting event 该方法用于具体执行事件，建议使用WMS系统服务和AMS系统服务，建议函数用日志开关（当然子类也可以不使用）
     * @param iwm wires to current window manager WMS系统服务
     * @param iam wires to current activity manager AMS系统服务
     * @param verbose a log switch log开关
     * @return INJECT_SUCCESS if it goes through, and INJECT_FAIL if it fails
     *         in the case of exceptions, return its corresponding error code 返回值表示事件结果，一共有4种事件结果：成功、失败、远程服务错误、安全错误
     *         每个子类具体实现如何真正执行一个事件
     *
     */
    public abstract int injectEvent(IWindowManager iwm, IActivityManager iam, int verbose);
}
