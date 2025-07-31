package com.example.root.ffttest2;

import android.util.Log;
import java.util.Collections;

public class CustomMessageTest {

    // --- 成员变量 ---
    private static String messageToSend = null;
    private static boolean strTestEnabled = false;
    // 【【【新增】】】定义信头的固定长度为16比特
    private static final int HEADER_LENGTH_BITS = 16;

    // --- 模式控制方法 (您的实现，保持不变) ---
    public static void switchStrTest() {
        strTestEnabled = !strTestEnabled;
    }

    public static boolean isStrTestEnabled() {
        return strTestEnabled;
    }

    // --- 消息准备方法 (您的实现，保持不变) ---
    public static void prepareMessage(String message) {
        messageToSend = message;
        Log.d("CustomMessageTest", "Message prepared for sending: " + message);
    }

    // --- 发送端核心 (完全重写) ---
    /**
     * 将准备好的消息（数字或字符串）转换为待编码的原始比特流。
     * 对于字符串，会自动在前面加上一个16位的长度信头。
     * @return 原始比特流 (uncoded bits)
     */
    public static String getOriginalBitsToEncode() {
        if (messageToSend == null) return null;

        // 尝试将输入解析为数字，如果成功，则按5比特ID格式处理 (兼容旧功能)
        try {
            int num = Integer.parseInt(messageToSend);
            String binaryId = Integer.toBinaryString(num);
            String paddedId = String.join("", Collections.nCopies(5 - binaryId.length(), "0")) + binaryId;
            Utils.debugLog("Prepared ID " + num + " as 5-bit string: " + paddedId);
            messageToSend = null; // 清空
            return paddedId;
        } catch (NumberFormatException e) {
            // 如果不是数字，则按字符串逻辑处理
            Utils.debugLog("Preparing to send a string.");
        }

        // --- 字符串处理逻辑 ---
        // 1. 将字符串转换为二进制的“有效载荷”
        byte[] bytes = messageToSend.getBytes();
        StringBuilder payloadBuilder = new StringBuilder();
        for (byte b : bytes) {
            int val = b;
            for (int i = 0; i < 8; i++) {
                payloadBuilder.append((val & 128) == 0 ? 0 : 1);
                val <<= 1;
            }
        }
        String payloadBits = payloadBuilder.toString();
        int payloadLength = payloadBits.length();

        // 2. 创建16位的长度信头
        String headerBits = Integer.toBinaryString(payloadLength);
        while (headerBits.length() < HEADER_LENGTH_BITS) {
            headerBits = "0" + headerBits;
        }

        // 3. 将信头和载荷拼接
        String originalBits = headerBits + payloadBits;

        Utils.debugLog("Header (length=" + payloadLength + "): " + headerBits);
        Utils.debugLog("Payload (" + messageToSend + "): " + payloadBits);
        Utils.debugLog("Full original bits to encode: " + originalBits);

        messageToSend = null; // 清空
        return originalBits;
    }

    // --- 接收端核心 (完全重写) ---
    /**
     * 将解码后的原始比特流（uncodedBits）翻译回字符串。
     * 它会先解析16位的长度信头。
     * @param uncodedBits Viterbi解码后得到的原始二进制字符串
     * @return 翻译后的字符串，或错误信息
     */
    public static String decodeBitsToString(String uncodedBits) {
        Utils.debugLog("In decodeBitsToString:"+uncodedBits);
        try {
            // 1. 检查长度是否足以包含一个信头
            if (uncodedBits == null || uncodedBits.length() < HEADER_LENGTH_BITS) {
                return "Error: Data too short for a header.";
            }

            // 2. 解析信头
            String headerBits = uncodedBits.substring(0, HEADER_LENGTH_BITS);
            int expectedPayloadLength = Integer.parseInt(headerBits, 2);

            // 3. 提取并验证载荷
            String payloadBits = uncodedBits.substring(HEADER_LENGTH_BITS);
            if (payloadBits.length() != expectedPayloadLength) {
                return "Error: Length mismatch. Header said " + expectedPayloadLength + ", got " + payloadBits.length();
            }

            // 4. 验证载荷是否是8的倍数
            if (expectedPayloadLength > 0 && expectedPayloadLength % 8 != 0) {
                return "Error: Payload length is not a multiple of 8.";
            }

            // 5. 将载荷比特翻译成字符串
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < payloadBits.length(); i += 8) {
                String charCode = payloadBits.substring(i, i + 8);
                int parsedChar = Integer.parseInt(charCode, 2);
                text.append((char) parsedChar);
            }

            Utils.debugLog("Successfully decoded string: " + text.toString());
            return text.toString();
        } catch (Exception e) {

            return "Error: Exception during string decoding.";
        }
    }
}