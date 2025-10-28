package com.example.root.ffttest2;

import android.app.Activity;
import android.util.Log;

import com.example.root.ffttest2.R;


import java.util.Arrays;

public class ChannelEstimate {


    /**
     * 【修正版】
     * 从一个包含 Preamble、Header 和 Training Symbols 的完整数据包中提取训练符号，
     * 并进行信道估计，最终返回一个推荐的频段。
     *
     * @param av                  Activity context.
     * @param full_packet_signal  接收到的完整音频信号块 (从 Preamble 开始)。
     * @param preamble_start_point Preamble 在 full_packet_signal 数组中的起始索引。
     * @param m_attempt           当前的测量尝试次数。
     * @return 一个包含 [start_bin, end_bin] 的整数数组，代表推荐的子载波索引。失败则返回空数组或特定错误值。
     */
    public static int[] extractSignal_withsymbol_helper(Activity av, double[] full_packet_signal, int preamble_start_point, int m_attempt) {

        // --- 1. 定义数据包各部分的长度（样本数） ---
        // Preamble 的长度（基于 naiser3 信号文件）
        final int preambleLen = PreambleGen.preamble_s().length;
        // Header 的长度
        final int headerLen = Constants.ADDR_SYMBOLS * (Constants.Ns + Constants.Cp);

        // --- 2. 计算训练符号部分的真实起始点 ---
        // 它在 Preamble + Header + Gap 之后
        final int training_symbols_start = preamble_start_point + preambleLen + headerLen + Constants.ChirpGap;

        // --- 3. 计算训练符号部分的结束点 ---
        final int training_symbols_end = training_symbols_start + ((Constants.Ns + Constants.Cp) * Constants.chanest_symreps) - 1;

        // --- 4. 安全检查 ---
        if (training_symbols_end >= full_packet_signal.length || training_symbols_start < 0) {
            Utils.log("!!!! ChannelEstimate Error: Calculated training symbol indices are out of bounds.");
            Utils.log("     -> preamble_start: " + preamble_start_point);
            Utils.log("     -> training_start: " + training_symbols_start);
            Utils.log("     -> training_end: " + training_symbols_end);
            Utils.log("     -> signal_length: " + full_packet_signal.length);
            return new int[]{}; // 返回空数组表示失败
        }

        // --- 5. 截取纯净的训练符号音频段 ---
        Utils.log("     ChannelEstimate: Slicing training symbols from index " + training_symbols_start + " to " + training_symbols_end);
        double[] rx_symbols = Utils.segment(full_packet_signal, training_symbols_start, training_symbols_end);

        // --- 后续的信道估计逻辑与原始代码保持完全一致 ---

        rx_symbols = Utils.div(rx_symbols, 30000);

        plotRxSyms(av, rx_symbols);

        int freqSpacing = Constants.fs / Constants.Ns;
        int[] fseq = Utils.linspace(Constants.f_range[0], freqSpacing, Constants.f_range[1]);

        double[] snrs;
        int thresh;
        Log.e("asdf","SNR METHOD "+Constants.snr_method);

        int cc = Constants.Cp;
        double[][][] spec_est = new double[2][Constants.subcarrier_number_default][Constants.chanest_symreps];

        for (int i = 0; i < Constants.chanest_symreps; i++) {
            // 安全检查，防止截取时越界
            if (cc + Constants.Ns -1 >= rx_symbols.length) {
                Utils.log("!!!! ChannelEstimate Error: Index out of bounds when segmenting for FFT.");
                return new int[]{};
            }
            double[] seg = Utils.segment(rx_symbols, cc, cc + Constants.Ns - 1);
            double[][] spec = Utils.fftcomplexoutnative_double(seg, seg.length);

            int bin_counter = 0;
            for (Integer bin : Constants.valid_carrier_default) {
                double realPart = spec[0][bin];
                double imagPart = spec[1][bin];
                spec_est[0][bin_counter][i] = realPart;
                spec_est[1][bin_counter++][i] = imagPart;
            }
            cc += Constants.Ns + Constants.Cp;
        }

        snrs = SNR_freq.calculate_snr(spec_est, Constants.pn60_syms, 0, Constants.chanest_symreps); // sym_start 设为0

        thresh = Constants.SNR_THRESH2;

        FileOperations.writetofile(MainActivity.av, Constants.snr_method + "",
                Utils.genName(Constants.SignalType.SNRMethod, m_attempt) + ".txt");

        int[] selected = Fre_adaptation.select_fre_bins(snrs, thresh);

        if (selected != null && selected.length == 2 && selected[0] != -1 && selected[1] != -1) {
            // 这部分逻辑用于确保选择的频段至少有2个bin宽，可以保留
            if (selected[1] - selected[0] == 1) {
                if (selected[0] > 0) {
                    selected[0] -= 1;
                } else {
                    selected[1] += 1;
                }
            }
            int[] freqs = new int[selected.length];
            for (int i = 0; i < selected.length; i++) {
                freqs[i] = fseq[selected[i]];
            }
            FileOperations.writetofile(MainActivity.av, freqs,
                    Utils.genName(Constants.SignalType.FreqEsts, m_attempt) + ".txt");
        }

        FileOperations.writetofile(MainActivity.av, Utils.trim(Arrays.toString(snrs)),
                Utils.genName(Constants.SignalType.SNRs, m_attempt) + ".txt");

        return selected;
    }
    public static int[] extractSignal_withsymbol_helper_old(Activity av, double[] rec, int start_point, int m_attempt) {
        int rx_preamble_start = start_point;
        rx_preamble_start+=240;

        //fix
        int headerLen = Constants.ADDR_SYMBOLS * (Constants.Ns + Constants.Cp); // 定义 Header 的长度（样本数）
        int preambleLen = PreambleGen.preamble_s().length;


        int rx_preamble_end = rx_preamble_start + (int) (((Constants.preambleTime / 1000.0) * Constants.fs)) - 1;

        if (rx_preamble_end - 1 > rec.length || rx_preamble_start < 0) {
            Utils.log("Error extracting preamble from sounding signal " + rx_preamble_start + "," + rx_preamble_end);
            return new int[]{};
        }

        ////////////////////////////////////////////////////////////////////////////////////

//        int rx_sym_start = rx_preamble_end + Constants.ChirpGap + 1;
        int rx_sym_start = rx_preamble_start + preambleLen + headerLen + Constants.ChirpGap;
        int rx_sym_end = rx_sym_start + ((Constants.Ns + Constants.Cp)* Constants.chanest_symreps) - 1;

        if (rx_sym_end - 1 > rec.length || rx_sym_start < 0) {
            Utils.log("Error extracting preamble from sounding signal");
            Utils.log(" -> rx_sym_start: " + rx_sym_start + ", rx_sym_end: " + rx_sym_end + ", rec.length: " + rec.length);
            return new int[]{};
        }

        double[] rx_symbols = Utils.segment(rec, rx_sym_start, rx_sym_end);
        rx_symbols = Utils.div(rx_symbols,30000);

        plotRxSyms(av, rx_symbols);

        int freqSpacing = Constants.fs/Constants.Ns;
        int[] fseq = Utils.linspace(Constants.f_range[0],freqSpacing,Constants.f_range[1]);

        double[] snrs=null;
        int thresh=0;
        Log.e("asdf","SNR METHOD "+Constants.snr_method);
        int cc=Constants.Cp;
        double [][][] spec_est = new double[2][Constants.subcarrier_number_default][Constants.chanest_symreps];
        for (int i = 0; i < Constants.chanest_symreps; i++) {
            Log.e("asdf","fft "+cc+","+(cc+Constants.Ns-1)+","+rx_symbols.length);
            double[] seg = Utils.segment(rx_symbols,cc,cc+Constants.Ns-1);
            double[][] spec = Utils.fftcomplexoutnative_double(seg,seg.length);

            int bin_counter=0;
            for (Integer bin : Constants.valid_carrier_default) {
                double realPart=spec[0][bin];
                double imagPart=spec[1][bin];
                spec_est[0][bin_counter][i] = realPart;
                spec_est[1][bin_counter++][i] = imagPart;
            }
            cc+=Constants.Ns+Constants.Cp;
        }

        snrs = SNR_freq.calculate_snr(spec_est, Constants.pn60_syms, 1, Constants.chanest_symreps);

        thresh=Constants.SNR_THRESH2;

        FileOperations.writetofile(MainActivity.av, Constants.snr_method + "",
                Utils.genName(Constants.SignalType.SNRMethod, m_attempt) + ".txt");

        int[] freqs = new int[]{-1,-1};
        int[] selected = null;
        selected = Fre_adaptation.select_fre_bins(snrs, thresh);
        if (Experiment.currentBandwidthMode == Experiment.BandwidthMode.FIXED) {
            // 如果当前是固定带宽模式
            Utils.debugLog("ChannelEstimate: FIXED mode detected. Overriding adaptive selection.");

            // 1. 直接从全局变量读取用户在et6/et7中设定的频率
            int start_freq = Experiment.fixedBandwidthHz;
            int end_freq = Experiment.fixedBandwidthHz;

            // 2. 将频率转换成子载波索引
            int inc = Constants.fs / Constants.Ns;
            int start_bin = start_freq / inc;
            int end_bin = end_freq / inc;

            // 3. 创建一个新的结果数组，用我们强制指定的值覆盖掉自适应算法的结果
            int[] fixed_selection = new int[]{start_bin, end_bin};

            Utils.debugLog("ChannelEstimate: Forcing selection to bins: " + start_bin + " - " + end_bin);
            Experiment.fixedBandwidthHz = start_bin;
            // 4. 返回这个被我们“篡改”过的结果
            return fixed_selection;
        }
        if (selected.length==2&&selected[0] != -1 && selected[1] != -1) {
            if (selected[1]-selected[0]==1) {
                if (selected[0] > 0) {
                    selected[0] -= 1;
                }
                else {
                    selected[1] += 1;
                }
            }
            freqs = new int[selected.length];
            for (int i = 0; i < selected.length; i++) {
                freqs[i] = fseq[selected[i]];
            }
        }

        FileOperations.writetofile(MainActivity.av, Utils.trim(Arrays.toString(snrs)),
                Utils.genName(Constants.SignalType.SNRs, m_attempt) + ".txt");
        FileOperations.writetofile(MainActivity.av, freqs,
                Utils.genName(Constants.SignalType.FreqEsts, m_attempt) + ".txt");

        return selected;
    }

