package com.devsouzx.UfgTurmas.mySQL;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

public class PopulaBanco {
    private static final String HOST = System.getenv().getOrDefault("MYSQL_HOST", "localhost");
    private static final String PORTA = System.getenv().getOrDefault("MYSQL_PORT", "3306");
    private static final String URL_BASE = "jdbc:mysql://" + HOST + ":" + PORTA + "/?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String URL_DB = "jdbc:mysql://" + HOST + ":" + PORTA + "/ufg_turmas?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String USUARIO = System.getenv().getOrDefault("MYSQL_USER", "root");
    private static final String SENHA = System.getenv().getOrDefault("MYSQL_PASSWORD", "");

    public static void main(String[] args) throws Exception {
        executar(args);
    }

    public static void executar(String[] args) throws Exception {
        String csvFile = args.length > 0 ? args[0] : "da_turmas_2023_2.csv";

        long linhasLidas = 0;
        long ignoradasSemId = 0;
        long ignoradasIdRepetido = 0;

        Set<Integer> idsTurmaProcessados = new HashSet<>();

        long inicioCriacaoSchema = System.currentTimeMillis();

            try (Connection connBase = DriverManager.getConnection(URL_BASE, USUARIO, SENHA);
                 Statement stmtBase = connBase.createStatement()) {
                stmtBase.executeUpdate("CREATE DATABASE IF NOT EXISTS ufg_turmas;");
            }

            try (Connection connDb = DriverManager.getConnection(URL_DB, USUARIO, SENHA);
                 Statement stmtDb = connDb.createStatement()) {

                stmtDb.executeUpdate("DROP TABLE IF EXISTS turma_docente;");
                stmtDb.executeUpdate("DROP TABLE IF EXISTS turma;");
                stmtDb.executeUpdate("DROP TABLE IF EXISTS docente;");
                stmtDb.executeUpdate("DROP TABLE IF EXISTS componente_curricular;");
                stmtDb.executeUpdate("DROP TABLE IF EXISTS curso;");
                stmtDb.executeUpdate("DROP TABLE IF EXISTS campus;");

                stmtDb.executeUpdate("CREATE TABLE campus (" +
                        "id_campus INT PRIMARY KEY, " +
                        "nome_campus VARCHAR(100) NOT NULL);");

                stmtDb.executeUpdate("CREATE TABLE curso (" +
                        "id_curso INT PRIMARY KEY, " +
                        "nome_curso VARCHAR(150) NOT NULL);");

                stmtDb.executeUpdate("CREATE TABLE componente_curricular (" +
                        "codigo_componente VARCHAR(20) PRIMARY KEY, " +
                        "nome_componente VARCHAR(200) NOT NULL, " +
                        "tipo_componente VARCHAR(50), " +
                        "ch_total INT, " +
                        "unidade_academica INT);");

                stmtDb.executeUpdate("CREATE TABLE docente (" +
                        "id_docente INT AUTO_INCREMENT PRIMARY KEY, " +
                        "nome_docente VARCHAR(200) NOT NULL UNIQUE);");

                stmtDb.executeUpdate("CREATE TABLE turma (" +
                        "id_turma INT PRIMARY KEY, " +
                        "codigo_turma VARCHAR(10) NOT NULL, " +
                        "tipo_turma VARCHAR(50), " +
                        "modalidade_turma VARCHAR(50), " +
                        "ano INT NOT NULL, " +
                        "periodo INT NOT NULL, " +
                        "total_vagas_ofertadas INT, " +
                        "total_matriculados INT, " +
                        "data_inicio DATE, " +
                        "data_fim DATE, " +
                        "matrizes_associadas TEXT, " +
                        "situacao_turma VARCHAR(100), " +
                        "horario VARCHAR(100), " +
                        "codigo_componente VARCHAR(20) NOT NULL, " +
                        "id_curso INT NOT NULL, " +
                        "id_campus INT NOT NULL, " +
                        "FOREIGN KEY (codigo_componente) REFERENCES componente_curricular(codigo_componente), " +
                        "FOREIGN KEY (id_curso) REFERENCES curso(id_curso), " +
                        "FOREIGN KEY (id_campus) REFERENCES campus(id_campus));");

                stmtDb.executeUpdate("CREATE TABLE turma_docente (" +
                        "id_turma INT NOT NULL, " +
                        "id_docente INT NOT NULL, " +
                        "PRIMARY KEY (id_turma, id_docente), " +
                        "FOREIGN KEY (id_turma) REFERENCES turma(id_turma), " +
                        "FOREIGN KEY (id_docente) REFERENCES docente(id_docente));");
            }

            long tempoCriacaoSchema = System.currentTimeMillis() - inicioCriacaoSchema;

            
            
            long inicioCarga = System.currentTimeMillis();

            try (Connection conn = DriverManager.getConnection(URL_DB, USUARIO, SENHA)) {
                conn.setAutoCommit(false); 

                String sqlCampus = "INSERT IGNORE INTO campus (id_campus, nome_campus) VALUES (?, ?)";
                String sqlCurso = "INSERT IGNORE INTO curso (id_curso, nome_curso) VALUES (?, ?)";
                String sqlComp = "INSERT IGNORE INTO componente_curricular (codigo_componente, nome_componente, tipo_componente, ch_total, unidade_academica) VALUES (?, ?, ?, ?, ?)";
                String sqlTurma = "INSERT IGNORE INTO turma (id_turma, codigo_turma, tipo_turma, modalidade_turma, ano, periodo, total_vagas_ofertadas, total_matriculados, data_inicio, data_fim, matrizes_associadas, situacao_turma, horario, codigo_componente, id_curso, id_campus) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                String sqlDocente = "INSERT IGNORE INTO docente (nome_docente) VALUES (?)";
                String sqlTurmaDocente = "INSERT IGNORE INTO turma_docente (id_turma, id_docente) VALUES (?, ?)";
                String sqlFindDoc = "SELECT id_docente FROM docente WHERE nome_docente = ?";

                PreparedStatement stmtCampus = conn.prepareStatement(sqlCampus);
                PreparedStatement stmtCurso = conn.prepareStatement(sqlCurso);
                PreparedStatement stmtComp = conn.prepareStatement(sqlComp);
                PreparedStatement stmtTurma = conn.prepareStatement(sqlTurma);
                PreparedStatement stmtDocente = conn.prepareStatement(sqlDocente);
                PreparedStatement stmtTurmaDoc = conn.prepareStatement(sqlTurmaDocente);
                PreparedStatement stmtFindDoc = conn.prepareStatement(sqlFindDoc);

                BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(csvFile), StandardCharsets.UTF_8));
                String line;
                boolean firstLine = true;

