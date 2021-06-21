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
import android.content.ComponentName;
import android.os.Bundle;
import android.os.RemoteException;
import android.view.IWindowManager;

/**
 * monkey instrumentation event
 * 表示instrumentation事件（每个对象），只在脚本作为命令时生效
 */
public class MonkeyInstrumentationEvent extends MonkeyEvent {
    String mRunnerName; //持有的执行的名字
    String mTestCaseName; //持有的测试用例名字

    /**
     * 创建对象时，必须指定case名字，运行名字
     * @param testCaseName case名
     * @param runnerName 运行名字
     */
    public MonkeyInstrumentationEvent(String testCaseName, String runnerName) {
        super(EVENT_TYPE_ACTIVITY);
        mTestCaseName = testCaseName;
        mRunnerName = runnerName;
    }

    /**
     * 事件执行的方法
     * @param iwm wires to current window manager 表示WMS系统服务提供的远程功能
     * @param iam wires to current activity manager 表示AMS系统服务提供的远程功能
     * @param verbose a log switch 用于切换log
     * @return 表示注入事件的结果
     */
    @Override
    public int injectEvent(IWindowManager iwm, IActivityManager iam, int verbose) {
        ComponentName cn = ComponentName.unflattenFromString(mRunnerName);
        if (cn == null || mTestCaseName == null)
            throw new IllegalArgumentException("Bad component name");

        Bundle args = new Bundle();
        args.putString("class", mTestCaseName);
        try {
            iam.startInstrumentation(cn, null, 0, args, null, null, 0, null);
        } catch (RemoteException e) {
            Logger.err.println("** Failed talking with activity manager!"); //AMS系统服务出现问题，说明注入事件失败，
            return MonkeyEvent.INJECT_ERROR_REMOTE_EXCEPTION;
        }
        return MonkeyEvent.INJECT_SUCCESS;
    }
}
