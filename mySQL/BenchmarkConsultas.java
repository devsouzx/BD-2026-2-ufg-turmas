import java.sql.*;

public class BenchmarkConsultas {

    private static final String URL = "jdbc:mysql://localhost:3306/ufg_turmas?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String USUARIO = "root";
    private static final String SENHA = "*****"; 

    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(URL, USUARIO, SENHA)) {
            System.out.println("=== BENCHMARK DE CONSULTAS MYSQL ===");

            medirConsulta(conn, "Q1 (Busca por ID de Turma)", 
                "SELECT * FROM turma WHERE id_turma = 1001119");

            medirConsulta(conn, "Q2 (Junção Turma + Componente + Curso + Campus)", 
                "SELECT t.id_turma, t.codigo_turma, c.nome_componente, cur.nome_curso, cam.nome_campus " +
                "FROM turma t " +
                "JOIN componente_curricular c ON t.codigo_componente = c.codigo_componente " +
                "JOIN curso cur ON t.id_curso = cur.id_curso " +
                "JOIN campus cam ON t.id_campus = cam.id_campus LIMIT 100");

            medirConsulta(conn, "Q3 (Listagem de Professores por Turma)", 
                "SELECT t.id_turma, c.nome_componente, d.nome_docente " +
                "FROM turma_docente td " +
                "JOIN turma t ON td.id_turma = t.id_turma " +
                "JOIN componente_curricular c ON t.codigo_componente = c.codigo_componente " +
                "JOIN docente d ON td.id_docente = d.id_docente LIMIT 100");

            medirConsulta(conn, "Q4 (Top 10 Professores com Mais Turmas)", 
                "SELECT d.nome_docente, COUNT(td.id_turma) AS total_turmas " +
                "FROM docente d " +
                "JOIN turma_docente td ON d.id_docente = td.id_docente " +
                "GROUP BY d.id_docente, d.nome_docente " +
                "ORDER BY total_turmas DESC LIMIT 10");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void medirConsulta(Connection conn, String nomeConsulta, String sql) {
        try (Statement stmt = conn.createStatement()) {
            long inicio = System.currentTimeMillis();
            ResultSet rs = stmt.executeQuery(sql);
            
            int totalLinhas = 0;
            while (rs.next()) {
                totalLinhas++;
            }
            long tempo = System.currentTimeMillis() - inicio;

            System.out.printf("%-45s | Retorno: %4d linhas | Tempo: %3d ms%n", nomeConsulta, totalLinhas, tempo);
        } catch (Exception e) {
            System.out.println("Erro na consulta " + nomeConsulta + ": " + e.getMessage());
        }
    }
}