                while ((line = br.readLine()) != null) {
                    if (firstLine) {
                        firstLine = false;
                        continue;
                    }

                    linhasLidas++;

                    String[] col = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
                    if (col.length < 25) {
                        ignoradasSemId++;
                        continue;
                    }

                    for (int i = 0; i < col.length; i++) {
                        col[i] = col[i].replace("\"", "").trim();
                    }

                    if (col[0].isEmpty()) {
                        ignoradasSemId++;
                        continue;
                    }

                    int idTurma;
                    try {
                        idTurma = Integer.parseInt(col[0]);
                    } catch (NumberFormatException e) {
                        ignoradasSemId++;
                        continue;
                    }

                    if (idsTurmaProcessados.contains(idTurma)) {
                        ignoradasIdRepetido++;
                        continue;
                    }
                    idsTurmaProcessados.add(idTurma);

                    String codComp = col[1];
                    String nomeComp = col[2];
                    String tipoComp = col[3];
                    int chTotal = col[4].isEmpty() ? 0 : Integer.parseInt(col[4]);
                    int undAcad = col[5].isEmpty() ? 0 : Integer.parseInt(col[5]);
                    String codTurma = col[7];
                    String tipoTurma = col[8];
                    String modalidade = col[9];
                    int ano = Integer.parseInt(col[10]);
                    int periodo = Integer.parseInt(col[11]);
                    int idCurso = Integer.parseInt(col[12]);
                    String nomeCurso = col[13];
                    int idCampus = Integer.parseInt(col[14]);
                    String nomeCampus = col[15];
                    int totalVagas = col[16].isEmpty() ? 0 : Integer.parseInt(col[16]);
                    String dtInicio = col[17].isEmpty() ? null : col[17];
                    String dtFim = col[18].isEmpty() ? null : col[18];
                    String matrizes = col[19];
                    String situacao = col[20];
                    String horario = col[21];
                    int totalMatric = col[22].isEmpty() ? 0 : Integer.parseInt(col[22]);
                    String rawDocentes = col[24];

                    stmtCampus.setInt(1, idCampus);
                    stmtCampus.setString(2, nomeCampus);
                    stmtCampus.executeUpdate();

                    stmtCurso.setInt(1, idCurso);
                    stmtCurso.setString(2, nomeCurso);
                    stmtCurso.executeUpdate();

                    stmtComp.setString(1, codComp);
                    stmtComp.setString(2, nomeComp);
                    stmtComp.setString(3, tipoComp);
                    stmtComp.setInt(4, chTotal);
                    stmtComp.setInt(5, undAcad);
                    stmtComp.executeUpdate();

                    stmtTurma.setInt(1, idTurma);
                    stmtTurma.setString(2, codTurma);
                    stmtTurma.setString(3, tipoTurma);
                    stmtTurma.setString(4, modalidade);
                    stmtTurma.setInt(5, ano);
                    stmtTurma.setInt(6, periodo);
                    stmtTurma.setInt(7, totalVagas);
                    stmtTurma.setInt(8, totalMatric);
                    stmtTurma.setDate(9, dtInicio != null ? java.sql.Date.valueOf(dtInicio) : null);
                    stmtTurma.setDate(10, dtFim != null ? java.sql.Date.valueOf(dtFim) : null);
                    stmtTurma.setString(11, matrizes);
                    stmtTurma.setString(12, situacao);
                    stmtTurma.setString(13, horario);
                    stmtTurma.setString(14, codComp);
                    stmtTurma.setInt(15, idCurso);
                    stmtTurma.setInt(16, idCampus);
                    stmtTurma.executeUpdate();

                    if (!rawDocentes.isEmpty()) {
                        String[] docs = rawDocentes.split(",");
                        for (String docNome : docs) {
                            docNome = docNome.trim();
                            if (!docNome.isEmpty()) {
                                stmtDocente.setString(1, docNome);
                                stmtDocente.executeUpdate();

                                stmtFindDoc.setString(1, docNome);
                                ResultSet rs = stmtFindDoc.executeQuery();
                                if (rs.next()) {
                                    int idDocente = rs.getInt("id_docente");
                                    stmtTurmaDoc.setInt(1, idTurma);
                                    stmtTurmaDoc.setInt(2, idDocente);
                                    stmtTurmaDoc.executeUpdate();
                                }
                            }
                        }
                    }
                }

