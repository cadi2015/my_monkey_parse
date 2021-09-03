/*
 * Copyright (C) 2010 The Android Open Source Project
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
import android.os.Build;


/**
 * Events for running the shell command.
 * 每个对象表示一个命令行命令……看来可以在monkey脚本文件中添加命令
 * 我可以利用这个使用adb命令，即284发出广播，当时必须绕过284的那个权限弹框（看看怎么能绕过去）
 */
public class MonkeyCommandEvent extends MonkeyEvent {

    private String mCmd; //MonkeyCommandEvent对象持有的字符串对象，用于保存执行的命令

    public MonkeyCommandEvent(String cmd) {
        super(EVENT_TYPE_ACTIVITY); //注意事件类型，这里临时使用的是EVENT_TYPE_ACTIVITY
        mCmd = cmd;
    }

    @Override
    public int injectEvent(IWindowManager iwm, IActivityManager iam, int verbose) {
        if (mCmd != null) {
            //Execute the shell command 用于执行shell命令
            try {
                java.lang.Process p = Runtime.getRuntime().exec(mCmd); //在子进程中执行命令
                int status = p.waitFor(); //等待子进程完成工作，并获取子进程的退出状态码
                Logger.err.println("// Shell command " + mCmd + " status was " + status); //标准错误流中输出命令名，以及子进程的退出状态码（命令执行结果）
            } catch (Exception e) {
                Logger.err.println("// Exception from " + mCmd + ":"); //发生的任何异常，都捕获，并告知异常是由哪个命令引起的
                Logger.err.println(e.toString()); //打印异常对象
            }
        }
        return MonkeyEvent.INJECT_SUCCESS; //最后返回执行事件成功
    }
}