    public static void plotRxSyms(Activity av, double[] rx_symbols) {
        double[] finalRx_symbols = rx_symbols;
        av.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int cc=Constants.Cp;
                for (int i = 0; i < Constants.chanest_symreps; i++) {
                    double[] seg = Utils.segment(finalRx_symbols, cc, cc + Constants.Ns - 1);
                    double[] spec = Utils.mag2db(Utils.fftnative_double(seg,seg.length));
                    if (i==0) {
                        Display.plotSpectrum(Constants.gview2, spec, true,
                                MainActivity.av.getResources().getColor(R.color.red), "");
                    }
                    else if (i==1) {
                        Display.plotSpectrum(Constants.gview2, spec, false,
                                MainActivity.av.getResources().getColor(R.color.orange), "");
                    }
                    else if (i==2) {
                        Display.plotSpectrum(Constants.gview2, spec, false,
                                MainActivity.av.getResources().getColor(R.color.yellow), "");
                    }
                    else if (i==3) {
                        Display.plotSpectrum(Constants.gview2, spec, false,
                                MainActivity.av.getResources().getColor(R.color.green), "");
                    }
                    else if (i==4) {
                        Display.plotSpectrum(Constants.gview2, spec, false,
                                MainActivity.av.getResources().getColor(R.color.blue), "");
                    }
                    else if (i==5) {
                        Display.plotSpectrum(Constants.gview2, spec, false,
                                MainActivity.av.getResources().getColor(R.color.purple), "");
                    }
                    else if (i==6) {
                        Display.plotSpectrum(Constants.gview2, spec, false,
                                MainActivity.av.getResources().getColor(R.color.black), "");
                    }
                    else {
                        Display.plotSpectrum(Constants.gview2, spec, false,
                                MainActivity.av.getResources().getColor(R.color.purple_500), "Symbol");
                    }
                    cc+=Constants.Ns+Constants.Cp;
                    break;
                }
            }
        });
    }
}
