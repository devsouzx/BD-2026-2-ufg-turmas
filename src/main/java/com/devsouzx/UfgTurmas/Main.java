package com.devsouzx.UfgTurmas;

import com.devsouzx.UfgTurmas.mongoDB.BenchmarkMongoDB;
import com.devsouzx.UfgTurmas.mongoDB.ImportadorMongoDB;
import com.devsouzx.UfgTurmas.mySQL.BenchmarkConsultas;
import com.devsouzx.UfgTurmas.mySQL.PopulaBanco;

import java.util.List;

public class Main {

    public static void main(String[] args) throws Exception {
        boolean carregar = !(args.length > 0 && args[0].equals("--pular-carga"));

        if (carregar) {
            System.out.println("=== Carga do MySQL ===");
            PopulaBanco.main(new String[0]);
            System.out.println();
            System.out.println("=== Carga do MongoDB ===");
            ImportadorMongoDB.main(new String[0]);
        }

        System.out.println();
        System.out.println("=== Benchmark ===");
        List<ResultadoBenchmark> mysql = BenchmarkConsultas.executar();
        List<ResultadoBenchmark> mongo = BenchmarkMongoDB.executar();

        ComparadorBenchmarks.imprimir(mysql, mongo);
        ComparadorBenchmarks.salvarCsv(mysql, mongo);
    }
}
