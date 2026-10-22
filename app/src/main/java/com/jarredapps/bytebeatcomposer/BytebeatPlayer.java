package com.jarredapps.bytebeatcomposer;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import net.objecthunter.exp4j.*;

public class BytebeatPlayer {

    private Thread thread;
    private boolean running;

    public void start(final String formula, final int sampleRate, final BytebeatType type) {

        stop();

        running = true;

        thread = new Thread(new Runnable() {

                @Override
                public void run() {

                    //int sampleRate = 8000;

                    AudioTrack track =
                        new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        sampleRate,
                        AudioTrack.MODE_STREAM);

                    track.play();

                    long t = 0;

                    short[] buffer =
                        new short[1024];
                        
                    Expression expr = new ExpressionBuilder(formula)
                        .variable("t")
                        .build()
                        .setVariable("t", t);

                    while (running) {

                        for (int i=0;i < buffer.length;i++) {

                            double value =
                                expr.evaluate();

                            short sample = 0;

                            if (type == BytebeatType.BYTEBEAT_SIGNED) {
                                sample =
                                    (short)(((long)value & 255) * 256);
                            } else if (type == BytebeatType.BYTEBEAT_UNSIGNED) {
                                sample =
                                    (short)((value - 128) * 256);
                            }

							buffer[i] = sample;

                            t++;
                        }

                        track.write(
                            buffer,
                            0,
                            buffer.length);
                    }

                    track.stop();
                    track.release();
                }
            });

        thread.start();
    }

    public void stop() {

        running = false;

        if (thread != null) {
            try {
                thread.join();
            } catch (Exception e) {
            }
        }
    }
}