package com.devsouzx.UfgTurmas.mySQL;

import com.devsouzx.UfgTurmas.ResultadoBenchmark;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BenchmarkConsultas {

    private static final String HOST = System.getenv().getOrDefault("MYSQL_HOST", "localhost");
    private static final String PORTA = System.getenv().getOrDefault("MYSQL_PORT", "3306");
    private static final String URL = System.getenv().getOrDefault(
            "MYSQL_URL",
            "jdbc:mysql://" + HOST + ":" + PORTA + "/ufg_turmas?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
    private static final String USUARIO = System.getenv().getOrDefault("MYSQL_USER", "root");
    private static final String SENHA = System.getenv().getOrDefault("MYSQL_PASSWORD", "");

    private static final int ID_TURMA_MODELO = 1001223;
    private static final int ID_TURMA_TESTE = 999_999_001;
    private static final int ID_CAMPUS_TESTE = 68700504;
    private static final String NOME_CAMPUS_TESTE = "CAMPUS RENOMEADO (TESTE)";
    private static final int AQUECIMENTO = 5;
    private static final int REPETICOES = 30;

    private record Consulta(
            String id,
            String grupo,
            String operacao,
            String sql,
            Object[] parametros,
            boolean escalar) {
    }

    @FunctionalInterface
    private interface Operacao {
        long executar() throws Exception;
    }

    @FunctionalInterface
    private interface Passo {
        void executar() throws Exception;
    }

    public static List<ResultadoBenchmark> executar() throws Exception {
        List<ResultadoBenchmark> resultados = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(URL, USUARIO, SENHA)) {
            String versao = valorTexto(conn, "SELECT VERSION()");
            long turmasAntes = contar(conn, "SELECT COUNT(*) FROM turma");
            long vinculosAntes = contar(conn, "SELECT COUNT(*) FROM turma_docente");
            String nomeCampusAntes = valorTexto(conn,
                    "SELECT nome_campus FROM campus WHERE id_campus = " + ID_CAMPUS_TESTE);
            int matriculadosAntes = valorInteiro(conn,
                    "SELECT total_matriculados FROM turma WHERE id_turma = " + ID_TURMA_MODELO);

            validarDadosDeTeste(conn);
            if (nomeCampusAntes == null) {
                throw new SQLException("Campus de teste não encontrado: " + ID_CAMPUS_TESTE);
            }
            System.out.println("MySQL " + versao + " | turmas: " + turmasAntes
                    + " | vínculos: " + vinculosAntes);

            for (Consulta consulta : consultas()) {
                try (PreparedStatement ps = conn.prepareStatement(consulta.sql())) {
                    for (int i = 0; i < consulta.parametros().length; i++) {
                        ps.setObject(i + 1, consulta.parametros()[i]);
                    }
                    medir(resultados, consulta.id(), consulta.grupo(), consulta.operacao(),
                            null, () -> executar(ps, consulta.escalar()), null);
                }
            }

            executarEscritas(conn, nomeCampusAntes, matriculadosAntes, resultados);

            long turmasDepois = contar(conn, "SELECT COUNT(*) FROM turma");
            long vinculosDepois = contar(conn, "SELECT COUNT(*) FROM turma_docente");
            String nomeCampusDepois = valorTexto(conn,
                    "SELECT nome_campus FROM campus WHERE id_campus = " + ID_CAMPUS_TESTE);
            int matriculadosDepois = valorInteiro(conn,
                    "SELECT total_matriculados FROM turma WHERE id_turma = " + ID_TURMA_MODELO);
            long turmaTemporaria = contar(conn,
                    "SELECT COUNT(*) FROM turma WHERE id_turma = " + ID_TURMA_TESTE);
            boolean restaurado = turmasAntes == turmasDepois
                    && vinculosAntes == vinculosDepois
                    && nomeCampusAntes.equals(nomeCampusDepois)
                    && matriculadosAntes == matriculadosDepois
                    && turmaTemporaria == 0;

            if (!restaurado) {
                throw new IllegalStateException("Os dados do MySQL não foram restaurados após as operações de escrita.");
            }
        }
        return List.copyOf(resultados);
    }

    private static List<Consulta> consultas() {
        return List.of(
                new Consulta("R1", "Leitura", "Turma por ID (atributos básicos)",
                        "SELECT id_turma, codigo_turma, ano, periodo, total_vagas_ofertadas, total_matriculados "
                                + "FROM turma WHERE id_turma = ?",
                        new Object[]{ID_TURMA_MODELO}, false),
                new Consulta("R2", "Leitura", "Turma por ID com dados relacionados",
                        "SELECT t.*, c.codigo_componente, c.nome_componente, c.tipo_componente, c.ch_total, "
                                + "cur.id_curso, cur.nome_curso, cam.id_campus, cam.nome_campus, d.nome_docente "
                                + "FROM turma t "
                                + "JOIN componente_curricular c ON c.codigo_componente = t.codigo_componente "
                                + "JOIN curso cur ON cur.id_curso = t.id_curso "
                                + "JOIN campus cam ON cam.id_campus = t.id_campus "
                                + "LEFT JOIN turma_docente td ON td.id_turma = t.id_turma "
                                + "LEFT JOIN docente d ON d.id_docente = td.id_docente "
                                + "WHERE t.id_turma = ?",
                        new Object[]{ID_TURMA_MODELO}, false),
                new Consulta("F1", "Filtro", "Abertas por campus (top 50)",
                        "SELECT id_turma, codigo_turma, total_vagas_ofertadas, total_matriculados, situacao_turma "
                                + "FROM turma WHERE situacao_turma = ? AND id_campus = ? "
                                + "ORDER BY total_matriculados DESC, id_turma LIMIT 50",
                        new Object[]{"1 - ABERTA", 68700506}, false),
                new Consulta("F2", "Filtro", "Abertas com 20+ vagas ociosas",
                        "SELECT COUNT(*) FROM turma WHERE situacao_turma = ? "
                                + "AND COALESCE(total_vagas_ofertadas, 0) - COALESCE(total_matriculados, 0) >= 20",
                        new Object[]{"1 - ABERTA"}, true),
                new Consulta("F3", "Filtro", "Componente por trecho do nome",
                        "SELECT t.id_turma, t.codigo_turma, c.nome_componente "
                                + "FROM turma t JOIN componente_curricular c "
                                + "ON c.codigo_componente = t.codigo_componente "
                                + "WHERE c.nome_componente LIKE ? ORDER BY t.id_turma",
                        new Object[]{"%BANCO DE DADOS%"}, false),
                new Consulta("F4", "Filtro", "A distância por data de início",
                        "SELECT COUNT(*) FROM turma WHERE modalidade_turma = ? AND data_inicio >= ?",
                        new Object[]{"À Distância", java.sql.Date.valueOf("2023-10-01")}, true),
                new Consulta("J1", "Relação", "Turmas de um docente",
                        "SELECT t.id_turma, c.codigo_componente, c.nome_componente "
                                + "FROM docente d "
                                + "JOIN turma_docente td ON td.id_docente = d.id_docente "
                                + "JOIN turma t ON t.id_turma = td.id_turma "
                                + "JOIN componente_curricular c ON c.codigo_componente = t.codigo_componente "
                                + "WHERE d.nome_docente = ? ORDER BY t.id_turma",
                        new Object[]{"MARCELLE FIGUEIRA MARQUES DA SILVA SALES"}, false),
                new Consulta("A1", "Agregação", "Top docentes por nº de turmas",
                        "SELECT d.nome_docente, COUNT(*) AS total_turmas "
                                + "FROM docente d JOIN turma_docente td ON td.id_docente = d.id_docente "
                                + "GROUP BY d.id_docente, d.nome_docente "
                                + "ORDER BY total_turmas DESC, d.nome_docente LIMIT 10",
                        new Object[]{}, false),
                new Consulta("A2", "Agregação", "Vagas e matriculados por campus",
                        "SELECT cam.id_campus, cam.nome_campus, COUNT(*) AS turmas, "
                                + "SUM(t.total_vagas_ofertadas) AS vagas, SUM(t.total_matriculados) AS matriculados "
                                + "FROM turma t JOIN campus cam ON cam.id_campus = t.id_campus "
                                + "GROUP BY cam.id_campus, cam.nome_campus "
                                + "ORDER BY turmas DESC, cam.id_campus",
                        new Object[]{}, false),
                new Consulta("A3", "Agregação", "Distribuição de docentes por turma",
                        "SELECT qtd_docentes, COUNT(*) AS turmas FROM ( "
                                + "SELECT t.id_turma, COUNT(td.id_docente) AS qtd_docentes "
                                + "FROM turma t LEFT JOIN turma_docente td ON td.id_turma = t.id_turma "
                                + "GROUP BY t.id_turma) AS distribuicao "
                                + "GROUP BY qtd_docentes ORDER BY qtd_docentes",
                        new Object[]{}, false)
        );
    }

    private static void executarEscritas(
            Connection conn,
            String nomeCampusOriginal,
            int matriculadosOriginais,
            List<ResultadoBenchmark> resultados) throws SQLException {
        if (contar(conn, "SELECT COUNT(*) FROM turma WHERE id_turma = " + ID_TURMA_TESTE) != 0) {
            throw new SQLException("O ID reservado para os testes já existe: " + ID_TURMA_TESTE);
        }

        String colunas = colunasDaTurmaSemId(conn);
        String sqlInserirTurma = "INSERT INTO turma (id_turma, " + colunas + ") "
                + "SELECT ?, " + colunas + " FROM turma WHERE id_turma = ?";

        try (PreparedStatement inserirTurma = conn.prepareStatement(sqlInserirTurma);
             PreparedStatement inserirVinculos = conn.prepareStatement(
                     "INSERT INTO turma_docente (id_turma, id_docente) "
                             + "SELECT ?, id_docente FROM turma_docente WHERE id_turma = ?");
             PreparedStatement excluirVinculos = conn.prepareStatement(
                     "DELETE FROM turma_docente WHERE id_turma = ?");
             PreparedStatement excluirTurma = conn.prepareStatement(
                     "DELETE FROM turma WHERE id_turma = ?");
             PreparedStatement atualizarMatriculados = conn.prepareStatement(
                     "UPDATE turma SET total_matriculados = ? WHERE id_turma = ?");
             PreparedStatement renomearCampus = conn.prepareStatement(
                     "UPDATE campus SET nome_campus = ? WHERE id_campus = ?")) {

            Passo inserirNova = () -> inserirTurma(conn, inserirTurma, inserirVinculos);
            Passo excluirNova = () -> excluirTurma(conn, excluirVinculos, excluirTurma);

            medir(resultados, "E1", "Escrita", "Inserir turma com docentes",
                    null,
                    () -> {
                        inserirNova.executar();
                        return 1;
                    },
                    excluirNova);

            medir(resultados, "E2", "Escrita", "Alterar matriculados por turma",
                    null,
                    () -> {
                        atualizarMatriculados.setInt(1, matriculadosOriginais + 1);
                        atualizarMatriculados.setInt(2, ID_TURMA_MODELO);
                        return atualizarMatriculados.executeUpdate();
                    },
                    () -> {
                        atualizarMatriculados.setInt(1, matriculadosOriginais);
                        atualizarMatriculados.setInt(2, ID_TURMA_MODELO);
                        atualizarMatriculados.executeUpdate();
                    });

            medir(resultados, "E3", "Escrita", "Renomear campus compartilhado",
                    null,
                    () -> {
                        renomearCampus.setString(1, NOME_CAMPUS_TESTE);
                        renomearCampus.setInt(2, ID_CAMPUS_TESTE);
                        return renomearCampus.executeUpdate();
                    },
                    () -> {
                        renomearCampus.setString(1, nomeCampusOriginal);
                        renomearCampus.setInt(2, ID_CAMPUS_TESTE);
                        renomearCampus.executeUpdate();
                    });

            medir(resultados, "E4", "Escrita", "Excluir turma com docentes",
                    inserirNova,
                    () -> excluirTurma(conn, excluirVinculos, excluirTurma),
                    excluirNova);
        }
    }

    private static void inserirTurma(
            Connection conn,
            PreparedStatement inserirTurma,
            PreparedStatement inserirVinculos) throws SQLException {
        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            inserirTurma.setInt(1, ID_TURMA_TESTE);
            inserirTurma.setInt(2, ID_TURMA_MODELO);
            int turmasInseridas = inserirTurma.executeUpdate();
            if (turmasInseridas != 1) {
                throw new SQLException("Não foi possível copiar a turma modelo.");
            }

            inserirVinculos.setInt(1, ID_TURMA_TESTE);
            inserirVinculos.setInt(2, ID_TURMA_MODELO);
            inserirVinculos.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    private static long excluirTurma(
            Connection conn,
            PreparedStatement excluirVinculos,
            PreparedStatement excluirTurma) throws SQLException {
        boolean autoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            excluirVinculos.setInt(1, ID_TURMA_TESTE);
            excluirVinculos.executeUpdate();
            excluirTurma.setInt(1, ID_TURMA_TESTE);
            long removidas = excluirTurma.executeUpdate();
            conn.commit();
            return removidas;
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(autoCommit);
        }
    }

    private static void validarDadosDeTeste(Connection conn) throws SQLException {
        if (contar(conn, "SELECT COUNT(*) FROM turma WHERE id_turma = " + ID_TURMA_MODELO) != 1) {
            throw new SQLException("Turma modelo não encontrada: " + ID_TURMA_MODELO);
        }
        if (contar(conn, "SELECT COUNT(*) FROM turma_docente WHERE id_turma = " + ID_TURMA_MODELO) != 5) {
            throw new SQLException("A turma modelo precisa ter cinco vínculos com docentes.");
        }
    }

    private static void medir(
            List<ResultadoBenchmark> resultados,
            String id,
            String grupo,
            String operacao,
            Passo preparar,
            Operacao executar,
            Passo limpar) {

        double[] tempos = new double[REPETICOES];
        long quantidade = 0;

        try {
            for (int i = 0; i < AQUECIMENTO + REPETICOES; i++) {
                if (preparar != null) {
                    preparar.executar();
                }

                try {
                    long inicio = System.nanoTime();
                    long atual = executar.executar();
                    double tempo = (System.nanoTime() - inicio) / 1_000_000.0;

                    if (i == 0) {
                        quantidade = atual;
                    }
                    if (i >= AQUECIMENTO) {
                        tempos[i - AQUECIMENTO] = tempo;
                    }
                } finally {
                    if (limpar != null) {
                        limpar.executar();
                    }
                }
            }

            Arrays.sort(tempos);
            double media = Arrays.stream(tempos).average().orElse(0);
            double mediana = (tempos[14] + tempos[15]) / 2.0;
            double p95 = tempos[(int) Math.ceil(REPETICOES * 0.95) - 1];

            resultados.add(new ResultadoBenchmark(id, grupo, operacao, quantidade,
                    mediana, media, p95, null));
        } catch (Exception e) {
            resultados.add(new ResultadoBenchmark(id, grupo, operacao, null,
                    null, null, null, mensagemErro(e)));
        }
    }

    private static long executar(PreparedStatement ps, boolean escalar) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            if (escalar) {
                if (!rs.next()) {
                    return 0;
                }
                return rs.getLong(1);
            }

            ResultSetMetaData meta = rs.getMetaData();
            int colunas = meta.getColumnCount();
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

    private static long contar(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static int valorInteiro(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException("Valor não encontrado: " + sql);
            }
            return rs.getInt(1);
        }
    }

    private static String valorTexto(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static String colunasDaTurmaSemId(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT * FROM turma WHERE 1 = 0");
             ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData meta = rs.getMetaData();
            List<String> nomes = new ArrayList<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                if (!meta.getColumnName(i).equalsIgnoreCase("id_turma")) {
                    nomes.add(meta.getColumnName(i));
                }
            }
            return nomes.stream().collect(Collectors.joining(", "));
        }
    }

    private static String mensagemErro(Exception e) {
        String mensagem = e.getMessage();
        return mensagem == null || mensagem.isBlank()
                ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + ": " + mensagem.replace('\n', ' ');
    }
}
