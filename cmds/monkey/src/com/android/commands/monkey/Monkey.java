/*
 * Copyright 2007, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.commands.monkey;

import android.app.ActivityManager;
import android.app.IActivityController;
import android.app.IActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.IPackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Debug;
import android.os.Environment;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.StrictMode;
import android.os.SystemClock;
import android.view.IWindowManager;
import android.view.Surface;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Application that injects random key events and other actions into the system.
 * 向系统中注入随机键事件和其他操作的应用程序，写的牛逼
 */
public class Monkey {

    /**
     * Monkey Debugging/Dev Support
     * <p>
     * All values should be zero when checking in.
     *
     */
    private final static int DEBUG_ALLOW_ANY_STARTS = 0; //允许的调试选项?

    private final static int DEBUG_ALLOW_ANY_RESTARTS = 0; //允许的调试选项？

    private IActivityManager mAm; //使用AMS服务,IActivityManager封装AMS提供哪些服务，一个IActivityManager对象，所有实现该接口的对象均可

    private IWindowManager mWm; //使用WMS服务，IWindowManager规定了WMS提供了哪些服务

    private IPackageManager mPm; //使用PMS服务，IPackageManager规定了PMS提供哪些服务

    /** Command line arguments */
    private String[] mArgs; //命令行参数

    /** Current argument being parsed */
    private int mNextArg; //用于指向数组中的某个命令行参数，第一个命令行参数的下标是0

    /** Data of current argument */
    private String mCurArgData;

    /** Running in verbose output mode? 1= verbose, 2=very verbose */
    private int mVerbose; //日志等级：1、verbose 2、very verbose 3、very very verbose

    /** Ignore any application crashes while running? */
    private boolean mIgnoreCrashes; //是否忽略App层的崩溃，不然monkey进程会停止

    /** Ignore any not responding timeouts while running? */
    private boolean mIgnoreTimeouts; //是否忽略运行超时？原来是ANR的选项

    /** Ignore security exceptions when launching activities */
    /** (The activity launch still fails, but we keep pluggin' away) */
    private boolean mIgnoreSecurityExceptions; //是否忽略安全异常?

    /** Monitor /data/tombstones and stop the monkey if new files appear. */
    private boolean mMonitorNativeCrashes; //是否需要监控/data/tombstones目录（监控native异常）

    /** Ignore any native crashes while running? */
    private boolean mIgnoreNativeCrashes; //忽略任何native异常

    /** Send no events. Use with long throttle-time to watch user operations */
    private boolean mSendNoEvents;

    /** This is set when we would like to abort the running of the monkey. */
    private boolean mAbort; //标记monkey进程,程序是否中断

    /**
     * Count each event as a cycle. Set to false for scripts so that each time
     * through the script increments the count.
     */
    private boolean mCountEvents = true; //是否计算循环执行事件的次数

    /**
     * This is set by the ActivityController thread to request collection of ANR
     * trace files
     */
    private boolean mRequestAnrTraces = false; //是否需要ANR的trace文件

    /**
     * This is set by the ActivityController thread to request a
     * "dumpsys meminfo"
     */
    private boolean mRequestDumpsysMemInfo = false; //是否需要内存信息

    /**
     * This is set by the ActivityController thread to request a
     * bugreport after ANR
     */
    private boolean mRequestAnrBugreport = false; //是否需要anr的报告

    /**
     * This is set by the ActivityController thread to request a
     * bugreport after a system watchdog report
     */
    private boolean mRequestWatchdogBugreport = false; //是否需要wathdog报告

    /**
     * Synchronization for the ActivityController callback to block
     * until we are done handling the reporting of the watchdog error.
     */
    private boolean mWatchdogWaiting = false; //是否需要线程等待watchdog报告的完成（同步要求）

    /**
     * This is set by the ActivityController thread to request a
     * bugreport after java application crash
     */
    private boolean mRequestAppCrashBugreport = false; //是否需要app崩溃的报告

    /**Request the bugreport based on the mBugreportFrequency. */
    private boolean mGetPeriodicBugreport = false; //

    /**
     * Request the bugreport based on the mBugreportFrequency.
     */
    private boolean mRequestPeriodicBugreport = false;

    /** Bugreport frequency. */
    private long mBugreportFrequency = 10;

    /** Failure process name */
    private String mReportProcessName; //用于存储上报进程的名字

    /**
     * This is set by the ActivityController thread to request a "procrank"
     */
    private boolean mRequestProcRank = false;

    /** Kill the process after a timeout or crash. */
    private boolean mKillProcessAfterError; //用于标记是否再ANR错误后，干掉进程（不然重启）

    /** Generate hprof reports before/after monkey runs */
    private boolean mGenerateHprof;

    /** If set, only match error if this text appears in the description text. */
    private String mMatchDescription; //如果在命令行设置了这个，表示包含内容的才做？

    /** Package denylist file. */
    private String mPkgBlacklistFile;

    /** Package allowlist file. */
    private String mPkgWhitelistFile; //用于持有白名单文件名

    /** Categories we are allowed to launch **/
    private ArrayList<String> mMainCategories = new ArrayList<String>(); //分类用的list对象

    /** Applications we can switch to. */
    private ArrayList<ComponentName> mMainApps = new ArrayList<ComponentName>(); //存储组件名的list对象（app），这些都是被成功启动Activity的信息

    /** The delay between event inputs **/
    long mThrottle = 0; //事件的延迟时间

    /** Whether to randomize each throttle (0-mThrottle ms) inserted between events. */
    boolean mRandomizeThrottle = false; //是否需要0-xx毫秒的随机延迟时间

    /** The number of iterations **/
    int mCount = 1000; //默认的事件数量

    /** The random number seed **/
    long mSeed = 0; //随机种子值

    /** The random number generator **/
    Random mRandom = null; //持有的Random对象

    /** Dropped-event statistics **/
    long mDroppedKeyEvents = 0;

    long mDroppedPointerEvents = 0;

    long mDroppedTrackballEvents = 0;

    long mDroppedFlipEvents = 0;

    long mDroppedRotationEvents = 0;

    /** The delay between user actions. This is for the scripted monkey. **/
    long mProfileWaitTime = 5000;

    /** Device idle time. This is for the scripted monkey. **/
    long mDeviceSleepTime = 30000;

    boolean mRandomizeScript = false;

    boolean mScriptLog = false;

    /** Capture bugreprot whenever there is a crash. **/
    private boolean mRequestBugreport = false;

    /** a filename to the setup script (if any) */
    private String mSetupFileName = null;

    /** filenames of the script (if any) */
    private ArrayList<String> mScriptFileNames = new ArrayList<String>();

    /** a TCP port to listen on for remote commands. */
    private int mServerPort = -1;

    private static final File TOMBSTONES_PATH = new File("/data/tombstones"); //native崩溃日志目录

    private static final String TOMBSTONE_PREFIX = "tombstone_";

    private static int NUM_READ_TOMBSTONE_RETRIES = 5; //表示tombsone文件是否正在写入的重试次数

    private HashSet<Long> mTombstones = null; //持有的用于记录native崩溃文件的情况，Long是tonmb文件的修改时间，在内存中持有对象，当然是为了记录了，赞

    float[] mFactors = new float[MonkeySourceRandom.FACTORZ_COUNT]; //创建一个float数组对象，存放12个元素，每个元素值表示某个事件的比例，不同的下标代表不同的事件类型

    MonkeyEventSource mEventSource; //持有的MonkeyEventSource对象（实际对象为子类对象，即MonkeySourceNetwork……

    private MonkeyNetworkMonitor mNetworkMonitor = new MonkeyNetworkMonitor(); //持有的MonkeyNetworkMonitor对象，用于监控网络

    private boolean mPermissionTargetSystem = false;

    // information on the current activity.
    public static Intent currentIntent; //Monkey类持有的currentIntent，表示当前发出的Intent（表示要启动哪个Activity)

    public static String currentPackage; //Monkey类持有的currentPackage，表示当前正在操作的包名（应用）

    /**
     * Monitor operations happening in the system. //Binder对象
     * ActivityManagerService系统服务控制的Binder对象，这些方法会被AMS系统服务调用（单独线程中回调）
     */
    private class ActivityController extends IActivityController.Stub {
        /**
         *  当某个Activity启动时，AMS系统服务会回调此方法（这个方法在哪个线程中执行？）
         * @param intent 启动Activity时的Intent对象（可序列号）
         * @param pkg 启动的包名（可序列号）
         * @return 是否允许启动Activity，靠，难道这里能控制AMS的行为，还真的能控制……
         */
        public boolean activityStarting(Intent intent, String pkg) {
            final boolean allow = isActivityStartingAllowed(intent, pkg); //allow表示是否可以启动Activity
            if (mVerbose > 0) {
                // StrictMode's disk checks end up catching this on
                // userdebug/eng builds due to PrintStream going to a
                // FileOutputStream in the end (perhaps only when
                // redirected to a file?)  So we allow disk writes
                // around this region for the monkey to minimize
                // harmless dropbox uploads from monkeys.
                StrictMode.ThreadPolicy savedPolicy = StrictMode.allowThreadDiskWrites();
                Logger.out.println("    // " + (allow ? "Allowing" : "Rejecting") + " start of "
                        + intent + " in package " + pkg);
                StrictMode.setThreadPolicy(savedPolicy);
            }
            currentPackage = pkg; //将AMS启动的包名保存到currentPackage中，Monkey即可知道正在启动的是哪个应用（个别需求会用到）
            currentIntent = intent; //将启动Activity的Intent对象也保存到这里一个，Monkey即可知道目前启动的Activity，用的哪个Intent（个别需求会用到）
            return allow; //返回值表示是否允许启动Activity
        }