                br.close();
                conn.commit(); 
            }

            long tempoCarga = System.currentTimeMillis() - inicioCarga;

           

            int totalCampi = 0, totalCursos = 0, totalComponentes = 0, totalDocentes = 0, totalTurmas = 0;

            try (Connection conn = DriverManager.getConnection(URL_DB, USUARIO, SENHA);
                 Statement stmt = conn.createStatement()) {

                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM campus");
                if (rs.next()) totalCampi = rs.getInt(1);

                rs = stmt.executeQuery("SELECT COUNT(*) FROM curso");
                if (rs.next()) totalCursos = rs.getInt(1);

                rs = stmt.executeQuery("SELECT COUNT(*) FROM componente_curricular");
                if (rs.next()) totalComponentes = rs.getInt(1);

                rs = stmt.executeQuery("SELECT COUNT(*) FROM docente");
                if (rs.next()) totalDocentes = rs.getInt(1);

                rs = stmt.executeQuery("SELECT COUNT(*) FROM turma");
                if (rs.next()) totalTurmas = rs.getInt(1);
            }

            System.out.println("=== Importação concluída ===");
            System.out.printf("Linhas lidas do CSV:        %d%n", linhasLidas);
            System.out.printf("Ignoradas (sem id_turma):   %d%n", ignoradasSemId);
            System.out.printf("Ignoradas (id repetido):    %d%n", ignoradasIdRepetido);
            System.out.printf("Turmas inseridas:          %d%n%n", totalTurmas);

            System.out.println("Distintos:");
            System.out.printf("  campi:        %d%n", totalCampi);
            System.out.printf("  cursos:       %d%n", totalCursos);
            System.out.printf("  componentes:  %d%n", totalComponentes);
            System.out.printf("  docentes:     %d%n%n", totalDocentes);

            System.out.printf("Tempo de criação das tabelas: %d ms%n", tempoCriacaoSchema);
            System.out.printf("Tempo de carga:               %d ms%n", tempoCarga);

    }
}
