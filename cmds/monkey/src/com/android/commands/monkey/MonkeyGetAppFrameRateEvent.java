/*
 * Copyright (C) 2012 The Android Open Source Project
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
import android.os.Environment;
import android.util.Log;
import android.view.IWindowManager;

import java.lang.Process;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Events for running a special shell command to capture the frame rate for a given app. To run
 * this test, the system property viewancestor.profile_rendering must be set to
 * true to force the currently focused window to render at 60 Hz.
 * 用于获取App帧率的事件，提供3个创建对象的方式
 */
public class MonkeyGetAppFrameRateEvent extends MonkeyEvent {

    private String GET_APP_FRAMERATE_TMPL = "dumpsys gfxinfo %s"; //使用的命令
    private String mStatus; //表示状态
    private static long sStartTime; // in millisecond ，开始时间
    private static long sEndTime; // in millisecond ， 结束时间
    private static float sDuration; // in seconds 发生的时长
    private static String sActivityName = null; //MonkeyGetAppFrameRateEvent类持有的Activity名称
    private static String sTestCaseName = null; //MonkeyGetAppFrameRateEvent类持有的测试用例名称
    private static int sStartFrameNo; //表示开始的帧编号
    private static int sEndFrameNo; //表示结束的帧编号

    private static final String TAG = "MonkeyGetAppFrameRateEvent"; //用于打印日志的TAG
    private static final String LOG_FILE = new File(Environment.getExternalStorageDirectory(),
            "avgAppFrameRateOut.txt").getAbsolutePath(); //表示存储日志的文件路径
    private static final Pattern NO_OF_FRAMES_PATTERN =
            Pattern.compile(".* ([0-9]*) frames rendered"); //正则表达式模式

    /**
     *
     * @param status 状态
     * @param activityName activity名字
     * @param testCaseName 测试用例名字
     */
    public MonkeyGetAppFrameRateEvent(String status, String activityName, String testCaseName) {
        super(EVENT_TYPE_ACTIVITY); //注意该事件的类型也是EVENT_TYPE_ACTIVITY
        mStatus = status;
        sActivityName = activityName;
        sTestCaseName = testCaseName;
    }

    /**
     * 不用指定测试用例名字，创建对象
     * @param status 状态
     * @param activityName activity名字
     */
    public MonkeyGetAppFrameRateEvent(String status, String activityName) {
        super(EVENT_TYPE_ACTIVITY);
        mStatus = status;
        sActivityName = activityName;
    }

    /**
     * 不用指定 activity名字
     * 不用指定 测试用例名字
     * @param status 只需指定状态
     */
    public MonkeyGetAppFrameRateEvent(String status) {
        super(EVENT_TYPE_ACTIVITY);
        mStatus = status;
    }

    // Calculate the average frame rate
    private float getAverageFrameRate(int totalNumberOfFrame, float duration) {
        float avgFrameRate = 0;
        if (duration > 0) {
            avgFrameRate = (totalNumberOfFrame / duration);
        }
        return avgFrameRate;
    }

    /**
     * Calculate the frame rate and write the output to a file on the SD card.
     */
    private void writeAverageFrameRate() {
        FileWriter writer = null;
        float avgFrameRate;
        int totalNumberOfFrame = 0;
        try {
            Log.w(TAG, "file: " +LOG_FILE);
            writer = new FileWriter(LOG_FILE, true); // true = append
            totalNumberOfFrame = sEndFrameNo - sStartFrameNo;
            avgFrameRate = getAverageFrameRate(totalNumberOfFrame, sDuration);
            writer.write(String.format("%s:%.2f\n", sTestCaseName, avgFrameRate));
        } catch (IOException e) {
            Log.w(TAG, "Can't write sdcard log file", e);
        } finally {
            try {
                if (writer != null)
                    writer.close();
            } catch (IOException e) {
                Log.e(TAG, "IOException " + e.toString());
            }
        }
    }

    // Parse the output of the dumpsys shell command call
    private String getNumberOfFrames(BufferedReader reader) throws IOException {
        String noOfFrames = null;
        String line = null;
        while((line = reader.readLine()) != null) {
            Matcher m = NO_OF_FRAMES_PATTERN.matcher(line);
            if (m.matches()) {
                noOfFrames = m.group(1);
                break;
            }
        }
        return noOfFrames;
    }

    /**
     * 注入事件(执行事件）
     * @param iwm wires to current window manager WMS系统服务
     * @param iam wires to current activity manager AMS系统服务
     * @param verbose a log switch log开关
     * @return
     */
    @Override
    public int injectEvent(IWindowManager iwm, IActivityManager iam, int verbose) {
        Process p = null; //创建局部变量，Process，Process对象表示子进程
        BufferedReader result = null; //创建局部变量，缓冲字符对象
        String cmd = String.format(GET_APP_FRAMERATE_TMPL, sActivityName); //格式化需要执行的命令，存放在局部变量cmd中
        try {
            p = Runtime.getRuntime().exec(cmd); //获取Runtime对象，执行它的exec（）方法，用于执行一个程序，返回一个Process对象，表示子进程
            int status = p.waitFor(); //执行线程等待子进程执行完任务，这是waitFor（）的作用，拿到的status为程序的退出状态码
            if (status != 0) { //退出状态码不为0，说明执行不成功
                Logger.err.println(String.format("// Shell command %s status was %s",
                        cmd, status)); //向标准错误流中打印，shell命令，以及退出状态码
            }
            result = new BufferedReader(new InputStreamReader(p.getInputStream())); //创建BufferedReader对象，从子进程的输出中，获取执行文本

            String output = getNumberOfFrames(result);

            if (output != null) {
                if ("start".equals(mStatus)) {
                    sStartFrameNo = Integer.parseInt(output);
                    sStartTime = System.currentTimeMillis();
                } else if ("end".equals(mStatus)) {
                    sEndFrameNo = Integer.parseInt(output);
                    sEndTime = System.currentTimeMillis();
                    long diff = sEndTime - sStartTime;
                    sDuration = (float) (diff / 1000.0);
                    writeAverageFrameRate();
                }
            }
        } catch (Exception e) {
            Logger.err.println("// Exception from " + cmd + ":");
            Logger.err.println(e.toString());
        } finally {
            try {
                if (result != null) {
                    result.close();
                }
                if (p != null) {
                    p.destroy();
                }
            } catch (IOException e) {
                Logger.err.println(e.toString());
            }
        }
        return MonkeyEvent.INJECT_SUCCESS;
    }
}
