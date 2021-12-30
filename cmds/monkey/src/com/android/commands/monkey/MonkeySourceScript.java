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

import android.content.ComponentName;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.NoSuchElementException;
import java.util.Random;

/**
 * monkey event queue. It takes a script to produce events sample script format:
 *
 * <pre>
 * type= raw events
 * count= 10
 * speed= 1.0
 * start data &gt;&gt;
 * captureDispatchPointer(5109520,5109520,0,230.75429,458.1814,0.20784314,0.06666667,0,0.0,0.0,65539,0)
 * captureDispatchKey(5113146,5113146,0,20,0,0,0,0)
 * captureDispatchFlip(true)
 * ...
 * </pre>
 * MonkeySourceScript is a MonkeyEventSource
 * 从脚本文件中获取事件，写的很好
 */
public class MonkeySourceScript implements MonkeyEventSource {
    private int mEventCountInScript = 0; // total number of events in the file //表示脚本数量

    private int mVerbose = 0; //用于调试打印信息的选项

    private double mSpeed = 1.0; //速度？

    private String mScriptFileName; //脚本文件的名称

    private MonkeyEventQueue mQ; //双向链表对象

    private static final String HEADER_COUNT = "count=";

    private static final String HEADER_SPEED = "speed=";

    private long mLastRecordedDownTimeKey = 0;

    private long mLastRecordedDownTimeMotion = 0;

    private long mLastExportDownTimeKey = 0;

    private long mLastExportDownTimeMotion = 0;

    private long mLastExportEventTime = -1;

    private long mLastRecordedEventTime = -1;

    // process scripts in line-by-line mode (true) or batch processing mode (false)
    private boolean mReadScriptLineByLine = false;

    private static final boolean THIS_DEBUG = false;

    // a parameter that compensates the difference of real elapsed time and
    // time in theory
    private static final long SLEEP_COMPENSATE_DIFF = 16;

    // if this header is present, scripts are read and processed in line-by-line mode
    private static final String HEADER_LINE_BY_LINE = "linebyline";

    // maximum number of events that we read at one time
    private static final int MAX_ONE_TIME_READS = 100;

    // event key word in the capture log
    private static final String EVENT_KEYWORD_POINTER = "DispatchPointer"; //规定的事件名

    private static final String EVENT_KEYWORD_TRACKBALL = "DispatchTrackball";

    private static final String EVENT_KEYWORD_ROTATION = "RotateScreen";

    private static final String EVENT_KEYWORD_KEY = "DispatchKey";

    private static final String EVENT_KEYWORD_FLIP = "DispatchFlip";

    private static final String EVENT_KEYWORD_KEYPRESS = "DispatchPress";

    private static final String EVENT_KEYWORD_ACTIVITY = "LaunchActivity";

    private static final String EVENT_KEYWORD_INSTRUMENTATION = "LaunchInstrumentation";

    private static final String EVENT_KEYWORD_WAIT = "UserWait";

    private static final String EVENT_KEYWORD_LONGPRESS = "LongPress";

    private static final String EVENT_KEYWORD_POWERLOG = "PowerLog";

    private static final String EVENT_KEYWORD_WRITEPOWERLOG = "WriteLog";

    private static final String EVENT_KEYWORD_RUNCMD = "RunCmd";

    private static final String EVENT_KEYWORD_TAP = "Tap";

    private static final String EVENT_KEYWORD_PROFILE_WAIT = "ProfileWait";

    private static final String EVENT_KEYWORD_DEVICE_WAKEUP = "DeviceWakeUp";

    private static final String EVENT_KEYWORD_INPUT_STRING = "DispatchString";

    private static final String EVENT_KEYWORD_PRESSANDHOLD = "PressAndHold";

    private static final String EVENT_KEYWORD_DRAG = "Drag";

    private static final String EVENT_KEYWORD_PINCH_ZOOM = "PinchZoom";

    private static final String EVENT_KEYWORD_START_FRAMERATE_CAPTURE = "StartCaptureFramerate";

    private static final String EVENT_KEYWORD_END_FRAMERATE_CAPTURE = "EndCaptureFramerate";

    private static final String EVENT_KEYWORD_START_APP_FRAMERATE_CAPTURE =
            "StartCaptureAppFramerate";

    private static final String EVENT_KEYWORD_END_APP_FRAMERATE_CAPTURE = "EndCaptureAppFramerate";

    // a line at the end of the header
    private static final String STARTING_DATA_LINE = "start data >>";

    private boolean mFileOpened = false;

    private static int LONGPRESS_WAIT_TIME = 2000; // wait time for the long

    private long mProfileWaitTime = 5000; //Wait time for each user profile

    private long mDeviceSleepTime = 30000; //Device sleep time

    FileInputStream mFStream;

    DataInputStream mInputStream;

    BufferedReader mBufferedReader;

