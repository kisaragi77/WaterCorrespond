package com.example.root.ffttest2;

import static com.example.root.ffttest2.Constants.tv4;

import android.app.Activity;
import android.os.AsyncTask;
import android.util.Log;

import java.util.Arrays;

public class SendChirpAsyncTask extends AsyncTask<Void, Void, Void> {
    Activity av;
    int num_measurements = 0;
    public SendChirpAsyncTask(Activity activity, int num_measurements) {
        this.av = activity;
        this.num_measurements = num_measurements;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    public void setupTimer() {
        av.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                double totalTime = 0;
                if (Constants.user.equals(Constants.User.Alice)) {
                    double soundingTimeTx = 1;
                    double extractionFeedbackTime = 1;
                    totalTime += (soundingTimeTx + Constants.WaitForFeedbackTime +
                            extractionFeedbackTime);
                    if (Constants.SEND_DATA) {
                        totalTime+=Constants.WaitForDataTime;
                    }
                    Constants.AliceTime = (int)totalTime;
                    totalTime *= 1000;
                    totalTime *= num_measurements;

                    totalTime += 1000+(Constants.initSleep*1000);
                }
                else if (Constants.user.equals(Constants.User.Bob)) {
                    int extractSoundingTime = 1;
                    int sendFeedbackTime = 1;
                    totalTime += Constants.WaitForSoundingTime+
                            extractSoundingTime+sendFeedbackTime;
                    totalTime += Constants.SoundingOffset;
                    if (Constants.SEND_DATA) {
                        totalTime+=Constants.WaitForDataTime;
                    }
                    Constants.BobTime = (int)totalTime;
                    totalTime *= 1000;
                    totalTime *= num_measurements;
                    totalTime += 1000+(Constants.initSleep*1000);
                }
            }
        });
    }

    @Override
    protected void onPostExecute(Void unused) {
        super.onPostExecute(unused);
        if (Experiment.isExperimentRunning) {
            // 如果是，就触发下一个数据包的发送
            // 我们给一个短暂的延迟，让系统有时间喘息
            new android.os.Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    Experiment.triggerNextPacket();
                }
            }, 300); // 延迟500毫秒

            // 直接返回，不执行下面的重启逻辑，因为下一个任务将由Experiment类启动
            return;
        }
        MainActivity.unreg(av);

        if (Constants.timer!=null) {
            Constants.timer.cancel();
            tv4.setText("0");
        }

        Constants.sp1=null;
        Constants._OfflineRecorder = null;
        Constants.user  = Constants.User.Bob;
        MainActivity.startMethod(av);
    }

    @Override
    protected Void doInBackground(Void... voids) {
        Constants.WaitForFeedbackTime = Constants.WaitForFeedbackTimeDefault + Constants.SyncLag;
        Constants.WaitForSoundingTime = Constants.WaitForSoundingTimeDefault + Constants.SyncLag - Constants.SoundingOffset;
        Constants.WaitForBerTime = Constants.WaitForBerTimeDefault + Constants.SyncLag;
        Constants.WaitForPerTime = Constants.WaitForPerTimeDefault + Constants.SyncLag;

        Constants.SEND_DATA=true;
        Constants.WaitForDataTime = Constants.WaitForPerTime;
        Constants.AdaptationMethod = 3;

        FileOperations.writetofile(MainActivity.av, Constants.SNR_THRESH2+"\n"+Constants.FreAdaptScaleFactor+"\n"+Constants.SNR_THRESH2_2,
                Utils.genName(Constants.SignalType.AdaptParams,0)+".txt");

        setupTimer();

        sleep(Constants.initSleep * 1000);

        Constants.StartingTimestamp = System.currentTimeMillis();
        appendToLog(Constants.SignalType.Start.toString());

        if (Constants.user.equals(Constants.User.Alice)) {
            FileOperations.writetofile(MainActivity.av, Constants.FLIP_SYMBOL + "",
                    Utils.genName(Constants.SignalType.FlipSyms, 0) + ".txt");
        }

        for (int i = 0; i < num_measurements; i++) {
            Log.e("timer","work "+i);
            int flag = work(i);
            updateTimer((i+1)+"");
            if (flag == -1) {
                updateTimer("-1");
                break;
            }
        }
        return null;
    }

    public static void appendToLog(String s) {
        if (s.equals(Constants.SignalType.Start.toString())) {
            if (Constants.user.equals(Constants.User.Alice)) {
                String ts = System.currentTimeMillis()+"";
                String filename = Constants.user.toString() + "-" + Constants.SignalType.Sounding + "-" + "log";
                FileOperations.appendtofile(MainActivity.av, ts + "\n", filename + ".txt");
                filename = Constants.user.toString() + "-" + Constants.SignalType.Data + "-" + "log";
                FileOperations.appendtofile(MainActivity.av, ts + "\n", filename + ".txt");
            }
            else {
                String filename = Constants.user.toString() + "-" + Constants.SignalType.Feedback + "-" + "log";
                FileOperations.appendtofile(MainActivity.av, System.currentTimeMillis() + "\n", filename + ".txt");
            }
        }
        else {
            String filename = Constants.user.toString() + "-" + s + "-" + "log";
            FileOperations.appendtofile(MainActivity.av, System.currentTimeMillis() + "\n", filename + ".txt");
        }
    }

    public void updateTimer(String ss) {
        MainActivity.av.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tv4.setText(ss);
            }
        });
    }
