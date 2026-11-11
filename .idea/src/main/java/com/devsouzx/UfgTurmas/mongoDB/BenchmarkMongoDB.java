package com.devsouzx.UfgTurmas.mongoDB;

import com.devsouzx.UfgTurmas.ResultadoBenchmark;

import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.FindIterable;

import org.bson.Document;
import org.bson.conversions.Bson;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class BenchmarkMongoDB {

    private static final String URI = System.getenv().getOrDefault("MONGO_URI", "mongodb://localhost:27017");
    private static final String BANCO = "ufg_turmas";
    private static final String COLECAO = "turma";

    private static final int ID_TURMA_MODELO = 1001223;
    private static final int ID_TURMA_TESTE = 999_999_001;
    private static final int ID_CAMPUS_TESTE = 68700504;
    private static final String NOME_CAMPUS_TESTE = "CAMPUS RENOMEADO (TESTE)";
    private static final int AQUECIMENTO = 5;
    private static final int REPETICOES = 30;

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

        try (MongoClient cliente = MongoClients.create(URI)) {
            MongoDatabase banco = cliente.getDatabase(BANCO);
            MongoCollection<Document> turmas = banco.getCollection(COLECAO);
            MongoCollection<Document> escrita = turmas.withWriteConcern(WriteConcern.JOURNALED);

            String versao = banco.runCommand(new Document("buildInfo", 1)).getString("version");
            long turmasAntes = turmas.countDocuments();

            Document modelo = turmas.find(new Document("_id", ID_TURMA_MODELO)).first();
            if (modelo == null) {
                throw new IllegalStateException("Turma modelo não encontrada: " + ID_TURMA_MODELO);
            }

            List<?> docentesModelo = modelo.getList("docentes", Object.class);
            if (docentesModelo == null || docentesModelo.size() != 5) {
                throw new IllegalStateException("A turma modelo precisa ter cinco docentes.");
            }
            if (turmas.countDocuments(new Document("_id", ID_TURMA_TESTE)) != 0) {
                throw new IllegalStateException("O ID reservado para os testes já existe: " + ID_TURMA_TESTE);
            }

            Document campus = turmas.find(new Document("campus.id", ID_CAMPUS_TESTE)).first();
            if (campus == null) {
                throw new IllegalStateException("Campus de teste não encontrado: " + ID_CAMPUS_TESTE);
            }
            String nomeCampusOriginal = campus.get("campus", Document.class).getString("nome");
            int matriculadosOriginais = ((Number) modelo.get("total_matriculados")).intValue();
            Document novaTurma = new Document(modelo);
            novaTurma.put("_id", ID_TURMA_TESTE);

            System.out.println("MongoDB " + versao + " | banco: " + BANCO + "." + COLECAO
                    + " | turmas: " + turmasAntes);

            medir(resultados, "R1", "Leitura", "Turma por ID (atributos básicos)",
                    null,
                    find(turmas,
                            new Document("_id", ID_TURMA_MODELO),
                            new Document("_id", 1)
                                    .append("codigo_turma", 1)
                                    .append("ano", 1)
                                    .append("periodo", 1)
                                    .append("total_vagas_ofertadas", 1)
                                    .append("total_matriculados", 1),
                            null, 0),
                    null);

            medir(resultados, "R2", "Leitura", "Turma por ID com dados relacionados",
                    null,
                    find(turmas, new Document("_id", ID_TURMA_MODELO), null, null, 0),
                    null);

            medir(resultados, "F1", "Filtro", "Abertas por campus (top 50)",
                    null,
                    find(turmas,
                            new Document("situacao_turma", "1 - ABERTA")
                                    .append("campus.id", 68700506),
                            new Document("_id", 1)
                                    .append("codigo_turma", 1)
                                    .append("total_vagas_ofertadas", 1)
                                    .append("total_matriculados", 1)
                                    .append("situacao_turma", 1),
                            new Document("total_matriculados", -1).append("_id", 1),
                            50),
                    null);

            medir(resultados, "F2", "Filtro", "Abertas com 20+ vagas ociosas",
                    null,
                    contar(turmas,
                            new Document("situacao_turma", "1 - ABERTA")
                                    .append("$expr", new Document("$gte", Arrays.asList(
                                            new Document("$subtract", Arrays.asList(
                                                    new Document("$ifNull", Arrays.asList("$total_vagas_ofertadas", 0)),
                                                    new Document("$ifNull", Arrays.asList("$total_matriculados", 0)))),
                                            20)))),
                    null);

            medir(resultados, "F3", "Filtro", "Componente por trecho do nome",
                    null,
                    find(turmas,
                            new Document("componente.nome",
                                    new Document("$regex", "BANCO DE DADOS").append("$options", "i")),
                            new Document("_id", 1)
                                    .append("codigo_turma", 1)
                                    .append("componente.nome", 1),
                            new Document("_id", 1), 0),
                    null);

            Date dataLimite = Date.from(LocalDate.of(2023, 10, 1)
                    .atStartOfDay(ZoneOffset.UTC).toInstant());
            medir(resultados, "F4", "Filtro", "A distância por data de início",
                    null,
                    contar(turmas,
                            new Document("modalidade_turma", "À Distância")
                                    .append("data_inicio", new Document("$gte", dataLimite))),
                    null);

            medir(resultados, "J1", "Relação", "Turmas de um docente",
                    null,
                    find(turmas,
                            new Document("docentes", "MARCELLE FIGUEIRA MARQUES DA SILVA SALES"),
                            new Document("_id", 1)
                                    .append("componente.codigo", 1)
                                    .append("componente.nome", 1),
                            new Document("_id", 1), 0),
                    null);

            medir(resultados, "A1", "Agregação", "Top docentes por nº de turmas",
                    null,
                    agregar(turmas, List.of(
                            new Document("$unwind", "$docentes"),
                            new Document("$group", new Document("_id", "$docentes")
                                    .append("total_turmas", new Document("$sum", 1))),
                            new Document("$sort", new Document("total_turmas", -1).append("_id", 1)),
                            new Document("$limit", 10))),
                    null);

            medir(resultados, "A2", "Agregação", "Vagas e matriculados por campus",
                    null,
                    agregar(turmas, List.of(
                            new Document("$group", new Document("_id", "$campus.id")
                                    .append("nome", new Document("$first", "$campus.nome"))
                                    .append("turmas", new Document("$sum", 1))
                                    .append("vagas", new Document("$sum", "$total_vagas_ofertadas"))
                                    .append("matriculados", new Document("$sum", "$total_matriculados"))),
                            new Document("$sort", new Document("turmas", -1).append("_id", 1)))),
                    null);

            medir(resultados, "A3", "Agregação", "Distribuição de docentes por turma",
                    null,
                    agregar(turmas, List.of(
                            new Document("$project", new Document("qtd_docentes",
                                    new Document("$size", new Document("$ifNull", Arrays.asList("$docentes", List.of()))))),
                            new Document("$group", new Document("_id", "$qtd_docentes")
                                    .append("turmas", new Document("$sum", 1))),
                            new Document("$sort", new Document("_id", 1)))),
                    null);

            executarEscritas(escrita, novaTurma, nomeCampusOriginal,
                    matriculadosOriginais, resultados);

            long turmasDepois = turmas.countDocuments();
            Document campusDepois = turmas.find(new Document("campus.id", ID_CAMPUS_TESTE)).first();
            String nomeDepois = campusDepois == null
                    ? null : campusDepois.get("campus", Document.class).getString("nome");
            boolean restaurado = turmasAntes == turmasDepois
                    && nomeCampusOriginal.equals(nomeDepois)
                    && turmas.countDocuments(new Document("_id", ID_TURMA_TESTE)) == 0;

            if (!restaurado) {
                throw new IllegalStateException("Os dados do MongoDB não foram restaurados após as operações de escrita.");
            }
        }
        return List.copyOf(resultados);
    }

    private static void executarEscritas(
            MongoCollection<Document> escrita,
            Document novaTurma,
            String nomeCampusOriginal,
            int matriculadosOriginais,
            List<ResultadoBenchmark> resultados) {

        Passo inserirNova = () -> escrita.insertOne(new Document(novaTurma));
        Passo excluirNova = () -> escrita.deleteOne(new Document("_id", ID_TURMA_TESTE));

        medir(resultados, "E1", "Escrita", "Inserir turma com docentes",
                null,
                () -> {
                    inserirNova.executar();
                    return 1;
                },
                excluirNova);

        medir(resultados, "E2", "Escrita", "Alterar matriculados por turma",
                null,
                () -> escrita.updateOne(
                        new Document("_id", ID_TURMA_MODELO),
                        new Document("$set", new Document("total_matriculados", matriculadosOriginais + 1)))
                        .getModifiedCount(),
                () -> escrita.updateOne(
                        new Document("_id", ID_TURMA_MODELO),
                        new Document("$set", new Document("total_matriculados", matriculadosOriginais))));

        medir(resultados, "E3", "Escrita", "Renomear campus compartilhado",
                null,
                () -> escrita.updateMany(
                        new Document("campus.id", ID_CAMPUS_TESTE),
                        new Document("$set", new Document("campus.nome", NOME_CAMPUS_TESTE)))
                        .getModifiedCount(),
                () -> escrita.updateMany(
                        new Document("campus.id", ID_CAMPUS_TESTE),
                        new Document("$set", new Document("campus.nome", nomeCampusOriginal))));

        medir(resultados, "E4", "Escrita", "Excluir turma com docentes",
                inserirNova,
                () -> escrita.deleteOne(new Document("_id", ID_TURMA_TESTE)).getDeletedCount(),
                excluirNova);
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

    private static Operacao find(
            MongoCollection<Document> colecao,
            Bson filtro,
            Bson projecao,
            Bson ordem,
            int limite) {

        return () -> {
            FindIterable<Document> consulta = colecao.find(filtro);
            if (projecao != null) {
                consulta = consulta.projection(projecao);
            }
            if (ordem != null) {
                consulta = consulta.sort(ordem);
            }
            if (limite > 0) {
                consulta = consulta.limit(limite);
            }

            long documentos = 0;
            for (Document documento : consulta) {
                documentos++;
            }
            return documentos;
        };
    }

    private static Operacao contar(MongoCollection<Document> colecao, Bson filtro) {
        return () -> colecao.countDocuments(filtro);
    }

    private static Operacao agregar(MongoCollection<Document> colecao, List<Bson> etapas) {
        return () -> {
            long documentos = 0;
            for (Document documento : colecao.aggregate(etapas).allowDiskUse(true)) {
                documentos++;
            }
            return documentos;
        };
    }


    private static String mensagemErro(Exception e) {
        String mensagem = e.getMessage();
        return mensagem == null || mensagem.isBlank()
                ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + ": " + mensagem.replace('\n', ' ');
    }
}