    // X and Y coordincates of last touch event. Array Index is the pointerId
    private float mLastX[] = new float[2];

    private float mLastY[] = new float[2];

    private long mScriptStartTime = -1;

    private long mMonkeyStartTime = -1;

    /**
     * Creates a MonkeySourceScript instance.
     * 用于创建MonkeySourceScript对象
     * 必须传入Random对象
     * @param filename The filename of the script (on the device).  脚本文件名，在设备中的文件（完整路径）
     * @param throttle The amount of time in ms to sleep between events. 事件之间的间隔时间
     */
    public MonkeySourceScript(Random random, String filename, long throttle,
                              boolean randomizeThrottle, long profileWaitTime, long deviceSleepTime) {
        mScriptFileName = filename; //由持有的mScriptFileName保存脚本文件名
        mQ = new MonkeyEventQueue(random, throttle, randomizeThrottle); //创建双向链表对象
        mProfileWaitTime = profileWaitTime; //加载文件时的等待时间
        mDeviceSleepTime = deviceSleepTime; //设备的休眠时间
    }

    /**
     * Resets the globals used to timeshift events.
     * 重置当前对象持有的实例变量
     */
    private void resetValue() {
        mLastRecordedDownTimeKey = 0;
        mLastRecordedDownTimeMotion = 0;
        mLastRecordedEventTime = -1;
        mLastExportDownTimeKey = 0;
        mLastExportDownTimeMotion = 0;
        mLastExportEventTime = -1;
    }

    /**
     * Reads the header of the script file.
     * 从脚本文件中读取header信息
     * @return True if the file header could be parsed, and false otherwise. 表示解析文件开头的结果，false表示解析失败
     * @throws IOException If there was an error reading the file. 该方法出现IOException，不会自己处理，需要调用者来处理IOException
     */
    private boolean readHeader() throws IOException {
        mFileOpened = true; //标记文件已经被打开

        mFStream = new FileInputStream(mScriptFileName); //创建文件输入流对象（读入到内存中操作）
        mInputStream = new DataInputStream(mFStream); //二进制输入字节流与文件输入流结合！
        mBufferedReader = new BufferedReader(new InputStreamReader(mInputStream)); //内存缓冲区，需要传入一个二进制字节流转化为字符流（使用编码）

        String line; //存储每一行的内容

        //这里是在每一行去查找需要的内容
        while ((line = mBufferedReader.readLine()) != null) { //读取一行内容，如果有内容，就继续执行
            line = line.trim(); //去除掉一行中的首尾的空白字符（空格、换行、还有制表符）

            if (line.indexOf(HEADER_COUNT) >= 0) { // 处理header_count行，如果某行字符串标记有count=，且将HEADER_COUNT在某行首次出现的位置与0进行比较，大于等于0时，说明存在
                try {
                    String value = line.substring(HEADER_COUNT.length() + 1).trim(); //提取count=后面的字符串，即事件数量
                    mEventCountInScript = Integer.parseInt(value); //提取在脚本文件中标记的事件数量，将字符串解析成整型
                } catch (NumberFormatException e) { //如果提取的字符串无法转化成整型时，会出现NumberFormatException异常
                    Logger.err.println("" + e); //先在标准错误流中输出日志
                    return false;  //再返回false，表示解析header时出现错误
                }
            } else if (line.indexOf(HEADER_SPEED) >= 0) { //找到speed=首次出现在某行的位置，看来是一行写一个了……
                try {
                    String value = line.substring(HEADER_COUNT.length() + 1).trim(); //提取speed=后面的值
                    mSpeed = Double.parseDouble(value); //将字符串转化为double
                } catch (NumberFormatException e) { //如果字符串转换double出错
                    Logger.err.println("" + e); //在标准错误流中提示，输出日志
                    return false; //返回解析header错误的结果
                }
            } else if (line.indexOf(HEADER_LINE_BY_LINE) >= 0) { //找到某行中是否有linebyline，当然也拿到首次出现的位置
                mReadScriptLineByLine = true; //表示脚本文件中存在linbebyline
            } else if (line.indexOf(STARTING_DATA_LINE) >= 0) { //找到某行中是否有start data >>
                return true; //找到了直接返回true，说明解析文件头部成功
            }
        }

        return false; //找遍整个脚本文件，都没有匹配的内容，最后返回false，说明文件的头部几行不符合要求
    }

    /**
     * Reads a number of lines and passes the lines to be processed.
     * 一次性将所有行解析完毕，
     * @return The number of lines read. 一共读取了多少行
     * @throws IOException If there was an error reading the file.
     */
    private int readLines() throws IOException {
        String line;
        for (int i = 0; i < MAX_ONE_TIME_READS; i++) {
            line = mBufferedReader.readLine();
            if (line == null) {
                return i;
            }
            line.trim();
            processLine(line);
        }
        return MAX_ONE_TIME_READS;
    }

