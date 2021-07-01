/**
** Copyright 2007, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package com.android.commands.monkey;

import android.app.IActivityManager;
import android.content.IIntentReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;

/**
 * Class for monitoring network connectivity during monkey runs.
 * 看下IIntentReceiver有哪些功能，一个Binder
 */
public class MonkeyNetworkMonitor extends IIntentReceiver.Stub {
    private static final boolean LDEBUG = false; //用于调试日志
    private final IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION); //MonkeyNetworkMonitor对象持有的IntentFilter对象
    private long mCollectionStartTime; // time we started collecting data
    private long mEventTime; // time of last event (connect, disconnect, etc.)
    private int mLastNetworkType = -1; // unknown //用于记录最后一个的网络类型
    private long mWifiElapsedTime = 0;  // accumulated time spent on wifi since start()
    private long mMobileElapsedTime = 0; // accumulated time spent on mobile since start()
    private long mElapsedTime = 0; // amount of time spent between start() and stop()

    /**
     * AMS远程调用的方法，执行在当前Binder线程池中
     * @param intent
     * @param resultCode
     * @param data
     * @param extras
     * @param ordered
     * @param sticky
     * @param sendingUser
     * @throws RemoteException
     */
    public void performReceive(Intent intent, int resultCode, String data, Bundle extras,
            boolean ordered, boolean sticky, int sendingUser) throws RemoteException {
        NetworkInfo ni = (NetworkInfo) intent.getParcelableExtra(
                ConnectivityManager.EXTRA_NETWORK_INFO); //从Intent对象解析出序列化的数据，是一个NetworkInfo对象
        if (LDEBUG) Logger.out.println("Network state changed: " 
                + "type=" + ni.getType() + ", state="  + ni.getState()); //如果debug时，向控制台输出日志
        updateNetworkStats(); //更新网络状态
        if (NetworkInfo.State.CONNECTED == ni.getState()) { //如果网络是连接状态
            if (LDEBUG) Logger.out.println("Network connected"); //向控制台打印网络状态
            mLastNetworkType = ni.getType();  //记录最后一次的网络类型
        } else if (NetworkInfo.State.DISCONNECTED == ni.getState()) {  //如果网络是断开状态状态
            if (LDEBUG) Logger.out.println("Network not connected");
            mLastNetworkType = -1; // unknown since we're disconnected
        }
        mEventTime = SystemClock.elapsedRealtime();
    }

    /**
     * 更新监控网络
     */
    private void updateNetworkStats() {
        long timeNow = SystemClock.elapsedRealtime(); //看下API文档，这是个什么时间
        long delta = timeNow - mEventTime;//现在的时间减去，最初开始监控的时间
        switch (mLastNetworkType) { //最后一次切换网络的类型
            case ConnectivityManager.TYPE_MOBILE: //使用移动网络的情况
                if (LDEBUG) Logger.out.println("Adding to mobile: " + delta);
                mMobileElapsedTime += delta;
                break;
            case ConnectivityManager.TYPE_WIFI: //使用wifi的情况
                if (LDEBUG) Logger.out.println("Adding to wifi: " + delta);
                mWifiElapsedTime += delta;
                break;
            default:
                if (LDEBUG) Logger.out.println("Unaccounted for: " + delta); //即不是移动网络、也不是WIFI
                break;
        }
        mElapsedTime = timeNow - mCollectionStartTime;
    }

    /**
     *  表示为开始成为监控Binder的时间点
     */
    public void start() {
        mWifiElapsedTime = 0; //wifi开始时间初始化
        mMobileElapsedTime = 0; //移动网络时间初始化
        mElapsedTime = 0; //花费时间的开始计算
        mEventTime = mCollectionStartTime = SystemClock.elapsedRealtime();
    }

    /**
     *
     * @param am AMS系统服务的远程引用
     * @throws RemoteException 可能会出现远程服务错误
     */
    public void register(IActivityManager am) throws RemoteException {
        if (LDEBUG) Logger.out.println("registering Receiver"); //如果日志开启，输出一段日志
        am.registerReceiverWithFeature(null, null, null, this, filter, null, UserHandle.USER_ALL,
                0); //将当前Binder对象注册到AMS中，当网络状态出现变化，AMS系统可以通知当前Binder对象，我靠，没想到Monkey使用两个Binder与AMS进行通信
    }

    /**
     *
     * @param am AMS系统服务的远程引用
     * @throws RemoteException 可能会出现远程服务错误
     */
    public void unregister(IActivityManager am) throws RemoteException {
        if (LDEBUG) Logger.out.println("unregistering Receiver");
        am.unregisterReceiver(this); //告知AMS，取消自己的注册，这样AMS就不会再通知当前Binder
    }

    public void stop() {
        updateNetworkStats();
    }

    /**
     * 获知网络情况
     * 总的花费时间
     * 移动网络的保持时间
     * wifi网络的保持时间
     * 无网络的保持时间
     */
    public void dump() {
        Logger.out.println("## Network stats: elapsed time=" + mElapsedTime + "ms (" 
                + mMobileElapsedTime + "ms mobile, "
                + mWifiElapsedTime + "ms wifi, "
                + (mElapsedTime - mMobileElapsedTime - mWifiElapsedTime) + "ms not connected)");
    }
 }
