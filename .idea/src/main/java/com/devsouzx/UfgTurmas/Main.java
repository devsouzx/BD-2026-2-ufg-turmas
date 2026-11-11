package com.devsouzx.UfgTurmas;

import com.devsouzx.UfgTurmas.mongoDB.BenchmarkMongoDB;
import com.devsouzx.UfgTurmas.mongoDB.ImportadorMongoDB;
import com.devsouzx.UfgTurmas.mySQL.BenchmarkConsultas;
import com.devsouzx.UfgTurmas.mySQL.PopulaBanco;

import java.util.Arrays;
import java.util.List;

public class Main {

    public static void main(String[] args) throws Exception {
        boolean pularCarga = Arrays.asList(args).contains("--pular-carga");
        String arquivoCsv = Arrays.stream(args)
                .filter(arg -> arg.startsWith("--csv="))
                .map(arg -> arg.substring("--csv=".length()))
                .findFirst()
                .orElse("da_turmas_2023_2.csv");

        if (!pularCarga) {
            executarEtapa("Carga do MySQL", () -> PopulaBanco.executar(new String[]{arquivoCsv}));
            executarEtapa("Carga do MongoDB", () -> ImportadorMongoDB.main(new String[]{arquivoCsv}));
        }

        List<ResultadoBenchmark> resultadosMysql = executarEtapa(
                "Benchmark MySQL", BenchmarkConsultas::executar);
        List<ResultadoBenchmark> resultadosMongo = executarEtapa(
                "Benchmark MongoDB", BenchmarkMongoDB::executar);

        ComparadorBenchmarks.imprimir(resultadosMysql, resultadosMongo);
        ComparadorBenchmarks.salvarCsv(resultadosMysql, resultadosMongo);

        if (ComparadorBenchmarks.possuiFalhas(resultadosMysql, resultadosMongo)) {
            throw new IllegalStateException("Um ou mais testes falharam. Consulte a tabela e os erros exibidos.");
        }
    }

    private static void executarEtapa(String nome, Etapa etapa) throws Exception {
        System.out.println();
        System.out.println("=== " + nome + " ===");
        etapa.executar();
    }

    private static <T> T executarEtapa(String nome, EtapaComRetorno<T> etapa) throws Exception {
        System.out.println();
        System.out.println("=== " + nome + " ===");
        return etapa.executar();
    }

    @FunctionalInterface
    private interface Etapa {
        void executar() throws Exception;
    }

    @FunctionalInterface
    private interface EtapaComRetorno<T> {
        T executar() throws Exception;
    }
}