        /**
         *  用于检查Activity是否允许启动，最终的结果会被AMS采纳
         * @param intent 传入的Intent对象
         * @param pkg 传入包名
         * @return
         */
        private boolean isActivityStartingAllowed(Intent intent, String pkg) {
            if (MonkeyUtils.getPackageFilter().checkEnteringPackage(pkg)) {  //把包名传入进去，由PackagerFilter对象的checkEnteringPackage()方法检查应用能否启动
                return true; //向调用者返回true，表示可以启动
            }
            if (DEBUG_ALLOW_ANY_STARTS != 0) { //当开启了允许调试
                return true;  //向调用者返回的为一直允许启动
            }
            // In case the activity is launching home and the default launcher
            // package is disabled, allow anyway to prevent ANR (see b/38121026)
            final Set<String> categories = intent.getCategories(); //获得Intent对象持有的所有Category，每个Category是个字符串，存放在一个集合对象中
            if (intent.getAction() == Intent.ACTION_MAIN //但Intent的action等于ACTION_MAIN,且存在Cetegory，且Category包含CATEGORY_HOME
                    && categories != null
                    && categories.contains(Intent.CATEGORY_HOME)) {
                try {
                    final ResolveInfo resolveInfo =
                            mPm.resolveIntent(intent, intent.getType(), 0,
                                    ActivityManager.getCurrentUser()); //通过PMS系统服务，获取当前用户权限内，符合Intent对象条件的ResolveInfo对象
                    final String launcherPackage = resolveInfo.activityInfo.packageName; //获取Launcher的包名
                    if (pkg.equals(launcherPackage)) {
                        return true;
                    } //如果应用是Launcher应用，那必须可以启动此Activity，返回true
                } catch (RemoteException e) {
                    Logger.err.println("** Failed talking with package manager!"); //当PMS系统服务出错，走这里
                    return false;
                }
            }
            return false; //其他情况下，不允许启动，就直接返回false了
        }

        /**
         * 当一个Activity准备好，AMS会回调此方法
         * @param pkg
         * @return
         */
        public boolean activityResuming(String pkg) {
            StrictMode.ThreadPolicy savedPolicy = StrictMode.allowThreadDiskWrites(); //与严格模式有关……
            Logger.out.println("    // activityResuming(" + pkg + ")");  //输出日志
            boolean allow = MonkeyUtils.getPackageFilter().checkEnteringPackage(pkg)
                    || (DEBUG_ALLOW_ANY_RESTARTS != 0); //获取是否为允许启动的应用
            if (!allow) { //如果是不同意启动的应用
                if (mVerbose > 0) { //只输出一行日志……
                    Logger.out.println("    // " + (allow ? "Allowing" : "Rejecting")
                            + " resume of package " + pkg);
                }
            }
            currentPackage = pkg;//记录当前屏幕中启动应用的包名
            StrictMode.setThreadPolicy(savedPolicy); //还是严格模式
            return allow; //返回Activity的是否允许启动……
        }

        /**
         * 出现App崩溃时，AMS系统服务会回调此方法，此方法运行在当前Monkey进程的binder线程池中，AMS牛逼，知道哪个进程崩溃了（看袁辉辉大佬的解读）
         * @param processName 进程名
         * @param pid 进程的pid
         * @param shortMsg 短的堆栈信息
         * @param longMsg 长的堆栈信息
         * @param timeMillis 时间戳
         * @param stackTrace 堆栈信息
         * @return 当返回true时，表示可以重启进程，当返回false时，表示立即杀死它（进程）AMS来控制
         */
        public boolean appCrashed(String processName, int pid,
                String shortMsg, String longMsg,
                long timeMillis, String stackTrace) {
            StrictMode.ThreadPolicy savedPolicy = StrictMode.allowThreadDiskWrites(); //
            Logger.err.println("// CRASH: " + processName + " (pid " + pid + ")"); //向标准错误流中输出日志（binder线程）
            Logger.err.println("// Short Msg: " + shortMsg);
            Logger.err.println("// Long Msg: " + longMsg);
            Logger.err.println("// Build Label: " + Build.FINGERPRINT);
            Logger.err.println("// Build Changelist: " + Build.VERSION.INCREMENTAL);
            Logger.err.println("// Build Time: " + Build.TIME);
            Logger.err.println("// " + stackTrace.replace("\n", "\n// "));
            StrictMode.setThreadPolicy(savedPolicy); //严格模式

            if (mMatchDescription == null
                    || shortMsg.contains(mMatchDescription)
                    || longMsg.contains(mMatchDescription)
                    || stackTrace.contains(mMatchDescription)) {
                if (!mIgnoreCrashes || mRequestBugreport) { //如果没有设置忽略崩溃，或者需要请求bugreport，会走这里
                    synchronized (Monkey.this) { //线程间同步，appCrashed方法在Monkey进程自己的Binder线程池里运行，这样Binder线程池里的线程会与Monkey的主线程竞争同一个对象锁
                                                 //Monkey主线程，每循环一次才释放一次Monkey对象锁，如果Monkey主线程一直持有的Monkey对象不放，则Binder线程池里的线程会一直被阻塞，等待这个Monkey对象锁
                        if (!mIgnoreCrashes) { //如果没有设置忽略崩溃的选项
                            mAbort = true; //设置Monkey进程会被中断的标志位
                        }
                        if (mRequestBugreport){ //如果用户设置了需要崩溃报告
                            mRequestAppCrashBugreport = true; //设置需要上报App崩溃的标志位，monkey主进程会在循环中读取这个值
                            mReportProcessName = processName; //设置需要上报的进程名字
                        }
                    } //这里，Binder线程池中的线程，会释放对象锁，Monkey主进程会继续执行（用对象锁，做的线程间同步）
                    return !mKillProcessAfterError; //这个值，是给AMS用的呀……默认值一定返回的是true啊，出现ANR，要求系统重启进程……
                }
            }
            return false; //当一个App进程出现崩溃后触发，当返回true时，表示可以重启进程，当返回false时，表示立即杀死它（进程）
        }

        /**
         * 当一鉴定为ANR时就很早触发（AMS用来鉴定ANR，袁辉辉那里讲的）
         * 鉴定为ANR时会触发？Early是什么意思？
         * @param processName 进程名
         * @param pid 进程id
         * @param annotation 描述？
         * @return 返回一个0
         */
        public int appEarlyNotResponding(String processName, int pid, String annotation) {
            return 0;
        }

        /**
         * app发生ANR时，AMS回调此方法，此方法在Monkey进程的某个binder线程池中的某个线程中执行
         * @param processName 进程名
         * @param pid 进程id
         * @param processStats 进程状态
         * @return 当返回0时，表示弹出应用无响应的dialog，如果返回1时，表示继续等待，如果返回-1时，表示立即杀死进程
         * 当一个应用进程出现ANR时会触发，卧槽，这个API我发现是给Setting App准备的吧，我算明白了……
         */
        public int appNotResponding(String processName, int pid, String processStats) {
            StrictMode.ThreadPolicy savedPolicy = StrictMode.allowThreadDiskWrites();
            Logger.err.println("// NOT RESPONDING: " + processName + " (pid " + pid + ")");
            Logger.err.println(processStats);
            StrictMode.setThreadPolicy(savedPolicy);

            if (mMatchDescription == null || processStats.contains(mMatchDescription)) {
                synchronized (Monkey.this) {
                    mRequestAnrTraces = true;
                    mRequestDumpsysMemInfo = true;
                    mRequestProcRank = true;
                    if (mRequestBugreport) {
                        mRequestAnrBugreport = true;
                        mReportProcessName = processName;
                    }
                }
                if (!mIgnoreTimeouts) { //如果没有设置忽略超时，原来ANR也得设置，卧槽，我设置没有？
                    synchronized (Monkey.this) { //与monkey主线程竞争Monkey对象锁，binder线程可能会阻塞在这里（那样AMS中的binder线程也会被阻塞吧，没错，看来Monkey主线程不能太累，会影响System_Server的执行（见袁辉辉篇)
                        mAbort = true; //Monkey程序是否中断的标志位
                    }
                }
            }

            return (mKillProcessAfterError) ? -1 : 1;
        }

