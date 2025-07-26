package com.example.root.ffttest2;

import android.util.Log;

public class CustomMessageTest {

    // 用于存储待发送消息的全局变量
    private static String messageToSend = null;
    private static boolean strTestEnabled = false;

    public static void switchStrTest(){
        strTestEnabled = !strTestEnabled;
    }


    public static boolean isStrTestEnabled(){
        return strTestEnabled;
    }

    public static String getMessageToSend(){
        return messageToSend;
    }

    /**
     * 【发送端入口】
     * 准备要发送的自定义字符串。
     * @param message 要发送的字符串，例如 "Hello, world!"
     */
    public static void prepareMessage(String message) {
        messageToSend = message;
        Log.d("CustomMessageTest", "Message prepared for sending: " + message);
    }

    /**
     * 【发送端核心】
     * 将准备好的字符串转换为经过FEC编码的比特流。
     * 如果没有准备消息，则返回null。
     * @return 编码后的 short[] 比特流，或 null
     */
    public static short[]   getCodedBitsForCustomMessage() {
        if (messageToSend == null || messageToSend.isEmpty()) {
            return null; // 没有自定义消息要发送
        }

        // 1. 将字符串转换为二进制字符串
        byte[] bytes = messageToSend.getBytes();
        StringBuilder binary = new StringBuilder();
        for (byte b : bytes) {
            int val = b;
            for (int i = 0; i < 8; i++) {
                binary.append((val & 128) == 0 ? 0 : 1);
                val <<= 1;
            }
        }
        String originalBits = binary.toString();

        // 2. 进行前向纠错编码 (FEC)
        String coded = "";
        if (Constants.CODING) {
            coded = Utils.encode(originalBits, Constants.cc[0], Constants.cc[1], Constants.cc[2]);
        } else {
            coded = originalBits;
        }

        // 3. 发送后清空消息，防止重复发送
        messageToSend = null;

        Log.d("CustomMessageTest", "Original bits: " + originalBits);
        Log.d("CustomMessageTest", "Coded bits for sending: " + coded);

        // 4. 转换成short[]并返回
        return Utils.convert(coded);
    }

    /**
     * 【接收端核心】
     * 将解码后的原始比特流翻译回字符串。
     * @param uncodedBits Viterbi解码后得到的原始二进制字符串
     * @return 翻译后的字符串，如果失败则返回错误信息
     */
    public static String decodeBitsToString(String uncodedBits) {
        try {
            // 检查比特流长度是否是8的倍数
            if (uncodedBits != null && !uncodedBits.isEmpty() && uncodedBits.length() % 8 == 0) {
                StringBuilder text = new StringBuilder();
                for (int i = 0; i < uncodedBits.length(); i += 8) {
                    String charCode = uncodedBits.substring(i, i + 8);
                    int parsedChar = Integer.parseInt(charCode, 2);
                    text.append((char) parsedChar);
                }
                Log.d("CustomMessageTest", "Successfully decoded message: " + text.toString());
                return text.toString();
            } else {
                if(uncodedBits == null){
                    return "NULL";
                }
                // 如果长度不匹配，说明这可能不是一个字符串消息
                Log.w("CustomMessageTest", "Decoded bits length is not a multiple of 8. Cannot convert to string. Bits: " + uncodedBits);
                StringBuilder text = new StringBuilder();
                for(int i = 0; i < uncodedBits.length(); i ++){
                    int ch = uncodedBits.indexOf(i);
                    if(ch==0){
                        text.append('0');
                    } else if(ch==1){
                        text.append('1');
                    } else {
                        text.append(Integer.toString(ch));
                    }
                }
                return text.toString();
//                return "Error: Non-string data received.";
            }
        } catch (Exception e) {
            Log.e("CustomMessageTest", "Error converting bits to string: " + e.toString());
            return "Error: Failed to parse bits.";
        }
    }
}