    /**
     * Reads one line and processes it.
     *
     * @return the number of lines read
     * @throws IOException If there was an error reading the file.
     */
    private int readOneLine() throws IOException {
        String line = mBufferedReader.readLine(); //BufferReader对象的readLine（）
        if (line == null) { //如果没有内容
            return 0; //直接返回0
        }
        line.trim(); //去除空格
        processLine(line); //解析行内容
        return 1;
    }


    /**
     * Creates an event and adds it to the event queue. If the parameters are
     * not understood, they are ignored and no events are added.
     *
     * @param s    The entire string from the script file. 从脚本文件中读取的一整行字符串
     * @param args An array of arguments extracted from the script file line. 从括号中提取的内容
     */
    private void handleEvent(String s, String[] args) {
        // Handle key event
        if (s.indexOf(EVENT_KEYWORD_KEY) >= 0 && args.length == 8) { //如果在一整行中包括DispatchKey，且（）括号中正好是8个字符串
            try {
                Logger.out.println(" old key\n");  //打印一行日志， old key
                long downTime = Long.parseLong(args[0]);  //解析括号中的第一个字符串
                long eventTime = Long.parseLong(args[1]);
                int action = Integer.parseInt(args[2]);
                int code = Integer.parseInt(args[3]);
                int repeat = Integer.parseInt(args[4]);
                int metaState = Integer.parseInt(args[5]);
                int device = Integer.parseInt(args[6]);
                int scancode = Integer.parseInt(args[7]); //解析括号中的最后一个字符串

                MonkeyKeyEvent e = new MonkeyKeyEvent(downTime, eventTime, action, code, repeat,
                        metaState, device, scancode); //创建MonkeyKeyEvent对象，牛逼
                Logger.out.println(" Key code " + code + "\n"); //输出Key code 值

                mQ.addLast(e); //将事件对象，添加到双向队列的尾部
                Logger.out.println("Added key up \n"); //打印一行日志，告知添加事件完毕
            } catch (NumberFormatException e) { //如果捕获到解析数字失败，什么也不干……
            }
            return; //事件添加完毕，方法结束，真的是一行一个事件
        }

        // Handle trackball or pointer events 处理轨迹球事件，还有键盘事件，因为它俩需要的参数都是12个
        if ((s.indexOf(EVENT_KEYWORD_POINTER) >= 0 || s.indexOf(EVENT_KEYWORD_TRACKBALL) >= 0)
                && args.length == 12) {
            try {
                long downTime = Long.parseLong(args[0]);
                long eventTime = Long.parseLong(args[1]);
                int action = Integer.parseInt(args[2]);
                float x = Float.parseFloat(args[3]);
                float y = Float.parseFloat(args[4]);
                float pressure = Float.parseFloat(args[5]);
                float size = Float.parseFloat(args[6]);
                int metaState = Integer.parseInt(args[7]);
                float xPrecision = Float.parseFloat(args[8]);
                float yPrecision = Float.parseFloat(args[9]);
                int device = Integer.parseInt(args[10]);
                int edgeFlags = Integer.parseInt(args[11]);

                MonkeyMotionEvent e;
                if (s.indexOf("Pointer") > 0) {
                    e = new MonkeyTouchEvent(action); //创建MonkeyTouchEvent对象
                } else {
                    e = new MonkeyTrackballEvent(action); //创建MonkeyTrackballEvent对象
                }

                e.setDownTime(downTime)
                        .setEventTime(eventTime)
                        .setMetaState(metaState)
                        .setPrecision(xPrecision, yPrecision)
                        .setDeviceId(device)
                        .setEdgeFlags(edgeFlags)
                        .addPointer(0, x, y, pressure, size);
                mQ.addLast(e); //添加到双向链表中
            } catch (NumberFormatException e) {
            }
            return;
        }

        // Handle trackball or multi-touch  pointer events. pointer ID is the 13th parameter
        if ((s.indexOf(EVENT_KEYWORD_POINTER) >= 0 || s.indexOf(EVENT_KEYWORD_TRACKBALL) >= 0)
                && args.length == 13) {
            try {
                long downTime = Long.parseLong(args[0]);
                long eventTime = Long.parseLong(args[1]);
                int action = Integer.parseInt(args[2]);
                float x = Float.parseFloat(args[3]);
                float y = Float.parseFloat(args[4]);
                float pressure = Float.parseFloat(args[5]);
                float size = Float.parseFloat(args[6]);
                int metaState = Integer.parseInt(args[7]);
                float xPrecision = Float.parseFloat(args[8]);
                float yPrecision = Float.parseFloat(args[9]);
                int device = Integer.parseInt(args[10]);
                int edgeFlags = Integer.parseInt(args[11]);
                int pointerId = Integer.parseInt(args[12]);

                MonkeyMotionEvent e;
                if (s.indexOf("Pointer") > 0) {
                    if (action == MotionEvent.ACTION_POINTER_DOWN) {
                        e = new MonkeyTouchEvent(MotionEvent.ACTION_POINTER_DOWN
                                | (pointerId << MotionEvent.ACTION_POINTER_INDEX_SHIFT))
                                .setIntermediateNote(true);
                    } else {
                        e = new MonkeyTouchEvent(action);
                    }
                    if (mScriptStartTime < 0) {
                        mMonkeyStartTime = SystemClock.uptimeMillis();
                        mScriptStartTime = eventTime;
                    }
                } else {
                    e = new MonkeyTrackballEvent(action);
                }

                if (pointerId == 1) {
                    e.setDownTime(downTime)
                            .setEventTime(eventTime)
                            .setMetaState(metaState)
                            .setPrecision(xPrecision, yPrecision)
                            .setDeviceId(device)
                            .setEdgeFlags(edgeFlags)
                            .addPointer(0, mLastX[0], mLastY[0], pressure, size)
                            .addPointer(1, x, y, pressure, size);
                    mLastX[1] = x;
                    mLastY[1] = y;
                } else if (pointerId == 0) {
                    e.setDownTime(downTime)
                            .setEventTime(eventTime)
                            .setMetaState(metaState)
                            .setPrecision(xPrecision, yPrecision)
                            .setDeviceId(device)
                            .setEdgeFlags(edgeFlags)
                            .addPointer(0, x, y, pressure, size);
                    if (action == MotionEvent.ACTION_POINTER_UP) {
                        e.addPointer(1, mLastX[1], mLastY[1]);
                    }
                    mLastX[0] = x;
                    mLastY[0] = y;
                }

                // Dynamically adjust waiting time to ensure that simulated evnets follow
                // the time tap specified in the script
                if (mReadScriptLineByLine) {
                    long curUpTime = SystemClock.uptimeMillis();
                    long realElapsedTime = curUpTime - mMonkeyStartTime;
                    long scriptElapsedTime = eventTime - mScriptStartTime;
                    if (realElapsedTime < scriptElapsedTime) {
                        long waitDuration = scriptElapsedTime - realElapsedTime;
                        mQ.addLast(new MonkeyWaitEvent(waitDuration));
                    }
                }
                mQ.addLast(e);
            } catch (NumberFormatException e) {
            }
            return;
        }

        // Handle screen rotation events
        if ((s.indexOf(EVENT_KEYWORD_ROTATION) >= 0) && args.length == 2) {
            try {
                int rotationDegree = Integer.parseInt(args[0]);
                int persist = Integer.parseInt(args[1]);
                if ((rotationDegree == Surface.ROTATION_0) ||
                        (rotationDegree == Surface.ROTATION_90) ||
                        (rotationDegree == Surface.ROTATION_180) ||
                        (rotationDegree == Surface.ROTATION_270)) {
                    mQ.addLast(new MonkeyRotationEvent(rotationDegree,
                            persist != 0));
                }
            } catch (NumberFormatException e) {
            }
            return;
        }

        // Handle tap event
        if ((s.indexOf(EVENT_KEYWORD_TAP) >= 0) && args.length >= 2) {
            try {
                float x = Float.parseFloat(args[0]);
                float y = Float.parseFloat(args[1]);
                long tapDuration = 0;
                if (args.length == 3) {
                    tapDuration = Long.parseLong(args[2]);
                }

                // Set the default parameters
                long downTime = SystemClock.uptimeMillis();
                MonkeyMotionEvent e1 = new MonkeyTouchEvent(MotionEvent.ACTION_DOWN)
                        .setDownTime(downTime)
                        .setEventTime(downTime)
                        .addPointer(0, x, y, 1, 5);
                mQ.addLast(e1);
                if (tapDuration > 0) {
                    mQ.addLast(new MonkeyWaitEvent(tapDuration));
                }
                MonkeyMotionEvent e2 = new MonkeyTouchEvent(MotionEvent.ACTION_UP)
                        .setDownTime(downTime)
                        .setEventTime(downTime)
                        .addPointer(0, x, y, 1, 5);
                mQ.addLast(e2);
            } catch (NumberFormatException e) {
                Logger.err.println("// " + e.toString());
            }
            return;
        }

        //Handle the press and hold
        if ((s.indexOf(EVENT_KEYWORD_PRESSANDHOLD) >= 0) && args.length == 3) {
            try {
                float x = Float.parseFloat(args[0]);
                float y = Float.parseFloat(args[1]);
                long pressDuration = Long.parseLong(args[2]);

                // Set the default parameters
                long downTime = SystemClock.uptimeMillis();

                MonkeyMotionEvent e1 = new MonkeyTouchEvent(MotionEvent.ACTION_DOWN)
                        .setDownTime(downTime)
                        .setEventTime(downTime)
                        .addPointer(0, x, y, 1, 5);
                MonkeyWaitEvent e2 = new MonkeyWaitEvent(pressDuration);
                MonkeyMotionEvent e3 = new MonkeyTouchEvent(MotionEvent.ACTION_UP)
                        .setDownTime(downTime + pressDuration)
                        .setEventTime(downTime + pressDuration)
                        .addPointer(0, x, y, 1, 5);
                mQ.addLast(e1);
                mQ.addLast(e2);
                mQ.addLast(e2);

            } catch (NumberFormatException e) {
                Logger.err.println("// " + e.toString());
            }
            return;
        }

        // Handle drag event
        if ((s.indexOf(EVENT_KEYWORD_DRAG) >= 0) && args.length == 5) {
            float xStart = Float.parseFloat(args[0]);
            float yStart = Float.parseFloat(args[1]);
            float xEnd = Float.parseFloat(args[2]);
            float yEnd = Float.parseFloat(args[3]);
            int stepCount = Integer.parseInt(args[4]);

            float x = xStart;
            float y = yStart;
            long downTime = SystemClock.uptimeMillis();
            long eventTime = SystemClock.uptimeMillis();

            if (stepCount > 0) {
                float xStep = (xEnd - xStart) / stepCount;
                float yStep = (yEnd - yStart) / stepCount;

                MonkeyMotionEvent e =
                        new MonkeyTouchEvent(MotionEvent.ACTION_DOWN).setDownTime(downTime)
                                .setEventTime(eventTime).addPointer(0, x, y, 1, 5);
                mQ.addLast(e);

                for (int i = 0; i < stepCount; ++i) {
                    x += xStep;
                    y += yStep;
                    eventTime = SystemClock.uptimeMillis();
                    e = new MonkeyTouchEvent(MotionEvent.ACTION_MOVE).setDownTime(downTime)
                            .setEventTime(eventTime).addPointer(0, x, y, 1, 5);
                    mQ.addLast(e);
                }

                eventTime = SystemClock.uptimeMillis();
                e = new MonkeyTouchEvent(MotionEvent.ACTION_UP).setDownTime(downTime)
                        .setEventTime(eventTime).addPointer(0, x, y, 1, 5);
                mQ.addLast(e);
            }
        }

        // Handle pinch or zoom action
        if ((s.indexOf(EVENT_KEYWORD_PINCH_ZOOM) >= 0) && args.length == 9) {
            //Parse the parameters
            float pt1xStart = Float.parseFloat(args[0]);
            float pt1yStart = Float.parseFloat(args[1]);
            float pt1xEnd = Float.parseFloat(args[2]);
            float pt1yEnd = Float.parseFloat(args[3]);

            float pt2xStart = Float.parseFloat(args[4]);
            float pt2yStart = Float.parseFloat(args[5]);
            float pt2xEnd = Float.parseFloat(args[6]);
            float pt2yEnd = Float.parseFloat(args[7]);

            int stepCount = Integer.parseInt(args[8]);

            float x1 = pt1xStart;
            float y1 = pt1yStart;
            float x2 = pt2xStart;
            float y2 = pt2yStart;

            long downTime = SystemClock.uptimeMillis();
            long eventTime = SystemClock.uptimeMillis();

            if (stepCount > 0) {
                float pt1xStep = (pt1xEnd - pt1xStart) / stepCount;
                float pt1yStep = (pt1yEnd - pt1yStart) / stepCount;

                float pt2xStep = (pt2xEnd - pt2xStart) / stepCount;
                float pt2yStep = (pt2yEnd - pt2yStart) / stepCount;

                mQ.addLast(new MonkeyTouchEvent(MotionEvent.ACTION_DOWN).setDownTime(downTime)
                        .setEventTime(eventTime).addPointer(0, x1, y1, 1, 5));

                mQ.addLast(new MonkeyTouchEvent(MotionEvent.ACTION_POINTER_DOWN
                        | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT)).setDownTime(downTime)
                        .addPointer(0, x1, y1).addPointer(1, x2, y2).setIntermediateNote(true));

                for (int i = 0; i < stepCount; ++i) {
                    x1 += pt1xStep;
                    y1 += pt1yStep;
                    x2 += pt2xStep;
                    y2 += pt2yStep;

                    eventTime = SystemClock.uptimeMillis();
                    mQ.addLast(new MonkeyTouchEvent(MotionEvent.ACTION_MOVE).setDownTime(downTime)
                            .setEventTime(eventTime).addPointer(0, x1, y1, 1, 5).addPointer(1, x2,
                                    y2, 1, 5));
                }
                eventTime = SystemClock.uptimeMillis();
                mQ.addLast(new MonkeyTouchEvent(MotionEvent.ACTION_POINTER_UP)
                        .setDownTime(downTime).setEventTime(eventTime).addPointer(0, x1, y1)
                        .addPointer(1, x2, y2));
            }
        }