        /**
         * 当系统看门狗监测到系统挂了会触发该方法
         * 系统没响应时，AMS会回调此方法
         * @param message 没响应的原因
         * @return 返回的数字，表示退出状态码
         * 如果返回1，表示继续等待，返回-1，表示系统正常自杀（这里的正常自杀，系统自己主动自杀，该保存的数据先保存好，然后自杀，并不是因为其他原因导致的自杀）
         */
        public int systemNotResponding(String message) {
            StrictMode.ThreadPolicy savedPolicy = StrictMode.allowThreadDiskWrites();
            Logger.err.println("// WATCHDOG: " + message);
            StrictMode.setThreadPolicy(savedPolicy);

            synchronized (Monkey.this) {
                if (mMatchDescription == null || message.contains(mMatchDescription)) {
                    if (!mIgnoreCrashes) {
                        mAbort = true;
                    }
                    if (mRequestBugreport) {
                        mRequestWatchdogBugreport = true;
                    }
                }
                mWatchdogWaiting = true;
            }
            synchronized (Monkey.this) {
                while (mWatchdogWaiting) {
                    try {
                        Monkey.this.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
            return (mKillProcessAfterError) ? -1 : 1;
        }
    }

    /**
     * Run the procrank tool to insert system status information into the debug
     * report.
     * 文件报告名为procrank
     * 工具名也为procrank
     */
    private void reportProcRank() {
        commandLineReport("procrank", "procrank");
    }

    /**
     * Dump the most recent ANR trace. Wait about 5 seconds first, to let the
     * asynchronous report writing complete.
     * 生成最近的ANR trace，先等5秒，让异步报告先写入完成（Monkey主线程会等待5秒）
     */
    private void reportAnrTraces() {
        try {
            Thread.sleep(5 * 1000);
        } catch (InterruptedException e) {
        }

        // The /data/anr directory might have multiple files, dump the most
        // recent of those files.
        File[] recentTraces = new File("/data/anr/").listFiles(); //先获取/data/anr/下的所有目录与文件，listFiles（）返回的是File数组对象
        if (recentTraces != null) { //当确实存在文件
            File mostRecent = null;
            long mostRecentMtime = 0;
            for (File trace : recentTraces) { //遍历每一个文件
                final long mtime = trace.lastModified(); //获取上一次的修改时间
                if (mtime > mostRecentMtime) {
                    mostRecentMtime = mtime;
                    mostRecent = trace;
                }
            }

            if (mostRecent != null) {
                commandLineReport("anr traces", "cat " + mostRecent.getAbsolutePath()); //竟然使用的是cat命令，读取到文件的内存，写入到anr trace文件中……
            }
        }
    }

    /**
     * Run "dumpsys meminfo"
     * <p>
     * NOTE: You cannot perform a dumpsys call from the ActivityController
     * callback, as it will deadlock. This should only be called from the main
     * loop of the monkey.
     * 报告名称meminfo
     * 可执行文件为dumpsys meminfo
     */
    private void reportDumpsysMemInfo() {
        commandLineReport("meminfo", "dumpsys meminfo");
    }

    /**
     * Print report from a single command line.
     * 从单个命令行中的命令执行，并输出一个持久化的报告
     * <p>
     * TODO: Use ProcessBuilder & redirectErrorStream(true) to capture both
     * streams (might be important for some command lines)
     * 在此方法中会创建一个子进程，且执行的线程（多数是Monkey主线程）会等待子进程完成工作后才会继续执行，这又涉及到进程间同步的知识点
     * @param reportName Simple tag that will print before the report and in
     *            various annotations. 报告名称（持久化文件）
     * @param command Command line to execute. 调用的可执行文件
     */
    private void commandLineReport(String reportName, String command) {
        Logger.err.println(reportName + ":"); //向标准错误流中输入报告名和一个冒号
        Runtime rt = Runtime.getRuntime(); //获取运行时对象，但是却没有使用这个对象，不应该啊？大佬也会犯错？
        Writer logOutput = null; //输出字符流对象（由内存到磁盘时使用）

        try {
            // Process must be fully qualified here because android.os.Process
            // is used elsewhere 这段话该怎么翻译？
            java.lang.Process p = Runtime.getRuntime().exec(command); //替换命令，在新的进程中执行某个程序，返回一个Process对象表示子进程

            if (mRequestBugreport) { //检查命令行参数中是否传入了需要使用bugreport
                logOutput =
                        new BufferedWriter(new FileWriter(new File(Environment //创建可缓存的输出字符流
                                .getLegacyExternalStorageDirectory(), reportName), true)); //创建一个通道，从内存中向reportName命名的文件中写入日志
            }
            // pipe everything from process stdout -> System.err，管道通常从进程的标准输出到标准错误？
            InputStream inStream = p.getInputStream(); //获取子进程的标准输入流对象（向内存读取内容）……，我猜测Monkey主进程会等待子进程完成工作
            InputStreamReader inReader = new InputStreamReader(inStream); //将输入字节流，转到成输入字符流
            BufferedReader inBuffer = new BufferedReader(inReader); //在内存中创建一个BufferedReader对象作为缓冲区，用于缓存字符串流中的数据
            String s; //创建一个String局部变量
            while ((s = inBuffer.readLine()) != null) { //一行一行的读缓冲区中的数据，知道没有数据为止
                if (mRequestBugreport) { //如果需要执行Bugreport命令保存到文件中，命令行控制
                    try {
                        // When no space left on the device the write will 如果没有磁盘控件，将会报IOException
                        // occurs an I/O exception, so we needed to catch it
                        // and continue to read the data of the sync pipe to
                        // aviod the bugreport hang forever.
                        logOutput.write(s); //把从子进程中执行的bugreport程序中的获取到的内容，从内存写入到文件中
                        logOutput.write("\n"); //每写完一行字符串，再写个换行符
                    } catch (IOException e) { //发生IO异常，直接捕获（磁盘没有空间时，会这样）
                        while(inBuffer.readLine() != null) {} //处理也比较粗暴，先不停的一行一行的读取，直到没有内容……没有磁盘空间，也要在内存中把数据处理完
                        Logger.err.println(e.toString()); //最后在monkey主线程会在标准错误流中打印异常的堆栈信息
                        break;
                    }
                } else {
                    Logger.err.println(s); //这里不需要将bugreport的内容保存在文件中，只是在monkey的标准错误流中输出
                }
            } //当读取完子进程的标准输出，或者磁盘没有空间，循环结束

            int status = p.waitFor(); //在这里，Monkey主进程（主线程）会做等待，等待子进程执行的命令行程序（可执行文件）当然不仅仅是bugreport（进程间同步），还要获取子进程的退出状态码
            Logger.err.println("// " + reportName + " status was " + status); //子进程完成工作后，Monkey向标准错误流打印日志，以及打印子进程的退出状态码

            if (logOutput != null) { //如果存在输出字符流对象
                logOutput.close(); //关闭输出字符串流对象（释放内存）
            }
        } catch (Exception e) { //捕获到任何其他的异常
            Logger.err.println("// Exception from " + reportName + ":");
            Logger.err.println(e.toString()); //直接到标准错误流中输出内容
        }
    }

    // Write the numbe of iteration to the log

    /**
     * 当没有提取到事件时，会走这个方法，这是用于调试的文件撒
     * @param count 表示事件的数量
     */
    private void writeScriptLog(int count) {
        // TO DO: Add the script file name to the log.
        try {
            Writer output = new BufferedWriter(new FileWriter(new File(
                    Environment.getLegacyExternalStorageDirectory(), "scriptlog.txt"), true)); //有一个scriptlog.txt文件，看下这是哪个目录，getLegacyExternalStorageDirectory，原来时/mnt/sdcard/目录，发现和/sdcard是同一个目录
            output.write("iteration: " + count + " time: "
                    + MonkeyUtils.toCalendarTime(System.currentTimeMillis()) + "\n");
            output.close();
        } catch (IOException e) {
            Logger.err.println(e.toString());
        }
    }

    // Write the bugreport to the sdcard，把bug report存储到SDk卡

    /**
     *
     * @param reportName 生成一个带有时间的报告的名字
     *  执行命令行中的命令，然后生成一份持久化的报告
     */
    private void getBugreport(String reportName) {
        reportName += MonkeyUtils.toCalendarTime(System.currentTimeMillis()); //再将文件名处增加一个生成的时间
        String bugreportName = reportName.replaceAll("[ ,:]", "_"); //把所有的空格字符、逗号、冒号，全部替换成下划线_
        commandLineReport(bugreportName + ".txt", "bugreport"); //使用shell命令，生成文件（在进程中运行）
    }

    /**
     * Command-line entry point.
     * Monkey的主线程入口函数，看看大佬的monkey怎么写的……
     * @param args The command-line arguments 表示命令行参数
     */
    public static void main(String[] args) {
        // Set the process name showing in "ps" or "top"
        Process.setArgV0("com.android.commands.monkey"); //修改monkey程序的进程名称

        Logger.err.println("args: " + Arrays.toString(args)); //向标准错误流，输出命令行参数信息
        int resultCode = (new Monkey()).run(args); //创建Monkey对象,调用run（）方法，将数组对象（命令行参数）传进去，退出状态码会保存在resultCode中
        System.exit(resultCode); //退出虚拟机进程，返回退出状态码，进程结束
    }

    /**
     * Run the command!
     * 执行monkey命令
     * @param args The command-line arguments 命令行参数
     * @return Returns a posix-style result code. 0 for no error. 返回一个退出状态码，0表示没有错误
     */
    private int run(String[] args) {
        // Super-early debugger wait
        for (String s : args) { //遍历所有的命令行参数，目的是为了查找--wait-dbg
            if ("--wait-dbg".equals(s)) {
                Debug.waitForDebugger(); //包含--wait-dbg参数时，执行Debugger操作
            }
        }

        // Default values for some command-line options 为Monkey对象持有的命令行选项赋初始值（将命令行参数保存到内存中，Monkey对象中）
        mVerbose = 0; //日志等级的默认值为0
        mCount = 1000; //次数初始值更新为1000
        mSeed = 0; //随机种子初始值更新为0
        mThrottle = 0; //延迟时间默认为0

        // prepare for command-line processing
        mArgs = args; //由实例变量mArgs开始持有包含的所有命令行参数，它是一个String数组对象
        for (String a: args) { //遍历所有命令行参数
            Logger.err.println(" arg: \"" + a + "\""); //遍历所有命令行参数，向标准错误流中输出
        }
        mNextArg = 0; //用于指向某个命令行参数，默认指向第一个命令行参数，保存的是数组的下标

        // set a positive value, indicating none of the factors is provided yet 数组赋初始值
        for (int i = 0; i < MonkeySourceRandom.FACTORZ_COUNT; i++) { //mFactors数组一共12个元素，这里遍历所有元素
            mFactors[i] = 1.0f; //为数组对象mFactors中的每个元素都赋值为1.0f，下标0-11
        }

        if (!processOptions()) { //处理所有的命令行参数，这是通过调用processOptions()函数，此时Monkey对象已经持有所有的命令行参数
            return -1; //如果命令行参数发生错误，返回退出状态码-1，这个退出状态码，shell可以拿到
        }

        if (!loadPackageLists()) { //检查并处理文件中持久的包名（白名单文件、黑名单文件）看来除了命令行指定包名，还可以指定文件
            return -1;
        }

        // now set up additional data in preparation for launch 用于操作Launch……
        if (mMainCategories.size() == 0) { //第一次会添加两个元素，都是Categoryies需要使用的……
            mMainCategories.add(Intent.CATEGORY_LAUNCHER);
            mMainCategories.add(Intent.CATEGORY_MONKEY); //说明你可以自己写一个Category为Monkey的Activity供启动？
        }

        if (mSeed == 0) { //随机种子没有设置时，使用时间戳+当前对象的hashCode值相加得到一个新的随机种子值
            mSeed = System.currentTimeMillis() + System.identityHashCode(this);
        }

        if (mVerbose > 0) {  //如果设置-v ,Logger是自己封装的Log工具类
            Logger.out.println(":Monkey: seed=" + mSeed + " count=" + mCount);
            MonkeyUtils.getPackageFilter().dump(); //输出一次设置的有效包名、无效包名
            if (mMainCategories.size() != 0) {
                Iterator<String> it = mMainCategories.iterator();
                while (it.hasNext()) { //遍历ArrayList
                    Logger.out.println(":IncludeCategory: " + it.next()); //打印包含的主要Category
                }
            }
        }

        if (!checkInternalConfiguration()) { //目前还没有实现这个功能，预留方法
            return -2; //内部配置出错，会返回-2
        }

        if (!getSystemInterfaces()) { //检查并初始化系统服务，这里非常重要，Monkey程序依赖系统服务的远程Binder，完成工作
            return -3; //系统服务出错，会返回-3
        }

        if (!getMainApps()) { //查找可启动的主Activity
            return -4; //没有找到可用的主Activity，返回-4
        }

        mRandom = new Random(mSeed); //创建Random对象……随机种子传给它,伪随机……

        //初始化Monkey对象持有的mEventSource，注意会优先走脚本文件、然后是网络、最后才是命令行的方式

        if (mScriptFileNames != null && mScriptFileNames.size() == 1) { //当指定1个脚本文件时，会走这里
            // script mode, ignore other options
            mEventSource = new MonkeySourceScript(mRandom, mScriptFileNames.get(0), mThrottle,
                    mRandomizeThrottle, mProfileWaitTime, mDeviceSleepTime); //创建MonkeySourceScript对象，mEventSource指向此对象
            mEventSource.setVerbose(mVerbose); //设置MonkeySourceScript的监控等级，同Monkey对象持有的mVerbose保持一致

            mCountEvents = false; //无需计算事件的次数
        } else if (mScriptFileNames != null && mScriptFileNames.size() > 1) { //当指定多个脚本文件时，会走这里
            if (mSetupFileName != null) { //如果指定了初始化脚本文件，通过--setup选项参数可指定
                mEventSource = new MonkeySourceRandomScript(mSetupFileName, //可见第一个参数就是mSetupFileName文件名
                        mScriptFileNames, mThrottle, mRandomizeThrottle, mRandom,
                        mProfileWaitTime, mDeviceSleepTime, mRandomizeScript);//创建一个MonkeySourceRandomScript对象，可以用来选择多个脚本文件中的一个
                mCount++; //抽空再看这里的代码，这里为何mCount需要+1，
            } else { //没有设置--setup的初始化脚本文件
                mEventSource = new MonkeySourceRandomScript(mScriptFileNames,
                        mThrottle, mRandomizeThrottle, mRandom,
                        mProfileWaitTime, mDeviceSleepTime, mRandomizeScript); //同样创建一个MonkeySourceRandomScript
            }
            mEventSource.setVerbose(mVerbose); //设置事件来源的日志等级（保持与Monkey中一样的等级）
            mCountEvents = false; //无需计算事件数量
        } else if (mServerPort != -1) { //TCP……，基于网络，mServerPort指定了一个端口
            try {
                mEventSource = new MonkeySourceNetwork(mServerPort); //创建MonkeySourceNetwork对象，事件源再次改变
            } catch (IOException e) {
                Logger.out.println("Error binding to network socket."); //表示无法绑定某个端口
                return -5; //返回退出状态为-5
            }
            mCount = Integer.MAX_VALUE; //直接将事件数量设置为最大值
        } else { //没有脚本文件、没有基于网络、当基于命令行参数时，走这里，它的优先级最低
            // random source by default
            if (mVerbose >= 2) { // check seeding performance
                Logger.out.println("// Seeded: " + mSeed); //向标准输出流输出随机种子数，前提是日志等级大于2
            }
            mEventSource = new MonkeySourceRandom(mRandom, mMainApps,
                    mThrottle, mRandomizeThrottle, mPermissionTargetSystem); //创建MonkeySourceRandom对象，看见了吗，将获取到的可用的Activity组件对象mMainApps，传了进去
            mEventSource.setVerbose(mVerbose); //将命令行中解析的日志等级同样赋值给MonkeySourceRandom对象
            // set any of the factors that has been set
            // 遍历Monkey对象持有的数组对象mFactors，如果发现元素值是负数，说明是用户设置的，就把该值赋值给MonkeySourceRandom对象持有的数组对象mFactors（同名，尴尬）
            for (int i = 0; i < MonkeySourceRandom.FACTORZ_COUNT; i++) {
                if (mFactors[i] <= 0.0f) { //遍历Monkey对象持有的数组对象mFactors
                    ((MonkeySourceRandom) mEventSource).setFactors(i, mFactors[i]); //将命令行中的指定的事件存放到MonkeySourceRandom对象持有的数组对象中
                    //只有用户指定的事件比例是个小于0的数字，这下子MonkeySourceRandom已经保存上用户需要事件比例了
                }
            }

            // in random mode, we start with a random activity，随机模式中，建立随机的Activity
            ((MonkeySourceRandom) mEventSource).generateActivity(); //生成Activity事件（首先启动Activity，这个没毛病）
        }

        // validate source generator 检查事件比例，如果使用的是MoneySourceRandom，则这里会走MonkeySourceRandom的计算规则（此规则定义在接口MonkeyEventSource，不同的事件源可以修改计算规则）
        if (!mEventSource.validate()) {
            return -5; //事件比例错误，直接返回退出状态码为-5
        }

        // If we're profiling, do it immediately before/after the main monkey
        // loop
        // 检查是否需要构建堆信息，命令行参数可指定
        if (mGenerateHprof) {
            signalPersistentProcesses();
        }

        mNetworkMonitor.start(); //开始监控网络,其实只是初始化一些时间NetworkMonitor对象持有的时间数据，它是一个Binder对象,其实在getSystemInterfaces（）方法中已经向AMS注册此Binder，AMS通过此Binder与Monkey进程通信网络情况
        int crashedAtCycle = 0; //保存执行Monkey过程中发现的崩溃数量
        try {
            crashedAtCycle = runMonkeyCycles(); //主线程，执行最重要的runMonkeyCycles（）方法，返回值是发现的崩溃数量
        } finally {
            // Release the rotation lock if it's still held and restore the
            // original orientation. //执行完Monkey，会走finally
            new MonkeyRotationEvent(Surface.ROTATION_0, false).injectEvent(
                mWm, mAm, mVerbose); //Monkey所有事件都完成后，最后注入一个MonkeyRotationEvent，为了调整屏幕吗？没错，就是为了调整屏幕
        }
        mNetworkMonitor.stop(); //停止监控网络

        //下面这部分代码，都是在运行事件流结束后执行……才会走到这里（应该是用于收尾工作的代码，也许monkey所有事件都结束后，就会走这里）
        synchronized (this) { //Monkey主线程需要先获取Monkey对象锁，才能继续执行代码块，这是为了与binder线程进行线程间的同步，因为他们都访问同样的共享变量
            if (mRequestAnrTraces) { //当AMS发现某个app出现Anr，会通过远程调用appNotResponse，然后该值当前monkey进程的binder线程池中赋值为true（由于binder线程池持有Monkey对象锁）
                reportAnrTraces(); //调用者获取anr trace的操作
                mRequestAnrTraces = false; //表示已经执行过anr trace的获取，不需要获取了
            }
            if (mRequestAnrBugreport){ //获取anr相关的，bugreport命令
                Logger.out.println("Print the anr report");
                getBugreport("anr_" + mReportProcessName + "_");
                mRequestAnrBugreport = false;
            }
            if (mRequestWatchdogBugreport) {
                Logger.out.println("Print the watchdog report");
                getBugreport("anr_watchdog_");
                mRequestWatchdogBugreport = false;
            }
            if (mRequestAppCrashBugreport){
                getBugreport("app_crash" + mReportProcessName + "_");
                mRequestAppCrashBugreport = false;
            }
            if (mRequestDumpsysMemInfo) {
                reportDumpsysMemInfo();
                mRequestDumpsysMemInfo = false;
            }
            if (mRequestPeriodicBugreport){
                getBugreport("Bugreport_");
                mRequestPeriodicBugreport = false;
            }
            if (mWatchdogWaiting) {
                mWatchdogWaiting = false;
                notifyAll();
            }
        }

        //收尾工作
        if (mGenerateHprof) {
            signalPersistentProcesses();
            if (mVerbose > 0) {
                Logger.out.println("// Generated profiling reports in /data/misc");
            }
        }

        try {
            mAm.setActivityController(null, true); //告知AMS，取消ActivityController Binder的注册
            mNetworkMonitor.unregister(mAm); //告知AMS，取消网络监听
        } catch (RemoteException e) {
            // just in case this was latent (after mCount cycles), make sure
            // we report it
            if (crashedAtCycle >= mCount) {
                crashedAtCycle = mCount - 1;
            }
        }

        // report dropped event stats
        if (mVerbose > 0) { //原来只是记录下来，告知用户有哪些事件失败了……
            Logger.out.println(":Dropped: keys=" + mDroppedKeyEvents
                    + " pointers=" + mDroppedPointerEvents
                    + " trackballs=" + mDroppedTrackballEvents
                    + " flips=" + mDroppedFlipEvents
                    + " rotations=" + mDroppedRotationEvents);
        }

        // report network stats
        mNetworkMonitor.dump(); //输出一下网络的情况，到标准输出流中

        if (crashedAtCycle < mCount - 1) {
            Logger.err.println("** System appears to have crashed at event " + crashedAtCycle
                    + " of " + mCount + " using seed " + mSeed); //当发现的崩溃数量小于执行次数，在标准错误流中输出一段日志
            return crashedAtCycle;  //返回发现的错误数量
        } else {
            if (mVerbose > 0) {
                Logger.out.println("// Monkey finished");
            }
            return 0; //表示退出状态码
        }
    }

    /**
     * Process the command-line options
     * 处理命令行参数的方法，将命令行参数全部保存到Monkey对象持有的实例变量中，即内存中（由当前Monkey对象持有的实例变量负责保存）
     * 忽略了一点，Java的数组对象表示的命令行参数，包括入口类的名字吗？
     * @return Returns true if options were parsed with no apparent errors. 解析命令行参数的结果，没有错误时，返回true
     */
    private boolean processOptions() {
        // quick (throwaway) check for unadorned command
        //mArgs持有着一个数组对象，这个数组对象的每个元素都是命令行参数
        if (mArgs.length < 1) { //检查命令行参数，如果命令行参数少于1个
            showUsage(); //向屏幕输出monkey命令怎么使用
            return false; //返回值false 表示解析命令行参数有错误
        }

        try {
            String opt; //临时局部变量，用于存储某个命令行参数（选项参数）
            Set<String> validPackages = new HashSet<>(); //创建一个Set对象，用于临时保存有效的包名
            while ((opt = nextOption()) != null) {
                if (opt.equals("-s")) { //当单个命令行参数为-s时
                    mSeed = nextOptionLong("Seed"); //此Seed仅用作提示……牛逼，此时获取到的随机种子值，会交给Monkey对象持有的mSeed负责保存
                } else if (opt.equals("-p")) {
                    validPackages.add(nextOptionData()); //可以看到-p参数后面的包名，会放到一个Set集合中，天生去重
                } else if (opt.equals("-c")) {
                    mMainCategories.add(nextOptionData()); //-c参数后面的参数放到了一个list中
                } else if (opt.equals("-v")) {
                    mVerbose += 1;// 一个-v参数，为其+1
                } else if (opt.equals("--ignore-crashes")) { //如果解析到这个选项参数
                    mIgnoreCrashes = true;  //更新Monkey对象持有的实例变量值
                } else if (opt.equals("--ignore-timeouts")) {
                    mIgnoreTimeouts = true;
                } else if (opt.equals("--ignore-security-exceptions")) {
                    mIgnoreSecurityExceptions = true;
                } else if (opt.equals("--monitor-native-crashes")) {
                    mMonitorNativeCrashes = true;
                } else if (opt.equals("--ignore-native-crashes")) {
                    mIgnoreNativeCrashes = true;
                } else if (opt.equals("--kill-process-after-error")) {
                    mKillProcessAfterError = true;
                } else if (opt.equals("--hprof")) { //指定hprof，会构建所有进程的信息
                    mGenerateHprof = true;
                } else if (opt.equals("--match-description")) {
                    mMatchDescription = nextOptionData();
                } else if (opt.equals("--pct-touch")) { //触摸事件比例
                    int i = MonkeySourceRandom.FACTOR_TOUCH; //获取FACTOR_TOUCH事件在数组中存储的下标
                    mFactors[i] = -nextOptionLong("touch events percentage"); //注意这里使用负值，因为……没有通过命令行传入的值，默认值都是1.0，这是为了方便后面区分用户配置的参数
                } else if (opt.equals("--pct-motion")) {
                    int i = MonkeySourceRandom.FACTOR_MOTION;
                    mFactors[i] = -nextOptionLong("motion events percentage"); //为何解析完，存储一个负数呢？
                } else if (opt.equals("--pct-trackball")) {
                    int i = MonkeySourceRandom.FACTOR_TRACKBALL;
                    mFactors[i] = -nextOptionLong("trackball events percentage");
                } else if (opt.equals("--pct-rotation")) {
                    int i = MonkeySourceRandom.FACTOR_ROTATION;
                    mFactors[i] = -nextOptionLong("screen rotation events percentage");
                } else if (opt.equals("--pct-syskeys")) {
                    int i = MonkeySourceRandom.FACTOR_SYSOPS;
                    mFactors[i] = -nextOptionLong("system (key) operations percentage");
                } else if (opt.equals("--pct-nav")) {
                    int i = MonkeySourceRandom.FACTOR_NAV;
                    mFactors[i] = -nextOptionLong("nav events percentage");
                } else if (opt.equals("--pct-majornav")) {
                    int i = MonkeySourceRandom.FACTOR_MAJORNAV;
                    mFactors[i] = -nextOptionLong("major nav events percentage");
                } else if (opt.equals("--pct-appswitch")) {
                    int i = MonkeySourceRandom.FACTOR_APPSWITCH;
                    mFactors[i] = -nextOptionLong("app switch events percentage");
                } else if (opt.equals("--pct-flip")) {
                    int i = MonkeySourceRandom.FACTOR_FLIP;
                    mFactors[i] = -nextOptionLong("keyboard flip percentage");
                } else if (opt.equals("--pct-anyevent")) {
                    int i = MonkeySourceRandom.FACTOR_ANYTHING;
                    mFactors[i] = -nextOptionLong("any events percentage");
                } else if (opt.equals("--pct-pinchzoom")) {
                    int i = MonkeySourceRandom.FACTOR_PINCHZOOM;
                    mFactors[i] = -nextOptionLong("pinch zoom events percentage");
                } else if (opt.equals("--pct-permission")) {
                    int i = MonkeySourceRandom.FACTOR_PERMISSION;
                    mFactors[i] = -nextOptionLong("runtime permission toggle events percentage");
                } else if (opt.equals("--pkg-blacklist-file")) {
                    mPkgBlacklistFile = nextOptionData();
                } else if (opt.equals("--pkg-whitelist-file")) {
                    mPkgWhitelistFile = nextOptionData();
                } else if (opt.equals("--throttle")) {
                    mThrottle = nextOptionLong("delay (in milliseconds) to wait between events");
                } else if (opt.equals("--randomize-throttle")) {
                    mRandomizeThrottle = true;
                } else if (opt.equals("--wait-dbg")) {
                    // do nothing - it's caught at the very start of run()
                } else if (opt.equals("--dbg-no-events")) {
                    mSendNoEvents = true;
                } else if (opt.equals("--port")) { //在命令行中指定一个端口
                    mServerPort = (int) nextOptionLong("Server port to listen on for commands");
                } else if (opt.equals("--setup")) {
                    mSetupFileName = nextOptionData();
                } else if (opt.equals("-f")) {
                    mScriptFileNames.add(nextOptionData());
                } else if (opt.equals("--profile-wait")) {
                    mProfileWaitTime = nextOptionLong("Profile delay" +
                                " (in milliseconds) to wait between user action");
                } else if (opt.equals("--device-sleep-time")) {
                    mDeviceSleepTime = nextOptionLong("Device sleep time" +
                                                      "(in milliseconds)");
                } else if (opt.equals("--randomize-script")) {
                    mRandomizeScript = true;
                } else if (opt.equals("--script-log")) {
                    mScriptLog = true;
                } else if (opt.equals("--bugreport")) {
                    mRequestBugreport = true;
                } else if (opt.equals("--periodic-bugreport")){
                    mGetPeriodicBugreport = true;
                    mBugreportFrequency = nextOptionLong("Number of iterations");
                } else if (opt.equals("--permission-target-system")){
                    mPermissionTargetSystem = true;
                } else if (opt.equals("-h")) {
                    showUsage();
                    return false;
                } else {
                    Logger.err.println("** Error: Unknown option: " + opt);
                    showUsage();
                    return false; //表示解析参数有问题
                }
            }
            MonkeyUtils.getPackageFilter().addValidPackages(validPackages); //把临时保存的有效包名，存放到PackageFilter对象持有的set中，统一保管
        } catch (RuntimeException ex) { //捕获所有运行时异常，输出异常对象字符串信息
            Logger.err.println("** Error: " + ex.toString()); //** Error: java.lang.NumberFormatException: For input string: "x"
            showUsage();
            return false;
        }

        // If a server port hasn't been specified, we need to specify 没有指定TCP方式，就必须指定数量
        // a count
        if (mServerPort == -1) { //不使用TCP远程命令时，会走这里，强行处理事件数
            String countStr = nextArg(); //获取事件数
            if (countStr == null) {
                Logger.err.println("** Error: Count not specified"); //看到你了，说明没有指定事件次数
                showUsage();
                return false;
            }

            try {
                mCount = Integer.parseInt(countStr); //最后解析执行次数……执行次数必须放到最后……不然会解析失败……
            } catch (NumberFormatException e) {
                Logger.err.println("** Error: Count is not a number: \"" + countStr + "\"");
                showUsage();
                return false;
            }
        }

        return true; //正常情况下走这里
    }

    /**
     * Load a list of package names from a file. 从持久化的文件中加载包名
     *
     * @param fileName The file name, with package names separated by new line. 每行一个包名
     * @param list The destination list.
     * @return Returns false if any error occurs.
     */
    private static boolean loadPackageListFromFile(String fileName, Set<String> list) {
        BufferedReader reader = null; //缓存的字符流对象
        try {
            reader = new BufferedReader(new FileReader(fileName));
            String s;
            while ((s = reader.readLine()) != null) { //逐行读取
                s = s.trim(); //干掉空白字符
                if ((s.length() > 0) && (!s.startsWith("#"))) { //如果每行的长度大于0个，且不是以#开头的
                    list.add(s); //添加到集合中……，大哥，你这命名不要脸了啊
                }
            }
        } catch (IOException ioe) { //读取文件出现异常
            Logger.err.println("" + ioe); //标准错误流打印日志
            return false; //返回表示错误的结果
        } finally { //不管发生任何异常，字符流必须关闭，释放内存
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ioe) {
                    Logger.err.println("" + ioe);
                }
            }
        }
        return true;
    }

