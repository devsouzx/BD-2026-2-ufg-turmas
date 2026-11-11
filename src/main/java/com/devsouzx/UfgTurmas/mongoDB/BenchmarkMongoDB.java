package com.devsouzx.UfgTurmas.mongoDB;

import com.devsouzx.UfgTurmas.ResultadoBenchmark;
import com.devsouzx.UfgTurmas.ResultadoBenchmark.Operacao;
import com.devsouzx.UfgTurmas.ResultadoBenchmark.Passo;

import com.mongodb.WriteConcern;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;

import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.devsouzx.UfgTurmas.ResultadoBenchmark.medir;

public class BenchmarkMongoDB {

    private static final String URI = "mongodb://localhost:27017";
    private static final String BANCO = "ufg_turmas";
    private static final String COLECAO = "turma";

    private static final int ID_TURMA = 1001223;
    private static final int ID_TURMA_TESTE = 999_999_001;
    private static final int ID_CAMPUS_TESTE = 68700504;

    public static List<ResultadoBenchmark> executar() throws Exception {
        List<ResultadoBenchmark> resultados = new ArrayList<>();
        try (MongoClient cliente = MongoClients.create(URI)) {
            MongoCollection<Document> turmas = cliente.getDatabase(BANCO).getCollection(COLECAO);
            leituras(turmas, resultados);
            escritas(turmas.withWriteConcern(WriteConcern.JOURNALED), resultados);
        }
        return resultados;
    }

    private static void leituras(MongoCollection<Document> turmas, List<ResultadoBenchmark> resultados) throws Exception {
        resultados.add(medir("R1", "Turma por ID (atributos básicos)",
                find(turmas, new Document("_id", ID_TURMA),
                        new Document("codigo_turma", 1).append("ano", 1).append("periodo", 1)
                                .append("total_vagas_ofertadas", 1).append("total_matriculados", 1),
                        null, 0)));

        resultados.add(medir("R2", "Turma por ID com dados relacionados",
                find(turmas, new Document("_id", ID_TURMA), null, null, 0)));

        resultados.add(medir("F1", "Abertas por campus (top 50)",
                find(turmas,
                        new Document("situacao_turma", "1 - ABERTA").append("campus.id", 68700506),
                        new Document("codigo_turma", 1).append("total_vagas_ofertadas", 1)
                                .append("total_matriculados", 1).append("situacao_turma", 1),
                        new Document("total_matriculados", -1).append("_id", 1), 50)));

        resultados.add(medir("F2", "Abertas com 20+ vagas ociosas",
                find(turmas,
                        new Document("situacao_turma", "1 - ABERTA")
                                .append("$expr", new Document("$gte", Arrays.asList(
                                        new Document("$subtract", Arrays.asList("$total_vagas_ofertadas", "$total_matriculados")),
                                        20))),
                        new Document("codigo_turma", 1).append("total_vagas_ofertadas", 1).append("total_matriculados", 1),
                        null, 0)));

        resultados.add(medir("F3", "Componente por trecho do nome",
                find(turmas,
                        new Document("componente.nome", new Document("$regex", "BANCO DE DADOS").append("$options", "i")),
                        new Document("codigo_turma", 1).append("componente.nome", 1),
                        null, 0)));

        resultados.add(medir("J1", "Turmas de um docente",
                find(turmas, new Document("docentes", "MARCELLE FIGUEIRA MARQUES DA SILVA SALES"),
                        new Document("componente.codigo", 1).append("componente.nome", 1),
                        null, 0)));

        resultados.add(medir("A1", "Top docentes por nº de turmas",
                agregar(turmas, List.of(
                        new Document("$unwind", "$docentes"),
                        new Document("$group", new Document("_id", "$docentes").append("total_turmas", new Document("$sum", 1))),
                        new Document("$sort", new Document("total_turmas", -1).append("_id", 1)),
                        new Document("$limit", 10)))));

        resultados.add(medir("A2", "Vagas e matriculados por campus",
                agregar(turmas, List.of(
                        new Document("$group", new Document("_id", "$campus.id")
                                .append("nome", new Document("$first", "$campus.nome"))
                                .append("turmas", new Document("$sum", 1))
                                .append("vagas", new Document("$sum", "$total_vagas_ofertadas"))
                                .append("matriculados", new Document("$sum", "$total_matriculados"))),
                        new Document("$sort", new Document("turmas", -1).append("_id", 1))))));

        resultados.add(medir("A3", "Distribuição de docentes por turma",
                agregar(turmas, List.of(
                        new Document("$group", new Document("_id", new Document("$size", "$docentes"))
                                .append("turmas", new Document("$sum", 1))),
                        new Document("$sort", new Document("_id", 1))))));
    }

    private static void escritas(MongoCollection<Document> turmas, List<ResultadoBenchmark> resultados) throws Exception {
        Document modelo = turmas.find(new Document("_id", ID_TURMA)).first();
        Document novaTurma = new Document(modelo).append("_id", ID_TURMA_TESTE);
        String nomeCampus = turmas.find(new Document("campus.id", ID_CAMPUS_TESTE)).first()
                .get("campus", Document.class).getString("nome");

        Passo inserir = () -> turmas.insertOne(novaTurma);
        Passo apagar = () -> turmas.deleteOne(new Document("_id", ID_TURMA_TESTE));

        resultados.add(medir("E1", "Inserir turma com docentes", null,
                () -> {
                    inserir.executar();
                    return 1;
                },
                apagar));

        resultados.add(medir("E2", "Alterar matriculados por turma", null,
                () -> alterarMatriculados(turmas, 1),
                () -> alterarMatriculados(turmas, -1)));

        resultados.add(medir("E3", "Renomear campus compartilhado", null,
                () -> renomearCampus(turmas, "CAMPUS RENOMEADO"),
                () -> renomearCampus(turmas, nomeCampus)));

        resultados.add(medir("E4", "Excluir turma com docentes", inserir,
                () -> turmas.deleteOne(new Document("_id", ID_TURMA_TESTE)).getDeletedCount(),
                null));
    }

    private static Operacao find(MongoCollection<Document> turmas, Bson filtro, Bson projecao, Bson ordem, int limite) {
        return () -> {
            FindIterable<Document> consulta = turmas.find(filtro).projection(projecao);
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

    private static Operacao agregar(MongoCollection<Document> turmas, List<Bson> etapas) {
        return () -> {
            long documentos = 0;
            for (Document documento : turmas.aggregate(etapas)) {
                documentos++;
            }
            return documentos;
        };
    }

    private static long alterarMatriculados(MongoCollection<Document> turmas, int variacao) {
        return turmas.updateOne(new Document("_id", ID_TURMA),
                new Document("$inc", new Document("total_matriculados", variacao))).getModifiedCount();
    }

    private static long renomearCampus(MongoCollection<Document> turmas, String nome) {
        return turmas.updateMany(new Document("campus.id", ID_CAMPUS_TESTE),
                new Document("$set", new Document("campus.nome", nome))).getModifiedCount();
    }
}
