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
 */
public class MonkeyActivityEvent extends MonkeyEvent {
    private ComponentName mApp;
    long mAlarmTime = 0;

    /**
     *
     * @param app 组件名对象
     */
    public MonkeyActivityEvent(ComponentName app) {
        super(EVENT_TYPE_ACTIVITY);
        mApp = app;
    }

    public MonkeyActivityEvent(ComponentName app, long arg) {
        super(EVENT_TYPE_ACTIVITY);
        mApp = app;
        mAlarmTime = arg;
    }

    /**
     * @return Intent for the new activity
     *  创建Intent对象，用于启动Activity
     */
    private Intent getEvent() {
        Intent intent = new Intent(Intent.ACTION_MAIN); //创建Intent对象，Action为ACTION_MAIN
        intent.addCategory(Intent.CATEGORY_LAUNCHER); //设置Category
        intent.setComponent(mApp); //设置ComponentName对象（new ComponentName(packageName, r.activityInfo.name)），可见包名和Activity名，组成了ComponentName
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED); //设置启动Activity的Task，在一个新的
        return intent; //返回Intent对象
    }

    /**
     *
     * @param iwm wires to current window manager 这里没有用
     * @param iam wires to current activity manager 这里用AMS
     * @param verbose a log switch log开关
     * @return
     */
    @Override
    public int injectEvent(IWindowManager iwm, IActivityManager iam, int verbose) {
        Intent intent = getEvent(); //获取Intent对象
        if (verbose > 0) {
            Logger.out.println(":Switch: " + intent.toUri(0));
        }

        if (mAlarmTime != 0){
            Bundle args = new Bundle();
            args.putLong("alarmTime", mAlarmTime);
            intent.putExtras(args);
        }

        try {
            iam.startActivityAsUserWithFeature(null, getPackageName(), null, intent, null, null,
                    null, 0, 0, null, null, ActivityManager.getCurrentUser()); //依靠AMS系统服务切换Activity
        } catch (RemoteException e) {
            Logger.err.println("** Failed talking with activity manager!");
            return MonkeyEvent.INJECT_ERROR_REMOTE_EXCEPTION; //当远程服务出错时，返回注入事件失败的错误码
        } catch (SecurityException e) {
            if (verbose > 0) {
                Logger.out.println("** Permissions error starting activity "
                        + intent.toUri(0));
            }
            return MonkeyEvent.INJECT_ERROR_SECURITY_EXCEPTION;
        }
        return MonkeyEvent.INJECT_SUCCESS;
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
