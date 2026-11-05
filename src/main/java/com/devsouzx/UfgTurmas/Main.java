package com.devsouzx.UfgTurmas;

import com.devsouzx.UfgTurmas.mongoDB.BenchmarkMongoDB;
import com.devsouzx.UfgTurmas.mongoDB.ImportadorMongoDB;
import com.devsouzx.UfgTurmas.mySQL.BenchmarkConsultas;
import com.devsouzx.UfgTurmas.mySQL.PopulaBanco;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Main {

    @FunctionalInterface
    private interface Etapa {
        void executar() throws Exception;
    }

    public static void main(String[] args) {
        List<String> opcoes = Arrays.asList(args);
        boolean pularCarga = opcoes.contains("--pular-carga");
        boolean mysql = !opcoes.contains("--so-mongo");
        boolean mongo = !opcoes.contains("--so-mysql");

        String[] semArgs = new String[0];
        List<String> resumo = new ArrayList<>();

        if (!pularCarga) {
            if (mysql) {
                executar("1. Carga do MySQL (PopulaBanco)", () -> PopulaBanco.main(semArgs), resumo);
            }
            if (mongo) {
                executar("2. Carga do MongoDB (ImportadorMongoDB)", () -> ImportadorMongoDB.main(semArgs), resumo);
            }
        }
        if (mysql) {
            executar("3. Benchmark do MySQL (BenchmarkConsultas)", () -> BenchmarkConsultas.main(semArgs), resumo);
        }
        if (mongo) {
            executar("4. Benchmark do MongoDB (BenchmarkMongoDB)", () -> BenchmarkMongoDB.main(semArgs), resumo);
        }

        System.out.println();
        System.out.println("################ RESUMO ################");
        resumo.forEach(System.out::println);
    }

    private static void executar(String nome, Etapa etapa, List<String> resumo) {
        System.out.println();
        System.out.println("################ " + nome + " ################");
        long inicio = System.currentTimeMillis();
        try {
            etapa.executar();
            resumo.add(String.format("OK    %-50s %6.1f s", nome, (System.currentTimeMillis() - inicio) / 1000.0));
        } catch (Throwable e) {
            e.printStackTrace();
            resumo.add(String.format("FALHA %-50s %s", nome, e));
        }
    }
}

