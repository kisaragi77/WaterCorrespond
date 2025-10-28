package com.example.root.ffttest2;

import static com.example.root.ffttest2.Constants.LOG;

import android.util.Log;

import com.example.root.ffttest2.R;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;

public class FeedbackSignal {



    /**
     * 【新函数】仅编码反馈信息本身，生成一个双音OFDM符号的音频信号。
     * 这个函数不包含 Preamble 或 Header。
     *
     * @param fbegin_idx 最佳频段的起始子载波索引 (相对于 f_range[0] 的偏移量)。
     * @param fend_idx   最佳频段的结束子载波索引 (相对于 f_range[0] 的偏移量)。
     * @return 一个 short[] 数组，代表可以直接播放的双音OFDM符号的PCM音频信号。
     */
    public static short[] encodeFeedbackSymbolOnly(int fbegin_idx, int fend_idx) {

        // -----------------------------------------------------------------
        // 1. 将子载波索引转换为实际的物理频率 (Hz)
        // -----------------------------------------------------------------
        // f_begin 和 f_end 是相对于 f_range[0] 的偏移索引。
        // 真实频率 = 基频 + 索引 * 子载波间隔
        int fbegin_hz = Constants.f_range[0] + (fbegin_idx * Constants.inc);
        int fend_hz   = Constants.f_range[0] + (fend_idx * Constants.inc);

        // 创建一个包含这两个目标频率的数组
        int[] freqs_to_encode = new int[]{fbegin_hz, fend_hz};

        // -----------------------------------------------------------------
        // 2. 准备OFDM符号的缓冲区
        // -----------------------------------------------------------------
        // 反馈符号的长度在 Constants.java 中定义，例如 fbackTime = 20ms。
        // 这里我们直接使用一个标准OFDM符号的长度，即 Ns+Cp。
        final int symbolLengthWithCp = Constants.Ns + Constants.Cp;
        double[] symbolTimeDomain = new double[symbolLengthWithCp];

        // -----------------------------------------------------------------
        // 3. 在时域上合成双音信号
        // -----------------------------------------------------------------
        // 遍历每一个要编码的频率
        for (int freq : freqs_to_encode) {
            // 遍历OFDM符号的每一个采样点 (包括CP区域)
            for (int i = 0; i < symbolLengthWithCp; i++) {
                // 计算当前采样点的时间 t = i / fs
                double time_sec = (double) i / Constants.fs;

                // 计算该频率在该时间点的正弦波值，并累加到缓冲区
                // 幅度除以编码的频率数量(这里是2)，以防止信号叠加后幅度过大导致削波
                symbolTimeDomain[i] += Math.sin(2.0 * Math.PI * freq * time_sec) / freqs_to_encode.length;
            }
        }

        // --- 另一种更符合OFDM思想的生成方式 (可选，但更标准) ---
        // a. 在频域上构造信号
        // double[][] spectrum = new double[2][Constants.Ns]; // [real, imag]
        // int bin1 = (int)Math.round((double)fbegin_hz / Constants.inc);
        // int bin2 = (int)Math.round((double)fend_hz / Constants.inc);
        // spectrum[0][bin1] = 1.0; // 在对应bin上设置一个实部为1的脉冲
        // spectrum[0][bin2] = 1.0;
        //
        // b. IFFT到时域
        // double[][] symbolIFFT = Utils.ifftnative2(spectrum);
        // double[] symbolTimeDomainFromIFFT = symbolIFFT[0]; // 取实部
        //
        // c. 添加循环前缀...
        // 这种方法更严谨，但我们上面的时域直接合成法在效果上是等价且更易理解的。

        // -----------------------------------------------------------------
        // 4. 将 double[] 信号转换为 short[] PCM 数据
        // -----------------------------------------------------------------
        short[] finalSymbolSignal = new short[symbolLengthWithCp];
        for (int i = 0; i < symbolLengthWithCp; i++) {
            // 乘以 short 类型的最大值的一半 (16383) 作为幅度缩放
            // 留出一半的动态范围以防万一
            finalSymbolSignal[i] = (short)(symbolTimeDomain[i] * 16383.0);
        }

        return finalSymbolSignal;
    }



//    public static int[] extractSignalHelper(double[] rec, int start_point, int m_attempt) {
//        double[] preamble = PreambleGen.preamble_d();
//        int end_point = start_point+preamble.length-1;
//        Log.e("extract",start_point+","+end_point+","+rec.length);
//        if (end_point-1 > rec.length || start_point < 0) {
//            Utils.log("Error extracting preamble from feedback signal");
//            FileOperations.writetofile(MainActivity.av, new int[]{-1,-1},
//                    Utils.genName(Constants.SignalType.FeedbackFreqs,m_attempt)+".txt");
//            return new int[]{-1,-1};
//        }
//        double[] preamble_rx = Utils.segment(rec, start_point, end_point);
//
//
//        //////////////////////////////////////////////////////////////////////////
//        // fix 1
//        int headerLen = Constants.ADDR_SYMBOLS * (Constants.Ns + Constants.Cp);
////        int rec_start = start_point+preamble.length+Constants.ChirpGap+1;
////        int rec_start = start_point + preamble.length + headerLen + Constants.ChirpGap + 1;
//        int rec_start = start_point + PreambleGen.preamble_s().length + headerLen + Constants.ChirpGap;
//        int rec_end = rec_start+(int)((Constants.fbackTime/1000.0)*Constants.fs)-1;
//        int rec_len = (rec_end - rec_start)+1;
//        Log.e(LOG, rec.length+","+rec_start+","+rec_end+","+rec_len);
//
//        if (rec_end-1 > rec.length || rec_start < 0) {
//            Utils.log("Error extracting feedback from feedback signal");
//            FileOperations.writetofile(MainActivity.av, new int[]{-1,-1},
//                    Utils.genName(Constants.SignalType.FeedbackFreqs,m_attempt)+".txt");
//            return new int[]{-1,-1};
//        }
//        double[] feedback = Utils.segment(rec, rec_start-1, rec_end-1);
//
//        int[] freqs = parse_signal(preamble_rx, feedback);
//        if (freqs.length == 2 && freqs[0] != -1) {
//            FileOperations.writetofile(MainActivity.av, freqs,
//                    Utils.genName(Constants.SignalType.FeedbackFreqs,m_attempt)+".txt");
//
//            freqs[0]=(int)Math.ceil(freqs[0]/(double)Constants.inc)*Constants.inc;
//            freqs[1]=(int)Math.floor(freqs[1]/(double)Constants.inc)*Constants.inc;
//
//            int[] freqs_all = expand_freqs(freqs);
//
//            FileOperations.writetofile(MainActivity.av, freqs_all,
//                    Utils.genName(Constants.SignalType.ExactFeedbackFreqs,m_attempt)+".txt");
//
//            int[] bins_all = Utils.freqs2bins(freqs_all);
//
//            return bins_all;
//        }
//        else {
//            FileOperations.writetofile(MainActivity.av, new int[]{-1,-1},
//                    Utils.genName(Constants.SignalType.FeedbackFreqs,m_attempt)+".txt");
//            return new int[]{-1, -1};
//        }
//    }
    /**
     * 【修正版】
     * 从一个包含 Preamble、Header 和 Feedback Symbol 的完整反馈包中，
     * 提取出双音反馈符号，并解码出频段信息。
     *
     * @param rec          接收到的完整反馈包音频信号。
     * @param start_point  Preamble 在 rec 数组中的起始索引。
     * @param m_attempt    当前的测量尝试次数。
     * @return 一个包含 [start_bin, ..., end_bin] 的整数数组。解码失败则返回 [-1, -1]。
     */
    public static int[] extractSignalHelper(double[] rec, int start_point, int m_attempt) {

        // --- 1. 定义数据包各部分的长度（样本数） ---
        final int preambleLen = PreambleGen.preamble_s().length;
        final int headerLen = Constants.ADDR_SYMBOLS * (Constants.Ns + Constants.Cp);

        // --- 2. 计算 Feedback Symbol 的真实起始点 ---
        // 它在 Preamble + Header + Gap 之后
        final int rec_start = start_point + preambleLen + headerLen + Constants.ChirpGap;

        // --- 3. 计算 Feedback Symbol 的结束点 ---
        // fbackTime 定义了反馈符号的持续时间
        final int rec_end = rec_start + (int)((Constants.fbackTime / 1000.0) * Constants.fs) - 1;

        Log.e("extract", "Preamble start: " + start_point + ", Feedback symbol start: " + rec_start + ", End: " + rec_end);

        // --- 4. 安全检查 ---
        if (rec_end >= rec.length || rec_start < 0) {
            Utils.log("!!!! FeedbackSignal Error: Calculated feedback symbol indices are out of bounds.");
            return new int[]{-1, -1};
        }

        // --- 5. 截取纯净的 Feedback Symbol 音频段 ---
        double[] feedback = Utils.segment(rec, rec_start, rec_end);

        // --- 后续的解码逻辑保持不变 ---
        // (注意：parse_signal 现在接收的是一个只包含双音符号的、纯净的信号段)
        int[] freqs = parse_signal_simplified(feedback); // 使用一个简化的 parse_signal

        if (freqs != null && freqs.length == 2 && freqs[0] != -1) {
            FileOperations.writetofile(MainActivity.av, freqs,
                    Utils.genName(Constants.SignalType.FeedbackFreqs, m_attempt) + ".txt");

            // 将频率转换为子载波索引
            int start_bin = (freqs[0] - Constants.f_range[0]) / Constants.inc;
            int end_bin = (freqs[1] - Constants.f_range[0]) / Constants.inc;

            // 为了与原始代码的返回格式兼容，我们返回一个包含所有bin的数组
            int[] bins_all = new int[end_bin - start_bin + 1];
            for (int i = 0; i < bins_all.length; i++) {
                bins_all[i] = start_bin + i;
            }

            return bins_all;
        } else {
            FileOperations.writetofile(MainActivity.av, new int[]{-1, -1},
                    Utils.genName(Constants.SignalType.FeedbackFreqs, m_attempt) + ".txt");
            return new int[]{-1, -1};
        }
    }

