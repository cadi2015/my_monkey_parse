/*
 * Copyright 2009, The Android Open Source Project
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

import android.content.Context;
import android.os.IPowerManager;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.Integer;
import java.lang.NumberFormatException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.StringTokenizer;

/**
 * An Event source for getting Monkey Network Script commands from
 * over the network. 表示从Socket提取事件的事件来源
 * 将Socket客户端传递过来的每行命令（约定好的协议）转化为Monkey支持的MonkeyEvent对象，完美的执行
 * 接下来我将先获取View树，然后解析成MonkeyEvent对象
 */
public class MonkeySourceNetwork implements MonkeyEventSource {
    private static final String TAG = "MonkeyStub";
    /* The version of the monkey network protocol */
    public static final int MONKEY_NETWORK_VERSION = 2;
    private static DeferredReturn deferredReturn; //MonkeySourceNetwork类持有的DeferredReturn对象

    /**
     * ReturnValue from the MonkeyCommand that indicates whether the
     * command was sucessful or not.
     * 每个MonkeyCommandReturn对象表示一个monkey命令的查找结果
     */
    public static class MonkeyCommandReturn {
        private final boolean success; //持有的是否成功的标志位
        private final String message; //持有的消息

        /**
         * 可以指定结果
         * @param success 表示是否成功
         */
        public MonkeyCommandReturn(boolean success) {
            this.success = success;
            this.message = null;
        }

        /**
         * 可以指定结果和消息
         * @param success 表示是否成功
         * @param message 表示消息是什么
         */
        public MonkeyCommandReturn(boolean success,
                                   String message) {
            this.success = success;
            this.message = message;
        }

        /**
         *
         * @return 返回是否存在提示信息
         */
        boolean hasMessage() {
            return message != null;
        }

        /**
         * 获取message存储的字符串
         * @return
         */
        String getMessage() {
            return message;
        }

        /**
         * 返回是否成功
         * @return
         */
        boolean wasSuccessful() {
            return success;
        }
    }

    public final static MonkeyCommandReturn OK = new MonkeyCommandReturn(true); //MonkeySourceNetwork类持有的MonkeyCommandReturn对象，表示解析命令成功
    public final static MonkeyCommandReturn ERROR = new MonkeyCommandReturn(false);//MonkeySourceNetwork类持有的MonkeyCommandReturn对象，表示解析命令失败
    public final static MonkeyCommandReturn EARG = new MonkeyCommandReturn(false,
                                                                            "Invalid Argument");//MonkeySourceNetwork类持有的MonkeyCommandReturn对象，表示解析命令失败，且包含有提示信息，说明是无效参数

    /**
     * Interface that MonkeyCommands must implement.
     * 表示具备命令解析能力的接口
     * 规定了一个接口方法， 表示用于解析命令
     */
    public interface MonkeyCommand {
        /**
         * Translate the command line into a sequence of MonkeyEvents.
         *
         * @param command the command line. 命令行传入的命令
         * @param queue the command queue.  用于保存命令的队列
         * @return MonkeyCommandReturn indicating what happened. 解析monkey命令的结果
         */
        MonkeyCommandReturn translateCommand(List<String> command, CommandQueue queue);
    }

    /**
     * Command to simulate closing and opening the keyboard.
     * 用于表示模拟关闭与开启键盘的命令
     */
    private static class FlipCommand implements MonkeyCommand {
        // flip open
        // flip closed
        public MonkeyCommandReturn translateCommand(List<String> command,
                                                    CommandQueue queue) {
            if (command.size() > 1) {
                String direction = command.get(1); //取出来分隔后的第二个参数，备注：第一个参数是flip
                if ("open".equals(direction)) { //当第二个参数是open
                    queue.enqueueEvent(new MonkeyFlipEvent(true)); //创建一个MonkeyFlipEvent对象，然后添加到命令的队列中
                    return OK;
                } else if ("close".equals(direction)) {
                    queue.enqueueEvent(new MonkeyFlipEvent(false));
                    return OK;
                }
            }
            return EARG;
        }
    }

