package com.devsouzx.UfgTurmas;

public record ResultadoBenchmark(
        String id,
        String grupo,
        String operacao,
        Long quantidade,
        Double mediana,
        Double media,
        Double p95,
        String erro) {
}