        // Handle flip events
        if (s.indexOf(EVENT_KEYWORD_FLIP) >= 0 && args.length == 1) {
            boolean keyboardOpen = Boolean.parseBoolean(args[0]);
            MonkeyFlipEvent e = new MonkeyFlipEvent(keyboardOpen);
            mQ.addLast(e);
        }

        // Handle launch events
        if (s.indexOf(EVENT_KEYWORD_ACTIVITY) >= 0 && args.length >= 2) {
            String pkg_name = args[0];
            String cl_name = args[1];
            long alarmTime = 0;

            ComponentName mApp = new ComponentName(pkg_name, cl_name);

            if (args.length > 2) {
                try {
                    alarmTime = Long.parseLong(args[2]);
                } catch (NumberFormatException e) {
                    Logger.err.println("// " + e.toString());
                    return;
                }
            }

            if (args.length == 2) {
                MonkeyActivityEvent e = new MonkeyActivityEvent(mApp);
                mQ.addLast(e);
            } else {
                MonkeyActivityEvent e = new MonkeyActivityEvent(mApp, alarmTime);
                mQ.addLast(e);
            }
            return;
        }

        //Handle the device wake up event
        if (s.indexOf(EVENT_KEYWORD_DEVICE_WAKEUP) >= 0) {
            String pkg_name = "com.google.android.powerutil";
            String cl_name = "com.google.android.powerutil.WakeUpScreen";
            long deviceSleepTime = mDeviceSleepTime;

            //Start the wakeUpScreen test activity to turn off the screen.
            ComponentName mApp = new ComponentName(pkg_name, cl_name);
            mQ.addLast(new MonkeyActivityEvent(mApp, deviceSleepTime));

            //inject the special key for the wakeUpScreen test activity.
            mQ.addLast(new MonkeyKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_0));
            mQ.addLast(new MonkeyKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_0));

            //Add the wait event after the device sleep event so that the monkey
            //can continue after the device wake up.
            mQ.addLast(new MonkeyWaitEvent(deviceSleepTime + 3000));

            //Insert the menu key to unlock the screen
            mQ.addLast(new MonkeyKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MENU));
            mQ.addLast(new MonkeyKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MENU));

            //Insert the back key to dismiss the test activity
            mQ.addLast(new MonkeyKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
            mQ.addLast(new MonkeyKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));

            return;
        }

        // Handle launch instrumentation events
        if (s.indexOf(EVENT_KEYWORD_INSTRUMENTATION) >= 0 && args.length == 2) {
            String test_name = args[0];
            String runner_name = args[1];
            MonkeyInstrumentationEvent e = new MonkeyInstrumentationEvent(test_name, runner_name);
            mQ.addLast(e);
            return;
        }

        // Handle wait events
        if (s.indexOf(EVENT_KEYWORD_WAIT) >= 0 && args.length == 1) {
            try {
                long sleeptime = Integer.parseInt(args[0]);
                MonkeyWaitEvent e = new MonkeyWaitEvent(sleeptime);
                mQ.addLast(e);
            } catch (NumberFormatException e) {
            }
            return;
        }


        // Handle the profile wait time
        if (s.indexOf(EVENT_KEYWORD_PROFILE_WAIT) >= 0) {
            MonkeyWaitEvent e = new MonkeyWaitEvent(mProfileWaitTime);
            mQ.addLast(e);
            return;
        }

        // Handle keypress events
        if (s.indexOf(EVENT_KEYWORD_KEYPRESS) >= 0 && args.length == 1) {
            String key_name = args[0];
            int keyCode = MonkeySourceRandom.getKeyCode(key_name);
            if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
                return;
            }
            MonkeyKeyEvent e = new MonkeyKeyEvent(KeyEvent.ACTION_DOWN, keyCode);
            mQ.addLast(e);
            e = new MonkeyKeyEvent(KeyEvent.ACTION_UP, keyCode);
            mQ.addLast(e);
            return;
        }

        // Handle longpress events
        if (s.indexOf(EVENT_KEYWORD_LONGPRESS) >= 0) {
            MonkeyKeyEvent e;
            e = new MonkeyKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER);
            mQ.addLast(e);
            MonkeyWaitEvent we = new MonkeyWaitEvent(LONGPRESS_WAIT_TIME);
            mQ.addLast(we);
            e = new MonkeyKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER);
            mQ.addLast(e);
        }

        //The power log event is mainly for the automated power framework
        if (s.indexOf(EVENT_KEYWORD_POWERLOG) >= 0 && args.length > 0) {
            String power_log_type = args[0];
            String test_case_status;

            if (args.length == 1) {
                MonkeyPowerEvent e = new MonkeyPowerEvent(power_log_type);
                mQ.addLast(e);
            } else if (args.length == 2) {
                test_case_status = args[1];
                MonkeyPowerEvent e = new MonkeyPowerEvent(power_log_type, test_case_status);
                mQ.addLast(e);
            }
        }

        //Write power log to sdcard
        if (s.indexOf(EVENT_KEYWORD_WRITEPOWERLOG) >= 0) {
            MonkeyPowerEvent e = new MonkeyPowerEvent();
            mQ.addLast(e);
        }

        //Run the shell command
        if (s.indexOf(EVENT_KEYWORD_RUNCMD) >= 0 && args.length == 1) {
            String cmd = args[0];
            MonkeyCommandEvent e = new MonkeyCommandEvent(cmd);
            mQ.addLast(e);
        }

        //Input the string through the shell command
        if (s.indexOf(EVENT_KEYWORD_INPUT_STRING) >= 0 && args.length == 1) {
            String input = args[0];
            String cmd = "input text " + input;
            MonkeyCommandEvent e = new MonkeyCommandEvent(cmd);
            mQ.addLast(e);
            return;
        }

        if (s.indexOf(EVENT_KEYWORD_START_FRAMERATE_CAPTURE) >= 0) {
            MonkeyGetFrameRateEvent e = new MonkeyGetFrameRateEvent("start");
            mQ.addLast(e);
            return;
        }

        if (s.indexOf(EVENT_KEYWORD_END_FRAMERATE_CAPTURE) >= 0 && args.length == 1) {
            String input = args[0];
            MonkeyGetFrameRateEvent e = new MonkeyGetFrameRateEvent("end", input);
            mQ.addLast(e);
            return;
        }

        if (s.indexOf(EVENT_KEYWORD_START_APP_FRAMERATE_CAPTURE) >= 0 && args.length == 1) {
            String app = args[0];
            MonkeyGetAppFrameRateEvent e = new MonkeyGetAppFrameRateEvent("start", app);
            mQ.addLast(e);
            return;
        }

        if (s.indexOf(EVENT_KEYWORD_END_APP_FRAMERATE_CAPTURE) >= 0 && args.length == 2) {
            String app = args[0];
            String label = args[1];
            MonkeyGetAppFrameRateEvent e = new MonkeyGetAppFrameRateEvent("end", app, label);
            mQ.addLast(e);
            return;
        }


    }

    /**
     * Extracts an event and a list of arguments from a line. If the line does
     * not match the format required, it is ignored.
     * 用于解析一行内容的方法
     * @param line A string in the form {@code cmd(arg1,arg2,arg3)}.
     */
    private void processLine(String line) {
        int index1 = line.indexOf('('); //先找到第一个出现的（的位置
        int index2 = line.indexOf(')'); //再找到第一个出现的）的位置

        if (index1 < 0 || index2 < 0) {
            return;  //容错，一行中没有（，或者没有）
        }

        String[] args = line.substring(index1 + 1, index2).split(","); //先把（）中的字符串取出来，然后用，分隔成字符串数组

        for (int i = 0; i < args.length; i++) {
            args[i] = args[i].trim();// 遍历字符串数组的每个字符串，并且把空白字符都去除掉，再次交给同一个数组对象保存
        }

        handleEvent(line, args); //将整行内容与字符串数组（括号中的字符串）都传入进去，一个handleEvent（）的方法中
    }

    /**
     * Closes the script file.
     *
     * @throws IOException If there was an error closing the file.
     */
    private void closeFile() throws IOException {
        mFileOpened = false; //表示，文件关闭

        try {
            mFStream.close(); //关闭文件输入流，释放内存
            mInputStream.close(); //关闭字节输入流，释放内存
        } catch (NullPointerException e) { //出现对象没有的情况，啥也不干，这时候往往是文件没有打开过，根本不需要从内存中释放
            // File was never opened so it can't be closed.
        }
    }

    /**
     * Read next batch of events from the script file into the event queue.
     * Checks if the script is open and then reads the next MAX_ONE_TIME_READS
     * events or reads until the end of the file. If no events are read, then
     * the script is closed.
     *
     * @throws IOException If there was an error reading the file.
     */
    private void readNextBatch() throws IOException {
        int linesRead = 0; //表示读取了几行的局部变量

        if (THIS_DEBUG) { //只有debug时，才会输出这个日志
            Logger.out.println("readNextBatch(): reading next batch of events");
        }

        if (!mFileOpened) { //如果文件没有打开过……
            resetValue(); //重置所有值为初始值
            readHeader(); //读取文件的前面几行，跟检查事件源时调用的方法都一样
        }

        if (mReadScriptLineByLine) { //如果标记了一行一行的读取脚本
            linesRead = readOneLine();  //每次读取一行
        } else {
            linesRead = readLines(); //一下子读取所有行
        }

        if (linesRead == 0) { //当读取数量为0时
            closeFile();  //关闭文件
        }
    }

    /**
     * Sleep for a period of given time. Used to introduce latency between
     * events.
     *
     * @param time The amount of time to sleep in ms
     */
    private void needSleep(long time) {
        if (time < 1) {
            return;
        }
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
        }
    }

    /**
     * Checks if the file can be opened and if the header is valid.
     * 检查事件源是否有效，主要是检查脚本文件的开头
     *
     * @return True if the file exists and the header is valid, false otherwise.
     */
    @Override
    public boolean validate() {
        boolean validHeader; //标记是否事件源有效的方法
        try {
            validHeader = readHeader();  //读取文件的开头,保存结果到局部变量中
            closeFile(); //关闭文件，释放内存
        } catch (IOException e) {
            return false; //如果打开文件出现错误，即IOException，直接返回false，表示事件源都错误
        }

        if (mVerbose > 0) {
            Logger.out.println("Replaying " + mEventCountInScript + " events with speed " + mSpeed); //输出Replaying，要回放哪个脚本文件
        }
        return validHeader; //向Monkey主执行流返回检查结果
    }

    /**
     * 设置日志等级，可以用来控制日志的输出
     *
     * @param verbose output mode? 1= verbose, 2=very verbose
     */
    @Override
    public void setVerbose(int verbose) {
        mVerbose = verbose;
    }

    /**
     * Adjust key downtime and eventtime according to both recorded values and
     * current system time.
     *
     * @param e A KeyEvent
     */
    private void adjustKeyEventTime(MonkeyKeyEvent e) {
        if (e.getEventTime() < 0) {
            return;
        }
        long thisDownTime = 0;
        long thisEventTime = 0;
        long expectedDelay = 0;

        if (mLastRecordedEventTime <= 0) {
            // first time event
            thisDownTime = SystemClock.uptimeMillis();
            thisEventTime = thisDownTime;
        } else {
            if (e.getDownTime() != mLastRecordedDownTimeKey) {
                thisDownTime = e.getDownTime();
            } else {
                thisDownTime = mLastExportDownTimeKey;
            }
            expectedDelay = (long) ((e.getEventTime() - mLastRecordedEventTime) * mSpeed);
            thisEventTime = mLastExportEventTime + expectedDelay;
            // add sleep to simulate everything in recording
            needSleep(expectedDelay - SLEEP_COMPENSATE_DIFF);
        }
        mLastRecordedDownTimeKey = e.getDownTime();
        mLastRecordedEventTime = e.getEventTime();
        e.setDownTime(thisDownTime);
        e.setEventTime(thisEventTime);
        mLastExportDownTimeKey = thisDownTime;
        mLastExportEventTime = thisEventTime;
    }

    /**
     * Adjust motion downtime and eventtime according to current system time.
     *
     * @param e A MotionEvent
     */
    private void adjustMotionEventTime(MonkeyMotionEvent e) {
        long thisEventTime = SystemClock.uptimeMillis();
        long thisDownTime = e.getDownTime();

        if (thisDownTime == mLastRecordedDownTimeMotion) {
            // this event is the same batch as previous one
            e.setDownTime(mLastExportDownTimeMotion);
        } else {
            // this event is the start of a new batch
            mLastRecordedDownTimeMotion = thisDownTime;
            // update down time to match current time
            e.setDownTime(thisEventTime);
            mLastExportDownTimeMotion = thisEventTime;
        }
        // always refresh event time
        e.setEventTime(thisEventTime);
    }

    /**
     * Gets the next event to be injected from the script. If the event queue is
     * empty, reads the next n events from the script into the queue, where n is
     * the lesser of the number of remaining events and the value specified by
     * MAX_ONE_TIME_READS. If the end of the file is reached, no events are
     * added to the queue and null is returned.
     *
     * @return The first event in the event queue or null if the end of the file
     * is reached or if an error is encountered reading the file.
     */
    @Override
    public MonkeyEvent getNextEvent() {
        long recordedEventTime = -1; //记录事件的触发时间
        MonkeyEvent ev;

        if (mQ.isEmpty()) { //只有双向链表为空时，说明没有事件了，需要构造事件
            try {
                readNextBatch();
            } catch (IOException e) {
                return null;
            }
        }

        try {
            ev = mQ.getFirst();
            mQ.removeFirst();
        } catch (NoSuchElementException e) {
            return null;
        }

        if (ev.getEventType() == MonkeyEvent.EVENT_TYPE_KEY) {
            adjustKeyEventTime((MonkeyKeyEvent) ev);
        } else if (ev.getEventType() == MonkeyEvent.EVENT_TYPE_TOUCH
                || ev.getEventType() == MonkeyEvent.EVENT_TYPE_TRACKBALL) {
            adjustMotionEventTime((MonkeyMotionEvent) ev);
        }
        return ev;
    }
}
