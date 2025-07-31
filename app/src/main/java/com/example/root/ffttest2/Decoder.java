package com.example.root.ffttest2;

import android.app.Activity;
import android.util.Log;

import com.example.root.ffttest2.R;

public class Decoder {
//    public static void decode_helper(Activity av, double[] data, int[] valid_bins) {
//         data = Utils.filter(data);
//
//        valid_bins[0]=valid_bins[0]+Constants.nbin1_default;
//        valid_bins[1]=valid_bins[1]+Constants.nbin1_default;
//
//        // bin fill order
//        // element 0 => number of transmitted data symbols
//        // element 1...n => number of bits in a symbol corresponding to data bits (the remaining are padding bits)
//        int[] binFillOrder = SymbolGeneration.binFillOrder(Utils.arange(valid_bins[0],valid_bins[1]));
//
//        // extract pilot symbols from the first OFDM symbol
//        // compare this to the transmitted the transmitted pilot symbols
//        // and perform frequency domain equalization
//        int ptime = (int)((Constants.preambleTime/1000.0)*Constants.fs);
//        int start = ptime+Constants.ChirpGap;
//        double[] rx_pilots=Utils.segment(data,start+Constants.Cp,start+Constants.Cp+Constants.Ns-1);
//        start = start+Constants.Cp+Constants.Ns;
//
//        double [] tx_pilots = Utils.convert(SymbolGeneration.getTrainingSymbol(Utils.arange(valid_bins[0],valid_bins[1])));
//        tx_pilots = Utils.segment(tx_pilots,Constants.Cp,Constants.Cp+Constants.Ns-1);
//
//        // obtain weights from frequency domain equalization
//        double[][] tx_spec = Utils.fftcomplexoutnative_double(tx_pilots, tx_pilots.length);
//        double[][] rx_spec = Utils.fftcomplexoutnative_double(rx_pilots, rx_pilots.length);
//        double[][] weights = Utils.dividenative(tx_spec, rx_spec);
//        double[][] recovered_pilot_sym = Utils.timesnative(rx_spec, weights);
//
//        // differential decoding
//        int numsyms = binFillOrder[0]; // number of data symbols
//        double[][][] symbols = new double[numsyms + 1][][];
//        symbols[0] = recovered_pilot_sym;
//
//        // extract each symbol and equalize with weights
//        for (int i = 0; i < numsyms; i++) {
//            double[] sym = Utils.segment(data, start + Constants.Cp, start + Constants.Cp + Constants.Ns - 1);
//            start = start + Constants.Cp + Constants.Ns;
//
//            double[][] sym_spec = Utils.fftcomplexoutnative_double(sym, sym.length);
//            sym_spec = Utils.timesnative(sym_spec, weights);
//            symbols[i + 1] = sym_spec;
//        }
//
//        // demodulate the symbols to bits
//        short[][] bits = Modulation.pskdemod_differential(symbols, valid_bins);
//
//        // for each symbol reorder the bits that were shuffled from interleaving
//        // extract bits from the symbol corresponding to valid data
//        String coded = "";
//        for (int i = 0; i < bits.length; i++) {
//            short[] newbits = bits[i];
//            newbits = SymbolGeneration.unshuffle(bits[i], i);
//            // extract the data bits
//            for (int j = 0; j < binFillOrder[i + 1]; j++) {
//                coded += newbits[j] + "";
//            }
//        }
//
//        // perform viterbi decoding
//        Utils.debugLog("Need decode: "+coded);
//        String uncoded = Utils.decode(coded, Constants.cc[0],Constants.cc[1],Constants.cc[2]);
//        Utils.debugLog("After decode: "+uncoded);
//        String finalMessage;
//        if(CustomMessageTest.isStrTestEnabled()){
//            String strMsg = CustomMessageTest.decodeBitsToString(uncoded);
//            Utils.debugLog("Custom decode: "+ strMsg);
//            if(strMsg.startsWith("Error")){
//                Log.e("Decoder", "Error on decoder: ");
//            }
//            finalMessage = strMsg;
//        }
//        else{
//            int messageID=Integer.parseInt(uncoded,2);
//            // display message
//            String message="Error";
//            if (Constants.mmap.containsKey(messageID)) { message = Constants.mmap.get(messageID); }
//            Utils.log(coded +"=>"+uncoded+"=>"+message);
//            Utils.debugLog("id:"+messageID + " msg:"+message);
//            finalMessage = message;
//        }
//        // extract messageID from bits
//        av.runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                Utils.sendNotification(av, "Notification",finalMessage, com.example.root.ffttest2.R.drawable.warning2);
//                Constants.msgview.setText(finalMessage);
//            }
//        });
//    }
    // 在 Decoder.java 中