// 在 SendChirpAsyncTask.java 中

    // ====================================================================
// 【【【 替换旧的 work 方法 】】】
// ====================================================================
//    public int work(int m_attempt) {
//        // 根据 Experiment 类中的当前模式，进行逻辑分发
//        if (Experiment.currentBandwidthMode == Experiment.BandwidthMode.ADAPTIVE) {
//            // 如果是自适应模式，调用自适应协议的实现
//            return work_adaptive(m_attempt);
//        } else {
//            // 如果是固定带宽模式，调用固定带宽协议的实现
//            return work_fixed(m_attempt);
//        }
//    }

    // 在 SendChirpAsyncTask.java 中，可以放在 work_adaptive 方法下面

//// ====================================================================
//// 【【【 新增 work_fixed 方法 】】】
//// ====================================================================
//    /**
//     * 执行新的、用于固定带宽传输的简化协议。
//     * @param m_attempt 当前的测量尝试次数
//     * @return 状态码，0表示成功
//     */
//    private int work_fixed(int m_attempt) {
//        if (Constants.user.equals(Constants.User.Alice)) {
//            // --- Alice的简化逻辑：直接发送 ---
//            Utils.debugLog("FIXED MODE: Alice sending data with " + Experiment.fixedBandwidthHz + " Hz bandwidth.");
//
//            // 1. 根据 Experiment.fixedBandwidthHz 计算出固定的子载波索引
//            int start_freq = 1000; // 起始频率固定为1kHz
//            int end_freq = start_freq + Experiment.fixedBandwidthHz;
//
//            int inc = Constants.fs / Constants.Ns;
//            int start_bin = start_freq / inc;
//            int end_bin = end_freq / inc;
//            int[] fixed_bins = Utils.arange(start_bin, end_bin);
//
//            // 2. 直接调用 sendData 发送数据，不进行探测和等待
//            sendData(fixed_bins, m_attempt);
//
//            // 简单等待一段时间以完成一个通信周期
//            sleep(5000);
//
//        } else if (Constants.user.equals(Constants.User.Bob)) {
//            // --- Bob的简化逻辑：直接接收 ---
//            Utils.debugLog("FIXED MODE: Bob waiting for data.");
//
//            // 1. 直接等待数据信号 (DataRx)，跳过探测和反馈
//            double[] data_signal = Utils.waitForChirp(Constants.SignalType.DataRx, m_attempt, 0);
//
//            if (data_signal != null) {
//                // 2. 解码时，Bob不知道Alice具体用了哪个固定带宽。
//                //    一个健壮的做法是让Bob总是假设一个最大的可能范围，
//                //    因为解码器中的训练符号均衡可以处理未被使用的子载波(能量为0)。
//                int inc = Constants.fs / Constants.Ns;
//                int[] max_possible_bins = Utils.arange(1000 / inc, 4000 / inc);
//
//                Decoder.decode_helper(av, data_signal, max_possible_bins);
//            }
//        }
//        return 0;
//    }

    public int work_old(int m_attempt) {
        double[] tx_preamble = PreambleGen.preamble_d();
        if (Constants.user.equals(Constants.User.Alice)) {
            int chirpLoopNumber = 0;
            double[] feedback_signal = null;
            do {
                short[] sig = PreambleGen.sounding_signal_s();
                FileOperations.writetofile(MainActivity.av, sig, Utils.genName(Constants.SignalType.Sounding, m_attempt) + ".txt");

                Constants.sp1 = new AudioSpeaker(av, sig, Constants.fs, 0, sig.length, false);
                appendToLog(Constants.SignalType.Sounding.toString());
                Constants.sp1.play(Constants.volume);

                int sig_len = (int)(((double)sig.length/Constants.fs)*1000);
                sleep(sig_len+Constants.SendPad);

                feedback_signal = Utils.waitForChirp(Constants.SignalType.Feedback, m_attempt, chirpLoopNumber);
                chirpLoopNumber++;
                if (chirpLoopNumber >= 3 || !Constants.work) {
                    return -1;
                }
            } while (feedback_signal == null);

            double[] seg = Utils.segment(feedback_signal,0,24000-1);
            double[] xcorr_out = Utils.xcorr_online(tx_preamble, seg);

            int[] valid_bins = FeedbackSignal.extractSignalHelper(feedback_signal, (int)xcorr_out[1], m_attempt);
            if (valid_bins == null || valid_bins.length < 2 || valid_bins[0] == -1) {
                Utils.log("!!!! [ALICE] Failed to decode band information from feedback.");
                return 0; // 解码失败，直接结束
            }

            if (Constants.SEND_DATA) {
                appendToLog(Constants.SignalType.Data.toString());
                if (valid_bins.length >= 1 && valid_bins[0] != -1) {
                    sendData(valid_bins, m_attempt);
                }
                try {
                    Thread.sleep(3000);
                }
                catch(Exception e){
                    Log.e("asdf",e.toString());
                }
            }
            return 0;
        }
        else if (Constants.user.equals(Constants.User.Bob)) {
            int chirpLoopNumber = 0;
            int[] valid_bins = null;
            double[] sounding_signal = null;
            do {
                sounding_signal = Utils.waitForChirp(Constants.SignalType.Sounding, m_attempt, chirpLoopNumber);
                if (sounding_signal == null) {
                    return -1;
                }

                double[] seg = Utils.segment(sounding_signal,0,24000-1);
                double[] xcorr_out = Utils.xcorr_online(tx_preamble, seg);

                valid_bins = ChannelEstimate.extractSignal_withsymbol_helper(av, sounding_signal, (int)xcorr_out[1], m_attempt);
                chirpLoopNumber++;

                if (!Constants.work) {
                    return -1;
                }
            } while (valid_bins == null || valid_bins.length == 0 || valid_bins[0] == -1);

            short[] feedback = FeedbackSignal.encodeFeedbackSignal(valid_bins[0], valid_bins[valid_bins.length - 1],
                    Constants.fbackTime, true, m_attempt);

            Constants.sp1 = new AudioSpeaker(av, feedback, Constants.fs, 0, feedback.length, false);
            appendToLog(Constants.SignalType.Feedback.toString());
            Constants.sp1.play(Constants.volume);

            int stime = (int) ((feedback.length / (double) Constants.fs) * 1000);
            sleep(stime+Constants.SendPad);

            double[] data_signal = null;
            if (Constants.SEND_DATA) {
                data_signal = Utils.waitForChirp(Constants.SignalType.DataRx, m_attempt, 0);
            }
            if (data_signal!=null) {
                Decoder.decode_helper(av, data_signal, valid_bins);
            }
            return 0;
        }
        return 0;
    }

    // 在 SendChirpAsyncTask.java 中

    public int work(int m_attempt) {
        double[] tx_preamble = PreambleGen.preamble_d();

        // ================================================================
        // Alice 端的逻辑 (发起方)
        // ================================================================
        if (Constants.user.equals(Constants.User.Alice)) {

            // ----------------------------------------------------------------
            // 通信①: Alice -> Bob (发送带地址的探测包)
            // ----------------------------------------------------------------
            Utils.log(">>>> [ALICE] Phase 1: Encoding SOUNDING packet with header (Src: " + Constants.ALICE_ID + ", Dst: " + Constants.BOB_ID + ")");

            // 1. 生成各部分信号
            short[] preamble = PreambleGen.preamble_s();
            short[] header = HeaderCodec.encodeHeader(Constants.ALICE_ID, Constants.BOB_ID);
            // 【修改点】确保调用的是只生成训练符号的函数
            short[] trainingSymbols = PreambleGen.generateTrainingSymbolsOnly();

            // 2. 拼接成最终的探测包
            short[] sounding_packet_with_header = Utils.concat_short(preamble, header);
            sounding_packet_with_header = Utils.concat_short(sounding_packet_with_header, trainingSymbols);

            // 3. 播放
            Constants.sp1 = new AudioSpeaker(av, sounding_packet_with_header, Constants.fs, 0, 0, false);
            appendToLog(Constants.SignalType.Sounding.toString());
            Constants.sp1.play(Constants.volume);
            int soundingTime = (int) ((sounding_packet_with_header.length / (double) Constants.fs) * 1000);
            sleep(soundingTime + Constants.SendPad);

            // ----------------------------------------------------------------
            // Alice 等待 Bob 的反馈
            // ----------------------------------------------------------------
            Utils.log("<<<< [ALICE] Phase 2: Listening for FEEDBACK packet...");
            double[] feedback_packet = Utils.waitForChirp(Constants.SignalType.Feedback, m_attempt, 0);
            if (feedback_packet == null) {
                Utils.log("<<<< [ALICE] Timeout: Did not receive FEEDBACK.");
                return -1;
            }

            // 4. 解码收到的反馈包 Header
            int preambleLen = PreambleGen.preamble_s().length;
            int headerLen = Constants.ADDR_SYMBOLS * (Constants.Ns + Constants.Cp);

            if (feedback_packet.length < preambleLen + headerLen) {
                Utils.log("<<<< [ALICE] Error: Received FEEDBACK packet is too short for header.");
                return 0;
            }
            double[] headerSignal = Utils.segment(feedback_packet, preambleLen, preambleLen + headerLen - 1);
            int[] ids = HeaderCodec.decodeHeader(headerSignal);

            // 5. 地址过滤
            if (ids != null && ids[0] == Constants.BOB_ID && ids[1] == Constants.ALICE_ID) {
                Utils.log("++++ [ALICE] FEEDBACK received and validated! From: " + ids[0] + ", For: " + ids[1]);
            } else {
                String reason = (ids == null) ? "header corrupted" : "wrong address (Src:" + (ids != null ? ids[0] : "?") + ", Dst:" + (ids != null ? ids[1] : "?") + ")";
                Utils.log("<<<< [ALICE] FEEDBACK ignored (" + reason + ").");
                return 0; // 丢弃，结束
            }

            // 6. 如果地址正确，继续解码频段信息
            double[] xcorr_out_fb = Utils.xcorr_online(tx_preamble, Utils.segment(feedback_packet, 0, 24000-1));

            // 【修改点】将 Preamble 检测到的起始点传入，让 extractSignalHelper 处理
            int detected_start_point_fb = (int)xcorr_out_fb[1];
            int[] valid_bins_relative = FeedbackSignal.extractSignalHelper(feedback_packet, detected_start_point_fb, m_attempt);

            if (valid_bins_relative == null || valid_bins_relative.length < 2 || valid_bins_relative[0] == -1) {
                Utils.log("!!!! [ALICE] Failed to decode band information from feedback.");
                return 0;
            }
            Utils.log("++++ [ALICE] Successfully decoded band info. Relative bins: " + valid_bins_relative[0] + " to " + valid_bins_relative[valid_bins_relative.length - 1]);


//             通信③ (数据阶段)
            if (Constants.SEND_DATA) {
                Utils.log(">>>> [ALICE] Phase 3: Now sending DATA to Bob.");
                if (Constants.messageID == -1) {
                    Utils.log("!!!! [ALICE] No message selected to send.");
                    return 0;
                }
                int[] valid_bins_absolute = new int[valid_bins_relative.length];
                for (int i = 0; i < valid_bins_relative.length; i++) {
                    valid_bins_absolute[i] = valid_bins_relative[i] + Constants.nbin1_default;
                }
                sendData(valid_bins_absolute, m_attempt);
            }

            return 0;
        }

        // ================================================================
        // Bob 端的逻辑 (接收方)
        // ================================================================
        else if (Constants.user.equals(Constants.User.Bob)) {

            // ----------------------------------------------------------------
            // Phase 1: Bob 等待并处理 Alice 的探测包
            // ----------------------------------------------------------------
            Utils.log("<<<< [BOB] Phase 1: Listening for SOUNDING packet...");
            double[] sounding_packet_with_header = Utils.waitForChirp(Constants.SignalType.Sounding, m_attempt, 0);

            if (sounding_packet_with_header == null) {
                Utils.log("<<<< [BOB] Timeout: Did not receive SOUNDING packet.");
                return -1;
            }

            // 1. 解码 Header
            int preambleLen = PreambleGen.preamble_s().length;
            int headerLen = Constants.ADDR_SYMBOLS * (Constants.Ns + Constants.Cp);

            if (sounding_packet_with_header.length < preambleLen + headerLen) {
                Utils.log("<<<< [BOB] Error: Received packet is too short for header.");
                return 0;
            }
            double[] headerSignal = Utils.segment(sounding_packet_with_header, preambleLen, preambleLen + headerLen - 1);
            int[] ids = HeaderCodec.decodeHeader(headerSignal);

            int sourceId;
            // 2. 地址过滤
            if (ids != null && ids[1] == Constants.BOB_ID) {
                sourceId = ids[0];
                Utils.log("++++ [BOB] SOUNDING received and validated! From: " + sourceId + ", For: " + ids[1]);
            } else {
                String reason = (ids == null) ? "header corrupted" : "wrong address";
                if (ids != null) {
                    reason += " (Src:" + ids[0] + ", Dst:" + ids[1] + ")";
                }
                Utils.log("<<<< [BOB] SOUNDING ignored (" + reason + ").");
                return 0;
            }

            // 3. 如果地址正确，进行信道估计
            //    首先，找到 Preamble 在我们接收到的这个数据块中的确切起始位置
            double[] xcorr_out_snd = Utils.xcorr_online(tx_preamble, Utils.segment(sounding_packet_with_header, 0, 24000-1));
            int detected_start_point_snd = (int) xcorr_out_snd[1];

            // 【【【 核心简化点 】】】
            // 直接将完整的包 和 Preamble 的起始点 传给已修复的 ChannelEstimate 函数
            int[] valid_bins = ChannelEstimate.extractSignal_withsymbol_helper(av, sounding_packet_with_header, detected_start_point_snd, m_attempt);

            if (valid_bins != null && valid_bins.length >= 2 && valid_bins[0] != -1) {
                Utils.log("++++ [BOB] Fre_adaptation selected bins: " + valid_bins[0] + " to " + valid_bins[valid_bins.length - 1]);
                int f_begin_hz = Constants.f_range[0] + (valid_bins[0] * Constants.inc);
                int f_end_hz   = Constants.f_range[0] + (valid_bins[valid_bins.length - 1] * Constants.inc);
                Utils.log("     Corresponding to Freq Range: " + f_begin_hz + " Hz to " + f_end_hz + " Hz");
            } else {
                // 如果 valid_bins 是 null 或无效，这里也会打印日志
                Utils.log("!!!! [BOB] Fre_adaptation failed to select any valid bins.");
            }

            if (valid_bins == null || valid_bins.length == 0 || valid_bins[0] == -1) {
                Utils.log("!!!! [BOB] Channel estimation failed after receiving valid packet. Not sending feedback.");
                return 0;
            }

            // ----------------------------------------------------------------
            // Phase 2: Bob 发送带地址的反馈包 (这部分逻辑不变)
            // ----------------------------------------------------------------
            Utils.log(">>>> [BOB] Phase 2: Encoding FEEDBACK packet with header (Src: " + Constants.BOB_ID + ", Dst: " + sourceId + ")");

            // 【【【 新增 HACK 代码 】】】
//            int forced_start_bin = 20; // 对应 1000 + 20*50 = 2000 Hz
//            int forced_end_bin   = 30; // 对应 1000 + 30*50 = 2500 Hz
//            Utils.log("!!!! [DEBUG HACK] Overriding feedback bins! Forcing to: " + forced_start_bin + " and " + forced_end_bin);
//
//            // 使用我们强制指定的 bin 来生成反馈符号
//            short[] feedback_symbol = FeedbackSignal.encodeFeedbackSymbolOnly(forced_start_bin, forced_end_bin);
            // 【【【 HACK 结束 】】】

            // 4. 生成各部分信号
            short[] feedback_preamble = PreambleGen.preamble_s();
            short[] feedback_header = HeaderCodec.encodeHeader(Constants.BOB_ID, sourceId);
            //被HACK注释
            short[] feedback_symbol = FeedbackSignal.encodeFeedbackSymbolOnly(valid_bins[0], valid_bins[valid_bins.length - 1]);

            // 5. 拼接
            short[] feedback_packet = Utils.concat_short(Utils.concat_short(feedback_preamble, feedback_header), feedback_symbol);

            // 6. 播放
            Constants.sp1 = new AudioSpeaker(av, feedback_packet, Constants.fs, 0, 0, false);
            appendToLog(Constants.SignalType.Feedback.toString());
            Constants.sp1.play(Constants.volume);

            int stime = (int) ((feedback_packet.length / (double) Constants.fs) * 1000);
            sleep(stime + Constants.SendPad);

            Utils.log("<<<< [BOB] Handshake complete. Returning to listen mode.");

            return 0;
        }
        return 0;
    }

    // ...
    public static void sendData(int[] valid_bins, int m_attempt) {
        send_data_per(valid_bins,m_attempt);
    }

    public static void send_data_helper(int numbits, int[] valid_bins, int m_attempt,
                                 Constants.SignalType sigType,Constants.ExpType expType) {
        short[] bits = SymbolGeneration.getCodedBits();

        String out="";
        for (int i = 0; i < bits.length; i++) {
            out+=bits[i]+"";
        }

        short[] txsig=SymbolGeneration.generateDataSymbols(bits, valid_bins, Constants.data_symreps, true, sigType,m_attempt);

        FileOperations.writetofile(MainActivity.av, txsig,
                Utils.genName(Constants.SignalType.DataAdapt, m_attempt) + ".txt");

        Constants.sp1 = new AudioSpeaker(MainActivity.av, txsig, Constants.fs, 0, txsig.length, false);
        Constants.sp1.play(Constants.volume);

        int sleepTime = (int) (((double) txsig.length / Constants.fs) * 1000);
        sleep(sleepTime + Constants.SendPad);
    }

    public static void send_data_ber(int[] valid_bins, int m_attempt) {
        FileOperations.writetofile(MainActivity.av, Constants.codeRate.toString(),
                Utils.genName(Constants.SignalType.CodeRate,m_attempt)+".txt");
        FileOperations.writetofile(MainActivity.av, Utils.trim(Arrays.toString(valid_bins)),
                Utils.genName(Constants.SignalType.ValidBins, m_attempt) + ".txt");

        // adaptive  //////////////////////////////////////////////
        send_data_helper(valid_bins.length*Constants.Nsyms,
                valid_bins, m_attempt,
                Constants.SignalType.DataAdapt,
                Constants.ExpType.BER);
        // full bandwidth//////////////////////////////////////////////
        int[] end_bins = new int[]{79,49,29};
        Constants.SignalType[] sigTypes = new Constants.SignalType[]{
                Constants.SignalType.DataFull_1000_4000,
                Constants.SignalType.DataFull_1000_2500,
                Constants.SignalType.DataFull_1000_1500,
        };
        for (int i = 0; i < end_bins.length; i++) {
            int[] bins = generateBins(20, end_bins[i]);
            send_data_helper(bins.length * Constants.Nsyms, bins, m_attempt,
                    sigTypes[i],Constants.ExpType.BER);
        }
        //////////////////////////////////////////////
    }

    public static int[] generateBins(int bin1, int bin2) {
        int[] bins = new int[bin2-bin1+1];
        int counter=0;
        for (int i = bin1; i <= bin2; i++) {
            bins[counter++]=i;
        }
        return bins;
    }

    public static void send_data_per(int[] valid_bins, int m_attempt) {
        FileOperations.writetofile(MainActivity.av, Constants.codeRate.toString(),
                Utils.genName(Constants.SignalType.CodeRate,m_attempt)+".txt");
        FileOperations.writetofile(MainActivity.av, Utils.trim(Arrays.toString(valid_bins)),
                Utils.genName(Constants.SignalType.ValidBins, m_attempt) + ".txt");

        // calc bits//////////////////////////////////////////////
        int msgbits = 16;
        int traceDepth = 0;
        msgbits += traceDepth;

        // adapt//////////////////////////////////////////////
        send_data_helper(msgbits,
                valid_bins, m_attempt,
                Constants.SignalType.DataAdapt, Constants.ExpType.PER);
        Log.e("numbits","adapt "+msgbits);
        // full bandwidth//////////////////////////////////////////////
    }

    public static void sleep(int s) {
        try {
            Thread.sleep(s);
        }
        catch (Exception e) {
            Utils.log(e.getMessage());
        }
    }
}
