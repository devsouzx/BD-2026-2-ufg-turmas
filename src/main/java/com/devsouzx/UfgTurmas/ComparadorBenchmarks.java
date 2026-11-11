package com.devsouzx.UfgTurmas;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ComparadorBenchmarks {

    private ComparadorBenchmarks() {
    }

    public static void imprimir(List<ResultadoBenchmark> mysql, List<ResultadoBenchmark> mongo) {
        Map<String, ResultadoBenchmark> mongoPorId = porId(mongo);
        int largura = mysql.stream().mapToInt(r -> r.operacao().length()).max().orElse(8);
        String formato = "%-4s  %-" + largura + "s  %7s  %10s  %12s  %17s%n";

        System.out.println();
        System.out.println("Comparação MySQL x MongoDB (mediana de " + ResultadoBenchmark.REPETICOES + " execuções)");
        System.out.println();
        System.out.printf(formato, "ID", "Operação", "Itens", "MySQL (s)", "MongoDB (s)", "Mongo - MySQL (s)");
        System.out.println("-".repeat(4 + largura + 7 + 10 + 12 + 17 + 10));

        for (ResultadoBenchmark m : mysql) {
            ResultadoBenchmark g = mongoPorId.get(m.id());
            System.out.printf(formato, m.id(), m.operacao(), m.itens() + "/" + g.itens(),
                    segundos(m.mediana()), segundos(g.mediana()),
                    String.format(Locale.ROOT, "%+.6f", g.mediana() - m.mediana()));
        }
    }

    public static void salvarCsv(List<ResultadoBenchmark> mysql, List<ResultadoBenchmark> mongo) throws IOException {
        Map<String, ResultadoBenchmark> mongoPorId = porId(mongo);
        List<String> linhas = new ArrayList<>();
        linhas.add("id,operacao,itens_mysql,itens_mongodb,mysql_s,mongodb_s");

        for (ResultadoBenchmark m : mysql) {
            ResultadoBenchmark g = mongoPorId.get(m.id());
            linhas.add(String.join(",", m.id(), "\"" + m.operacao() + "\"",
                    String.valueOf(m.itens()), String.valueOf(g.itens()),
                    segundos(m.mediana()), segundos(g.mediana())));
        }

        Path arquivo = Path.of("resultados", "benchmark_comparativo.csv");
        Files.createDirectories(arquivo.getParent());
        Files.write(arquivo, linhas, StandardCharsets.UTF_8);
        System.out.println();
        System.out.println("Resultados salvos em " + arquivo.toAbsolutePath());
    }

    private static Map<String, ResultadoBenchmark> porId(List<ResultadoBenchmark> resultados) {
        return resultados.stream().collect(Collectors.toMap(ResultadoBenchmark::id, Function.identity()));
    }

    private static String segundos(double valor) {
        return String.format(Locale.ROOT, "%.6f", valor);
    }
}
