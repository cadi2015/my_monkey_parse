/*
 * Copyright (C) 2008 Google Inc.
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

import java.io.FileOutputStream;
import java.io.IOException;

import android.app.IActivityManager;
import android.view.IWindowManager;
/**
 * monkey keyboard flip event
 * 表示键盘反转事件
 */
public class MonkeyFlipEvent extends MonkeyEvent {

    // Raw keyboard flip event data
    // Works on emulator and dream

    /**
     * 字节数组，16个字节
     */
    private static final byte[] FLIP_0 = {
        0x7f, 0x06,
        0x00, 0x00,
        (byte) 0xe0, 0x39,
        0x01, 0x00,
        0x05, 0x00,
        0x00, 0x00,
        0x01, 0x00,
        0x00, 0x00 };

    /**
     * 字节数组，16个字节
     */
    private static final byte[] FLIP_1 = {
        (byte) 0x85, 0x06,
        0x00, 0x00,
        (byte) 0x9f, (byte) 0xa5,
        0x0c, 0x00,
        0x05, 0x00,
        0x00, 0x00,
        0x00, 0x00,
        0x00, 0x00 };

    private final boolean mKeyboardOpen; //MonkeyFlipEvent对象持有的标志位，表示键盘是否开启

    public MonkeyFlipEvent(boolean keyboardOpen) {
        super(EVENT_TYPE_FLIP);
        mKeyboardOpen = keyboardOpen;
    }

    /**
     *
     * @param iwm wires to current window manager 未使用
     * @param iam wires to current activity manager 未使用
     * @param verbose a log switch 使用
     * @return
     */
    @Override
    public int injectEvent(IWindowManager iwm, IActivityManager iam, int verbose) {
        if (verbose > 0) {
            Logger.out.println(":Sending Flip keyboardOpen=" + mKeyboardOpen); //只有日志等级大于0时，才会打印这个日志，打印打开键盘的状态
        }

        // inject flip event
        try {
            FileOutputStream f = new FileOutputStream("/dev/input/event0");
            f.write(mKeyboardOpen ? FLIP_0 : FLIP_1); //操作文件等同于操作硬件……，向文件中写入二进制字节流
            f.close(); //关闭文件输出流
            return MonkeyEvent.INJECT_SUCCESS; //表示注入事件成功
        } catch (IOException e) {
            Logger.out.println("Got IOException performing flip" + e); //操作文件，如果出现IOException
            return MonkeyEvent.INJECT_FAIL; //算作注入事件失败
        }
    }
}
