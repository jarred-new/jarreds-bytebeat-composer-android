
package com.jarredapps.bytebeatcomposer;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.objecthunter.exp4j.operator.Operator;

public class BytebeatPlayer {

    private static final String TAG = "BytebeatPlayer";

    private volatile boolean running = false;
    private Thread thread;
    private volatile AudioTrack audioTrack;

    private static final int BUFFER_SAMPLES = 1024;

    private static Operator makeOperator(
            String symbol, int operands, int precedence) {

        return new Operator(symbol, operands, true, precedence) {
            @Override
            public double apply(double... args) {
                int a = (int) args[0];
                int b = operands == 2 ? (int) args[1] : 0;

                switch (symbol) {
                    case "&":
                        return a & b;
                    case "|":
                        return a | b;
                    case "#":
                        return a ^ b;
                    case "<<":
                        return a << (b & 31);
                    case ">>":
                        return a >> (b & 31);
                    case ">>>":
                        return a >>> (b & 31);
                    case "~":
                        return ~a;
                    default:
                        throw new IllegalArgumentException(
                                "Unsupported operator: " + symbol);
                }
            }
        };
    }

    private static Expression compileFormula(String formula) {

        int shiftPrec = Operator.PRECEDENCE_ADDITION - 10;
        int andPrec = Operator.PRECEDENCE_ADDITION - 20;
        int xorPrec = Operator.PRECEDENCE_ADDITION - 30;
        int orPrec = Operator.PRECEDENCE_ADDITION - 40;

        Operator leftShift =
                makeOperator("<<", 2, shiftPrec);
        Operator rightShift =
                makeOperator(">>", 2, shiftPrec);
        Operator unsignedShift =
                makeOperator(">>>", 2, shiftPrec);

        Operator bitwiseAnd =
                makeOperator("&", 2, andPrec);
        Operator bitwiseXor =
                makeOperator("#", 2, xorPrec);
        Operator bitwiseOr =
                makeOperator("|", 2, orPrec);

        Operator bitwiseNot =
                makeOperator("~", 1,
                        Operator.PRECEDENCE_POWER + 10);

        // In Bytebeat, ^ means bitwise XOR.
        // exp4j uses ^ for exponentiation, so map it to #.
        String parsedFormula = formula.replace("^", "#");

        return new ExpressionBuilder(parsedFormula)
                .variable("t")
                .operator(
                        leftShift,
                        rightShift,
                        unsignedShift,
                        bitwiseAnd,
                        bitwiseXor,
                        bitwiseOr,
                        bitwiseNot)
                .build();
    }

    public synchronized void start(
            final String formula,
            final int sampleRate,
            final BytebeatType type) {

        stop();

        if (formula == null || formula.trim().isEmpty()) {
            throw new IllegalStateException(
                                "Formula is empty");
        }

        if (sampleRate <= 0) {
            throw new IllegalStateException("Invalid sample rate: " + sampleRate);
        }

        running = true;

        thread = new Thread(new Runnable() {
            @Override
            public void run() {

                AudioTrack track = null;

                try {
                    // Compile once, not once per sample.
                    final Expression expression =
                            compileFormula(formula);

                    int minBufferSize =
                            AudioTrack.getMinBufferSize(
                                    sampleRate,
                                    AudioFormat.CHANNEL_OUT_MONO,
                                    AudioFormat.ENCODING_PCM_16BIT);

                    if (minBufferSize <= 0) {
                        throw new IllegalStateException(
                                "Invalid AudioTrack buffer size: "
                                        + minBufferSize);
                    }

                    int bufferBytes = Math.max(
                            minBufferSize,
                            BUFFER_SAMPLES * 2);

                    // Legacy constructor supports Android API 21+.
                    track = new AudioTrack(
                            AudioManager.STREAM_MUSIC,
                            sampleRate,
                            AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT,
                            bufferBytes,
                            AudioTrack.MODE_STREAM);

                    if (track.getState()
                            != AudioTrack.STATE_INITIALIZED) {
                        throw new IllegalStateException(
                                "AudioTrack initialization failed");
                    }

                    audioTrack = track;
                    track.play();

                    short[] buffer =
                            new short[BUFFER_SAMPLES];

                    long t = 0;

                    while (running
                            && !Thread.currentThread().isInterrupted()) {

                        for (int i = 0;
                                i < buffer.length && running;
                                i++, t++) {

                            double value = expression
                                    .setVariable("t", (double) t)
                                    .evaluate();

                            if (Double.isNaN(value)
                                    || Double.isInfinite(value)) {
                                value = 0;
                            }

                            int byteValue = (int) value & 255;

                            if (type == BytebeatType.BYTEBEAT_SIGNED) {
                                buffer[i] =
                                        (short) ((byteValue - 128) * 256);
                            } else if (
                                    type == BytebeatType.BYTEBEAT_UNSIGNED) {
                                buffer[i] =
                                        (short) (byteValue * 256);
                            } else {
                                buffer[i] = 0;
                            }
                        }

                        if (!running) {
                            break;
                        }

                        int written =
                                track.write(buffer, 0, buffer.length);

                        if (written < 0) {
                            throw new IllegalStateException(
                                    "AudioTrack write failed: " + written);
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();

                } finally {
                    running = false;
                    audioTrack = null;

                    if (track != null) {
                        try {
                            if (track.getPlayState()
                                    == AudioTrack.PLAYSTATE_PLAYING) {
                                track.stop();
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        try {
                            track.release();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }, "BytebeatAudioThread");

        thread.start();
    }

    public boolean isRunning() {
        return running;
    }

    public synchronized void stop() {
        running = false;

        AudioTrack track = audioTrack;

        if (track != null) {
            try {
                track.pause();
                track.flush();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Thread oldThread = thread;

        if (oldThread != null && oldThread != Thread.currentThread()) {
            oldThread.interrupt();

            try {
                oldThread.join(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            thread = null;
        }
    }
}