    /**
     * Load package denylist or allowlist (if specified).
     * 加载拒绝的包名与同意的包名，如果指定了文件
     * @return Returns false if any error occurs. //如果发生任何错误，则会返回false
     */
    private boolean loadPackageLists() {
        if (((mPkgWhitelistFile != null) || (MonkeyUtils.getPackageFilter().hasValidPackages()))
                && (mPkgBlacklistFile != null)) { //如果通过命令行已经指定了白名单的文件名，且又可以获取设置的有效的包名，且黑名单不为空
            Logger.err.println("** Error: you can not specify a package blacklist "
                    + "together with a whitelist or individual packages (via -p)."); //告知用户你不能再指定黑名单列表
            return false;
        }
        Set<String> validPackages = new HashSet<>(); //临时创建一个HashSet对象，用于保存文件中的包名
        if ((mPkgWhitelistFile != null) //如果指定了白名单文件
                && (!loadPackageListFromFile(mPkgWhitelistFile, validPackages))) {
            return false;
        }
        MonkeyUtils.getPackageFilter().addValidPackages(validPackages);//把文件中的包名添加到包过滤器的集合中……
        Set<String> invalidPackages = new HashSet<>();
        if ((mPkgBlacklistFile != null) //如果指定了包含黑名单包名的文件
                && (!loadPackageListFromFile(mPkgBlacklistFile, invalidPackages))) {
            return false;
        }
        MonkeyUtils.getPackageFilter().addInvalidPackages(invalidPackages); //把黑名单文件中的包名，添加到包过滤器对象持有的无效包名集合中
        return true;
    }