    /**
     * 【新增】一个简化的 parse_signal 版本，直接处理纯净的 feedback 信号。
     * @param feedback 只包含双音符号的音频信号段。
     * @return 一个包含 [freq1, freq2] 的数组，失败则返回 [-1, -1]。
     */
    private static int[] parse_signal_simplified(double[] feedback) {
        Log.e("FeedbackSignal", "Parsing simplified feedback signal...");

        double[] feedback_spec = Utils.fftnative_double(feedback, feedback.length);
        double[] feedback_spec_db = Utils.mag2db(feedback_spec);

        int[] freqs = decodeFeedbackSignal(feedback_spec_db);

        if (freqs != null && freqs.length == 2) {
            Utils.log("feedback freqs " + freqs[0] + "," + freqs[1]);
        } else {
            Utils.log("no frequencies selected or error in decodeFeedbackSignal");
        }

        // (可选) 在UI上绘制频谱图用于调试
        (MainActivity.av).runOnUiThread(() -> {
            String title = "Rx Feedback";
            if(freqs != null && freqs.length == 2 && freqs[0] != -1) {
                title += " (" + freqs[0] + " Hz, " + freqs[1] + " Hz)";
            }
            Display.plotSpectrum(Constants.gview2, feedback_spec_db, true, MainActivity.av.getResources().getColor(R.color.purple_500), title);
        });

        return freqs;
    }
    public static int[] expand_freqs(int[] freqs) {
        int freqSpacing = Constants.fs/Constants.Ns;
        int numbins = (freqs[freqs.length-1]-freqs[0])/freqSpacing;

        int[] out = new int[numbins+1];
        for (int i = 0; i <= numbins; i++) {
            out[i] = freqs[0]+(i*freqSpacing);
        }
        return out;
    }

