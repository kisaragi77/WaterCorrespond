package com.example.root.ffttest2;

import android.app.Activity;
import android.util.Log;

import com.example.root.ffttest2.R;

import java.util.Collections;

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
            // --- 新的、基于“贪婪拆箱”的字符串逻辑 ---
            Utils.debugLog("Decoder entering STRING mode (Greedy Strategy).");

            final int HEADER_LENGTH_BITS = 16;
            String fullCodedBits = "";
            int expectedUncodedLength = -1;
            int expectedCodedLength = -1;

            double[][][] symbols_so_far = new double[2][][];
            symbols_so_far[1] = recovered_pilot_sym;

            while (true) {
                symbols_so_far[0] = symbols_so_far[1];

                if (start + Constants.Cp + Constants.Ns - 1 >= data.length) {
                    Utils.debugLog("Decoder Warning: Ran out of data. Decoding with what was received.");
                    break;
                }

                double[] sym = Utils.segment(data, start + Constants.Cp, start + Constants.Cp + Constants.Ns - 1);
                start = start + Constants.Cp + Constants.Ns;
                double[][] sym_spec = Utils.fftcomplexoutnative_double(sym, sym.length);
                symbols_so_far[1] = Utils.timesnative(sym_spec, weights);

                short[][] demodulatedBits = Modulation.pskdemod_differential(symbols_so_far, valid_bins);
                short[] unshuffledBits = SymbolGeneration.unshuffle(demodulatedBits[0], fullCodedBits.length() / valid_carrier_range.length);

                // 【【【 核心修改：贪婪拼接 】】】
                // 每次都将解调出的一个完整符号的比特全部拼接起来
                for (short bit : unshuffledBits) {
                    fullCodedBits += bit;
                }

                // 检查是否可以解码信头了
                if (expectedUncodedLength == -1) {
                    String decodedHeaderAttempt = Utils.decode(fullCodedBits, Constants.cc[0], Constants.cc[1], Constants.cc[2]);
                    if (decodedHeaderAttempt.length() >= HEADER_LENGTH_BITS) {
                        String headerBits = decodedHeaderAttempt.substring(0, HEADER_LENGTH_BITS);
                        int payloadLength = Integer.parseInt(headerBits, 2);
                        expectedUncodedLength = HEADER_LENGTH_BITS + payloadLength;

                        // 【重要】精确计算编码后的总长度
                        // 编码器会添加 (constraint-1) 个冲刷比特
                        int constraint = Constants.cc[2];
                        String tempUncoded = String.join("", Collections.nCopies(expectedUncodedLength, "0"));
                        expectedCodedLength = Utils.encode(tempUncoded, Constants.cc[0], Constants.cc[1], constraint).length();

                        Utils.debugLog("Header decoded! Payload length: " + payloadLength + ", Full uncoded length: " + expectedUncodedLength);
                        Utils.debugLog("Expected final coded length: " + expectedCodedLength);
                    }
                }

                // 检查是否已接收到足够的编码比特
                if (expectedCodedLength != -1 && fullCodedBits.length() >= expectedCodedLength) {
                    Utils.debugLog("Received enough coded bits (" + fullCodedBits.length() + "). Finalizing decoding.");
                    break; // 跳出循环，进行最终解码
                }
            }

            // 进行最终的Viterbi解码和字符串翻译
            String fullUncodedBits = Utils.decode(fullCodedBits, Constants.cc[0], Constants.cc[1], Constants.cc[2]);
            Utils.debugLog("Full uncoded bits received: " + fullUncodedBits);
            finalMessage = CustomMessageTest.decodeBitsToString(fullUncodedBits);

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
