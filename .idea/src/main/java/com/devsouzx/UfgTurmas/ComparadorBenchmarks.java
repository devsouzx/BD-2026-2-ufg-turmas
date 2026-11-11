package com.devsouzx.UfgTurmas;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ComparadorBenchmarks {

    private static final int LARGURA_OPERACAO = 34;

    private ComparadorBenchmarks() {
    }

    public static void imprimir(List<ResultadoBenchmark> mysql,
                                List<ResultadoBenchmark> mongo) {
        Map<String, ResultadoBenchmark> mongoPorId = indexar(mongo, "MongoDB");
        validarIds(mysql, mongoPorId);

        String borda = "+------+" + "-".repeat(LARGURA_OPERACAO + 2)
                + "+-----------------+--------------+--------------+-------------+";

        System.out.println();
        System.out.println("====================== COMPARAÇÃO MYSQL x MONGODB ======================");
        System.out.println("Mediana de 30 execuções após 5 aquecimentos. Valores em milissegundos.");
        System.out.println(borda);
        System.out.printf("| %-4s | %-" + LARGURA_OPERACAO
                        + "s | %15s | %12s | %12s | %11s |%n",
                "ID", "Operação", "Itens SQL/Mongo", "MySQL (ms)",
                "MongoDB (ms)", "Diferença");
        System.out.println(borda);

        for (ResultadoBenchmark resultadoMysql : mysql) {
            ResultadoBenchmark resultadoMongo = mongoPorId.get(resultadoMysql.id());
            List<String> partes = quebrar(resultadoMysql.operacao(), LARGURA_OPERACAO);
            String itens = quantidade(resultadoMysql.quantidade())
                    + "/" + quantidade(resultadoMongo.quantidade());
            String tempoMysql = tempo(resultadoMysql.mediana());
            String tempoMongo = tempo(resultadoMongo.mediana());
            String diferenca = diferenca(resultadoMysql, resultadoMongo);

            for (int i = 0; i < partes.size(); i++) {
                if (i == 0) {
                    System.out.printf("| %-4s | %-" + LARGURA_OPERACAO
                                    + "s | %15s | %12s | %12s | %11s |%n",
                            resultadoMysql.id(), partes.get(i), itens,
                            tempoMysql, tempoMongo, diferenca);
                } else {
                    System.out.printf("| %-4s | %-" + LARGURA_OPERACAO
                                    + "s | %15s | %12s | %12s | %11s |%n",
                            "", partes.get(i), "", "", "", "");
                }
            }

            if (resultadoMysql.erro() != null) {
                System.out.println("|      | MySQL: " + limitar(resultadoMysql.erro(), 105));
            }
            if (resultadoMongo.erro() != null) {
                System.out.println("|      | MongoDB: " + limitar(resultadoMongo.erro(), 102));
            }
        }

        System.out.println(borda);
        System.out.println("Itens: linhas/documentos retornados ou registros afetados.");
        System.out.println("Diferença positiva: MongoDB mais rápido. Diferença negativa: MySQL mais rápido.");
        System.out.println("R2 pode ter quantidades diferentes: o MySQL retorna uma linha por docente; o MongoDB incorpora os docentes no documento.");
    }

    public static void salvarCsv(List<ResultadoBenchmark> mysql,
                                 List<ResultadoBenchmark> mongo) throws IOException {
        Map<String, ResultadoBenchmark> mongoPorId = indexar(mongo, "MongoDB");
        validarIds(mysql, mongoPorId);

        List<String> linhas = new ArrayList<>();
        linhas.add("id,grupo,operacao,itens_mysql,itens_mongodb,mediana_mysql_ms,mediana_mongodb_ms,diferenca_percentual,media_mysql_ms,media_mongodb_ms,p95_mysql_ms,p95_mongodb_ms,erro_mysql,erro_mongodb");

        for (ResultadoBenchmark m : mysql) {
            ResultadoBenchmark d = mongoPorId.get(m.id());
            linhas.add(String.join(",",
                    csv(m.id()), csv(m.grupo()), csv(m.operacao()),
                    quantidade(m.quantidade()), quantidade(d.quantidade()),
                    numero(m.mediana()), numero(d.mediana()), numero(calcularDiferenca(m, d)),
                    numero(m.media()), numero(d.media()), numero(m.p95()), numero(d.p95()),
                    csv(m.erro()), csv(d.erro())));
        }

        Path pasta = Path.of("resultados");
        Files.createDirectories(pasta);
        Path arquivo = pasta.resolve("benchmark_comparativo.csv");
        Files.write(arquivo, linhas, StandardCharsets.UTF_8);
        System.out.println("Arquivo comparativo: " + arquivo.toAbsolutePath());
    }

    public static boolean possuiFalhas(List<ResultadoBenchmark> mysql,
                                       List<ResultadoBenchmark> mongo) {
        return mysql.stream().anyMatch(r -> r.erro() != null)
                || mongo.stream().anyMatch(r -> r.erro() != null);
    }

    private static Map<String, ResultadoBenchmark> indexar(
            List<ResultadoBenchmark> resultados, String banco) {
        Map<String, ResultadoBenchmark> mapa = new HashMap<>();
        for (ResultadoBenchmark resultado : resultados) {
            if (mapa.put(resultado.id(), resultado) != null) {
                throw new IllegalStateException("ID duplicado no benchmark " + banco + ": " + resultado.id());
            }
        }
        return mapa;
    }

    private static void validarIds(List<ResultadoBenchmark> mysql,
                                   Map<String, ResultadoBenchmark> mongoPorId) {
        Set<String> idsMysql = new HashSet<>();
        for (ResultadoBenchmark resultado : mysql) {
            if (!idsMysql.add(resultado.id())) {
                throw new IllegalStateException("ID duplicado no benchmark MySQL: " + resultado.id());
            }
            ResultadoBenchmark correspondente = mongoPorId.get(resultado.id());
            if (correspondente == null) {
                throw new IllegalStateException("Consulta " + resultado.id() + " não existe no benchmark MongoDB.");
            }
            if (!resultado.grupo().equals(correspondente.grupo())
                    || !resultado.operacao().equals(correspondente.operacao())) {
                throw new IllegalStateException("Descrição divergente na consulta " + resultado.id() + ".");
            }
        }
        if (!idsMysql.equals(mongoPorId.keySet())) {
            throw new IllegalStateException("Os benchmarks MySQL e MongoDB possuem conjuntos diferentes de consultas.");
        }
    }

    private static String diferenca(ResultadoBenchmark mysql, ResultadoBenchmark mongo) {
        Double valor = calcularDiferenca(mysql, mongo);
        return valor == null ? "-" : String.format(Locale.ROOT, "%+.1f%%", valor);
    }

    private static Double calcularDiferenca(ResultadoBenchmark mysql, ResultadoBenchmark mongo) {
        if (mysql.erro() != null || mongo.erro() != null
                || mysql.mediana() == null || mongo.mediana() == null
                || mysql.mediana() <= 0) {
            return null;
        }
        return (mysql.mediana() - mongo.mediana()) / mysql.mediana() * 100.0;
    }

    private static String tempo(Double valor) {
        return valor == null ? "ERRO" : String.format(Locale.ROOT, "%.3f", valor);
    }

    private static String quantidade(Long valor) {
        return valor == null ? "-" : valor.toString();
    }

    private static String numero(Number valor) {
        return valor == null ? "" : String.format(Locale.ROOT, "%.4f", valor.doubleValue());
    }


    private static String csv(String valor) {
        return valor == null ? "" : "\"" + valor.replace("\"", "\"\"") + "\"";
    }

    private static List<String> quebrar(String texto, int largura) {
        List<String> linhas = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        for (String palavra : texto.split("\\s+")) {
            if (atual.length() > 0 && atual.length() + palavra.length() + 1 > largura) {
                linhas.add(atual.toString());
                atual.setLength(0);
            }
            if (atual.length() > 0) {
                atual.append(' ');
            }
            atual.append(palavra);
        }
        if (atual.length() > 0) {
            linhas.add(atual.toString());
        }
        return linhas;
    }

    private static String limitar(String texto, int tamanho) {
        String linha = texto.replace('\n', ' ');
        return linha.length() <= tamanho ? linha : linha.substring(0, tamanho - 3) + "...";
    }
}
