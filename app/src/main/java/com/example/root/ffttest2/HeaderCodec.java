package com.example.root.ffttest2;

/**
 * HeaderCodec (Header Encoder/Decoder)
 *
 * 负责将源地址和目标地址编码成一段音频信号（地址头），
 * 以及从音频信号中解码出地址信息。
 *
 * 采用FSK（频移键控）风格的调制，每个比特使用一个单音OFDM符号，
 * 以确保在未知信道下的高鲁棒性。
 */
public class HeaderCodec {

    /**
     * 将源地址和目标地址编码成一个音频信号段 (Header).
     *
     * @param sourceId  发送方的ID.
     * @param destId    接收方的ID.
     * @return 一个 short[] 数组，代表可以直接播放的PCM音频信号。
     */
    public static short[] encodeHeader(int sourceId, int destId) {
        // 1. 将整数ID转换为一个总的比特数组
        final int totalBits = Constants.ADDR_BITS * 2;
        boolean[] bits = new boolean[totalBits];

        // 逐位提取 sourceId 和 destId 的比特
        for (int i = 0; i < Constants.ADDR_BITS; i++) {
            // 从最高位开始提取
            bits[i] = ((sourceId >> (Constants.ADDR_BITS - 1 - i)) & 1) == 1;
            bits[i + Constants.ADDR_BITS] = ((destId >> (Constants.ADDR_BITS - 1 - i)) & 1) == 1;
        }

        // 2. 准备信号缓冲区
        final int samplesPerSymbol = Constants.Ns + Constants.Cp;
        short[] headerSignal = new short[totalBits * samplesPerSymbol];
        int currentSampleIndex = 0;

        // 3. 为每一个比特生成一个单音OFDM符号
        for (boolean bit : bits) {
            // 根据比特值选择频率
            int frequency = Constants.ADDR_BASE_FREQ;
            if (bit) { // 比特为 '1'
                frequency += Constants.ADDR_FREQ_STEP;
            }

            // --- 生成一个OFDM符号 ---
            double[] symbolTimeDomain = new double[Constants.Ns];
            for (int t = 0; t < Constants.Ns; t++) {
                // 生成纯正弦波: sin(2 * pi * f * t)
                // t = sample_index / sampling_frequency
                symbolTimeDomain[t] = Math.sin(2.0 * Math.PI * frequency * ((double)t / Constants.fs));
            }

            // --- 转换为 short 格式并添加循环前缀 (CP) ---
            short[] symbolWithCp = new short[samplesPerSymbol];
            // 填充数据部分，并进行幅度缩放以防止削波
            for (int t = 0; t < Constants.Ns; t++) {
                symbolWithCp[Constants.Cp + t] = (short)(symbolTimeDomain[t] * 16383); // 使用约 0.5 的最大幅度
            }
            // 从数据末尾复制数据到开头，形成循环前缀
            System.arraycopy(symbolWithCp, Constants.Ns, symbolWithCp, 0, Constants.Cp);

            // --- 将生成的符号拼接到最终的 Header 信号中 ---
            System.arraycopy(symbolWithCp, 0, headerSignal, currentSampleIndex, samplesPerSymbol);
            currentSampleIndex += samplesPerSymbol;
        }

        return headerSignal;
    }

    /**
     * 从给定的音频信号段中解码出源地址和目标地址.
     *
     * @param headerSignal 一个 double[] 数组，代表从录音中截取出的Header音频段。
     * @return 一个包含 [sourceId, destId] 的整数数组。如果解码失败（如长度不匹配），则返回 null。
     */
// 在 HeaderCodec.java 中