    /**
     * Check for any internal configuration (primarily build-time) errors.
     * 写死了为true……默认就是各种爽，检查任意的内部配置
     * @return Returns true if ready to rock. //返回true表示准备好rock
     */
    private boolean checkInternalConfiguration() {
        return true;
    }

    /**
     * Attach to the required system interfaces.
     *
     * @return Returns true if all system interfaces were available. true 表示所有的系统服务都可以获取
     * 初始化各种系统服务
     */
    private boolean getSystemInterfaces() {
        mAm = ActivityManager.getService(); //获取AMS系统服务
        if (mAm == null) { //如果没有获取到AMS系统服务
            Logger.err.println("** Error: Unable to connect to activity manager; is the system "
                    + "running?"); //告知无法获取AMS服务，反问你系统到底有没有运行
            return false; //方法结束，直接返回false，表示获取所有系统服务失败
        }

        mWm = IWindowManager.Stub.asInterface(ServiceManager.getService("window")); //获取WMS系统服务，这个跟AMS有啥区别？
        if (mWm == null) { //当没有获取到WMS系统服务
            Logger.err.println("** Error: Unable to connect to window manager; is the system "
                    + "running?");
            return false;
        }

        mPm = IPackageManager.Stub.asInterface(ServiceManager.getService("package")); //获取PMS系统服务
        if (mPm == null) {
            Logger.err.println("** Error: Unable to connect to package manager; is the system "
                    + "running?");
            return false;
        }

        try {
            mAm.setActivityController(new ActivityController(), true);//向AMS注册一个Binder对象，AMS通过此Binder对象，可以与Monkey进程通信
            mNetworkMonitor.register(mAm); //用于监听网络的Binder注册到AMS系统服务中
        } catch (RemoteException e) {
            Logger.err.println("** Failed talking with activity manager!"); //AMS服务挂了……
            return false;
        }

        return true; //返回true，说明已经成功获取到所有系统服务的句柄……可以使用系统服务了……
    }

