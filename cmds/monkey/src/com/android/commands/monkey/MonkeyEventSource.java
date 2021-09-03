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

/**
 * event source interface，表示具备事件源能力的接口，谁实现了该接口，谁就具备了作为事件源的能力
 */
public interface MonkeyEventSource {
    /**
     * @return the next monkey event from the source
     * 用于从事件源获取下一个事件
     */
    public MonkeyEvent getNextEvent();

    /**
     * set verbose to allow different level of log
     * 用于设置日志等级
     * @param verbose output mode? 1= verbose, 2=very verbose
     */
    public void setVerbose(int verbose);

    /**
     * check whether precondition is satisfied
     *
     * @return false if something fails, e.g. factor failure in random source or
     *         file can not open from script source etc
     * 用于检查事件源是否有效
     */
    public boolean validate();
}
