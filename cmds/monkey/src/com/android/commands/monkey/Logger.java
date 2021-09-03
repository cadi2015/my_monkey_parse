/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.util.Log;

/**
 * 有开关，默认均为true
 * 用于向标准输出流打印文本，同时使用logcat支持的Log.i输出日志
 * 用于向标准错误流打印文本，同时使用logcat支持的Log.w输出日志
 * 定义了作为日志的功能，println（）功能
 */
public abstract class Logger {
    private static final String TAG = "Monkey";

    public static Logger out = new Logger() { //Logger类持有的Logger对象
        public void println(String s) {
            if (stdout) { // 说明可在控制台调试（标准输出流）
                System.out.println(s);
            }
            if (logcat) { // 说明可用logcat调试
                Log.i(TAG, s);
            }
        }
    };
    public static Logger err = new Logger() {
        public void println(String s) { //Logger类持有的另一个Logger对象
            if (stdout) {
                System.err.println(s);
            }
            if (logcat) {
                Log.w(TAG, s);
            }
        }
    };

    public static boolean stdout = true;
    public static boolean logcat = true;

    public abstract void println(String s);

    /**
     * Log an exception (throwable) at the ERROR level with an accompanying message.
     *
     * @param msg The message accompanying the exception. 需要向标准错误流打印的文本信息
     * @param t The exception (throwable) to log. 需要输出哪个异常对象的堆栈信息
     */
    public static void error(String msg, Throwable t) {
        err.println(msg); //输出文本信息
        err.println(Log.getStackTraceString(t)); //输出线程堆栈
    }
}