    public static short[] encodeFeedbackSignal(int fbegin, int fend, int len_ms, boolean preamble, int m_attempt) {
        int len = (int)((len_ms/1000.0)*Constants.fs);
        if (preamble) {
            len += ((Constants.preambleTime/1000.0)*Constants.fs)+Constants.ChirpGap;
        }
        short[] txsig = new short[len];

        int counter = 0;
        if (preamble) {
            for (Short s : PreambleGen.preamble_s()) {
                txsig[counter++] = s;
            }
            counter += Constants.ChirpGap;
        }

        fbegin=Constants.f_range[0]+(fbegin*Constants.inc);
        fend=Constants.f_range[0]+(fend*Constants.inc);

        fbegin=Math.round(fbegin/10)*10;
        fend=Math.round(fend/10)*10;

        // encode the feedback frequencies
        int freqs[] = new int[]{fbegin,fend};
        int fbackLen=(int)((Constants.fbackTime/1000.0)*Constants.fs);
        for (int freq = 0; freq < freqs.length; freq++) {
            int ff = freqs[freq];
            for (int i = counter; i < len; i++) {
                txsig[i] += (Math.sin(2.0 * Math.PI * ff * ((double)i / Constants.fs)))*(32767/2);
            }
        }

        short[] feedback = new short[fbackLen];
        for (int i = 0; i < feedback.length; i++) {
            feedback[i] = txsig[counter++];
        }

        double[] spec_fback = Utils.fftnative_short(feedback, feedback.length);
        double[] spec_fback_db = Utils.mag2db(spec_fback);

        FileOperations.writetofile(MainActivity.av, txsig, Utils.genName(Constants.SignalType.Feedback,m_attempt)+".txt");

        // plot transmitted frequencies
        int finalFbegin = fbegin;
        int finalFend = fend;
        MainActivity.av.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Display.plotSpectrum(Constants.gview3, spec_fback_db, true, MainActivity.av.getResources().getColor(com.example.root.ffttest2.R.color.purple_500),
                        "Tx feedback "+finalFbegin+","+finalFend);
                Display.plotVerticalLine(Constants.gview3, Constants.f_seq.get(Constants.nbin1_chanest -2));
                Display.plotVerticalLine(Constants.gview3, Constants.f_seq.get(Constants.nbin2_chanest +2));
            }
        });

        return txsig;
    }

    public static int[] parse_signal(double[] preamble, double[] feedback) {
        Log.e(LOG,"FeedbackSignal_parse_signal");

        double[] preamble_spec = Utils.fftnative_double(preamble, preamble.length);

        double[] feedback_spec = Utils.fftnative_double(feedback, feedback.length);

        double[] preamble_spec_db = Utils.mag2db(preamble_spec);
        double[] feedback_spec_db = Utils.mag2db(feedback_spec);

        int[] freqs= decodeFeedbackSignal(feedback_spec_db);

        if (freqs.length==2) {
            Utils.log("feedback freqs " + freqs[0] + "," + freqs[freqs.length - 1]);
        }
        else {
            Utils.log("no frequencies selected");
        }

        (MainActivity.av).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Constants.gview2.removeAllSeries();
                Constants.gview3.removeAllSeries();
                Constants.gview2.setTitle("");
                Constants.gview3.setTitle("");
                Display.plotSpectrum(Constants.gview, preamble_spec_db, true, MainActivity.av.getResources().getColor(R.color.purple_500),"");

                Display.plotVerticalLine(Constants.gview, Constants.f_seq.get(Constants.nbin1_chanest));
                Display.plotVerticalLine(Constants.gview, Constants.f_seq.get(Constants.nbin2_chanest));

                if (freqs.length==2) {
                    Display.plotSpectrum(Constants.gview2, feedback_spec_db, true, MainActivity.av.getResources().getColor(R.color.purple_500),
                            "Rx Feedback " + freqs[0] + "," + freqs[freqs.length - 1]);
                }
                else {
                    Display.plotSpectrum(Constants.gview2, feedback_spec_db, true, MainActivity.av.getResources().getColor(R.color.purple_500),
                            "Rx Feedback");
                }

                Display.plotVerticalLine(Constants.gview2, Constants.f_seq.get(Constants.nbin1_default -2));
                Display.plotVerticalLine(Constants.gview2, Constants.f_seq.get(Constants.nbin2_default +2));
            }
        });

        return freqs;
    }

