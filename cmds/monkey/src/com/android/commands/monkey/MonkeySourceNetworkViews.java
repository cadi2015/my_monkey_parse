/*
 * Copyright 2011, The Android Open Source Project
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

import static com.android.commands.monkey.MonkeySourceNetwork.EARG;

import android.app.ActivityManager;
import android.app.UiAutomation;
import android.app.UiAutomationConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.graphics.Rect;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.view.accessibility.AccessibilityInteractionClient;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.android.commands.monkey.MonkeySourceNetwork.CommandQueue;
import com.android.commands.monkey.MonkeySourceNetwork.MonkeyCommand;
import com.android.commands.monkey.MonkeySourceNetwork.MonkeyCommandReturn;

import dalvik.system.DexClassLoader;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class that enables Monkey to perform view introspection when issued Monkey Network
 * Script commands over the network.
 * 这个里面作者已经使用UiAutomation对象了……
 */
public class MonkeySourceNetworkViews {

    protected static android.app.UiAutomation sUiTestAutomationBridge; //MonkeySourceNetworkViews类持有的UiAutomation对象

    private static IPackageManager sPm =
            IPackageManager.Stub.asInterface(ServiceManager.getService("package")); //MonkeySourceNetworkViews类持有的PMS系统服务的Binder
    private static Map<String, Class<?>> sClassMap = new HashMap<String, Class<?>>(); //MonkeySourceNetworkViews类持有的HashMap对象，key为String对象，Value为Class对象

    private static final String HANDLER_THREAD_NAME = "UiAutomationHandlerThread"; //常量，表示拥有Looper的线程名字

    private static final String REMOTE_ERROR =
            "Unable to retrieve application info from PackageManager"; //使用系统服务出错时的提示
    private static final String CLASS_NOT_FOUND = "Error retrieving class information"; //类没有找到
    private static final String NO_ACCESSIBILITY_EVENT = "No accessibility event has occured yet"; //没有accessbility事件的提示
    private static final String NO_NODE = "Node with given ID does not exist"; //不存在Node结点
    private static final String NO_CONNECTION = "Failed to connect to AccessibilityService, "
                                                + "try restarting Monkey"; //不能连接AccessibilityService的提示（应该是AccessibilityManagerService）

    private static final Map<String, ViewIntrospectionCommand> COMMAND_MAP =
            new HashMap<String, ViewIntrospectionCommand>(); //MonkeySourceNetworkViews类持有的HashMap对象，Key为String，Value为ViewIntrospectionCommand对象

    /* Interface for view queries */

    /**
     * 表示具备什么样的能力的接口？查询能力
     * 实现此接口的类，都具备查询能力
     */
    private static interface ViewIntrospectionCommand {
        /**
         * Get the response to the query
         * @return the response to the query 返回查找结果
         */
        public MonkeyCommandReturn query(AccessibilityNodeInfo node, List<String> args);
    }

    /**
     *
     */
    static {
        COMMAND_MAP.put("getlocation", new GetLocation());
        COMMAND_MAP.put("gettext", new GetText());
        COMMAND_MAP.put("getclass", new GetClass());
        COMMAND_MAP.put("getchecked", new GetChecked());
        COMMAND_MAP.put("getenabled", new GetEnabled());
        COMMAND_MAP.put("getselected", new GetSelected());
        COMMAND_MAP.put("setselected", new SetSelected());
        COMMAND_MAP.put("getfocused", new GetFocused());
        COMMAND_MAP.put("setfocused", new SetFocused());
        COMMAND_MAP.put("getparent", new GetParent());
        COMMAND_MAP.put("getchildren", new GetChildren());
        COMMAND_MAP.put("getaccessibilityids", new GetAccessibilityIds());
    }

    private static final HandlerThread sHandlerThread = new HandlerThread(HANDLER_THREAD_NAME); //类必须持有一个带有Looper的HandlerThread对象,创建此工作线程