    /**
     * 从给定的音频信号段中解码出源地址和目标地址.
     *
     * @param headerSignal 一个 double[] 数组，代表从录音中截取出的Header音频段。
     * @return 一个包含 [sourceId, destId] 的整数数组。如果解码失败（如长度不匹配），则返回 null。
     */
    public static int[] decodeHeader(double[] headerSignal) {
        final int samplesPerSymbol = Constants.Ns + Constants.Cp;
        final int expectedTotalBits = Constants.ADDR_BITS * 2;

        // --- 【新增日志 1: 概览信息】 ---
        Utils.log("========== Header Decode Start ==========");
        Utils.log("Input signal length: " + headerSignal.length);
        Utils.log("Expected total bits: " + expectedTotalBits);
        Utils.log("Samples per symbol: " + samplesPerSymbol);

        // 1. 安全检查: 信号长度是否符合预期？
        if (headerSignal.length % samplesPerSymbol != 0) {
            Utils.log("HeaderCodec Error: Signal length is not a valid multiple of symbol length. Aborting.");
            return null;
        }

        final int actualTotalSymbols = headerSignal.length / samplesPerSymbol;
        Utils.log("Actual symbols found in signal: " + actualTotalSymbols);
        if (actualTotalSymbols != expectedTotalBits) {
            Utils.log("HeaderCodec Error: Number of symbols (" + actualTotalSymbols + ") does not match expected (" + expectedTotalBits + "). Aborting.");
            return null;
        }

        boolean[] decodedBits = new boolean[expectedTotalBits];
        int currentSampleIndex = 0;

        // 用于构建日志的 StringBuilder
        StringBuilder bitsLog = new StringBuilder("Decoded bits: ");

        // 2. 逐个符号进行解码
        for (int i = 0; i < expectedTotalBits; i++) {
            // 截取一个OFDM符号的数据部分 (去除CP)
            double[] symbolData = Utils.segment(headerSignal, currentSampleIndex + Constants.Cp, currentSampleIndex + samplesPerSymbol - 1);

            // 3. FFT变换
            double[] spectrum = Utils.fftnative_double(symbolData, symbolData.length);

            // 4. 计算目标频率对应的FFT仓 (bin) 的索引
            final int fftSize = symbolData.length;
            int bin_for_freq0 = (int) Math.round((double)Constants.ADDR_BASE_FREQ * fftSize / Constants.fs);
            int bin_for_freq1 = (int) Math.round((double)(Constants.ADDR_BASE_FREQ + Constants.ADDR_FREQ_STEP) * fftSize / Constants.fs);

            // 5. 比较能量并决策比特值
            double energy_at_freq0 = spectrum[bin_for_freq0];
            double energy_at_freq1 = spectrum[bin_for_freq1];

            if (energy_at_freq1 > energy_at_freq0) {
                decodedBits[i] = true; // '1'
                bitsLog.append("1");
            } else {
                decodedBits[i] = false; // '0'
                bitsLog.append("0");
            }

            // --- 【新增日志 2: 循环内详细信息】 ---
            String logLine = String.format(
                    "Bit #%02d | fftSize:%d | Bin0:%d (%.2f) | Bin1:%d (%.2f) | Decoded: %d",
                    i,
                    fftSize,
                    bin_for_freq0, energy_at_freq0,
                    bin_for_freq1, energy_at_freq1,
                    decodedBits[i] ? 1 : 0
            );
            Utils.log(logLine);

            currentSampleIndex += samplesPerSymbol;
        }

        // 在源地址和目标地址之间加一个空格，便于阅读
        bitsLog.insert(Constants.ADDR_BITS, ' ');
        Utils.log(bitsLog.toString());

        // 6. 将解码出的比特数组转换回整数ID
        int sourceId = 0;
        int destId = 0;
        for (int i = 0; i < Constants.ADDR_BITS; i++) {
            if (decodedBits[i]) {
                sourceId |= (1 << (Constants.ADDR_BITS - 1 - i));
            }
            if (decodedBits[i + Constants.ADDR_BITS]) {
                destId |= (1 << (Constants.ADDR_BITS - 1 - i));
            }
        }

        // --- 【新增日志 3: 最终结果】 ---
        Utils.log("Decoded Source ID: " + sourceId);
        Utils.log("Decoded Dest ID: " + destId);
        Utils.log("=========== Header Decode End ===========");

        return new int[]{sourceId, destId};
    }
}