    /**
     * Command to send touch events to the input system.
     *  通过IPS服务，发送touch 事件
     */
    private static class TouchCommand implements MonkeyCommand {
        // touch [down|up|move] [x] [y]
        // touch down 120 120 按下某处坐标的命令
        // touch move 140 140 移动某处坐标的命令
        // touch up 140 140   弹起某处坐标的命令
        public MonkeyCommandReturn translateCommand(List<String> command,
                                                    CommandQueue queue) {
            if (command.size() == 4) {
                String actionName = command.get(1);
                int x = 0;
                int y = 0;
                try {
                    x = Integer.parseInt(command.get(2));
                    y = Integer.parseInt(command.get(3));
                } catch (NumberFormatException e) {
                    // Ok, it wasn't a number
                    Log.e(TAG, "Got something that wasn't a number", e);
                    return EARG;
                }

                // figure out the action
                int action = -1;
                if ("down".equals(actionName)) {
                    action = MotionEvent.ACTION_DOWN;
                } else if ("up".equals(actionName)) {
                    action = MotionEvent.ACTION_UP;
                } else if ("move".equals(actionName)) {
                    action = MotionEvent.ACTION_MOVE;
                }
                if (action == -1) {
                    Log.e(TAG, "Got a bad action: " + actionName);
                    return EARG;
                }

                queue.enqueueEvent(new MonkeyTouchEvent(action)
                        .addPointer(0, x, y));
                return OK;
            }
            return EARG;
        }
    }

    /**
     * Command to send Trackball events to the input system.
     */
    private static class TrackballCommand implements MonkeyCommand {
        // trackball [dx] [dy]
        // trackball 1 0 -- move right
        // trackball -1 0 -- move left
        public MonkeyCommandReturn translateCommand(List<String> command,
                                                    CommandQueue queue) {
            if (command.size() == 3) {
                int dx = 0;
                int dy = 0;
                try {
                    dx = Integer.parseInt(command.get(1));
                    dy = Integer.parseInt(command.get(2));
                } catch (NumberFormatException e) {
                    // Ok, it wasn't a number
                    Log.e(TAG, "Got something that wasn't a number", e);
                    return EARG;
                }
                queue.enqueueEvent(new MonkeyTrackballEvent(MotionEvent.ACTION_MOVE)
                        .addPointer(0, dx, dy));
                return OK;

            }
            return EARG;
        }
    }

    /**
     * Command to send Key events to the input system.
     */
    private static class KeyCommand implements MonkeyCommand {
        // key [down|up] [keycode]
        // key down 82
        // key up 82
        public MonkeyCommandReturn translateCommand(List<String> command,
                                                    CommandQueue queue) {
            if (command.size() == 3) {
                int keyCode = getKeyCode(command.get(2));
                if (keyCode < 0) {
                    // Ok, you gave us something bad.
                    Log.e(TAG, "Can't find keyname: " + command.get(2));
                    return EARG;
                }
                Log.d(TAG, "keycode: " + keyCode);
                int action = -1;
                if ("down".equals(command.get(1))) {
                    action = KeyEvent.ACTION_DOWN;
                } else if ("up".equals(command.get(1))) {
                    action = KeyEvent.ACTION_UP;
                }
                if (action == -1) {
                    Log.e(TAG, "got unknown action.");
                    return EARG;
                }
                queue.enqueueEvent(new MonkeyKeyEvent(action, keyCode));
                return OK;
            }
            return EARG;
        }
    }