    /**
     * Registers the event listener for AccessibilityEvents.
     * Also sets up a communication connection so we can query the
     * accessibility service.
     * 为accesbilityevents注册事件监听器
     * 并且建立通信连接，以便我们可以查询
     * 可访问性服务。
     */
    public static void setup() {
        sHandlerThread.setDaemon(true); //设置工作线程为守护线程
        sHandlerThread.start(); //启动线程
        sUiTestAutomationBridge = new UiAutomation(sHandlerThread.getLooper(), //将线程中创建的Looper传进去
                new UiAutomationConnection()); //原来自己创建一个UiAutomationConnection对象即可，这个UiAutomation对象封装了很多功能
        sUiTestAutomationBridge.connect(); //与AcessiblityManagerService建立连接，这就是其中一个最重要的功能
    }

    public static void teardown() {
        sHandlerThread.quit();
    } //工作线程结束

    /**
     * Get the ID class for the given package.
     * This will cause issues if people reload a package with different
     * resource identifiers, but don't restart the Monkey server.
     *
     * @param packageName The package that we want to retrieve the ID class for
     * @return The ID class for the given package
     */
    private static Class<?> getIdClass(String packageName, String sourceDir)
            throws ClassNotFoundException {
        // This kind of reflection is expensive, so let's only do it
        // if we need to
        Class<?> klass = sClassMap.get(packageName);
        if (klass == null) {
            DexClassLoader classLoader = new DexClassLoader(
                    sourceDir, "/data/local/tmp",
                    null, ClassLoader.getSystemClassLoader());
            klass = classLoader.loadClass(packageName + ".R$id");
            sClassMap.put(packageName, klass);
        }
        return klass;
    }

    private static AccessibilityNodeInfo getNodeByAccessibilityIds(
            String windowString, String viewString) {
        int windowId = Integer.parseInt(windowString);
        int viewId = Integer.parseInt(viewString);
        int connectionId = sUiTestAutomationBridge.getConnectionId();
        AccessibilityInteractionClient client = AccessibilityInteractionClient.getInstance();
        return client.findAccessibilityNodeInfoByAccessibilityId(connectionId, windowId, viewId,
                false, 0, null);
    }

    /**
     *
     * @param viewId 表示控件的id
     * @return AccessibilityNodeInfo对象，此对象表示一个控件
     * @throws MonkeyViewException 可能会抛出的异常
     */
    private static AccessibilityNodeInfo getNodeByViewId(String viewId) throws MonkeyViewException {
        int connectionId = sUiTestAutomationBridge.getConnectionId();
        AccessibilityInteractionClient client = AccessibilityInteractionClient.getInstance();
        List<AccessibilityNodeInfo> infos = client.findAccessibilityNodeInfosByViewId(
                connectionId, AccessibilityWindowInfo.ACTIVE_WINDOW_ID,
                AccessibilityNodeInfo.ROOT_NODE_ID, viewId);
        return (!infos.isEmpty()) ? infos.get(0) : null;
    }

    /**
     * Command to list all possible view ids for the given application.
     * This lists all view ids regardless if they are on screen or not.
     * 看看ListView命令是怎么解析的，除了在MonkeySourceNetwork中，还在这里处理传递过来来的命令行参数
     */
    public static class ListViewsCommand implements MonkeyCommand {
        //listviews
        //单独的一行命令行值，即listviews
        public MonkeyCommandReturn translateCommand(List<String> command,
                                                    CommandQueue queue) {
            AccessibilityNodeInfo node = sUiTestAutomationBridge.getRootInActiveWindow(); //View树的根节点对象，通过UiAutomation对象获取
            /* Occasionally the API will generate an event with no source, which is essentially the
             * same as it generating no event at all */
            if (node == null) { //说明没有获取到View树的根节点对象
                return new MonkeyCommandReturn(false, NO_ACCESSIBILITY_EVENT); //返回MonkeyCommandReturn对象，用于调试……
            }
            String packageName = node.getPackageName().toString(); //获取View树所在的包名
            try{
                Class<?> klass; //创建Class的局部变量
                ApplicationInfo appInfo = sPm.getApplicationInfo(packageName, 0,
                        ActivityManager.getCurrentUser()); //通过PMS系统服务，获取应用的信息
                klass = getIdClass(packageName, appInfo.sourceDir); //获得一个类，通过包名和ApplicationInfo的sourceDir可以获得一个id类，难道是R类吗？
                StringBuilder fieldBuilder = new StringBuilder();
                Field[] fields = klass.getFields();
                for (Field field : fields) { //遍历所有字段
                    fieldBuilder.append(field.getName() + " ");
                }
                return new MonkeyCommandReturn(true, fieldBuilder.toString());
            } catch (RemoteException e){
                return new MonkeyCommandReturn(false, REMOTE_ERROR);
            } catch (ClassNotFoundException e){
                return new MonkeyCommandReturn(false, CLASS_NOT_FOUND);
            }
        }
    }

