/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.app.AppGlobals;
import android.app.IActivityManager;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.permission.IPermissionManager;
import android.view.IWindowManager;

/**
 * 权限事件
 */
public class MonkeyPermissionEvent extends MonkeyEvent {
    private String mPkg; //持有的包名
    private PermissionInfo mPermissionInfo; //持有的权限信息，一个PermissionInfo对象

    public MonkeyPermissionEvent(String pkg, PermissionInfo permissionInfo) {
        super(EVENT_TYPE_PERMISSION);
        mPkg = pkg;
        mPermissionInfo = permissionInfo;
    }

    /**
     *
     * @param iwm wires to current window manager WMS系统服务 未使用
     * @param iam wires to current activity manager AMS系统服务 未使用
     * @param verbose a log switch log开关 未使用
     * @return 表示注入事件的结果
     */
    @Override
    public int injectEvent(IWindowManager iwm, IActivityManager iam, int verbose) {
        final IPermissionManager permissionManager = AppGlobals.getPermissionManager(); //获取权限管理器系统服务
        final int currentUser = ActivityManager.getCurrentUser(); //获取当前用户？
        try {
            // determine if we should grant or revoke permission
            int perm = permissionManager.checkPermission(mPermissionInfo.name, mPkg, currentUser); //检查当前用户、当前包，还有权限名，对应的权限获取情况
            boolean grant = perm == PackageManager.PERMISSION_DENIED; //判断是否获得了权限
            // log before calling pm in case we hit an error
            Logger.out.println(String.format(":Permission %s %s to package %s",
                    grant ? "grant" : "revoke", mPermissionInfo.name, mPkg)); //向标准输出流打印获取的权限情况
            if (grant) { //如果对应的权限被拒了
                permissionManager.grantRuntimePermission(mPkg, mPermissionInfo.name, currentUser); //直接使用permissionManager获取运行时权限
            } else {
                permissionManager.revokeRuntimePermission(mPkg, mPermissionInfo.name, currentUser,
                        null); //这里竟然是取消运行时权限
            }
            return MonkeyEvent.INJECT_SUCCESS; //返回注入结果为成功
        } catch (RemoteException re) {
            return MonkeyEvent.INJECT_ERROR_REMOTE_EXCEPTION; //系统服务出错，返回远程异常
        }
    }
}