//    public static int[] decodeFeedbackSignal(double[] feedback_spec_db) {
//
//        LinkedList<Bin> bins = new LinkedList<>();
//        double[] smooth_sig = feedback_spec_db;
//
//        int feedbackFreqSpacing = Constants.fs/feedback_spec_db.length;
//        int startIdx = Constants.f_range[0]/feedbackFreqSpacing;
//        int endIdx = Constants.f_range[1]/feedbackFreqSpacing;
//
//        for (int i = startIdx; i < endIdx; i++) {
//            int freq = i*feedbackFreqSpacing;
//
//            double signal = smooth_sig[i];
//            double[] noise1 = Utils.segment(smooth_sig,i-5,i-2);
//            double[] noise2 = Utils.segment(smooth_sig,i+2,i+5);
//
//            double val=0;
//            for(Double d : noise1) {
//                val+=d;
//            }
//            for(Double d : noise2) {
//                val+=d;
//            }
//            double noise = val / (noise1.length+noise2.length);
//
//            double snr = signal-noise;
//            double prom = getProm(smooth_sig,i,i-2,i+2);
//
//            if (snr >= Constants.FEEDBACK_SNR_THRESH) {
//                bins.add(new Bin(freq, snr, signal, noise, prom));
//            }
//        }
//
//        Collections.sort(bins, new Comparator<Bin>() {
//            @Override
//            public int compare(Bin c1, Bin c2) {
//                double met1 = c1.prom+c1.snr;
//                double met2 = c2.prom+c2.snr;
//                if (met1 > met2) {return 1;}
//                else if (met1 == met2) {return 0;}
//                else {return -1;}
//            }
//        });
//
//        LinkedList<Integer> remove=new LinkedList<>();
//        for (int i = bins.size()-1; i >= 1; i--) {
//            if (Math.abs(bins.get(i).freq - bins.get(i-1).freq)==feedbackFreqSpacing) {
//                if (bins.get(i).snr > bins.get(i-1).snr) {
//                    remove.add(i);
//                }
//                else {
//                    remove.add(i-1);
//                }
//            }
//        }
//
//        for (Integer i : remove) {
//            bins.remove(bins.get(i));
//        }
//
//        if (bins.size() >= 2) {
//            int f1 = bins.get(bins.size() - 1).freq;
//            int f2 = bins.get(bins.size() - 2).freq;
//            double s1 = bins.get(bins.size() - 1).snr;
//            double s2 = bins.get(bins.size() - 2).snr;
//
//            Bin nf1 = search(bins, f1 / 2);
//            Bin nf2 = search(bins, f2 / 2);
//            if (nf1 != null && nf1.snr > s1) {
//                f1 = nf1.freq;
//                s1 = nf1.snr;
//            }
//            if (nf2 != null && nf2.snr > s2) {
//                f2 = nf2.freq;
//                s2 = nf2.snr;
//            }
//
//            if (s1 >= Constants.FEEDBACK_SNR_THRESH && s2 >= Constants.FEEDBACK_SNR_THRESH) {
//                if (f1 > f2) {
//                    return new int[]{f2, f1};
//                }
//                return new int[]{f1, f2};
//            }
//        }
//        else if (bins.size() == 1) {
//            return new int[]{bins.get(0).freq,bins.get(0).freq};
//        }
//        return new int[]{-1,-1};
//    }
// 在 FeedbackSignal.java 中
// 在 FeedbackSignal.java 中

    /**
     * 【最终修正版 V3】
     * 从给定的频谱图中解码出双音频率。
     * 这个版本采用更简单的局部最大值查找，并结合一个全局阈值，以提高鲁棒性。
     *
     * @param feedback_spec_db 接收到的反馈信号的幅度谱 (dB单位)。
     * @return 一个包含 [freq1, freq2] 的数组，失败则返回 [-1, -1]。
     */
    public static int[] decodeFeedbackSignal(double[] feedback_spec_db) {

        LinkedList<Bin> candidate_peaks = new LinkedList<>();
        int freqSpacing = Constants.fs / feedback_spec_db.length;

        // -----------------------------------------------------------------
        // 步骤 1: 寻找所有局部最大值 (Local Maxima)
        // -----------------------------------------------------------------
        // 设定一个绝对的幅度阈值，低于此值的点直接忽略
        // 从您的截图中看，噪声基线在 90-100 左右，峰值在 120 左右。
        // 设定一个 105dB 的阈值应该可以有效过滤噪声。
        double absolute_threshold = 105.0;

        int search_start_idx = Constants.f_range[0] / freqSpacing;
        int search_end_idx = Constants.f_range[1] / freqSpacing;

        // 寻找所有高于阈值的局部峰值
        for (int i = search_start_idx + 1; i < search_end_idx - 1; i++) {
            double current_val = feedback_spec_db[i];
            double prev_val = feedback_spec_db[i-1];
            double next_val = feedback_spec_db[i+1];

            // 判断条件：当前点高于阈值，并且比它紧邻的左右两点都高
            if (current_val > absolute_threshold && current_val > prev_val && current_val > next_val) {
                int freq = i * freqSpacing;
                // 使用信号的绝对强度作为排序依据
                candidate_peaks.add(new Bin(freq, current_val, 0, 0, 0));
            }
        }

        Utils.log("decodeFeedbackSignal: Found " + candidate_peaks.size() + " candidate peaks above threshold " + absolute_threshold);
        if (candidate_peaks.size() < 2) {
            Utils.log("decodeFeedbackSignal: Not enough candidate peaks found.");
            return new int[]{-1, -1};
        }

        // -----------------------------------------------------------------
        // 步骤 2: 对候选峰值进行排序和筛选
        // -----------------------------------------------------------------
        // 按信号强度 (snr字段现在存的是强度) 从高到低排序
        Collections.sort(candidate_peaks, (b1, b2) -> Double.compare(b2.snr, b1.snr));

        // 筛选出最强的两个峰值，并确保它们之间有足够的距离
        LinkedList<Bin> top_peaks = new LinkedList<>();
        top_peaks.add(candidate_peaks.get(0)); // 最强的峰值肯定入选

        for (int i = 1; i < candidate_peaks.size(); i++) {
            Bin current_peak = candidate_peaks.get(i);
            // 确保当前峰值与已选的最强峰值距离足够远
            if (Math.abs(current_peak.freq - top_peaks.get(0).freq) > Constants.ADDR_FREQ_STEP / 2.0) {
                top_peaks.add(current_peak);
                break; // 已经找到两个相距足够远的峰值
            }
        }

        if (top_peaks.size() < 2) {
            Utils.log("decodeFeedbackSignal: Found peaks, but they are too close to each other.");
            return new int[]{-1, -1};
        }

        // -----------------------------------------------------------------
        // 步骤 3: 返回结果
        // -----------------------------------------------------------------
        int f1 = top_peaks.get(0).freq;
        int f2 = top_peaks.get(1).freq;

        // 排序后返回
        return (f1 > f2) ? new int[]{f2, f1} : new int[]{f1, f2};
    }

    /**
     * 【修正版 V2】
     * 从给定的频谱图中解码出双音频率。
     * 这个版本更鲁棒，它会在一个小的频率窗口内搜索峰值，以对抗多普勒频移。
     *
     * @param feedback_spec_db 接收到的反馈信号的幅度谱 (dB单位)。
     * @return 一个包含 [freq1, freq2] 的数组，失败则返回 [-1, -1]。
     */
    public static int[] decodeFeedbackSignal_v2(double[] feedback_spec_db) {

        LinkedList<Bin> bins = new LinkedList<>();
        int feedbackFreqSpacing = Constants.fs / feedback_spec_db.length;

        // -----------------------------------------------------------------
        // 步骤 1: 寻找所有可能的候选峰值
        // -----------------------------------------------------------------
        // 遍历整个感兴趣的频段
        int startIdx = Constants.f_range[0] / feedbackFreqSpacing;
        int endIdx = Constants.f_range[1] / feedbackFreqSpacing;

        for (int i = startIdx; i < endIdx; i++) {
            // 计算当前点的 SNR
            double signal = feedback_spec_db[i];
            // 计算局部噪声基线 (周围几个点的平均值)
            double noise = 0;
            int noise_window = 5;
            if (i > noise_window && i < feedback_spec_db.length - noise_window) {
                noise = (Utils.mean(feedback_spec_db, i - noise_window, i-2) +
                        Utils.mean(feedback_spec_db, i + 2, i + noise_window)) / 2.0;
            }

            double snr = signal - noise;

            // 如果信噪比超过阈值，则认为是一个潜在的峰值
            if (snr >= Constants.FEEDBACK_SNR_THRESH) {
                int freq = i * feedbackFreqSpacing;
                bins.add(new Bin(freq, snr, signal, noise, 0)); // prom暂时不用
            }
        }

        if (bins.size() < 2) {
            Utils.log("decodeFeedbackSignal: Not enough candidate peaks found (" + bins.size() + "). Threshold might be too high.");
            return new int[]{-1, -1};
        }

        // -----------------------------------------------------------------
        // 步骤 2: 对候选峰值进行排序和筛选
        // -----------------------------------------------------------------

        // 按 SNR 从高到低排序
        Collections.sort(bins, new Comparator<Bin>() {
            @Override
            public int compare(Bin b1, Bin b2) {
                return Double.compare(b2.snr, b1.snr); // 降序排序
            }
        });

        // 筛选出最强的两个峰值，并确保它们之间有足够的距离，以避免选择同一个宽峰的两个点
        LinkedList<Bin> top_peaks = new LinkedList<>();
        top_peaks.add(bins.get(0)); // 最强的峰值肯定入选

        for (int i = 1; i < bins.size(); i++) {
            Bin current_peak = bins.get(i);
            boolean isFarEnough = true;
            for (Bin selected_peak : top_peaks) {
                // 如果当前峰值与已选峰值的频率太近，则忽略
                if (Math.abs(current_peak.freq - selected_peak.freq) < Constants.ADDR_FREQ_STEP / 2.0) {
                    isFarEnough = false;
                    break;
                }
            }
            if (isFarEnough) {
                top_peaks.add(current_peak);
                if (top_peaks.size() == 2) {
                    break; // 已经找到两个相距足够远的峰值
                }
            }
        }

        if (top_peaks.size() < 2) {
            Utils.log("decodeFeedbackSignal: Found peaks, but they are too close to each other.");
            return new int[]{-1, -1};
        }

        // -----------------------------------------------------------------
        // 步骤 3: 返回结果
        // -----------------------------------------------------------------
        int f1 = top_peaks.get(0).freq;
        int f2 = top_peaks.get(1).freq;

        // 排序后返回
        if (f1 > f2) {
            return new int[]{f2, f1};
        }
        return new int[]{f1, f2};
    }
    public static Bin search(LinkedList<Bin> bins, int freq) {
        for (Bin bin : bins) {
            if (bin.freq == freq) {
                return bin;
            }
        }
        return null;
    }

    private static class Bin {
        int freq;
        double snr;
        double signal;
        double noise;
        double prom;
        public Bin(int freq, double snr, double signal, double noise, double prom) {
            this.freq = freq;
            this.snr = snr;
            this.signal = signal;
            this.noise = noise;
            this.prom = prom;
        }
    }

    public static double getProm(double[] ar, int peakloc, int beginloc, int endloc) {
        double peakval = ar[peakloc];
        int leftmarker = 0;
        int rightmarker = endloc;
        for (int i = peakloc-1; i >= beginloc ; i--) {
            if (ar[i] >= peakval) {
                leftmarker=i;
                break;
            }
        }
        for(int i = peakloc+1; i < endloc; i++) {
            if (ar[i] >= peakval) {
                rightmarker=i;
                break;
            }
        }

        double leftmin=ar[beginloc];
        double rightmin=ar[endloc];
        for(int i = beginloc; i < leftmarker; i++) {
            if (ar[i] < leftmin) {
                leftmin=ar[i];
            }
        }

        for(int i = rightmarker; i<endloc; i++) {
            if (ar[i] < rightmin) {
                rightmin=ar[i];
            }
        }

        double ref = leftmin > rightmin ? leftmin : rightmin;

        double out = peakval - ref;

        return out;
    }
}