    /**
     * A command that allows for querying of views. It takes an id type, the requisite ids,
     * and the command for querying the view.
     */
    public static class QueryViewCommand implements MonkeyCommand {
        //queryview [id type] [id(s)] [command]
        //queryview viewid button1 gettext
        //queryview accessibilityids 12 5 getparent
        public MonkeyCommandReturn translateCommand(List<String> command,
                                                    CommandQueue queue) {
            if (command.size() > 2) {
                String idType = command.get(1);
                AccessibilityNodeInfo node;
                String viewQuery;
                List<String> args;
                if ("viewid".equals(idType)) {
                    try {
                        node = getNodeByViewId(command.get(2));
                        viewQuery = command.get(3);
                        args = command.subList(4, command.size());
                    } catch (MonkeyViewException e) {
                        return new MonkeyCommandReturn(false, e.getMessage());
                    }
                } else if (idType.equals("accessibilityids")) {
                    try {
                        node = getNodeByAccessibilityIds(command.get(2), command.get(3)); //通过id获取某个控件
                        viewQuery = command.get(4);
                        args = command.subList(5, command.size());
                    } catch (NumberFormatException e) {
                        return EARG;
                    }
                } else {
                    return EARG;
                }
                if (node == null) {
                    return new MonkeyCommandReturn(false, NO_NODE);
                }
                ViewIntrospectionCommand getter = COMMAND_MAP.get(viewQuery);
                if (getter != null) {
                    return getter.query(node, args);
                } else {
                    return EARG;
                }
            }
            return EARG;
        }
    }

    /**
     * A command that returns the accessibility ids of the root view.
     * 用于获取View树根节点的命令封装
     *
     */
    public static class GetRootViewCommand implements MonkeyCommand {
        // getrootview
        public MonkeyCommandReturn translateCommand(List<String> command,
                                                    CommandQueue queue) {
            AccessibilityNodeInfo node = sUiTestAutomationBridge.getRootInActiveWindow();
            return (new GetAccessibilityIds()).query(node, new ArrayList<String>());
        }
    }

    /**
     * A command that returns the accessibility ids of the views that contain the given text.
     * It takes a string of text and returns the accessibility ids of the nodes that contain the
     * text as a list of integers separated by spaces.
     * 靠，首页指定模式的思路已经有了
     */
    public static class GetViewsWithTextCommand implements MonkeyCommand {
        // getviewswithtext [text]
        // getviewswithtext "some text here"
        public MonkeyCommandReturn translateCommand(List<String> command,
                                                    CommandQueue queue) {
            if (command.size() == 2) {
                String text = command.get(1);
                int connectionId = sUiTestAutomationBridge.getConnectionId();
                List<AccessibilityNodeInfo> nodes = AccessibilityInteractionClient.getInstance()
                    .findAccessibilityNodeInfosByText(connectionId,
                            AccessibilityWindowInfo.ACTIVE_WINDOW_ID,
                            AccessibilityNodeInfo.ROOT_NODE_ID, text);
                ViewIntrospectionCommand idGetter = new GetAccessibilityIds();
                List<String> emptyArgs = new ArrayList<String>();
                StringBuilder ids = new StringBuilder();
                for (AccessibilityNodeInfo node : nodes) {
                    MonkeyCommandReturn result = idGetter.query(node, emptyArgs);
                    if (!result.wasSuccessful()){
                        return result;
                    }
                    ids.append(result.getMessage()).append(" ");
                }
                return new MonkeyCommandReturn(true, ids.toString());
            }
            return EARG;
        }
    }

    /**
     * Command to retrieve the location of the given node.
     * Returns the x, y, width and height of the view, separated by spaces.
     */
    public static class GetLocation implements ViewIntrospectionCommand {
        //queryview [id type] [id] getlocation
        //queryview viewid button1 getlocation
        public MonkeyCommandReturn query(AccessibilityNodeInfo node,
                                         List<String> args) {
            if (args.size() == 0) {
                Rect nodePosition = new Rect();
                node.getBoundsInScreen(nodePosition);
                StringBuilder positions = new StringBuilder();
                positions.append(nodePosition.left).append(" ").append(nodePosition.top);
                positions.append(" ").append(nodePosition.right-nodePosition.left).append(" ");
                positions.append(nodePosition.bottom-nodePosition.top);
                return new MonkeyCommandReturn(true, positions.toString());
            }
            return EARG;
        }
    }