    public static void decode_helper(Activity av, double[] data, int[] valid_bins) {
        // 0. 预处理 (保持不变)
        data = Utils.filter(data);
        valid_bins[0] = valid_bins[0] + Constants.nbin1_default;
        valid_bins[1] = valid_bins[1] + Constants.nbin1_default;
        int[] valid_carrier_range = Utils.arange(valid_bins[0], valid_bins[1]);

        // 1. 提取训练符号并计算信道均衡权重 (保持不变)
        int ptime = (int)((Constants.preambleTime / 1000.0) * Constants.fs);
        int start = ptime + Constants.ChirpGap;
        double[] rx_pilots = Utils.segment(data, start + Constants.Cp, start + Constants.Cp + Constants.Ns - 1);
        start = start + Constants.Cp + Constants.Ns;

        double[] tx_pilots = Utils.convert(SymbolGeneration.getTrainingSymbol(valid_carrier_range));
        tx_pilots = Utils.segment(tx_pilots, Constants.Cp, Constants.Cp + Constants.Ns - 1);

        double[][] tx_spec = Utils.fftcomplexoutnative_double(tx_pilots, tx_pilots.length);
        double[][] rx_spec = Utils.fftcomplexoutnative_double(rx_pilots, rx_pilots.length);
        double[][] weights = Utils.dividenative(tx_spec, rx_spec);
        double[][] recovered_pilot_sym = Utils.timesnative(rx_spec, weights);

        // 2. 【【【 核心修改：动态解包逻辑 】】】
        String finalMessage;
        if (CustomMessageTest.isStrTestEnabled()) {
            // --- 新的、针对可变长度字符串的逻辑 ---
            Utils.debugLog("Decoder entering STRING mode.");

            final int HEADER_LENGTH_BITS = 16;
            String fullCodedBits = "";
            String fullUncodedBits = "";

            // a. 先解码出包含信头的“第一批”比特
            // 我们需要先解码出足够多的比特来解析信头。一个OFDM符号承载 valid_carrier_range.length 个比特。
            // 假设FEC码率为1/2，约束长度为7，那么编码后的信头长度约为 (16 + 7-1) * 2 = 44比特。
            // 通常解码1-2个OFDM符号就足够了。这里我们用一个循环来确保这一点。
            int symbolsProcessed = 0;
            int expectedUncodedLength = -1;
            int totalSymbolsNeeded = -1;

            double[][][] symbols_so_far = new double[2][][]; // [0] for previous, [1] for current
            symbols_so_far[1] = recovered_pilot_sym;

            while(true) {
                symbols_so_far[0] = symbols_so_far[1]; // Shift current to previous

                // 检查是否还有足够的数据来切片下一个符号
                if (start + Constants.Cp + Constants.Ns - 1 >= data.length) {
                    Utils.debugLog("Decoder Error: Ran out of data before decoding could complete.");
                    finalMessage = "Error: Incomplete packet.";
                    break;
                }

                // b. 提取、均衡下一个OFDM符号
                double[] sym = Utils.segment(data, start + Constants.Cp, start + Constants.Cp + Constants.Ns - 1);
                start = start + Constants.Cp + Constants.Ns;
                double[][] sym_spec = Utils.fftcomplexoutnative_double(sym, sym.length);
                symbols_so_far[1] = Utils.timesnative(sym_spec, weights);
                symbolsProcessed++;

                // c. 差分解调这两个连续的符号
                short[][] demodulatedBits = Modulation.pskdemod_differential(symbols_so_far, valid_bins);

                // d. 反交织并拼接
                short[] unshuffledBits = SymbolGeneration.unshuffle(demodulatedBits[0], symbolsProcessed - 1);
                for (short bit : unshuffledBits) {
                    fullCodedBits += bit;
                }

                // e. 尝试解码，看是否能解析出信头
                if (expectedUncodedLength == -1) {
                    String decodedSoFar = Utils.decode(fullCodedBits, Constants.cc[0], Constants.cc[1], Constants.cc[2]);
                    if (decodedSoFar.length() >= HEADER_LENGTH_BITS) {
                        // 成功解码出信头！
                        String headerBits = decodedSoFar.substring(0, HEADER_LENGTH_BITS);
                        expectedUncodedLength = Integer.parseInt(headerBits, 2) + HEADER_LENGTH_BITS;
                        Utils.debugLog("Header decoded! Expected uncoded length (header+payload): " + expectedUncodedLength);

                        // 计算总共需要多少个OFDM符号
                        // (这里是一个估算，实际可能因FEC和填充而略有不同，但足够健壮)
                        int bitsPerSymbol = valid_carrier_range.length;
                        double approxCodingRate = 0.5; // 假设码率
                        int approxCodedLength = (int)(expectedUncodedLength / approxCodingRate);
                        totalSymbolsNeeded = (int) Math.ceil((double) approxCodedLength / bitsPerSymbol);
                        Utils.debugLog("Estimated total symbols needed: " + totalSymbolsNeeded);
                    }
                }

                // f. 检查是否已处理完所有需要的符号
                if (totalSymbolsNeeded != -1 && symbolsProcessed >= totalSymbolsNeeded) {
                    // 所有符号都已处理完毕
                    fullUncodedBits = Utils.decode(fullCodedBits, Constants.cc[0], Constants.cc[1], Constants.cc[2]);
                    Utils.debugLog("Full uncoded bits received: " + fullUncodedBits);
                    finalMessage = CustomMessageTest.decodeBitsToString(fullUncodedBits);
                    break;
                }
            }

        } else {
            // --- 旧的、针对固定长度ID的逻辑 (保持不变) ---
            Utils.debugLog("Decoder entering ID mode.");
            int[] binFillOrder = SymbolGeneration.binFillOrder(valid_carrier_range);
            int numsyms = binFillOrder[0];
            double[][][] symbols = new double[numsyms + 1][][];
            symbols[0] = recovered_pilot_sym;

            for (int i = 0; i < numsyms; i++) {
                double[] sym = Utils.segment(data, start + Constants.Cp, start + Constants.Cp + Constants.Ns - 1);
                start = start + Constants.Cp + Constants.Ns;
                double[][] sym_spec = Utils.fftcomplexoutnative_double(sym, sym.length);
                symbols[i + 1] = Utils.timesnative(sym_spec, weights);
            }

            short[][] bits = Modulation.pskdemod_differential(symbols, valid_bins);
            String coded = "";
            for (int i = 0; i < bits.length; i++) {
                short[] newbits = SymbolGeneration.unshuffle(bits[i], i);
                for (int j = 0; j < binFillOrder[i + 1]; j++) {
                    coded += newbits[j] + "";
                }
            }

            Utils.debugLog("Need decode (ID mode): " + coded);
            String uncoded = Utils.decode(coded, Constants.cc[0], Constants.cc[1], Constants.cc[2]);
            Utils.debugLog("After decode (ID mode): " + uncoded);

            int messageID = Integer.parseInt(uncoded, 2);
            String message = "Error";
            if (Constants.mmap.containsKey(messageID)) { message = Constants.mmap.get(messageID); }
            finalMessage = message;
        }

        // UI更新部分 (保持不变)
        String uiMessage = finalMessage;
        av.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Utils.sendNotification(av, "Notification", uiMessage, com.example.root.ffttest2.R.drawable.warning2);
                Constants.msgview.setText(uiMessage);
            }
        });
    }
}
