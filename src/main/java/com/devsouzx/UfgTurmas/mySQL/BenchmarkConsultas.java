package com.devsouzx.UfgTurmas.mySQL;

import com.devsouzx.UfgTurmas.ResultadoBenchmark;
import com.devsouzx.UfgTurmas.ResultadoBenchmark.Operacao;
import com.devsouzx.UfgTurmas.ResultadoBenchmark.Passo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static com.devsouzx.UfgTurmas.ResultadoBenchmark.medir;

public class BenchmarkConsultas {

    private static final String URL = "jdbc:mysql://localhost:3306/ufg_turmas?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String USUARIO = "root";
    private static final String SENHA = System.getenv().getOrDefault("MYSQL_PASSWORD", "@Joaoemanuel19");

    private static final int ID_TURMA = 1001223;
    private static final int ID_TURMA_TESTE = 999_999_001;
    private static final int ID_CAMPUS_TESTE = 68700504;
    private static final String COLUNAS_TURMA = "codigo_turma, tipo_turma, modalidade_turma, ano, periodo, "
            + "total_vagas_ofertadas, total_matriculados, data_inicio, data_fim, matrizes_associadas, "
            + "situacao_turma, horario, codigo_componente, id_curso, id_campus";

    public static List<ResultadoBenchmark> executar() throws Exception {
        List<ResultadoBenchmark> resultados = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(URL, USUARIO, SENHA)) {
            leituras(conn, resultados);
            escritas(conn, resultados);
        }
        return resultados;
    }

    private static void leituras(Connection conn, List<ResultadoBenchmark> resultados) throws Exception {
        consultar(conn, resultados, "R1", "Turma por ID (atributos básicos)",
                "SELECT id_turma, codigo_turma, ano, periodo, total_vagas_ofertadas, total_matriculados "
                        + "FROM turma WHERE id_turma = ?",
                ID_TURMA);

        consultar(conn, resultados, "R2", "Turma por ID com dados relacionados",
                "SELECT t.*, c.nome_componente, c.tipo_componente, c.ch_total, cur.nome_curso, cam.nome_campus, d.nome_docente "
                        + "FROM turma t "
                        + "JOIN componente_curricular c ON c.codigo_componente = t.codigo_componente "
                        + "JOIN curso cur ON cur.id_curso = t.id_curso "
                        + "JOIN campus cam ON cam.id_campus = t.id_campus "
                        + "LEFT JOIN turma_docente td ON td.id_turma = t.id_turma "
                        + "LEFT JOIN docente d ON d.id_docente = td.id_docente "
                        + "WHERE t.id_turma = ?",
                ID_TURMA);

        consultar(conn, resultados, "F1", "Abertas por campus (top 50)",
                "SELECT id_turma, codigo_turma, total_vagas_ofertadas, total_matriculados, situacao_turma "
                        + "FROM turma WHERE situacao_turma = ? AND id_campus = ? "
                        + "ORDER BY total_matriculados DESC, id_turma LIMIT 50",
                "1 - ABERTA", 68700506);

        consultar(conn, resultados, "F2", "Abertas com 20+ vagas ociosas",
                "SELECT id_turma, codigo_turma, total_vagas_ofertadas, total_matriculados "
                        + "FROM turma WHERE situacao_turma = ? AND total_vagas_ofertadas - total_matriculados >= 20",
                "1 - ABERTA");

        consultar(conn, resultados, "F3", "Componente por trecho do nome",
                "SELECT t.id_turma, t.codigo_turma, c.nome_componente "
                        + "FROM turma t JOIN componente_curricular c ON c.codigo_componente = t.codigo_componente "
                        + "WHERE c.nome_componente LIKE ?",
                "%BANCO DE DADOS%");

        consultar(conn, resultados, "J1", "Turmas de um docente",
                "SELECT t.id_turma, c.codigo_componente, c.nome_componente "
                        + "FROM docente d "
                        + "JOIN turma_docente td ON td.id_docente = d.id_docente "
                        + "JOIN turma t ON t.id_turma = td.id_turma "
                        + "JOIN componente_curricular c ON c.codigo_componente = t.codigo_componente "
                        + "WHERE d.nome_docente = ?",
                "MARCELLE FIGUEIRA MARQUES DA SILVA SALES");

        consultar(conn, resultados, "A1", "Top docentes por nº de turmas",
                "SELECT d.nome_docente, COUNT(*) AS total_turmas "
                        + "FROM docente d JOIN turma_docente td ON td.id_docente = d.id_docente "
                        + "GROUP BY d.id_docente, d.nome_docente "
                        + "ORDER BY total_turmas DESC, d.nome_docente LIMIT 10");

        consultar(conn, resultados, "A2", "Vagas e matriculados por campus",
                "SELECT cam.id_campus, cam.nome_campus, COUNT(*) AS turmas, "
                        + "SUM(t.total_vagas_ofertadas) AS vagas, SUM(t.total_matriculados) AS matriculados "
                        + "FROM turma t JOIN campus cam ON cam.id_campus = t.id_campus "
                        + "GROUP BY cam.id_campus, cam.nome_campus "
                        + "ORDER BY turmas DESC, cam.id_campus");

        consultar(conn, resultados, "A3", "Distribuição de docentes por turma",
                "SELECT qtd_docentes, COUNT(*) AS turmas FROM ("
                        + "SELECT t.id_turma, COUNT(td.id_docente) AS qtd_docentes "
                        + "FROM turma t LEFT JOIN turma_docente td ON td.id_turma = t.id_turma "
                        + "GROUP BY t.id_turma) AS por_turma "
                        + "GROUP BY qtd_docentes ORDER BY qtd_docentes");
    }

    private static void escritas(Connection conn, List<ResultadoBenchmark> resultados) throws Exception {
        String nomeCampus;
        try (PreparedStatement ps = conn.prepareStatement("SELECT nome_campus FROM campus WHERE id_campus = ?")) {
            ps.setInt(1, ID_CAMPUS_TESTE);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                nomeCampus = rs.getString(1);
            }
        }

        conn.setAutoCommit(false);
        try (PreparedStatement inserirTurma = conn.prepareStatement(
                     "INSERT INTO turma (id_turma, " + COLUNAS_TURMA + ") "
                             + "SELECT ?, " + COLUNAS_TURMA + " FROM turma WHERE id_turma = ?");
             PreparedStatement inserirDocentes = conn.prepareStatement(
                     "INSERT INTO turma_docente (id_turma, id_docente) "
                             + "SELECT ?, id_docente FROM turma_docente WHERE id_turma = ?");
             PreparedStatement apagarDocentes = conn.prepareStatement("DELETE FROM turma_docente WHERE id_turma = ?");
             PreparedStatement apagarTurma = conn.prepareStatement("DELETE FROM turma WHERE id_turma = ?");
             PreparedStatement alterarMatriculados = conn.prepareStatement(
                     "UPDATE turma SET total_matriculados = total_matriculados + ? WHERE id_turma = ?");
             PreparedStatement renomearCampus = conn.prepareStatement(
                     "UPDATE campus SET nome_campus = ? WHERE id_campus = ?")) {

            Passo inserir = () -> {
                inserirTurma.setInt(1, ID_TURMA_TESTE);
                inserirTurma.setInt(2, ID_TURMA);
                inserirTurma.executeUpdate();
                inserirDocentes.setInt(1, ID_TURMA_TESTE);
                inserirDocentes.setInt(2, ID_TURMA);
                inserirDocentes.executeUpdate();
                conn.commit();
            };

            Operacao apagar = () -> {
                apagarDocentes.setInt(1, ID_TURMA_TESTE);
                apagarDocentes.executeUpdate();
                apagarTurma.setInt(1, ID_TURMA_TESTE);
                int apagadas = apagarTurma.executeUpdate();
                conn.commit();
                return apagadas;
            };

            resultados.add(medir("E1", "Inserir turma com docentes", null,
                    () -> {
                        inserir.executar();
                        return 1;
                    },
                    apagar::executar));

            resultados.add(medir("E2", "Alterar matriculados por turma", null,
                    () -> alterarMatriculados(conn, alterarMatriculados, 1),
                    () -> alterarMatriculados(conn, alterarMatriculados, -1)));

            resultados.add(medir("E3", "Renomear campus compartilhado", null,
                    () -> renomearCampus(conn, renomearCampus, "CAMPUS RENOMEADO"),
                    () -> renomearCampus(conn, renomearCampus, nomeCampus)));

            resultados.add(medir("E4", "Excluir turma com docentes", inserir, apagar, null));
        }
    }

    private static void consultar(Connection conn, List<ResultadoBenchmark> resultados,
                                  String id, String operacao, String sql, Object... parametros) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) {
                ps.setObject(i + 1, parametros[i]);
            }
            resultados.add(medir(id, operacao, () -> lerLinhas(ps)));
        }
    }

    private static long lerLinhas(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
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

    private static int alterarMatriculados(Connection conn, PreparedStatement ps, int variacao) throws SQLException {
        ps.setInt(1, variacao);
        ps.setInt(2, ID_TURMA);
        int alteradas = ps.executeUpdate();
        conn.commit();
        return alteradas;
    }

    private static int renomearCampus(Connection conn, PreparedStatement ps, String nome) throws SQLException {
        ps.setString(1, nome);
        ps.setInt(2, ID_CAMPUS_TESTE);
        int alteradas = ps.executeUpdate();
        conn.commit();
        return alteradas;
    }
}
