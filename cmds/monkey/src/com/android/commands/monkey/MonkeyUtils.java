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

import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Misc utilities.
 * 第一次见工具类使用abstract修饰，拽，牛逼
 */
public abstract class MonkeyUtils {

    private static final java.util.Date DATE = new java.util.Date(); //MonkeyUtils类持有的Date对象
    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat(
        "yyyy-MM-dd HH:mm:ss.SSS "); //MonkeyUtils类持有的SimpleDateFormat对象
    private static PackageFilter sFilter;

    private MonkeyUtils() {
    }

    /**
     * Return calendar time in pretty string.
     */
    public static synchronized String toCalendarTime(long time) {
        DATE.setTime(time);
        return DATE_FORMATTER.format(DATE);
    }

    public static PackageFilter getPackageFilter() {
        if (sFilter == null) {
            sFilter = new PackageFilter();
        }
        return sFilter;
    }

    /**
     * 静态内部类，创建的PackageFilter对象会持有两个Set对象
     */
    public static class PackageFilter {
        private Set<String> mValidPackages = new HashSet<>(); //持有一个，用于保存有效包名的Set对象
        private Set<String> mInvalidPackages = new HashSet<>(); //持有另外一个，用于持有无效包名的Set对象

        /**
         * 私有构造方法，不允许通过构造方法创建对象，但是同一个类中的外部类可以，牛逼！思路清晰,所谓只在一个类中使用……经典
         */
        private PackageFilter() {
        }

        /**
         * 将某个Set对象中持有的所有元素，全部添加到mValidPackages集合中
         * @param validPackages Set对象
         */
        public void addValidPackages(Set<String> validPackages) {
            mValidPackages.addAll(validPackages);
        }

        public void addInvalidPackages(Set<String> invalidPackages) {
            mInvalidPackages.addAll(invalidPackages);
        }

        /**
         * 当Set中持有的元素大于0时，说明存在有效的包名
         * @return 返回是否存在有效包
         */
        public boolean hasValidPackages() {
            return mValidPackages.size() > 0;
        }

        public boolean isPackageValid(String pkg) {
            return mValidPackages.contains(pkg);
        }

        public boolean isPackageInvalid(String pkg) {
            return mInvalidPackages.contains(pkg);
        }

        /**
         * Check whether we should run against the given package.
         *
         * @param pkg The package name.
         * @return Returns true if we should run against pkg.
         */
        public boolean checkEnteringPackage(String pkg) {
            if (mInvalidPackages.size() > 0) { //先检查无效包名的集合中，是否包含pkg
                if (mInvalidPackages.contains(pkg)) {
                    return false;  //如果包含，返回false，表示包名无需输入
                }
            } else if (mValidPackages.size() > 0) {  //没有设置无效包名，直接检查有效包名的Set
                if (!mValidPackages.contains(pkg)) { //如果有效的Set中没有pkg，则返回false，表示无效
                    return false;
                }
            }
            return true;
        }

        /**
         * 遍历两个set中的包名，并输出日志
         * 1、有效包名
         * 2、无效包名
         */
        public void dump() {
            if (mValidPackages.size() > 0) {
                Iterator<String> it = mValidPackages.iterator();
                while (it.hasNext()) {
                    Logger.out.println(":AllowPackage: " + it.next());
                }
            }
            if (mInvalidPackages.size() > 0) {
                Iterator<String> it = mInvalidPackages.iterator(); //获取迭代器对象
                while (it.hasNext()) { //如果存在元素
                    Logger.out.println(":DisallowPackage: " + it.next()); //输出日志
                }
            }
        }
    }
}