    /**
     * Get an integer keycode value from a given keyname.
     *
     * @param keyName the key name to get the code for
     * @return the integer keycode value, or -1 on error.
     */
    private static int getKeyCode(String keyName) {
        int keyCode = -1;
        try {
            keyCode = Integer.parseInt(keyName);
        } catch (NumberFormatException e) {
            // Ok, it wasn't a number, see if we have a
            // keycode name for it
            keyCode = MonkeySourceRandom.getKeyCode(keyName);
            if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
                // OK, one last ditch effort to find a match.
                // Build the KEYCODE_STRING from the string
                // we've been given and see if that key
                // exists.  This would allow you to do "key
                // down menu", for example.
                keyCode = MonkeySourceRandom.getKeyCode("KEYCODE_" + keyName.toUpperCase());
                if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
                    // Still unknown
                    return -1;
                }
            }
        }
        return keyCode;
    }

    /**
     * Command to put the Monkey to sleep.
     * 休眠命令封装
     */
    private static class SleepCommand implements MonkeyCommand {
        // sleep 2000
        //格式：sleep 2000
        public MonkeyCommandReturn translateCommand(List<String> command,
                                                    CommandQueue queue) {
            if (command.size() == 2) { //如果命令行行分割后的元素数量为2
                int sleep = -1; //用于保存休眠时间
                String sleepStr = command.get(1); //获取休眠时间的字符串
                try {
                    sleep = Integer.parseInt(sleepStr); //休眠时间由字符串转化成整型
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Not a number: " + sleepStr, e);
                    return EARG; //如果转化错误，说明第二个参数不是数字，返回EARG对象，说明是参数问题
                }
                queue.enqueueEvent(new MonkeyThrottleEvent(sleep)); //添加一个MonkeyThrottleEvent对象，作为休眠事件
                return OK;
            }
            return EARG;
        }
    }

    /**
     * Command to type a string
     */
    private static class TypeCommand implements MonkeyCommand {
        // wake
        public MonkeyCommandReturn translateCommand(List<String> command,
                                                    CommandQueue queue) {
            if (command.size() == 2) {
                String str = command.get(1);

                char[] chars = str.toString().toCharArray();

                // Convert the string to an array of KeyEvent's for
                // the built in keymap.
                KeyCharacterMap keyCharacterMap = KeyCharacterMap.
                        load(KeyCharacterMap.VIRTUAL_KEYBOARD);
                KeyEvent[] events = keyCharacterMap.getEvents(chars);

                // enqueue all the events we just got.
                for (KeyEvent event : events) {
                    queue.enqueueEvent(new MonkeyKeyEvent(event));
                }
                return OK;
            }
            return EARG;
        }
    }

    /**
     * Command to wake the device up
     * 唤醒设备的命令封装
     * WakeCommand对象
     */
    private static class WakeCommand implements MonkeyCommand {
        // wake
        public MonkeyCommandReturn translateCommand(List<String> command,
                                                    CommandQueue queue) {
            if (!wake()) {
                return ERROR;
            }
            return OK;
        }
    }

    /**
     * Command to "tap" at a location (Sends a down and up touch
     * event).
     */
    private static class TapCommand implements MonkeyCommand {
        // tap x y
        public MonkeyCommandReturn translateCommand(List<String> command,
                                                    CommandQueue queue) {
            if (command.size() == 3) {
                int x = 0;
                int y = 0;
                try {
                    x = Integer.parseInt(command.get(1));
                    y = Integer.parseInt(command.get(2));
                } catch (NumberFormatException e) {
                    // Ok, it wasn't a number
                    Log.e(TAG, "Got something that wasn't a number", e);
                    return EARG;
                }

                queue.enqueueEvent(new MonkeyTouchEvent(MotionEvent.ACTION_DOWN)
                        .addPointer(0, x, y));
                queue.enqueueEvent(new MonkeyTouchEvent(MotionEvent.ACTION_UP)
                        .addPointer(0, x, y));
                return OK;
            }
            return EARG;
        }
    }

    /**
     * Command to "press" a buttons (Sends an up and down key event.)
     */
    private static class PressCommand implements MonkeyCommand {
        // press keycode
        public MonkeyCommandReturn translateCommand(List<String> command,
                                                    CommandQueue queue) {
            if (command.size() == 2) {
                int keyCode = getKeyCode(command.get(1));
                if (keyCode < 0) {
                    // Ok, you gave us something bad.
                    Log.e(TAG, "Can't find keyname: " + command.get(1));
                    return EARG;
                }

                queue.enqueueEvent(new MonkeyKeyEvent(KeyEvent.ACTION_DOWN, keyCode));
                queue.enqueueEvent(new MonkeyKeyEvent(KeyEvent.ACTION_UP, keyCode));
                return OK;

            }
            return EARG;
        }
    }

    /**
     * Command to defer the return of another command until the given event occurs.
     * deferreturn takes three arguments. It takes an event to wait for (e.g. waiting for the
     * device to display a different activity would the "screenchange" event), a
     * timeout, which is the number of microseconds to wait for the event to occur, and it takes
     * a command. The command can be any other Monkey command that can be issued over the network
     * (e.g. press KEYCODE_HOME). deferreturn will then run this command, return an OK, wait for
     * the event to occur and return the deferred return value when either the event occurs or
     * when the timeout is reached (whichever occurs first). Note that there is no difference
     * between an event occurring and the timeout being reached; the client will have to verify
     * that the change actually occured.
     *
     * Example:
     *     deferreturn screenchange 1000 press KEYCODE_HOME
     * This command will press the home key on the device and then wait for the screen to change
     * for up to one second. Either the screen will change, and the results fo the key press will
     * be returned to the client, or the timeout will be reached, and the results for the key
     * press will be returned to the client.
     */
    private static class DeferReturnCommand implements MonkeyCommand {
        // deferreturn [event] [timeout (ms)] [command]
        // deferreturn screenchange 100 tap 10 10
        public MonkeyCommandReturn translateCommand(List<String> command,
                                                    CommandQueue queue) {
            if (command.size() > 3) {
                String event = command.get(1);
                int eventId;
                if (event.equals("screenchange")) {
                    eventId = DeferredReturn.ON_WINDOW_STATE_CHANGE;
                } else {
                    return EARG;
                }
                long timeout = Long.parseLong(command.get(2));
                MonkeyCommand deferredCommand = COMMAND_MAP.get(command.get(3));
                if (deferredCommand != null) {
                    List<String> parts = command.subList(3, command.size());
                    MonkeyCommandReturn ret = deferredCommand.translateCommand(parts, queue);
                    deferredReturn = new DeferredReturn(eventId, ret, timeout);
                    return OK;
                }
            }
            return EARG;
        }
    }


    /**
     * Force the device to wake up.
     * 强制手机唤醒^
     * @return true if woken up OK. 返回true，说明唤醒成功
     */
    private static final boolean wake() {
        IPowerManager pm =
                IPowerManager.Stub.asInterface(ServiceManager.getService(Context.POWER_SERVICE));//先获取PowerManagerSystem服务
        try {
            pm.wakeUp(SystemClock.uptimeMillis(), PowerManager.WAKE_REASON_UNKNOWN,
                    "Monkey", null); //调用它的wakeup（）接口，卧槽尼玛，这个方法在哪调用呀？起码得等系统服务回应呀，应该会阻塞当前线程（同步调用）
        } catch (RemoteException e) {
            Log.e(TAG, "Got remote exception", e); //远程服务出错，会走这里
            return false;
        }
        return true; //只要PowerManagerService没有出错，就说明成功
    }

    // This maps from command names to command implementations.
    private static final Map<String, MonkeyCommand> COMMAND_MAP = new HashMap<String, MonkeyCommand>(); //MonkeySourceNetwork类持有的Ma对象，

    /**
     * MonkeySourceNetwork类加载时执行
     */
    static {
        // Add in all the commands we support //初始化支持的命令到一个HashMap对象中
        COMMAND_MAP.put("flip", new FlipCommand()); //flip命令
        COMMAND_MAP.put("touch", new TouchCommand()); //touch命令
        COMMAND_MAP.put("trackball", new TrackballCommand()); //trackball事件
        COMMAND_MAP.put("key", new KeyCommand()); //key事件
        COMMAND_MAP.put("sleep", new SleepCommand()); //sleep事件
        COMMAND_MAP.put("wake", new WakeCommand()); //wake事件
        COMMAND_MAP.put("tap", new TapCommand()); //tap事件
        COMMAND_MAP.put("press", new PressCommand()); //press事件
        COMMAND_MAP.put("type", new TypeCommand()); //type事件
        COMMAND_MAP.put("listvar", new MonkeySourceNetworkVars.ListVarCommand()); //listvar事件
        COMMAND_MAP.put("getvar", new MonkeySourceNetworkVars.GetVarCommand()); //getvar事件
        COMMAND_MAP.put("listviews", new MonkeySourceNetworkViews.ListViewsCommand()); //listviews事件，目前来看，没有使用……
        COMMAND_MAP.put("queryview", new MonkeySourceNetworkViews.QueryViewCommand()); //queryview事件
        COMMAND_MAP.put("getrootview", new MonkeySourceNetworkViews.GetRootViewCommand()); //getrootview事件
        COMMAND_MAP.put("getviewswithtext", //getviewswitchtext是按
                        new MonkeySourceNetworkViews.GetViewsWithTextCommand());
        COMMAND_MAP.put("deferreturn", new DeferReturnCommand()); //这个fefer return事件真他妈的怪……
    }

    // QUIT command
    private static final String QUIT = "quit";
    // DONE command
    private static final String DONE = "done";

    // command response strings
    private static final String OK_STR = "OK";
    private static final String ERROR_STR = "ERROR";

    /**
     * 一个表示具备命令队列的接口，实现此接口，将具备入队事件的能力，每个事件为MonkeyEvent
     */
    public static interface CommandQueue {
        /**
         * Enqueue an event to be returned later.  This allows a
         * command to return multiple events.  Commands using the
         * command queue still have to return a valid event from their
         * translateCommand method.  The returned command will be
         * executed before anything put into the queue.
         *
         * @param e the event to be enqueued.
         */
        public void enqueueEvent(MonkeyEvent e);
    };

    // Queue of Events to be processed.  This allows commands to push
    // multiple events into the queue to be processed.
    // 用于处理事件的队列对象，
    private static class CommandQueueImpl implements CommandQueue{
        private final Queue<MonkeyEvent> queuedEvents = new LinkedList<MonkeyEvent>(); //CommandQueueImpl对象持有的1个LinkedList对象，每个元素为MonkeyEvent对象

        public void enqueueEvent(MonkeyEvent e) {
            queuedEvents.offer(e);
        } //向队列的尾部添加元素

        /**
         * Get the next queued event to excecute.
         *
         * @return the next event, or null if there aren't any more.
         */
        public MonkeyEvent getNextQueuedEvent() {
            return queuedEvents.poll();
        } //删除并获取队列头部的元素
    };

    // A holder class for a deferred return value. This allows us to defer returning the success of
    // a call until a given event has occurred.
    private static class DeferredReturn {
        public static final int ON_WINDOW_STATE_CHANGE = 1;

        private int event; //持有的事件类型
        private MonkeyCommandReturn deferredReturn; //持有的可返回的MonkeyCommandReturn对象
        private long timeout;

        public DeferredReturn(int event, MonkeyCommandReturn deferredReturn, long timeout) {
            this.event = event;
            this.deferredReturn = deferredReturn;
            this.timeout = timeout;
        }

        /**
         * Wait until the given event has occurred before returning the value.
         * @return The MonkeyCommandReturn from the command that was deferred.
         */
        public MonkeyCommandReturn waitForEvent() {
            switch(event) {
                case ON_WINDOW_STATE_CHANGE:
                    try {
                        synchronized(MonkeySourceNetworkViews.class) {
                            MonkeySourceNetworkViews.class.wait(timeout);
                        }
                    } catch(InterruptedException e) {
                        Log.d(TAG, "Deferral interrupted: " + e.getMessage());
                    }
            }
            return deferredReturn;
        }
    };

    private final CommandQueueImpl commandQueue = new CommandQueueImpl(); //MonkeySourceNetwork持有的CommandQueueImpl对象，用于在队列中保存作为事件的MonkeyEvent对象

    private BufferedReader input; //MonkeySourceNetwork对象持有的BufferedReader对象，用于从磁盘或者键盘读取二进制字节流
    private PrintWriter output;
    private boolean started = false; //表示监听端口是否开始，默认值为没有开始

    private ServerSocket serverSocket; //持有的ServerSocket
    private Socket clientSocket; //Socket对象，表示客户端进程

    /**
     *
     * @param port 表示需要监听的端口
     * @throws IOException 可能会抛出IOException
     */
    public MonkeySourceNetwork(int port) throws IOException {
        // Only bind this to local host.  This means that you can only
        // talk to the monkey locally, or though adb port forwarding. //只能绑定本地主机的某个端口，两种使用方法，monkey在本地，或者通过adb的端口转发
        serverSocket = new ServerSocket(port,
                                        0, // default backlog
                                        InetAddress.getLocalHost()); //创建ServerSocket对象，监听在本地主机的某个端口上
    }

    /**
     * Start a network server listening on the specified port.  The
     * network protocol is a line oriented protocol, where each line
     * is a different command that can be run. //开始监听在指定的端口中，这个网络协议是命令行中的规定的协议，每行内容代表不同的命令，然后就可以运行了
     *
     * @param port the port to listen on 表示需要进程监听的端口
     */
    private void startServer() throws IOException {
        clientSocket = serverSocket.accept(); //调用ServerSocket的accept（）方法，可以获得一个Socket对象，注意此处为线程会被阻塞，直到获得一个客户端的连接
        // At this point, we have a client connected.
        // Attach the accessibility listeners so that we can start receiving
        // view events. Do this before wake so we can catch the wake event
        // if possible.
        MonkeySourceNetworkViews.setup(); //初始化AccessibilityManagerService服务，只有在客户端连接成功后，才会去再去做AccessibilityManagerService的连接
        // Wake the device up in preparation for doing some commands.
        wake(); //唤醒手机

        input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream())); //从Socket的客户端获取二进制字节流，再将二进制字节流转化为字符流，在内存中即可读取
        // auto-flush
        output = new PrintWriter(clientSocket.getOutputStream(), true); //将clientSocket的输出字节流全部添加到打印输出流
    }

    /**
     * Stop the server from running so it can reconnect a new client.
     */
    private void stopServer() throws IOException {
        clientSocket.close();
        MonkeySourceNetworkViews.teardown();
        input.close();
        output.close();
        started = false;
    }

    /**
     * Helper function for commandLineSplit that replaces quoted
     * charaters with their real values.
     *
     * @param input the string to do replacement on.
     * @return the results with the characters replaced.
     */
    private static String replaceQuotedChars(String input) {
        return input.replace("\\\"", "\"");
    }

    /**
     * This function splits the given line into String parts.  It obey's quoted
     * strings and returns them as a single part.
     * 用于将整行命令，分隔到一个list中
     * "This is a test" -> returns only one element
     * This is a test -> returns four elements
     *
     * @param line the line to parse 需要解析的命令行
     * @return the List of elements 返回命令行解析后的list
     */
    private static List<String> commandLineSplit(String line) {
        ArrayList<String> result = new ArrayList<String>(); //创建一个list对象
        StringTokenizer tok = new StringTokenizer(line); //创建StringTokenizer对象，用于分隔字符串

        boolean insideQuote = false; //标志位
        StringBuffer quotedWord = new StringBuffer(); //创建一个StringBuffer对象
        while (tok.hasMoreTokens()) { //检查是否还有分隔符……
            String cur = tok.nextToken(); //得到分隔符前的一个字符串，哈哈
            if (!insideQuote && cur.startsWith("\"")) { //如果不是insideQuote，当然字符串是以\开头的情况
                // begin quote
                quotedWord.append(replaceQuotedChars(cur)); //多个\替换成一个？然后再添加StringBuffer中
                insideQuote = true; //标记替换完成……
            } else if (insideQuote) { //如果已经将多个\替换成一个\，走这里
                // end quote
                if (cur.endsWith("\"")) {
                    insideQuote = false;
                    quotedWord.append(" ").append(replaceQuotedChars(cur));
                    String word = quotedWord.toString();

                    // trim off the quotes
                    result.add(word.substring(1, word.length() - 1));
                } else {
                    quotedWord.append(" ").append(replaceQuotedChars(cur));
                }
            } else {
                result.add(replaceQuotedChars(cur)); //平常没有以\开头的……，每个直接添加到list中了
            }
        }
        return result;
    }

    /**
     * Translate the given command line into a MonkeyEvent.
     * 解析命令，并转化为MonkeyEvent
     * @param commandLine the full command line given. 完整的由Socket Client传过来的一整行
     */
    private void translateCommand(String commandLine) {
        Log.d(TAG, "translateCommand: " + commandLine); //向控制台打印即将要的解析命令
        List<String> parts = commandLineSplit(commandLine); //调用commandLineSplit（）方法，将一行命令分隔到一个线性表中
        if (parts.size() > 0) { //如果取得的命令行，有多个元素组成
            MonkeyCommand command = COMMAND_MAP.get(parts.get(0)); //取出来命令行中的第一个命令，然后去Map查找到对应的MonkeyCommand对象
            if (command != null) { //找到，说明支持该命令
                MonkeyCommandReturn ret = command.translateCommand(parts, commandQueue); //调用对应命令的translateCommand，并将表示整行命令行参数的list，和一个保存命令的队列对象传入
                handleReturn(ret); //命令解析结果对象传入到handleReturn（）方法中，主要是在控制台输出，别的没干啥……
            }
        }
    }

    /**
     *
     * @param ret 命令解析结果对象
     */
    private void handleReturn(MonkeyCommandReturn ret) {
        if (ret.wasSuccessful()) { //如果是成功
            if (ret.hasMessage()) { //如果有打打印Message
                returnOk(ret.getMessage()); //将待打印的信息传入进去
            } else {
                returnOk(); //没有需要打印的消息，使用默认的打印功能接口
            }
        } else { //这里是命令解析失败的情况
            if (ret.hasMessage()) {
                returnError(ret.getMessage());
            } else {
                returnError();
            }
        }
    }


    public MonkeyEvent getNextEvent() {
        if (!started) { //第一次获取事件时，才会做等待一个客户端的连接
            try {
                startServer(); //开始进入等待客户端的连接，注意里面有阻塞方法的调用
            } catch (IOException e) {
                Log.e(TAG, "Got IOException from server", e);
                return null;
            }
            started = true; //标志位标注只有第一次会进入等待客户端连接，看来只能连接一个客户端进程
        }

        // Now, get the next command.  This call may block, but that's OK
        try {
            while (true) { //进入循环获取事件（只有在获取到事件、
                // Check to see if we have any events queued up.  If
                // we do, use those until we have no more.  Then get
                // more input from the user.
                MonkeyEvent queuedEvent = commandQueue.getNextQueuedEvent(); //如果成功从事件队列中获取到一个事件
                if (queuedEvent != null) {
                    // dispatch the event
                    return queuedEvent; //直接返回事件对象，此方法结束
                }

                // Check to see if we have any returns that have been deferred. If so, now that
                // we've run the queued commands, wait for the given event to happen (or the timeout
                // to be reached), and handle the deferred MonkeyCommandReturn.
                // 检查一下我们是否有延期的退货。
                // 如果是，那么现在，我们已经运行了排队命令，等待给定的事件发生(或超时)，并处理延迟的MonkeyCommandReturn
                //
                if (deferredReturn != null) {
                    Log.d(TAG, "Waiting for event");
                    MonkeyCommandReturn ret = deferredReturn.waitForEvent();
                    deferredReturn = null;
                    handleReturn(ret);
                }

                String command = input.readLine(); //从输入字符流中读取一行（这行命令是从Socket 客户端发过来的）
                if (command == null) { //如果没有从输入字节流中获取到任何的命令，理论上客户端不发送命令的话，这里是不是应该是空的字符串?难道为null的时候真的是客户端断开了吗？
                    Log.d(TAG, "Connection dropped."); //说明连接断开了
                    // Treat this exactly the same as if the user had
                    // ended the session cleanly with a done commant.
                    command = DONE; //将当前命令直接指定为done
                }

                //如果命令中，包含done
                if (DONE.equals(command)) {
                    // stop the server so it can accept new connections
                    try {
                        stopServer(); //停止客户端的连接，这样就能和一个新的客户端建立Socket连接了……
                    } catch (IOException e) {
                        Log.e(TAG, "Got IOException shutting down!", e);
                        return null; //针对发生IO异常的情况，这里返回null……整个方法返回null，说明没有获取到事件
                    }
                    // return a noop event so we keep executing the main
                    // loop
                    return new MonkeyNoopEvent(); //为了确保monkey主线程可以继续执行
                }

                // Do quit checking here
                if (QUIT.equals(command)) { //如果命令中有包括有quit
                    // then we're done
                    Log.d(TAG, "Quit requested");
                    // let the host know the command ran OK
                    returnOk(); //向控制输出一行
                    return null; //返回值为null，说明没有获取到事件
                }

                // Do comment checking here.  Comments aren't a
                // command, so we don't echo anything back to the
                // user.
                // 纯检查，如果命令中是以#开头的，什么也不干，循环中断一次，继续……这个主要是起到注释的作用
                if (command.startsWith("#")) {
                    // keep going
                    continue;
                }

                // Translate the command line.  This will handle returning error/ok to the user
                // 解析socket client传过来的一行指令，除了其他已经处理过的done、quit指令
                translateCommand(command);
            }
        } catch (IOException e) {
            Log.e(TAG, "Exception: ", e);
            return null;
        }
    }

    /**
     * Returns ERROR to the user.
     * 向用户返回错误（标准输出流）
     */
    private void returnError() {
        output.println(ERROR_STR);
    }

    /**
     * Returns ERROR to the user.
     *
     * @param msg the error message to include
     */
    private void returnError(String msg) {
        output.print(ERROR_STR);
        output.print(":");
        output.println(msg);
    }

    /**
     * Returns OK to the user.
     */
    private void returnOk() {
        output.println(OK_STR);
    }

    /**
     * Returns OK to the user.
     * 在标准输出流中打印信息
     * @param returnValue the value to return from this command.
     */
    private void returnOk(String returnValue) {
        output.print(OK_STR); //打印OK_STR
        output.print(":"); //打印：
        output.println(returnValue); //打印指定的信息
    }

    public void setVerbose(int verbose) {
        // We're not particualy verbose
    }

    public boolean validate() {
        // we have no pre-conditions to validate
        return true;
    }
}