    /**
     * Command to retrieve the text of the given node
     */
    public static class GetText implements ViewIntrospectionCommand {
        //queryview [id type] [id] gettext
        //queryview viewid button1 gettext
        public MonkeyCommandReturn query(AccessibilityNodeInfo node,
                                         List<String> args) {
            if (args.size() == 0) {
                if (node.isPassword()){
                    return new MonkeyCommandReturn(false, "Node contains a password");
                }
                /* Occasionally we get a null from the accessibility API, rather than an empty
                 * string */
                if (node.getText() == null) {
                    return new MonkeyCommandReturn(true, "");
                }
                return new MonkeyCommandReturn(true, node.getText().toString());
            }
            return EARG;
        }
    }


    /**
     * Command to retrieve the class name of the given node
     */
    public static class GetClass implements ViewIntrospectionCommand {
        //queryview [id type] [id] getclass
        //queryview viewid button1 getclass
        public MonkeyCommandReturn query(AccessibilityNodeInfo node,
                                         List<String> args) {
            if (args.size() == 0) {
                return new MonkeyCommandReturn(true, node.getClassName().toString());
            }
            return EARG;
        }
    }
    /**
     * Command to retrieve the checked status of the given node
     */
    public static class GetChecked implements ViewIntrospectionCommand {
        //queryview [id type] [id] getchecked
        //queryview viewid button1 getchecked
        public MonkeyCommandReturn query(AccessibilityNodeInfo node,
                                         List<String> args) {
            if (args.size() == 0) {
                return new MonkeyCommandReturn(true, Boolean.toString(node.isChecked()));
            }
            return EARG;
        }
    }

    /**
     * Command to retrieve whether the given node is enabled
     */
    public static class GetEnabled implements ViewIntrospectionCommand {
        //queryview [id type] [id] getenabled
        //queryview viewid button1 getenabled
        public MonkeyCommandReturn query(AccessibilityNodeInfo node,
                                         List<String> args) {
            if (args.size() == 0) {
                return new MonkeyCommandReturn(true, Boolean.toString(node.isEnabled()));
            }
            return EARG;
        }
    }

    /**
     * Command to retrieve whether the given node is selected
     */
    public static class GetSelected implements ViewIntrospectionCommand {
        //queryview [id type] [id] getselected
        //queryview viewid button1 getselected
        public MonkeyCommandReturn query(AccessibilityNodeInfo node,
                                         List<String> args) {
            if (args.size() == 0) {
                return new MonkeyCommandReturn(true, Boolean.toString(node.isSelected()));
            }
            return EARG;
        }
    }

    /**
     * Command to set the selected status of the given node. Takes a boolean value as its only
     * argument.
     */
    public static class SetSelected implements ViewIntrospectionCommand {
        //queryview [id type] [id] setselected [boolean]
        //queryview viewid button1 setselected true
        public MonkeyCommandReturn query(AccessibilityNodeInfo node,
                                         List<String> args) {
            if (args.size() == 1) {
                boolean actionPerformed;
                if (Boolean.valueOf(args.get(0))) {
                    actionPerformed = node.performAction(AccessibilityNodeInfo.ACTION_SELECT);
                } else if (!Boolean.valueOf(args.get(0))) {
                    actionPerformed =
                            node.performAction(AccessibilityNodeInfo.ACTION_CLEAR_SELECTION);
                } else {
                    return EARG;
                }
                return new MonkeyCommandReturn(actionPerformed);
            }
            return EARG;
        }
    }

    /**
     * Command to get whether the given node is focused.
     */
    public static class GetFocused implements ViewIntrospectionCommand {
        //queryview [id type] [id] getfocused
        //queryview viewid button1 getfocused
        public MonkeyCommandReturn query(AccessibilityNodeInfo node,
                                         List<String> args) {
            if (args.size() == 0) {
                return new MonkeyCommandReturn(true, Boolean.toString(node.isFocused()));
            }
            return EARG;
        }
    }

