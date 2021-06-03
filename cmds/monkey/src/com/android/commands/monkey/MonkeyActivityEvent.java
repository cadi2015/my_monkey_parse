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

import android.app.ActivityManager;
import android.app.IActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.IPackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.view.IWindowManager;

/**
 * monkey activity event
 * 表示操作Activity的事件，依赖于AM启动Activity
 * 两种创建对象的方式，根据需求，创建不同初始化参数的对象
 */
public class MonkeyActivityEvent extends MonkeyEvent {
    private ComponentName mApp; //持有的ComponentName对象（目前是用来启动每个App的Launcher Activity）
    long mAlarmTime = 0; //持有的通知时间，目前来看是脚本形式的时候可能会用到这个时间，命令行启动的时候并没有使用这个时间

    /**
     *
     * @param app 传入组件名对象
     */
    public MonkeyActivityEvent(ComponentName app) {
        super(EVENT_TYPE_ACTIVITY);
        mApp = app;
    }

    /**
     *
     * @param app 传入ComponentName对象
     * @param arg 传入一个表示通知时间传递参数
     */
    public MonkeyActivityEvent(ComponentName app, long arg) {
        super(EVENT_TYPE_ACTIVITY);
        mApp = app;
        mAlarmTime = arg;
    }

    /**
     * @return Intent for the new activity
     *  创建Intent对象，用于启动Activity
     *  目前创建的Intent，均是用于启动某个App的主Activity用的
     */
    private Intent getEvent() {
        Intent intent = new Intent(Intent.ACTION_MAIN); //创建Intent对象，Action为ACTION_MAIN
        intent.addCategory(Intent.CATEGORY_LAUNCHER); //设置Category
        intent.setComponent(mApp); //设置ComponentName对象（new ComponentName(packageName, r.activityInfo.name)），可见包名和Activity名，组成了ComponentName
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED); //设置启动Activity的Task，在一个新的
        return intent; //返回Intent对象
    }

    /**
     * 所谓实际执行事件的方法
     * @param iwm wires to current window manager 这里没有用
     * @param iam wires to current activity manager 这里用AMS
     * @param verbose a log switch log开关
     * @return 成功、或者出错的整型码（并没有使用失败的错误码）
     */
    @Override
    public int injectEvent(IWindowManager iwm, IActivityManager iam, int verbose) {
        Intent intent = getEvent(); //先获取Intent对象
        if (verbose > 0) { //根据日志等级，输出日志（标准输出流）
            Logger.out.println(":Switch: " + intent.toUri(0));
        }

        if (mAlarmTime != 0){
            Bundle args = new Bundle(); //创建Bundle对象，用于持有属性
            args.putLong("alarmTime", mAlarmTime); //向Intent对象添加一个alarmTime
            intent.putExtras(args); //在这为Intent设置此属性，传入创建的Bundle对象，可以看下这个Intent的alarmTime有啥用，估计AMS中会处理
        }

        try {
            iam.startActivityAsUserWithFeature(null, getPackageName(), null, intent, null, null,
                    null, 0, 0, null, null, ActivityManager.getCurrentUser()); //依靠AMS系统服务切换Activity，这个startActivityAsUserWithFeature方法完成Activity的启动
        } catch (RemoteException e) {
            Logger.err.println("** Failed talking with activity manager!");
            return MonkeyEvent.INJECT_ERROR_REMOTE_EXCEPTION; //当远程AMS服务出错时，返回注入事件失败的错误码
        } catch (SecurityException e) {
            if (verbose > 0) {
                Logger.out.println("** Permissions error starting activity "
                        + intent.toUri(0));
            }
            return MonkeyEvent.INJECT_ERROR_SECURITY_EXCEPTION; //表示因安全异常，导致的异常错误码
        }
        return MonkeyEvent.INJECT_SUCCESS; //表示注入事件成功（成功启动Activity）
    }

    /**
     * Obtain the package name of the current process using the current UID. The package name has to
     * match the current UID in IActivityManager#startActivityAsUser to be allowed to start an
     * activity.
     */
    private static String getPackageName() {
        try {
            IPackageManager pm = IPackageManager.Stub.asInterface(
                    ServiceManager.getService("package"));
            if (pm == null) {
                return null;
            }
            String[] packages = pm.getPackagesForUid(Binder.getCallingUid());
            return packages != null ? packages[0] : null;
        } catch (RemoteException e) {
            Logger.err.println("** Failed talking with package manager!");
            return null;
        }
    }
}
