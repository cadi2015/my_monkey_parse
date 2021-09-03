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

import android.Manifest;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Build;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.permission.IPermissionManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Utility class that encapsulates runtime permission related methods for monkey
 * Monkey使用的权限工具
 */
public class MonkeyPermissionUtil {

    private static final String PERMISSION_PREFIX = "android.permission."; //权限前缀
    private static final String PERMISSION_GROUP_PREFIX = "android.permission-group."; //权限组前缀

    // from com.android.packageinstaller.permission.utils
    private static final String[] MODERN_PERMISSION_GROUPS = { //现代需要的权限组，好多……
            Manifest.permission_group.CALENDAR, Manifest.permission_group.CAMERA,
            Manifest.permission_group.CONTACTS, Manifest.permission_group.LOCATION,
            Manifest.permission_group.SENSORS, Manifest.permission_group.SMS,
            Manifest.permission_group.PHONE, Manifest.permission_group.MICROPHONE,
            Manifest.permission_group.STORAGE
    };

    // from com.android.packageinstaller.permission.utils

    /**
     *
     * @param name 权限组字符串名称
     * @return 是否为现代权限组
     */
    private static boolean isModernPermissionGroup(String name) {
        for (String modernGroup : MODERN_PERMISSION_GROUPS) {
            if (modernGroup.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * actual list of packages to target, with invalid packages excluded, and may optionally include
     * system packages
     */
    private List<String> mTargetedPackages; //持有的所有目标包
    /** if we should target system packages regardless if they are listed */
    private boolean mTargetSystemPackages; //持有的是否为系统包
    private IPackageManager mPm; //PMS系统服务
    private final IPermissionManager mPermManager; //PermissionManager权限

    /** keep track of runtime permissions requested for each package targeted */
    private Map<String, List<PermissionInfo>> mPermissionMap; //持有的Map，Key为String，Value为List对象，list的每个元素PermissionInfo对象

    /**
     * 创建MonkeyPermissionUtil对象
     */
    public MonkeyPermissionUtil() {
        mPm = IPackageManager.Stub.asInterface(ServiceManager.getService("package")); //初始化PMS系统的本地Binder
        mPermManager =
                IPermissionManager.Stub.asInterface(ServiceManager.getService("permissionmgr")); //初始化permissionmgr系统服务的本地Binder
    }

    /**
     * 设置是否需要系统应用
     * @param targetSystemPackages
     */
    public void setTargetSystemPackages(boolean targetSystemPackages) {
        mTargetSystemPackages = targetSystemPackages;
    }

    /**
     * Decide if a package should be targeted by permission monkey
     * @param info 包信息对象
     * @return 是否为目标包（可用的应用）
     */
    private boolean shouldTargetPackage(PackageInfo info) {
        // target if permitted by white listing / black listing rules
        if (MonkeyUtils.getPackageFilter().checkEnteringPackage(info.packageName)) {
            return true; //如果是允许进入的包，则直接返回true，不管别的事情
        }
        if (mTargetSystemPackages //如果支持系统应用、且不是无效包、应用的flags与FLAG_SYSTEM位与不等于0，直接返回true
                // not explicitly black listed
                && !MonkeyUtils.getPackageFilter().isPackageInvalid(info.packageName)
                // is a system app
                && (info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
            return true;
        }
        return false; //说不是支持的应用
    }

    private boolean shouldTargetPermission(String pkg, PermissionInfo pi) throws RemoteException {
        int flags = mPermManager.getPermissionFlags(pi.name, pkg, UserHandle.myUserId());
        int fixedPermFlags = PackageManager.FLAG_PERMISSION_SYSTEM_FIXED
                | PackageManager.FLAG_PERMISSION_POLICY_FIXED;
        return pi.group != null && pi.protectionLevel == PermissionInfo.PROTECTION_DANGEROUS
                && ((flags & fixedPermFlags) == 0)
                && isModernPermissionGroup(pi.group);
    }

    /**
     *
     * @return
     */
    public boolean populatePermissionsMapping() {
        mPermissionMap = new HashMap<>(); //创建HashMap对象
        try {
            List<?> pkgInfos = mPm.getInstalledPackages(
                    PackageManager.GET_PERMISSIONS, UserHandle.myUserId()).getList(); //通过PMS获取所有已安装包的信息
            for (Object o : pkgInfos) { //遍历所有安装包信息
                PackageInfo info = (PackageInfo)o; //元素向下转型为PackageInfo对象
                if (!shouldTargetPackage(info)) { //检查是不是可以使用的应用
                    continue; //如果不是目标包，直接中断一次循环
                }
                List<PermissionInfo> permissions = new ArrayList<>(); //创建一个List对象，每个元素为PermissionInfo对象
                if (info.applicationInfo.targetSdkVersion <= Build.VERSION_CODES.LOLLIPOP_MR1) { //判断API版本
                    // skip apps targetting lower API level
                    continue;
                }
                if (info.requestedPermissions == null) {
                    continue;
                }
                for (String perm : info.requestedPermissions) {
                    PermissionInfo pi = mPermManager.getPermissionInfo(perm, "shell", 0);
                    if (pi != null && shouldTargetPermission(info.packageName, pi)) {
                        permissions.add(pi);
                    }
                }
                if (!permissions.isEmpty()) {
                    mPermissionMap.put(info.packageName, permissions); //包名作为Key，List对象为Value，这个Map保存着每个应用的拥有权限情况，因为一个应用，会有多个权限，所以Key为包名，Value为一个List
                }
            }
        } catch (RemoteException re) {
            Logger.err.println("** Failed talking with package manager!");
            return false; //PMS出错，会走这里
        }
        if (!mPermissionMap.isEmpty()) {
            mTargetedPackages = new ArrayList<>(mPermissionMap.keySet());
        }
        return true;
    }

    /**
     * 向标准错误流输出权限信息
     */
    public void dump() {
        Logger.out.println("// Targeted packages and permissions:"); //向标准错误流输出一行日志
        for (Map.Entry<String, List<PermissionInfo>> e : mPermissionMap.entrySet()) { //遍历Map，包名为key，权限列表为Value
            Logger.out.println(String.format("//  + Using %s", e.getKey())); //打印包名
            for (PermissionInfo pi : e.getValue()) { //遍历一个包，获取到的所有权限
                String name = pi.name; //权限名称
                if (name != null) { //如果有权限名称
                    if (name.startsWith(PERMISSION_PREFIX)) { //如果权限名称是以android.permission.开头的
                        name = name.substring(PERMISSION_PREFIX.length()); //提取android.permission.后面的字符串
                    }
                }
                String group = pi.group; //获取所在的权限组
                if (group != null) { //如果权限组不为空
                    if (group.startsWith(PERMISSION_GROUP_PREFIX)) { //检查权限组是否以android.permission-group.
                        group = group.substring(PERMISSION_GROUP_PREFIX.length()); //提取android.permission-group.后面的字符串
                    }
                }
                Logger.out.println(String.format("//    Permission: %s [%s]", name, group)); //输出权限名，还有所在的权限组
            }
        }
    }

    /**
     * 构建一个MonkeyPermissionEvent事件
     * @param random Random对象
     * @return 返回构建好的构建一个MonkeyPermissionEvent事件，该事件通过权限系统服务，可以开启或者关闭某个应用获取到的权限
     */
    public MonkeyPermissionEvent generateRandomPermissionEvent(Random random) {
        String pkg = mTargetedPackages.get(random.nextInt(mTargetedPackages.size())); //从指定的包名中，随机获取一个包名
        List<PermissionInfo> infos = mPermissionMap.get(pkg); //获取该包名中所有对应的权限信息
        return new MonkeyPermissionEvent(pkg, infos.get(random.nextInt(infos.size()))); //创建MonkeyPermissionEvent对象，并且把包名、还有该包名支持的一个权限传入进去（PermissionInfo对象）
    }
}