    /**
     * Using the restrictions provided (categories & packages), generate a list
     * of activities that we can actually switch to.
     * 这里不仅检查App是否存在，还构建出了该App对应的Launcher Activity
     * @return Returns true if it could successfully build a list of target
     *         activities //返回true，表示获取到对应包的Launcher Activity
     */
    private boolean getMainApps() {
        try {
            final int N = mMainCategories.size(); //获取Category的数量，默认是两个，通过-c的命令行参数可以添加
            for (int i = 0; i < N; i++) { //遍历所有的Category
                Intent intent = new Intent(Intent.ACTION_MAIN); //创建Intent对象
                String category = mMainCategories.get(i); //获取List中的一个Category
                if (category.length() > 0) { //如果拿到的Category字符串大于0
                    intent.addCategory(category); //为Intent对象设置Category
                }
                List<ResolveInfo> mainApps = mPm.queryIntentActivities(intent, null, 0,
                        ActivityManager.getCurrentUser()).getList(); //使用PMS系统服务的查询queryIntentActivities方法，用于查询所有当前用户可以使用的主Activity，只有App在Manifest文件中注册的，才能被查找到
                //返回的是一个List对象，元素为ResolveInfo对象
                if (mainApps == null || mainApps.size() == 0) { //如果没有获取可以使用的主Activity
                    Logger.err.println("// Warning: no activities found for category " + category); //标准错误流中输出日志
                    continue; //当前循环结束
                }
                if (mVerbose >= 2) { // very verbose
                    Logger.out.println("// Selecting main activities from category " + category);
                } //这里说明获取到可用的Activiy了，会输出一个日志
                final int NA = mainApps.size(); // 读取获取到的主Activity数量
                for (int a = 0; a < NA; a++) { //遍历所有的ResolveInfo，注意一个主Activity对应一个ResolveInfo
                    ResolveInfo r = mainApps.get(a); //先去除其中一个ResoloveInfo对象
                    String packageName = r.activityInfo.applicationInfo.packageName; //使用ActivityInfo对象，然后再获取ApplicationInfo对象，然后再拿到对应Activity的包名
                    if (MonkeyUtils.getPackageFilter().checkEnteringPackage(packageName)) { //检查包名是否是可以启动的主activity
                        if (mVerbose >= 2) { // very verbose
                            Logger.out.println("//   + Using main activity " + r.activityInfo.name
                                    + " (from package " + packageName + ")");
                        } //如果日志等级大于等于2，标准输出流中输出日志
                        mMainApps.add(new ComponentName(packageName, r.activityInfo.name)); //创建一个ComponentName对象，其中记录了包名和Activity名，然后记录到一个List中
                    } else { //如果不是可以进入的包名
                        if (mVerbose >= 3) { // very very verbose
                            Logger.out.println("//   - NOT USING main activity "
                                    + r.activityInfo.name + " (from package " + packageName + ")"); //标记未使用的主Activity信息
                        }
                    }
                }
            }
        } catch (RemoteException e) {
            Logger.err.println("** Failed talking with package manager!"); //PMS系统服务出错，走这里
            return false; //说明没有获取到可以启动的主Activity
        }

        if (mMainApps.size() == 0) {
            Logger.out.println("** No activities found to run,  monkey aborted."); //没有找到可用的主Activity
            return false;  //返回false，会造成Monkey程序结束
        }

        return true;
    }

