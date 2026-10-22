package com.jarredapps.bytebeatcomposer;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import net.objecthunter.exp4j.*;

public class BytebeatPlayer {

    private Thread thread;
    protected boolean running;

    public void start(final String formula, final int sampleRate, final BytebeatType type) {

        stop();

        running = true;

        thread =
                new Thread(
                        new Runnable() {

                            @Override
                            public void run() {

                                // int sampleRate = 8000;

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

                                short[] buffer = new short[1024];

                                // --- Bitwise Shifts (Precedence slightly below Addition) ---
                                int PRECEDENCE_SHIFT = Operator.PRECEDENCE_ADDITION - 10;

                                Operator leftShift =
                                        new Operator("<<", 2, true, PRECEDENCE_SHIFT) {
                                            @Override
                                            public double apply(double... args) {
                                                return (double) ((long) args[0] << (long) args[1]);
                                            }
                                        };
                                Operator rightShift =
                                        new Operator(">>", 2, true, PRECEDENCE_SHIFT) {
                                            @Override
                                            public double apply(double... args) {
                                                return (double) ((long) args[0] >> (long) args[1]);
                                            }
                                        };

                                // --- Bitwise AND, XOR, OR (Precedence below Shifts) ---
                                Operator bitwiseAnd =
                                        new Operator("&", 2, true, PRECEDENCE_SHIFT - 10) {
                                            @Override
                                            public double apply(double... args) {
                                                return (double) ((long) args[0] & (long) args[1]);
                                            }
                                        };
                                        
                                // NOTE: Standard exp4j already maps '^' to power.
                                // We override or use an alternate symbol like 'XOR' to avoid conflicts.
                                Operator bitwiseXor =
                                        new Operator("XOR", 2, true, PRECEDENCE_SHIFT - 20) {
                                            @Override
                                            public double apply(double... args) {
                                                return (double) ((long) args[0] ^ (long) args[1]);
                                            }
                                        };
                                Operator bitwiseOr =
                                        new Operator("|", 2, true, PRECEDENCE_SHIFT - 30) {
                                            @Override
                                            public double apply(double... args) {
                                                return (double) ((long) args[0] | (long) args[1]);
                                            }
                                        };

                                while (running) {

                                    for (int i = 0; i < buffer.length; i++) {
                                        Expression expr =
                                                new ExpressionBuilder(formula)
                                                        .variable("t")
                                                        .operator(leftShift, rightShift, bitwiseAnd, bitwiseOr, bitwiseXor)
                                                        .build()
                                                        .setVariable("t", t);

                                        double value = expr.evaluate();

                                        short sample = 0;

                                        if (type == BytebeatType.BYTEBEAT_SIGNED) {
                                            sample = (short) (((long) value & 255) * 256);
                                        } else if (type == BytebeatType.BYTEBEAT_UNSIGNED) {
                                            sample = (short) ((value - 128) * 256);
                                        }

                                        buffer[i] = sample;

                                        t++;
                                    }

                                    track.write(buffer, 0, buffer.length);
                                }

                                track.stop();
                                track.release();
                            }
                        });

        thread.start();
    }

    public boolean isRunning() {
    	return this.running;
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