    /**
     * Command to set the focus status of the given node. Takes a boolean value
     * as its only argument.
     */
    public static class SetFocused implements ViewIntrospectionCommand {
        //queryview [id type] [id] setfocused [boolean]
        //queryview viewid button1 setfocused false
        public MonkeyCommandReturn query(AccessibilityNodeInfo node,
                                         List<String> args) {
            if (args.size() == 1) {
                boolean actionPerformed;
                if (Boolean.valueOf(args.get(0))) {
                    actionPerformed = node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                } else if (!Boolean.valueOf(args.get(0))) {
                    actionPerformed = node.performAction(AccessibilityNodeInfo.ACTION_CLEAR_FOCUS);
                } else {
                    return EARG;
                }
                return new MonkeyCommandReturn(actionPerformed);
            }
            return EARG;
        }
    }

    /**
     * Command to get the accessibility ids of the given node. Returns the accessibility ids as a
     * space separated pair of integers with window id coming first, followed by the accessibility
     * view id.
     */
    public static class GetAccessibilityIds implements ViewIntrospectionCommand {
        //queryview [id type] [id] getaccessibilityids
        //queryview viewid button1 getaccessibilityids
        public MonkeyCommandReturn query(AccessibilityNodeInfo node,
                                         List<String> args) {
            if (args.size() == 0) {
                int viewId;
                try {
                    Class<?> klass = node.getClass(); //获取控件的类型
                    Field field = klass.getDeclaredField("mAccessibilityViewId"); //获取控件的id字段,看来AccessibilityNodeInfo对象是对View对象的封装呀，为啥要封装啊，mAccessibilityViewId只有View对象里面才有呀
                    field.setAccessible(true); //设置可访问私有字段
                    viewId = ((Integer) field.get(node)).intValue(); //获取整型的View id
                } catch (NoSuchFieldException e) {
                    return new MonkeyCommandReturn(false, NO_NODE); //字段不存在是，走这里，说明肯定不是一个具体的View
                } catch (IllegalAccessException e) {
                    return new MonkeyCommandReturn(false, "Access exception"); //无法访问字段走这里
                }
                String ids = node.getWindowId() + " " + viewId; //卧槽，还要获取window的id，和view的id组合在一起
                return new MonkeyCommandReturn(true, ids);
            }
            return EARG; //只要list保存的元素数量不是0，就会走这里，说明是无效参数
        }
    }

    /**
     * Command to get the accessibility ids of the parent of the given node. Returns the
     * accessibility ids as a space separated pair of integers with window id coming first followed
     * by the accessibility view id.
     */
    public static class GetParent implements ViewIntrospectionCommand {
        //queryview [id type] [id] getparent
        //queryview viewid button1 getparent
        public MonkeyCommandReturn query(AccessibilityNodeInfo node,
                                         List<String> args) {
            if (args.size() == 0) {
                AccessibilityNodeInfo parent = node.getParent();
                if (parent == null) {
                  return new MonkeyCommandReturn(false, "Given node has no parent");
                }
                return (new GetAccessibilityIds()).query(parent, new ArrayList<String>());
            }
            return EARG;
        }
    }

    /**
     * Command to get the accessibility ids of the children of the given node. Returns the
     * children's ids as a space separated list of integer pairs. Each of the pairs consists of the
     * window id, followed by the accessibility id.
     */
    public static class GetChildren implements ViewIntrospectionCommand {
        //queryview [id type] [id] getchildren
        //queryview viewid button1 getchildren
        public MonkeyCommandReturn query(AccessibilityNodeInfo node,
                                         List<String> args) {
            if (args.size() == 0) {
                ViewIntrospectionCommand idGetter = new GetAccessibilityIds();
                List<String> emptyArgs = new ArrayList<String>();
                StringBuilder ids = new StringBuilder();
                int totalChildren = node.getChildCount();
                for (int i = 0; i < totalChildren; i++) {
                    MonkeyCommandReturn result = idGetter.query(node.getChild(i), emptyArgs);
                    if (!result.wasSuccessful()) {
                        return result;
                    } else {
                        ids.append(result.getMessage()).append(" ");
                    }
                }
                return new MonkeyCommandReturn(true, ids.toString());
            }
            return EARG;
        }
    }
}