    /**
     * Run mCount cycles and see if we hit any crashers.
     * <p>
     * TODO: Meta state on keys
     * 开始执行monkey事件，核心方法
     *
     * @return Returns the last cycle which executed. If the value == mCount, no
     *         errors detected. 返回值为发现的崩溃数量
     * Monkey主线程会一直循环在这个方法里执行
     *
     */
    private int runMonkeyCycles() {
        int eventCounter = 0; //临时存储事件总数
        int cycleCounter = 0; //临时存储循环次数

        boolean shouldReportAnrTraces = false; //记录是否应该报告ANR的标志位
        boolean shouldReportDumpsysMemInfo = false; //记录是否应该报告内存信息的标志位
        boolean shouldAbort = false; //记录是否应该中断monkey主线程的标志位，即monkey程序是否应该终止
        boolean systemCrashed = false; //记录系统是否发生崩溃的标志位，比如AMS服务可能会停止工作，那么Monkey进程也会停止了……有道理……

        try {
            // 1、系统本身未出现崩溃
            // 2、Monkey的执行次数未到
            // 两个条件同时满足时，monkey程序会一直运行（monkey主线程进入循环中）
            while (!systemCrashed && cycleCounter < mCount) {
                synchronized (this) { //Monkey的主线程需要获取Monkey对象锁，可继续运行此代码块（Monkey对象自身的锁)，后面你知道为何使用这个对象锁，主要是为了线程间同步
                    if (mRequestProcRank) { //monkey主线程执行到这里，检查标志位，是否需要报告进程信息，也是AMS远程调用指定的，发生ANR时，指定
                        reportProcRank(); //报告进程评分，创建子进程，调用命令行工具
                        mRequestProcRank = false; //防止下次循环继续上报，因为程序在主线程循环执行中
                    }
                    if (mRequestAnrTraces) {//需要上报anr的traces，也是AMS远程调用指定的，发生ANR时指定
                        mRequestAnrTraces = false; //防止下次循环直接上报ANR的traces
                        shouldReportAnrTraces = true; //表示本次循环应该上报AnrTraces的标志位
                    }
                    if (mRequestAnrBugreport){ //需要上报anr的bugreport，它的值是AMS远程调用指定的，发生ANR时指定
                        getBugreport("anr_" + mReportProcessName + "_"); //报告的文件名为anr_进程名_
                        mRequestAnrBugreport = false; //防止下次循环中直接上报
                    }
                    if (mRequestWatchdogBugreport) { //需要上报watchdog的bugreport，这个值是当系统挂了的时候，也是由AMS远程调用指定的
                        Logger.out.println("Print the watchdog report"); //标注输出流输出日志
                        getBugreport("anr_watchdog_"); //子进程中执行命令行程序，持久化名字为anr_watchdog_
                        mRequestWatchdogBugreport = false; //这个标志位会由AMS修改，跨进程的告诉Monkey主线程可以干什么
                    }
                    if (mRequestAppCrashBugreport){ //需要上报app崩溃的bugreport,这个值也是AMS远程调用赋值，草，Binder进程间通信太重要了
                        getBugreport("app_crash" + mReportProcessName + "_"); //生成app_crash文件，同样在子进程中进行
                        mRequestAppCrashBugreport = false; //防止下次循环中执行
                    }
                    if (mRequestPeriodicBugreport){ //需要上报什么?这个蒙了，这是没有提取到事件的时候，会赋值为true
                        getBugreport("Bugreport_"); //单纯的调用bugreport，卧槽，闹半天，每份报告都是单纯的bugreport，因为没有拿到事件
                        mRequestPeriodicBugreport = false; //防止下次循环中执行
                    }
                    if (mRequestDumpsysMemInfo) { //是否需要请求系统内存信息，这个值也是AMS发现出现ANR后，远程调用方法，并修改的此值
                        mRequestDumpsysMemInfo = false; //防止下次循环中直接执行（或者说，只能由AMS来赋值）
                        shouldReportDumpsysMemInfo = true; //标记应该上报内存信息
                    }
                    if (mMonitorNativeCrashes) { //是否需要监控native的崩溃信息，这是由命令行参数--monitor-native-crashes决定的
                        // first time through, when eventCounter == 0, just set up
                        // the watcher (ignore the error)
                        if (checkNativeCrashes() && (eventCounter > 0)) { //发现本地崩溃，且事件数量大于0（这里没有系统服务的回调，而是一直目录中的文件数量）
                            Logger.out.println("** New native crash detected."); //在标准输出流，打印natvie崩溃找到的消息
                            if (mRequestBugreport) { //同样调用bugreport命令
                                getBugreport("native_crash_"); //只不过文件名是这个……，这里子进程中进行
                            }
                            mAbort = mAbort || !mIgnoreNativeCrashes || mKillProcessAfterError; //检查是否需要中断monkey进程，有一个值为true，即会赋值给mAbort，说明Monkey程序即将要结束了
                                              //mAbort、mIgnoreNativeCrashes、mKillProcessAfterError
                        }
                    }
                    if (mAbort) { //如果Monkey程序需要中断
                        shouldAbort = true; //局部变量赋值应该中断
                    }
                    if (mWatchdogWaiting) { //如果已经通知watchdog等待
                        mWatchdogWaiting = false;
                        notifyAll();  //通知所有停留在Monkey对象上的线程，继续运行（不过其他线程如果阻塞在这里，还得获取到Monkey对象锁，才能继续运行，尴尬）
                    }
                }

                // Report ANR, dumpsys after releasing lock on this.
                // This ensures the availability of the lock to Activity controller's appNotResponding
                if (shouldReportAnrTraces) { //如果必须上报ANR Trace，上面都标记过了，果然局部变量在下面会使用
                    shouldReportAnrTraces = false; //防止下次循环上报
                    reportAnrTraces(); //通过此方法，上报AnrTraces
                }

                if (shouldReportDumpsysMemInfo) { //必须上报内存信息的情况
                    shouldReportDumpsysMemInfo = false; //防止下次循环直接上报
                    reportDumpsysMemInfo(); //报告内存信息，使用的命令是：dumpsys meminfo
                }

                if (shouldAbort) { //应该中断monkey进程的处理
                    shouldAbort = false; //防止下次循环……好像没有什么必要了……作者多写了……
                    Logger.out.println("** Monkey aborted due to error."); //标准错误流输出Monkey中断的错误
                    Logger.out.println("Events injected: " + eventCounter); //输出事件数量
                    return eventCounter; //返回事件数量，循环结束……
                }

                // In this debugging mode, we never send any events. This is
                // primarily here so you can manually test the package or category
                // limits, while manually exercising the system.
                if (mSendNoEvents) { //原来是调试事件数量用的，这个debug用的好，如果指定了命令行参数--dbg-no-events，且Monkey程序没有中断的时候，会走这里
                    eventCounter++; //将事件数量增加1
                    cycleCounter++; //将循环数量增加1
                    continue; //本次循环结束，不再往下执行，下面是什么？为啥不然执行了
                }

                if ((mVerbose > 0) && (eventCounter % 100) == 0 && eventCounter != 0) { //这里是事件数量达到100的时候会执行
                    String calendarTime = MonkeyUtils.toCalendarTime(System.currentTimeMillis()); //计算花费的时间
                    long systemUpTime = SystemClock.elapsedRealtime(); //在拿一个事件
                    Logger.out.println("    //[calendar_time:" + calendarTime + " system_uptime:"
                            + systemUpTime + "]"); //输出花费的时间，以及系统启动的时间？
                    Logger.out.println("    // Sending event #" + eventCounter); //输出事件总数
                } //每执行100个事件，输出一次日志

                MonkeyEvent ev = mEventSource.getNextEvent(); //从EventSource对象中提取事件，从命令行执行时，实际是从MonkeySourceRandom的getNextEvent（）方法中提取事件的，每次循环都从MonkeySourceEvent中提取事件，假设有两个点事件在队列中
               if (ev != null) {  //如果成功提取到事件……
                    int injectCode = ev.injectEvent(mWm, mAm, mVerbose); //回调每个MonkeyEvent的injectEvent（）方法，并且把WMS、AMS、还有日志等级都传了进去，具体的操作，由具体的事件对象自己执行，注入码表示成功或者失败
                    if (injectCode == MonkeyEvent.INJECT_FAIL) { //处理失败的情况，卧槽还要+1
                        Logger.out.println("    // Injection Failed");
                        if (ev instanceof MonkeyKeyEvent) { //若事件为MonkeyKeyEvent对象
                            mDroppedKeyEvents++; //则丢弃的事件增加1
                        } else if (ev instanceof MonkeyMotionEvent) { //若事件为MonkeyMotionEvent
                            mDroppedPointerEvents++;  //则丢弃的mDroppedPointerEvents事件增加1
                        } else if (ev instanceof MonkeyFlipEvent) {
                            mDroppedFlipEvents++;
                        } else if (ev instanceof MonkeyRotationEvent) {
                            mDroppedRotationEvents++;
                        }
                    } else if (injectCode == MonkeyEvent.INJECT_ERROR_REMOTE_EXCEPTION) { //注入事件时，发生错误
                        systemCrashed = true; //说明操作系统发生崩溃，可能SystemServer进程重启
                        Logger.err.println("** Error: RemoteException while injecting event."); //此时标准错误流输出一条，注入事件的事件的时候，远程服务发生错误
                    } else if (injectCode == MonkeyEvent.INJECT_ERROR_SECURITY_EXCEPTION) {  //这是因为安全问题，未注入事件
                        systemCrashed = !mIgnoreSecurityExceptions; //如果命令行参数设置忽略参数，则不算系统出现错误
                        if (systemCrashed) {
                            Logger.err.println("** Error: SecurityException while injecting event."); //标准错误流输出日志，表明注入事件时，发生系统安全错误
                        }
                    }

                    // Don't count throttling as an event.
                    if (!(ev instanceof MonkeyThrottleEvent)) { //只有不是MonkeyThrottleEvent事件对象时，才计算总的次数，完美的将间隔事件忽略掉了
                        eventCounter++;
                        if (mCountEvents) {
                            cycleCounter++;
                        }
                    }
                } else { //这是从双向链表中，没有提取到事件对象的情况，厉害，这里基本走不到……牛逼，这个调试方法好
                    if (!mCountEvents) { //如果不需要计算事件数量
                        cycleCounter++; //循环次数增加1
                        writeScriptLog(cycleCounter); //把循环数量写入脚本文件
                        //Capture the bugreport after n iteration
                        if (mGetPeriodicBugreport) { //这是处理呢
                            if ((cycleCounter % mBugreportFrequency) == 0) {
                                mRequestPeriodicBugreport = true;
                            }
                        }
                    } else { //需要计算的时候，啥也不干……，中断循环完事
                        // Event Source has signaled that we have no more events to process
                        break;
                    }
                }
                //每完整的循环执行一次，Monkey对象锁会释放掉，不然别人哪有机会……
            }
        } catch (RuntimeException e) {
            Logger.error("** Error: A RuntimeException occurred:", e); //捕获到运行时异常，标准错误流输出结果，以及在标准错误流中打印异常对象的调用堆栈信息
        }
        Logger.out.println("Events injected: " + eventCounter); //当系统出现错误，或者事件数量到了，标准输出流中输出事件数
        return eventCounter; //返回注入的事件数
    }

    /**
     * Send SIGNAL_USR1 to all processes. This will generate large (5mb)
     * profiling reports in data/misc, so use with care.
     * 发送一个信号，给所有进程，报告会存放到data/misc目录下
     * 用于对比所有进程的信息，牛逼
     * 没发生崩溃时，持久化一份
     * 发生崩溃时，也可以持久化一份
     */
    private void signalPersistentProcesses() {
        try {
            mAm.signalPersistentProcesses(Process.SIGNAL_USR1); //使用AMS的signalPersistentProcesses方法，并把信号传递过去，SIGNAL_USR1这个信号是一个自定义信号

            synchronized (this) { //monkey主线程获取Monkey对象锁
                wait(2000); //主线程休息2s
            } //释放Monkey对象锁
        } catch (RemoteException e) {
            Logger.err.println("** Failed talking with activity manager!"); //发生远程服务错误，在标准错误流中输出一行信息
        } catch (InterruptedException e) {
        }
    }

