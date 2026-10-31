package com.jarredapps.bytebeatcomposer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.objecthunter.exp4j.operator.Operator;

public final class WavExporter {

    private static final int BUFFER_SIZE = 8192;

    private WavExporter() {}

    private static Operator makeOperator(
            final String symbol, int operands, int precedence) {

        return new Operator(symbol, operands, true, precedence) {
            @Override
            public double apply(double... args) {
                int a = (int) args[0];
                int b = operands == 2 ? (int) args[1] : 0;

                switch (symbol) {
                    case "&": return a & b;
                    case "|": return a | b;
                    case "#": return a ^ b;
                    case "<<": return a << (b & 31);
                    case ">>": return a >> (b & 31);
                    case ">>>": return a >>> (b & 31);
                    case "~": return ~a;
                    default:
                        throw new IllegalArgumentException(
                                "Unsupported operator: " + symbol);
                }
            }
        };
    }

    private static Expression compileFormula(String formula) {

        String parsedFormula = formula.replace("^", "#");

        return new ExpressionBuilder(parsedFormula)
                .variable("t")
                .operator(
                        makeOperator("<<", 2,
                                Operator.PRECEDENCE_ADDITION - 10),
                        makeOperator(">>", 2,
                                Operator.PRECEDENCE_ADDITION - 10),
                        makeOperator(">>>", 2,
                                Operator.PRECEDENCE_ADDITION - 10),
                        makeOperator("&", 2,
                                Operator.PRECEDENCE_ADDITION - 20),
                        makeOperator("#", 2,
                                Operator.PRECEDENCE_ADDITION - 30),
                        makeOperator("|", 2,
                                Operator.PRECEDENCE_ADDITION - 40),
                        makeOperator("~", 1,
                                Operator.PRECEDENCE_POWER + 10))
                .build();
    }

    public static void export(
            String formula,
            BytebeatType type,
            OutputStream out,
            int sampleRate,
            int seconds) throws IOException {

        if (formula == null || formula.trim().isEmpty())
            throw new IllegalArgumentException("Formula is empty");

        if (type == null || out == null)
            throw new IllegalArgumentException("Invalid export parameters");

        if (sampleRate < 8000 || sampleRate > 192000)
            throw new IllegalArgumentException("Invalid sample rate");

        if (seconds <= 0)
            throw new IllegalArgumentException("Invalid duration");

        long samples = (long) sampleRate * seconds;
        long dataSize = samples * 2L;

        if (dataSize > 0xFFFFFFFFL - 36L)
            throw new IllegalArgumentException("WAV file is too large");

        // Compile only once.
        final Expression expression;

        try {
            expression = compileFormula(formula);
        } catch (RuntimeException e) {
            throw new IOException(
                    "Invalid formula: " + e.getMessage(), e);
        }

        writeHeader(out, sampleRate, dataSize);

        byte[] buffer = new byte[BUFFER_SIZE];
        int position = 0;

        for (long t = 0; t < samples; t++) {

            double value;

            try {
                value = expression
                        .setVariable("t", (double) t)
                        .evaluate();
            } catch (RuntimeException e) {
                throw new IOException(
                        "Evaluation failed at sample " + t, e);
            }

            if (Double.isNaN(value) || Double.isInfinite(value)) {
                value = 0;
            }

            int byteValue = ((int) value) & 255;

            short pcm;

            if (type == BytebeatType.BYTEBEAT_SIGNED) {
                pcm = (short) ((byteValue - 128) * 256);
            } else {
                pcm = (short) (byteValue * 256);
            }

            buffer[position++] = (byte) (pcm & 0xFF);
            buffer[position++] = (byte) ((pcm >>> 8) & 0xFF);

            if (position == buffer.length) {
                out.write(buffer, 0, position);
                position = 0;
            }
        }

        if (position > 0) {
            out.write(buffer, 0, position);
        }

        out.flush();
    }

    private static void writeHeader(
            OutputStream out,
            int sampleRate,
            long dataSize) throws IOException {

        writeString(out, "RIFF");
        writeInt(out, (int) (36L + dataSize));
        writeString(out, "WAVE");

        writeString(out, "fmt ");
        writeInt(out, 16);

        writeShort(out, 1); // PCM
        writeShort(out, 1); // Mono

        writeInt(out, sampleRate);
        writeInt(out, sampleRate * 2);

        writeShort(out, 2);
        writeShort(out, 16);

        writeString(out, "data");
        writeInt(out, (int) dataSize);
    }

    private static void writeString(
            OutputStream out, String value) throws IOException {

        out.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static void writeInt(
            OutputStream out, int value) throws IOException {

        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 24) & 0xFF);
    }

    private static void writeShort(
            OutputStream out, int value) throws IOException {

        out.write(value & 0xFF);
        out.write((value >>> 8) & 0xFF);
    }
}