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
 * 表示Monkey事件的抽象类，它子类产生的对象，表示一个具体的事件
 * 程序中描述了8个事件，不过对于用户来说是不一样的……
 */
public abstract class MonkeyEvent {
    protected int eventType; //每个事件对象持有的事件类型，表示事件类型
    public static final int EVENT_TYPE_KEY = 0; //此常量值表示KEY事件，第一个
    public static final int EVENT_TYPE_TOUCH = 1; //表示TOUCH事件，第二个
    public static final int EVENT_TYPE_TRACKBALL = 2; //TRACKBALL，第三个
    public static final int EVENT_TYPE_ROTATION = 3;  // Screen rotation，第四个
    public static final int EVENT_TYPE_ACTIVITY = 4; //第五个
    public static final int EVENT_TYPE_FLIP = 5; // Keyboard flip 第五个
    public static final int EVENT_TYPE_THROTTLE = 6; //延迟也算一个事件，只不过什么也没做而已，有道理 第六个
    public static final int EVENT_TYPE_PERMISSION = 7; //第七个
    public static final int EVENT_TYPE_NOOP = 8; //第八个

    public static final int INJECT_SUCCESS = 1; //表示注入事件成功的提示码，为1
    public static final int INJECT_FAIL = 0; //表示注入事件失败的提示码，为0

    // error code for remote exception during injection
    public static final int INJECT_ERROR_REMOTE_EXCEPTION = -1; //表示远程服务出错的错误码，父类中定义（所有事件通用）
    // error code for security exception during injection
    public static final int INJECT_ERROR_SECURITY_EXCEPTION = -2; //表示注入事件时出现安全异常的错误码，同样父类中定义

    public MonkeyEvent(int type) {
        eventType = type;
    }

    /**
     * @return event type
     */
    public int getEventType() {
        return eventType;
    }

    /**
     * @return true if it is safe to throttle after this event, and false otherwise.
     * 不是所有事件都支持中间可以插入一个停留的间隔时间的！所以才有这个方法
     */
    public boolean isThrottlable() {
        return true;
    }


    /**
     * a method for injecting event
     * @param iwm wires to current window manager
     * @param iam wires to current activity manager
     * @param verbose a log switch
     * @return INJECT_SUCCESS if it goes through, and INJECT_FAIL if it fails
     *         in the case of exceptions, return its corresponding error code
     */
    public abstract int injectEvent(IWindowManager iwm, IActivityManager iam, int verbose);
}
