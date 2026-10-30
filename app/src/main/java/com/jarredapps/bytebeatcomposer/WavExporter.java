package com.jarredapps.bytebeatcomposer;

import java.io.OutputStream;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.objecthunter.exp4j.operator.Operator;

public class WavExporter {

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
        
    public static void export(
        String formula,
        final BytebeatType type,
        OutputStream out,
        int sampleRate,
        int seconds)
    throws Exception {

        int samples =
            sampleRate * seconds;

        int dataSize =
            samples * 2;

        writeHeader(
            out,
            sampleRate,
            dataSize);

        for(long t = 0; t < samples; t++) {

            final Expression expr = compileFormula(formula);

            double value = expr.setVariable("t", (double) t).evaluate();

            short s = 0;

            int byteValue = (int) value & 255;

            if (type == BytebeatType.BYTEBEAT_SIGNED) {
                s =
                    (short) ((byteValue - 128) * 256);
            } else if (type == BytebeatType.BYTEBEAT_UNSIGNED) {
                s =
                    (short) (byteValue * 256);
            }

            out.write(s & 0xFF);
            out.write((s >> 8) & 0xFF);
        }

        out.flush();
        //out.close();
    }

    private static void writeHeader(
        OutputStream out,
        int sampleRate,
        int dataSize)
    throws Exception {

        writeString(out,"RIFF");
        writeInt(out,36 + dataSize);
        writeString(out,"WAVE");

        writeString(out,"fmt ");
        writeInt(out,16);

        writeShort(out,(short)1);
        writeShort(out,(short)1);

        writeInt(out,sampleRate);
        writeInt(out,sampleRate * 2);

        writeShort(out,(short)2);
        writeShort(out,(short)16);

        writeString(out,"data");
        writeInt(out,dataSize);
    }

    private static void writeString(
        OutputStream out,
        String s)
    throws Exception {

        out.write(s.getBytes("ASCII"));
    }

    private static void writeInt(
        OutputStream out,
        int v)
    throws Exception {

        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
        out.write((v >> 16) & 0xFF);
        out.write((v >> 24) & 0xFF);
    }

    private static void writeShort(
        OutputStream out,
        short v)
    throws Exception {

        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
    }
}