    /**
     * Watch for appearance of new tombstone files, which indicate native
     * crashes.
     * 检查native崩溃的方法，检查tobstones文件的数量，每执行一个事件都会检查一下文件数量
     * @return Returns true if new files have appeared in the list
     */
    private boolean checkNativeCrashes() {
        String[] tombstones = TOMBSTONES_PATH.list(); //检查这个/data/tombstones目录，获取到所有文件名

        // shortcut path for usually empty directory, so we don't waste even
        // more objects
        if (tombstones == null || tombstones.length == 0) {
            mTombstones = null; //mTombstones持有的集合对象，每个元素是文件的最后修改时间，厉害，不用文件名，用文件的修改时间
            return false; //说明没有native崩溃
        }

        boolean result = false;

        // use set logic to look for new files
        HashSet<Long> newStones = new HashSet<Long>(); //创建一个Set对象
        for (String t : tombstones) { //遍历所有的tombstones文件名
            if (t.startsWith(TOMBSTONE_PREFIX)) { //如果前缀是tombstone_开头的文件
                File f = new File(TOMBSTONES_PATH, t);
                newStones.add(f.lastModified()); //把文件最后修改的时间，添加到Set集合中
                if (mTombstones == null || !mTombstones.contains(f.lastModified())) { //要是mTombstones还没有赋值，文件的修改时间也没有临时添加到临时创建的Set中
                    result = true; //先把结果赋值为true，说明发现native崩溃
                    waitForTombstoneToBeWritten(Paths.get(TOMBSTONES_PATH.getPath(), t)); //这里貌似没啥用啊……
                    Logger.out.println("** New tombstone found: " + f.getAbsolutePath()
                                       + ", size: " + f.length()); //打印找到的tombstone文件，以及字节数
                }
            }
        }

        // keep the new list for the next time
        mTombstones = newStones; //将临时创建的Set对象，赋值给Monkey对象持有的Set对象负责持有，这样旧的Set对象，在方法结束后，会被回收掉

        return result; //返回是否发生native崩溃的结果
    }

    /**
     * Wait for the given tombstone file to be completely written.
     * 用于等待文件写入完成
     * @param path The path of the tombstone file.
     */
    private void waitForTombstoneToBeWritten(Path path) {
        boolean isWritten = false;
        try {
            // Ensure file is done writing by sleeping and comparing the previous and current size
            for (int i = 0; i < NUM_READ_TOMBSTONE_RETRIES; i++) {  //确保文件已经写完了，只重试5次（相当于只等5s
                long size = Files.size(path); //获取文件的字节数（注意不是行数）
                try {
                    Thread.sleep(1000); //线程休息1秒
                } catch (InterruptedException e) { }
                if (size > 0 && Files.size(path) == size) { //字节数没有改变，则说明文件不再写入了
                    //File size is bigger than 0 and hasn't changed
                    isWritten = true;
                    break;
                }
            }
        } catch (IOException e) {
            Logger.err.println("Failed to get tombstone file size: " + e.toString());
        }
        if (!isWritten) {
            Logger.err.println("Incomplete tombstone file.");
            return;
        }
    }

    /**
     * Return the next command line option. This has a number of special cases
     * which closely, but not exactly, follow the POSIX command line options
     * patterns:
     *
     * <pre>
     * -- means to stop processing additional options
     * -z means option z
     * -z ARGS means option z with (non-optional) arguments ARGS
     * -zARGS means option z with (optional) arguments ARGS
     * --zz means option zz
     * --zz ARGS means option zz with (non-optional) arguments ARGS
     * </pre>
     *
     * Note that you cannot combine single letter options; -abc != -a -b -c
     *
     * @return Returns the option string, or null if there are no more options.
     */
    private String nextOption() {
        if (mNextArg >= mArgs.length) { //检查下标是否大于等于命令行参数的总长度，mNextArg的初始值是0
            return null; //返回null，说明没有参数可以可以使用了
        }
        String arg = mArgs[mNextArg]; //获取指定的一个命令行参数（从第一个获取）
        if (!arg.startsWith("-")) { //如果参数不是以-开头的，直接返回null，看来monkey的命令行参数已经规定死使用选项参数了
            return null;
        }
        mNextArg++;  //下标值+1，因为-参数的后面跟着是选项参数的值，看来接下来要处理选项参数的值了（应该是在别的方法中处理）
        if (arg.equals("--")) { //如果命令行参数的只等于--，也不行
            return null; //返回null
        }
        if (arg.length() > 1 && arg.charAt(1) != '-') { //单个命令行参数的长度大于1、且第二个字符不等于-时
            if (arg.length() > 2) { //单个命令行参数大于2时,貌似是这样的参数-500
                mCurArgData = arg.substring(2); //截取前两个字符，并赋值给mCurArgData
                return arg.substring(0, 2); //返回前两个字符…… -s cao,即会返回-s
            } else { //单个命令行参数出版大于1，且第二个参数为-，如果单纯是一个-的参数呢
                mCurArgData = null;
                return arg; //直接返回单个命令行参数，即--ignore-crashes，这样的参数,会被返回
            }
        }
        mCurArgData = null;
        Logger.err.println("arg=\"" + arg + "\" mCurArgData=\"" + mCurArgData + "\" mNextArg="
                + mNextArg + " argwas=\"" + mArgs[mNextArg-1] + "\"" + " nextarg=\"" +
                mArgs[mNextArg] + "\""); //arg="--throttle" mCurArgData="null" mNextArg=1 argwas="--throttle" nextarg="500"
        return arg;
    }

    /**
     * Return the next data associated with the current option.
     * 用于返回 选项参数的值的方法
     * @return Returns the data string, or null of there are no more arguments. 返回字符串参数，哈哈
     */
    private String nextOptionData() {
        if (mCurArgData != null) {
            return mCurArgData; //当 -s 50，这样的参数时，mCurargData为null，所以不会走这里
        }
        if (mNextArg >= mArgs.length) { //说明已经解析到最后一个参数了
            return null;
        }
        String data = mArgs[mNextArg]; //因为mNextArg在解析选项参数时，已经+过1了，所以这里刚好获取到选项参数值
        Logger.err.println("data=\"" + data + "\""); //data="500"
        mNextArg++; //继续获取下一个选项参数
        return data; //返回选项参数值
    }

    /**
     * Returns a long converted from the next data argument, with error handling
     * if not available.
     *
     * @param opt The name of the option.
     * @return Returns a long converted from the argument.
     */
    private long nextOptionLong(final String opt) {
        long result;
        try {
            result = Long.parseLong(nextOptionData()); //费心了，转成long，如果不能转换
        } catch (NumberFormatException e) { //这里捕获不能转换为数字的异常（说明字符串不是数字）
            Logger.err.println("** Error: " + opt + " is not a number");
            throw e;
        }
        return result;
    }

    /**
     * Return the next argument on the command line.
     * 用于返回下一个命令行参数值的方法
     * @return Returns the argument string, or null if we have reached the end.
     */
    private String nextArg() {
        if (mNextArg >= mArgs.length) {
            return null;
        }
        String arg = mArgs[mNextArg];
        mNextArg++;
        return arg;
    }

    /**
     * Print how to use this command.
     * 告知用户怎么使用monkey命令行
     */
    private void showUsage() {
        StringBuffer usage = new StringBuffer();
        usage.append("usage: monkey [-p ALLOWED_PACKAGE [-p ALLOWED_PACKAGE] ...]\n");
        usage.append("              [-c MAIN_CATEGORY [-c MAIN_CATEGORY] ...]\n");
        usage.append("              [--ignore-crashes] [--ignore-timeouts]\n");
        usage.append("              [--ignore-security-exceptions]\n");
        usage.append("              [--monitor-native-crashes] [--ignore-native-crashes]\n");
        usage.append("              [--kill-process-after-error] [--hprof]\n");
        usage.append("              [--match-description TEXT]\n");
        usage.append("              [--pct-touch PERCENT] [--pct-motion PERCENT]\n");
        usage.append("              [--pct-trackball PERCENT] [--pct-syskeys PERCENT]\n");
        usage.append("              [--pct-nav PERCENT] [--pct-majornav PERCENT]\n");
        usage.append("              [--pct-appswitch PERCENT] [--pct-flip PERCENT]\n");
        usage.append("              [--pct-anyevent PERCENT] [--pct-pinchzoom PERCENT]\n");
        usage.append("              [--pct-permission PERCENT]\n");
        usage.append("              [--pkg-blacklist-file PACKAGE_BLACKLIST_FILE]\n");
        usage.append("              [--pkg-whitelist-file PACKAGE_WHITELIST_FILE]\n");
        usage.append("              [--wait-dbg] [--dbg-no-events]\n");
        usage.append("              [--setup scriptfile] [-f scriptfile [-f scriptfile] ...]\n");
        usage.append("              [--port port]\n");
        usage.append("              [-s SEED] [-v [-v] ...]\n");
        usage.append("              [--throttle MILLISEC] [--randomize-throttle]\n");
        usage.append("              [--profile-wait MILLISEC]\n");
        usage.append("              [--device-sleep-time MILLISEC]\n");
        usage.append("              [--randomize-script]\n");
        usage.append("              [--script-log]\n");
        usage.append("              [--bugreport]\n");
        usage.append("              [--periodic-bugreport]\n");
        usage.append("              [--permission-target-system]\n");
        usage.append("              COUNT\n");
        Logger.err.println(usage.toString());
    }
}
