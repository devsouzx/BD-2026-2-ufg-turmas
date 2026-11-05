package com.devsouzx.UfgTurmas.mySQL;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class BenchmarkConsultas {

    private static final String URL = System.getenv().getOrDefault("MYSQL_URL",
            "jdbc:mysql://localhost:3306/ufg_turmas?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
    private static final String USUARIO = System.getenv().getOrDefault("MYSQL_USER", "root");
    private static final String SENHA = System.getenv().getOrDefault("MYSQL_PASSWORD", "");

    private static final int ID_TURMA_MODELO = 1001223;
    private static final int ID_TURMA_NOVA = 999_999_001;
    private static final int CAMPUS_SAMAMBAIA = 68700506;
    private static final int CAMPUS_CIDADE_DE_GOIAS = 68700504;
    private static final String NOME_TEMPORARIO_CAMPUS = "CAMPUS RENOMEADO (TESTE)";

    private record Consulta(String id, String categoria, String titulo, boolean escalar, String sql, Object[] params) {
    }

    public static void main(String[] args) throws Exception {
        List<Consulta> consultas = new ArrayList<>();
        consultas.add(new Consulta("L1", "Leitura pontual", "Turma por id (registro cru)", false,
                """
                SELECT * FROM turma WHERE id_turma = ?""",
                new Object[]{1001223}));
        consultas.add(new Consulta("L2", "Leitura pontual", "Turma por id (completa: componente, curso, campus e docentes)", false,
                """
                SELECT t.id_turma, t.codigo_turma, c.nome_componente, cur.nome_curso, cam.nome_campus, d.nome_docente
                FROM turma t
                JOIN componente_curricular c ON c.codigo_componente = t.codigo_componente
                JOIN curso cur ON cur.id_curso = t.id_curso
                JOIN campus cam ON cam.id_campus = t.id_campus
                LEFT JOIN turma_docente td ON td.id_turma = t.id_turma
                LEFT JOIN docente d ON d.id_docente = td.id_docente
                WHERE t.id_turma = ?""",
                new Object[]{1001223}));
        consultas.add(new Consulta("F1", "Filtro", "Turmas abertas de um campus, top 50 por matriculados", false,
                """
                SELECT id_turma, codigo_turma, total_vagas_ofertadas, total_matriculados, situacao_turma
                FROM turma WHERE situacao_turma = ? AND id_campus = ?
                ORDER BY total_matriculados DESC, id_turma LIMIT 50""",
                new Object[]{"1 - ABERTA", 68700506}));
        consultas.add(new Consulta("F2", "Filtro", "Contagem de turmas abertas com 20+ vagas ociosas (expressão entre colunas)", true,
                """
                SELECT COUNT(*) FROM turma WHERE situacao_turma = ? AND total_vagas_ofertadas - total_matriculados >= 20""",
                new Object[]{"1 - ABERTA"}));
        consultas.add(new Consulta("F3", "Filtro", "Busca textual no nome da disciplina (contém 'BANCO DE DADOS')", false,
                """
                SELECT t.id_turma, t.codigo_turma, c.nome_componente
                FROM turma t JOIN componente_curricular c ON c.codigo_componente = t.codigo_componente
                WHERE c.nome_componente LIKE ? ORDER BY t.id_turma""",
                new Object[]{"%BANCO DE DADOS%"}));
        consultas.add(new Consulta("F4", "Filtro", "Contagem de turmas a distância iniciadas a partir de 01/10/2023 (intervalo de data)", true,
                """
                SELECT COUNT(*) FROM turma WHERE modalidade_turma = ? AND data_inicio >= ?""",
                new Object[]{"À Distância", "2023-10-01"}));
        consultas.add(new Consulta("J1", "Junção N:N", "Turmas de um docente", false,
                """
                SELECT t.id_turma, c.codigo_componente, c.nome_componente
                FROM docente d
                JOIN turma_docente td ON td.id_docente = d.id_docente
                JOIN turma t ON t.id_turma = td.id_turma
                JOIN componente_curricular c ON c.codigo_componente = t.codigo_componente
                WHERE d.nome_docente = ? ORDER BY t.id_turma""",
                new Object[]{"MARCELLE FIGUEIRA MARQUES DA SILVA SALES"}));
        consultas.add(new Consulta("J2", "Junção N:N", "Docentes de um curso com nº de turmas (top 20)", false,
                """
                SELECT d.nome_docente, COUNT(*) AS qtd
                FROM turma t
                JOIN turma_docente td ON td.id_turma = t.id_turma
                JOIN docente d ON d.id_docente = td.id_docente
                WHERE t.id_curso = ?
                GROUP BY d.id_docente, d.nome_docente ORDER BY qtd DESC, d.nome_docente LIMIT 20""",
                new Object[]{69673254}));
        consultas.add(new Consulta("J3", "Junção N:N", "Todos os vínculos turma x docente x disciplina (junção completa)", false,
                """
                SELECT t.id_turma, c.nome_componente, d.nome_docente
                FROM turma t
                JOIN componente_curricular c ON c.codigo_componente = t.codigo_componente
                JOIN turma_docente td ON td.id_turma = t.id_turma
                JOIN docente d ON d.id_docente = td.id_docente""",
                new Object[]{}));
        consultas.add(new Consulta("A1", "Agregação", "Top 10 docentes com mais turmas", false,
                """
                SELECT d.nome_docente, COUNT(*) AS total_turmas
                FROM docente d JOIN turma_docente td ON td.id_docente = d.id_docente
                GROUP BY d.id_docente, d.nome_docente ORDER BY total_turmas DESC, d.nome_docente LIMIT 10""",
                new Object[]{}));
        consultas.add(new Consulta("A2", "Agregação", "Turmas, vagas e matriculados por campus", false,
                """
                SELECT cam.id_campus, cam.nome_campus, COUNT(*) AS turmas, SUM(t.total_vagas_ofertadas) AS vagas, SUM(t.total_matriculados) AS matriculados
                FROM turma t JOIN campus cam ON cam.id_campus = t.id_campus
                GROUP BY cam.id_campus, cam.nome_campus ORDER BY turmas DESC, cam.id_campus""",
                new Object[]{}));
        consultas.add(new Consulta("A3", "Agregação", "Top 10 cursos por total de matriculados", false,
                """
                SELECT cur.id_curso, cur.nome_curso, COUNT(*) AS turmas, SUM(t.total_matriculados) AS matriculados
                FROM turma t JOIN curso cur ON cur.id_curso = t.id_curso
                GROUP BY cur.id_curso, cur.nome_curso ORDER BY matriculados DESC, cur.id_curso LIMIT 10""",
                new Object[]{}));
        consultas.add(new Consulta("A4", "Agregação", "Top 10 disciplinas mais ofertadas", false,
                """
                SELECT c.codigo_componente, c.nome_componente, COUNT(*) AS turmas
                FROM turma t JOIN componente_curricular c ON c.codigo_componente = t.codigo_componente
                GROUP BY c.codigo_componente, c.nome_componente ORDER BY turmas DESC, c.codigo_componente LIMIT 10""",
                new Object[]{}));
        consultas.add(new Consulta("A5", "Agregação", "Distribuição de turmas por nº de docentes", false,
                """
                SELECT n, COUNT(*) AS turmas FROM (
                  SELECT t.id_turma, COUNT(td.id_docente) AS n FROM turma t LEFT JOIN turma_docente td ON td.id_turma = t.id_turma GROUP BY t.id_turma) x
                GROUP BY n ORDER BY n""",
                new Object[]{}));
        consultas.add(new Consulta("A6", "Agregação", "Turmas e matriculados por modalidade e situação (agrupa por 2 campos)", false,
                """
                SELECT modalidade_turma, situacao_turma, COUNT(*) AS turmas, SUM(total_matriculados) AS matriculados
                FROM turma GROUP BY modalidade_turma, situacao_turma""",
                new Object[]{}));

        try (Connection conn = DriverManager.getConnection(URL, USUARIO, SENHA)) {
            String versao = escalarTexto(conn, "SELECT VERSION()");
            long antes = contagem(conn, "SELECT COUNT(*) FROM turma");
            System.out.println("MySQL " + versao + " | linhas em turma: " + antes);

            imprimirCabecalho("MYSQL " + versao);

            // ----------------------------------------------------------------- leituras
            for (Consulta q : consultas) {
                try {
                    PreparedStatement ps = conn.prepareStatement(q.sql());
                    for (int i = 0; i < q.params().length; i++) {
                        ps.setObject(i + 1, q.params()[i]);
                    }
                    medir(q.id(), q.categoria(), q.titulo(), () -> executar(ps, q.escalar()));
                } catch (SQLException e) {
                    System.out.println(q.id() + "   ERRO ao preparar: " + e.getMessage());
                }
            }

            // ----------------------------------------------------------------- escritas
            // Cada escrita tem "preparar" e "limpar" fora da medição, para o banco voltar ao estado original.
            String colunas = colunasDaTurmaSemId(conn);   // funciona com ou sem a coluna matrizes_associadas

            PreparedStatement insTurma = conn.prepareStatement(
                    "INSERT INTO turma (id_turma, " + colunas + ") SELECT ?, " + colunas + " FROM turma WHERE id_turma = ?");
            PreparedStatement insDocentes = conn.prepareStatement(
                    "INSERT INTO turma_docente (id_turma, id_docente) SELECT ?, id_docente FROM turma_docente WHERE id_turma = ?");

            PreparedStatement delDocentes = conn.prepareStatement("DELETE FROM turma_docente WHERE id_turma = ?");
            PreparedStatement delTurma = conn.prepareStatement("DELETE FROM turma WHERE id_turma = ?");

            PreparedStatement upMatric = conn.prepareStatement("UPDATE turma SET total_matriculados = total_matriculados + ? WHERE id_turma = ?");
            PreparedStatement upVagas = conn.prepareStatement("UPDATE turma SET total_vagas_ofertadas = total_vagas_ofertadas + ? WHERE id_campus = ?");
            PreparedStatement upCampus = conn.prepareStatement("UPDATE campus SET nome_campus = ? WHERE id_campus = ?");

            String nomeOriginalCampus = escalarTexto(conn,
                    "SELECT nome_campus FROM campus WHERE id_campus = " + CAMPUS_CIDADE_DE_GOIAS);

            // Clone completo da turma modelo (turma + docentes) numa única transação.
            Operacao inserirNova = () -> {
                conn.setAutoCommit(false);
                try {
                    insTurma.setInt(1, ID_TURMA_NOVA);
                    insTurma.setInt(2, ID_TURMA_MODELO);
                    long n = insTurma.executeUpdate();
                    insDocentes.setInt(1, ID_TURMA_NOVA);
                    insDocentes.setInt(2, ID_TURMA_MODELO);
                    insDocentes.executeUpdate();
                    conn.commit();
                    return n;
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
            };
            Passo apagarNova = () -> {
                conn.setAutoCommit(false);
                try {
                    delDocentes.setInt(1, ID_TURMA_NOVA);
                    delDocentes.executeUpdate();
                    delTurma.setInt(1, ID_TURMA_NOVA);
                    delTurma.executeUpdate();
                    conn.commit();
                } finally {
                    conn.setAutoCommit(true);
                }
            };
            Operacao removerNova = () -> {
                conn.setAutoCommit(false);
                try {
                    long n = 0;
                    delDocentes.setInt(1, ID_TURMA_NOVA);
                    delDocentes.executeUpdate();
                    delTurma.setInt(1, ID_TURMA_NOVA);
                    n = delTurma.executeUpdate();
                    conn.commit();
                    return n;
                } finally {
                    conn.setAutoCommit(true);
                }
            };
            Passo inserirSemMedir = () -> inserirNova.executar();

            medir("E1", "Escrita", "Inserir turma nova (clone da L1, com os 5 docentes)",
                    apagarNova, inserirNova, apagarNova);

            medir("E2", "Escrita", "Atualizar 1 turma por id (+1 matriculado)",
                    null,
                    () -> { upMatric.setInt(1, 1); upMatric.setInt(2, ID_TURMA_MODELO); return upMatric.executeUpdate(); },
                    () -> { upMatric.setInt(1, -1); upMatric.setInt(2, ID_TURMA_MODELO); upMatric.executeUpdate(); });

            medir("E3", "Escrita", "Atualização em massa: +1 vaga em todas as turmas de um campus",
                    null,
                    () -> { upVagas.setInt(1, 1); upVagas.setInt(2, CAMPUS_SAMAMBAIA); return upVagas.executeUpdate(); },
                    () -> { upVagas.setInt(1, -1); upVagas.setInt(2, CAMPUS_SAMAMBAIA); upVagas.executeUpdate(); });

            // No relacional o nome do campus existe em UMA linha; no Mongo está copiado em cada turma.
            medir("E4", "Escrita", "Renomear um campus (1 linha no SQL x N documentos no Mongo)",
                    null,
                    () -> { upCampus.setString(1, NOME_TEMPORARIO_CAMPUS); upCampus.setInt(2, CAMPUS_CIDADE_DE_GOIAS); return upCampus.executeUpdate(); },
                    () -> { upCampus.setString(1, nomeOriginalCampus); upCampus.setInt(2, CAMPUS_CIDADE_DE_GOIAS); upCampus.executeUpdate(); });

            medir("E5", "Escrita", "Remover a turma inserida",
                    inserirSemMedir, removerNova, null);

            long depois = contagem(conn, "SELECT COUNT(*) FROM turma");
            System.out.println();
            System.out.println(depois == antes
                    ? "Conferência: tabela voltou ao estado original (" + depois + " linhas)."
                    : "ATENÇÃO: linhas antes=" + antes + " depois=" + depois + " (verifique a limpeza das escritas).");

            salvarCsv("mysql", "resultados_mysql.csv");
        }
    }

    // ---------------------------------------------------------------------- medição (mesma metodologia nos dois bancos)
    // 1ª execução registrada à parte (a frio); AQUECIMENTO execuções descartadas; REPETICOES medidas com System.nanoTime().
    // "preparar" e "limpar" (usados nas escritas) ficam FORA do tempo medido.
    private static final int AQUECIMENTO = 5;
    private static final int REPETICOES = 30;
    private static final List<String> CSV = new ArrayList<>();

    /** Operação medida: devolve quantos itens (linhas/documentos) produziu ou alterou. */
    @FunctionalInterface
    private interface Operacao {
        long executar() throws Exception;
    }

    /** Passo fora da medição. */
    @FunctionalInterface
    private interface Passo {
        void executar() throws Exception;
    }

    private static void imprimirCabecalho(String banco) {
        System.out.println();
        System.out.println("=== BENCHMARK " + banco + " ===");
        System.out.printf("Aquecimento: %d execuções descartadas | Medidas: %d execuções por consulta%n", AQUECIMENTO, REPETICOES);
        System.out.printf("%-4s %-16s %-62s %8s %9s %9s %9s %9s %9s%n",
                "ID", "Categoria", "Consulta", "Itens", "1ª (ms)", "Mín", "Mediana", "Média", "P95");
        System.out.println("-".repeat(145));
        CSV.clear();
        CSV.add("banco,id,categoria,titulo,itens,primeira_ms,min_ms,mediana_ms,media_ms,p95_ms,max_ms,desvio_ms,erro");
    }

    private static void medir(String id, String categoria, String titulo, Operacao operacao) {
        medir(id, categoria, titulo, null, operacao, null);
    }

    private static void medir(String id, String categoria, String titulo, Passo preparar, Operacao operacao, Passo limpar) {
        String curto = titulo.length() > 62 ? titulo.substring(0, 59) + "..." : titulo;
        try {
            double[] t = new double[REPETICOES];
            double primeira = 0;
            long itens = 0;
            for (int i = 0; i < AQUECIMENTO + REPETICOES; i++) {
                if (preparar != null) {
                    preparar.executar();
                }
                long inicio = System.nanoTime();
                long n = operacao.executar();
                double ms = (System.nanoTime() - inicio) / 1_000_000.0;
                if (limpar != null) {
                    limpar.executar();
                }
                if (i == 0) {
                    primeira = ms;
                    itens = n;
                }
                if (i >= AQUECIMENTO) {
                    t[i - AQUECIMENTO] = ms;
                }
            }
            double[] s = t.clone();
            java.util.Arrays.sort(s);
            int n = s.length;
            double media = java.util.Arrays.stream(s).average().orElse(0);
            double mediana = n % 2 == 1 ? s[n / 2] : (s[n / 2 - 1] + s[n / 2]) / 2.0;
            double p95 = s[Math.max(0, (int) Math.ceil(0.95 * n) - 1)];
            double soma = 0;
            for (double v : s) {
                soma += (v - media) * (v - media);
            }
            double desvio = n > 1 ? Math.sqrt(soma / (n - 1)) : 0;

            System.out.printf(Locale.ROOT, "%-4s %-16s %-62s %8d %9.3f %9.3f %9.3f %9.3f %9.3f%n",
                    id, categoria, curto, itens, primeira, s[0], mediana, media, p95);
            CSV.add(String.format(Locale.ROOT, "%s,\"%s\",\"%s\",%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,",
                    id, categoria, titulo.replace("\"", "'"), itens, primeira, s[0], mediana, media, p95, s[n - 1], desvio));
        } catch (Exception e) {
            System.out.printf("%-4s %-16s %-62s ERRO: %s%n", id, categoria, curto, e.getMessage());
            CSV.add(id + ",\"" + categoria + "\",\"" + titulo.replace("\"", "'") + "\",,,,,,,,,\"" + e.getMessage() + "\"");
        }
    }

    /** CSV com a mesma estrutura nos dois bancos (junte pelo id para montar a tabela comparativa). */
    private static void salvarCsv(String banco, String arquivo) throws java.io.IOException {
        List<String> saida = new ArrayList<>();
        for (int i = 0; i < CSV.size(); i++) {
            saida.add(i == 0 ? CSV.get(i) : banco + "," + CSV.get(i));
        }
        java.nio.file.Files.write(java.nio.file.Path.of(arquivo), saida, java.nio.charset.StandardCharsets.UTF_8);
        System.out.println();
        System.out.println("Resultados salvos em: " + java.nio.file.Path.of(arquivo).toAbsolutePath());
    }

    // ---------------------------------------------------------------------- auxiliares

    /** Executa a consulta e percorre TODAS as linhas lendo TODAS as colunas (como o Mongo decodifica todo o documento). */
    private static long executar(PreparedStatement ps, boolean escalar) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            if (escalar) {
                rs.next();
                return rs.getLong(1);
            }
            int colunas = rs.getMetaData().getColumnCount();
            long linhas = 0;
            while (rs.next()) {
                for (int i = 1; i <= colunas; i++) {
                    rs.getObject(i);
                }
                linhas++;
            }
            return linhas;
        }
    }

    private static long contagem(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static String escalarTexto(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        }
    }


    /** Lista as colunas de turma (menos id_turma) para clonar uma linha sem depender do esquema exato. */
    private static String colunasDaTurmaSemId(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM turma WHERE 1 = 0");
             ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData md = rs.getMetaData();
            List<String> nomes = new ArrayList<>();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                if (!md.getColumnName(i).equalsIgnoreCase("id_turma")) {
                    nomes.add(md.getColumnName(i));
                }
            }
            return nomes.stream().collect(Collectors.joining(", "));
        }
    }
}
