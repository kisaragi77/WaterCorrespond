package com.example.root.ffttest2;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 一个用于管理和配置不同实验模式，并收集统计数据的静态工具类。
 */
public class Experiment {

    public static boolean onExperiment = false;

    public static int EXPECTED_MESSAGE_ID = 5;
    public static String FLAG_MSG = "Test";
    public static boolean isExperimentRunning = false; // 标志位，表示“百次实验”是否正在进行
    public static int packetsSentCount = 0;           // 当前已发送的数据包计数器
    public static int TOTAL_PACKETS_TO_SEND = 5; // 目标发送总数

    public static int sleepTime = 0;

    // --- 模式定义部分 (保持不变) ---
    public enum BandwidthMode {
        ADAPTIVE, // 自适应模式
        FIXED     // 固定带宽模式
    }

    public static BandwidthMode currentBandwidthMode = BandwidthMode.ADAPTIVE;

    public static int fixedBandwidthHz = -1;
    public static void switchBandwidthMode() {
        if (currentBandwidthMode == BandwidthMode.ADAPTIVE) {
            currentBandwidthMode = BandwidthMode.FIXED;
            fixedBandwidthHz = Constants.f_range[1];
        } else {
            currentBandwidthMode = BandwidthMode.ADAPTIVE;
            fixedBandwidthHz = -1;
        }
        Utils.debugLog("Mode switched. Current Mode: " + currentBandwidthMode + ", Fixed Bandwidth: " + fixedBandwidthHz + " Hz");
    }


    // ====================================================================
    // 【【【 新增代码：统计数据部分 】】】
    // ====================================================================

    // --- 内部数据结构 ---
    // 使用一个Map来存储每个模式(加上带宽)的统计结果

    // 一个内部类，用于存放单个配置的统计数据
    public static int totalPackets = 0;
    public static int failedPackets = 0;
    public static List<Double> selectedBitrates = new ArrayList<>(); // 只用于自适应模式

    /**
     * 【核心记录方法】
     * 在每次数据包解码尝试后调用此方法来记录结果。
     *
     * @param isSuccess     解码是否成功 (原始比特流完全匹配)
     * @param bitrateBps    本次传输的比特率 (仅在自适应模式且成功时有意义)
     */
    public static void recordPacketResult(boolean isSuccess, double bitrateBps) {

        // 3. 更新统计数据
        totalPackets++;
        if (!isSuccess) {
            failedPackets++;
        }

        // 4. 如果是自适应模式且成功，则记录比特率
        if (isSuccess) {
            selectedBitrates.add(bitrateBps);
            MainActivity.showToast("BitRate = " + bitrateBps);
        }
        MainActivity.showToast("Per = " + failedPackets + "/" + totalPackets +" = " +(double)failedPackets/totalPackets);
    }

    /**
     * 【生成报告并打印】
     * 在日志中打印出当前所有模式的统计总结。
     */

    /**
     * 【重置所有统计数据】
     * 在开始新一轮实验前调用，以清空旧数据。
     */
    public static void resetStats() {
        totalPackets = 0;
        failedPackets = 0;
        selectedBitrates.clear();
        Utils.debugLog("Experiment stats have been reset.");
    }

    /**
     * 辅助方法，根据当前实验配置生成一个唯一的标识符。
     * @return 例如 "ADAPTIVE" 或 "FIXED_1500Hz"
     */
    private static String generateKey() {
        if (currentBandwidthMode == BandwidthMode.ADAPTIVE) {
            return "ADAPTIVE";
        } else {
            return "FIXED_" + fixedBandwidthHz + "Hz";
        }
    }

    // 在 Experiment.java 中

// ... (已有的 Mode, currentMode, fixedBandwidthHz, switch... 方法保持不变)

// ====================================================================
// 【【【 新增代码：实验流程控制 】】】
// ====================================================================


    /**
     * 开始一次连续发送的实验。
     * 这个方法由UI按钮调用。
     */
    public static void startExperimentRun() {
        if (isExperimentRunning) {
            MainActivity.showToast("Experiment is already running.");
            return;
        }

        Utils.debugLog("====== BATCH EXPERIMENT STARTED ======");
        isExperimentRunning = true;
        packetsSentCount = 0;
        resetStats(); // 开始新实验前，清空旧的统计数据
        MainActivity.showToast("Experiment will start in " + sleepTime + " seconds...");
        //TODO:延迟sleepTime秒再发送第一个数据包
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isExperimentRunning) {
                    // 延迟结束后，正式触发第一个数据包的发送
                    triggerNextPacket();
                } else {
                    Utils.debugLog("Experiment was cancelled during the initial delay.");
                }
            }
        }, sleepTime * 1000L); // 第二个参数是毫秒，所以需要乘以1000
//        triggerNextPacket();
    }

    /**
     * 触发发送下一个数据包。
     * 这个方法由AsyncTask在完成上一个任务后回调。
     */
    public static void triggerNextPacket() {
        if (!isExperimentRunning) return; // 如果实验被中途停止，则不再继续

        if (packetsSentCount < TOTAL_PACKETS_TO_SEND) {
            packetsSentCount++;
            MainActivity.showToast("Sending packet " + packetsSentCount + " / " + TOTAL_PACKETS_TO_SEND);
            Utils.debugLog("Triggering packet #" + packetsSentCount);

            // 调用MainActivity中的方法来启动一个通信任务
            // 我们假设Alice总是发送固定的ID=5
            Constants.messageID = 5;
            MainActivity.startWrapper();

        } else {
            // 所有数据包都已发送完毕
            isExperimentRunning = false;
            packetsSentCount = 0;
        }
    }


    public static void printData(){
        String filename = "expr1.txt";
        Utils.debugLog("======[" + FLAG_MSG + "_" + generateKey() +"] EXPERIMENT STATS REPORT ======",'I',filename);

        if (totalPackets > 0) {
            double per = (double) (failedPackets + (TOTAL_PACKETS_TO_SEND - totalPackets)) / TOTAL_PACKETS_TO_SEND;
            Utils.debugLog(String.format("  Total Received Packets: %d", totalPackets),'I',filename);
            Utils.debugLog(String.format("  Error Packets: %d", failedPackets),'I',filename);
            Utils.debugLog(String.format("  Lost Packets: %d", TOTAL_PACKETS_TO_SEND - totalPackets),'I',filename);
            Utils.debugLog(String.format("  Packet Error Rate (PER): %.2f%%", per * 100),'I',filename);
            double avgBitrate = 0;
            if (!selectedBitrates.isEmpty()) {
                avgBitrate = 0;
                for (double bitrate : selectedBitrates) {
                    avgBitrate += bitrate;
                }
                avgBitrate /= selectedBitrates.size();
                Utils.debugLog(String.format("  Average Selected Bitrate: %.2f bps", avgBitrate),'I',filename);
            }
            MainActivity.showToast(String.format("  Packet Error Rate (PER): %.2f%%, Average Selected Bitrate: %.2f bps", per * 100,avgBitrate));
        }
        MainActivity.showToast("Done");
        Utils.debugLog("=====================================",'I',filename);
        MainActivity.showToast("Experiment finished! Report generated.");
    }
}