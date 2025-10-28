package com.example.root.ffttest2;

public class PreambleGen {

    public static short[] sounding_signal_s_old() {
        return SymbolGeneration.generatePreamble(Constants.pn60_bits, Constants.valid_carrier_data,
                Constants.chanest_symreps, true, Constants.SignalType.Sounding); }

    public static short[] preamble_s() {
        return Utils.convert_s(preamble_d());
    }

    public static double[] preamble_d() { return (Constants.naiser); }

    public static short[] generateTrainingSymbolsOnly() {
        // 这个函数就是原来 sounding_signal_s 逻辑中，去除Preamble拼接的部分
        // 它只负责生成用于信道估计的OFDM符号串
        return SymbolGeneration.generatePreamble(Constants.pn60_bits, Constants.valid_carrier_data,
                Constants.chanest_symreps, false, Constants.SignalType.Sounding); // 注意 preamble 参数为 false
    }


    public static short[] sounding_signal_s() {
        // 旧的完整探测信号
        short[] preamble = preamble_s();
        short[] training = generateTrainingSymbolsOnly();
        return Utils.concat_short(preamble, training);
    }
}

