package com.devsouzx.UfgTurmas;

import java.util.Arrays;

public record ResultadoBenchmark(String id, String operacao, long itens, double mediana) {

    public static final int AQUECIMENTO = 5;
    public static final int REPETICOES = 30;

    @FunctionalInterface
    public interface Operacao {
        long executar() throws Exception;
    }

    @FunctionalInterface
    public interface Passo {
        void executar() throws Exception;
    }

    public static ResultadoBenchmark medir(String id, String operacao, Operacao executar) throws Exception {
        return medir(id, operacao, null, executar, null);
    }

    public static ResultadoBenchmark medir(String id, String operacao, Passo preparar,
                                           Operacao executar, Passo limpar) throws Exception {
        double[] tempos = new double[REPETICOES];
        long itens = 0;

        for (int i = 0; i < AQUECIMENTO + REPETICOES; i++) {
            if (preparar != null) {
                preparar.executar();
            }
            long inicio = System.nanoTime();
            long resultado;
            double segundos;
            try {
                resultado = executar.executar();
                segundos = (System.nanoTime() - inicio) / 1e9;
            } finally {
                if (limpar != null) {
                    limpar.executar();
                }
            }
            if (i == 0) {
                itens = resultado;
            }
            if (i >= AQUECIMENTO) {
                tempos[i - AQUECIMENTO] = segundos;
            }
        }

        Arrays.sort(tempos);
        double mediana = (tempos[REPETICOES / 2 - 1] + tempos[REPETICOES / 2]) / 2;
        return new ResultadoBenchmark(id, operacao, itens, mediana);
    }
}
