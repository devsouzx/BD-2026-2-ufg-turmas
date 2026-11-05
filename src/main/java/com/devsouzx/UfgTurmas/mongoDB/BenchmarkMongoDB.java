package com.devsouzx.UfgTurmas.mongoDB;

import com.mongodb.WriteConcern;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.FindIterable;

import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Benchmark do MongoDB (banco ufg_turmas, coleção turma - carregada pelo ImportadorMongoDB).
 *
 * Espelho do BenchmarkConsultas (MySQL): mesmos IDs, mesmas 15 consultas de leitura e as mesmas 5 operações de escrita.
 * As consultas estão escritas em JSON (a mesma sintaxe do mongosh), então dá para copiar e testar no Compass.
 *
 * Execução: pela IDE (classe BenchmarkMongoDB) ou pelo Main, que carrega os dois bancos e roda os dois benchmarks.
 * Variável opcional: MONGO_URI (padrão mongodb://localhost:27017)
 */
public class BenchmarkMongoDB {

    private static final String URI = System.getenv().getOrDefault("MONGO_URI", "mongodb://localhost:27017");
    private static final String BANCO = "ufg_turmas";
    private static final String COLECAO = "turma";

    // Parâmetros das operações de escrita (mesmos valores usados no BenchmarkMySQL)
    private static final int ID_TURMA_MODELO = 1001223;     // turma com 5 docentes, usada como modelo do clone
    private static final int ID_TURMA_NOVA = 999_999_001;    // id que NÃO existe no CSV
    private static final int CAMPUS_SAMAMBAIA = 68700506;    // 3.765 turmas
    private static final int CAMPUS_CIDADE_DE_GOIAS = 68700504;  // 295 turmas
    private static final String NOME_TEMPORARIO_CAMPUS = "CAMPUS RENOMEADO (TESTE)";

    public static void main(String[] args) throws Exception {
        try (MongoClient cliente = MongoClients.create(URI)) {
            MongoCollection<Document> turmas = cliente.getDatabase(BANCO).getCollection(COLECAO);

            // Escritas só confirmam depois de gravadas no journal - equivalente ao commit durável do InnoDB.
            MongoCollection<Document> escrita = turmas.withWriteConcern(WriteConcern.JOURNALED);

            String versao = cliente.getDatabase(BANCO).runCommand(new Document("buildInfo", 1)).getString("version");
            long antes = turmas.countDocuments(new Document());
            System.out.println("MongoDB " + versao + " | documentos em " + BANCO + "." + COLECAO + ": " + antes);

            imprimirCabecalho("MONGODB " + versao);

            // ----------------------------------------------------------------- leituras
            medir("L1", "Leitura pontual", "Turma por id (registro cru)",
                    find(turmas,
                            """
                            {"_id": 1001223}""",
                            null,
                            null, 0));
            medir("L2", "Leitura pontual", "Turma por id (completa: componente, curso, campus e docentes)",
                    find(turmas,
                            """
                            {"_id": 1001223}""",
                            null,
                            null, 0));  // SQL devolve 1 linha por docente (5); Mongo devolve 1 documento já completo
            medir("F1", "Filtro", "Turmas abertas de um campus, top 50 por matriculados",
                    find(turmas,
                            """
                            {"situacao_turma": "1 - ABERTA", "campus.id": 68700506}""",
                            """
                            {"codigo_turma": 1, "total_vagas_ofertadas": 1, "total_matriculados": 1, "situacao_turma": 1}""",
                            """
                            {"total_matriculados": -1, "_id": 1}""", 50));
            medir("F2", "Filtro", "Contagem de turmas abertas com 20+ vagas ociosas (expressão entre colunas)",
                    contar(turmas,
                            """
                            {"situacao_turma": "1 - ABERTA", "$expr": {"$gte": [{"$subtract": ["$total_vagas_ofertadas", "$total_matriculados"]}, 20]}}"""));
            medir("F3", "Filtro", "Busca textual no nome da disciplina (contém 'BANCO DE DADOS')",
                    find(turmas,
                            """
                            {"componente.nome": {"$regex": "BANCO DE DADOS", "$options": "i"}}""",
                            """
                            {"codigo_turma": 1, "componente.nome": 1}""",
                            """
                            {"_id": 1}""", 0));
            medir("F4", "Filtro", "Contagem de turmas a distância iniciadas a partir de 01/10/2023 (intervalo de data)",
                    contar(turmas,
                            """
                            {"modalidade_turma": "À Distância", "data_inicio": {"$gte": {"$date": "2023-10-01T00:00:00Z"}}}"""));
            medir("J1", "Junção N:N", "Turmas de um docente",
                    find(turmas,
                            """
                            {"docentes": "MARCELLE FIGUEIRA MARQUES DA SILVA SALES"}""",
                            """
                            {"componente.codigo": 1, "componente.nome": 1}""",
                            """
                            {"_id": 1}""", 0));
            medir("J2", "Junção N:N", "Docentes de um curso com nº de turmas (top 20)",
                    agregar(turmas,
                            """
                            [{"$match": {"curso.id": 69673254}}, {"$unwind": "$docentes"}, {"$group": {"_id": "$docentes", "qtd": {"$sum": 1}}}, {"$sort": {"qtd": -1, "_id": 1}}, {"$limit": 20}]"""));
            medir("J3", "Junção N:N", "Todos os vínculos turma x docente x disciplina (junção completa)",
                    agregar(turmas,
                            """
                            [{"$unwind": "$docentes"}, {"$project": {"componente.nome": 1, "docentes": 1}}]"""));
            medir("A1", "Agregação", "Top 10 docentes com mais turmas",
                    agregar(turmas,
                            """
                            [{"$unwind": "$docentes"}, {"$group": {"_id": "$docentes", "total_turmas": {"$sum": 1}}}, {"$sort": {"total_turmas": -1, "_id": 1}}, {"$limit": 10}]"""));
            medir("A2", "Agregação", "Turmas, vagas e matriculados por campus",
                    agregar(turmas,
                            """
                            [{"$group": {"_id": "$campus.id", "nome": {"$first": "$campus.nome"}, "turmas": {"$sum": 1}, "vagas": {"$sum": "$total_vagas_ofertadas"}, "matriculados": {"$sum": "$total_matriculados"}}}, {"$sort": {"turmas": -1, "_id": 1}}]"""));
            medir("A3", "Agregação", "Top 10 cursos por total de matriculados",
                    agregar(turmas,
                            """
                            [{"$group": {"_id": "$curso.id", "nome": {"$first": "$curso.nome"}, "turmas": {"$sum": 1}, "matriculados": {"$sum": "$total_matriculados"}}}, {"$sort": {"matriculados": -1, "_id": 1}}, {"$limit": 10}]"""));
            medir("A4", "Agregação", "Top 10 disciplinas mais ofertadas",
                    agregar(turmas,
                            """
                            [{"$group": {"_id": "$componente.codigo", "nome": {"$first": "$componente.nome"}, "turmas": {"$sum": 1}}}, {"$sort": {"turmas": -1, "_id": 1}}, {"$limit": 10}]"""));
            medir("A5", "Agregação", "Distribuição de turmas por nº de docentes",
                    agregar(turmas,
                            """
                            [{"$group": {"_id": {"$size": "$docentes"}, "turmas": {"$sum": 1}}}, {"$sort": {"_id": 1}}]"""));
            medir("A6", "Agregação", "Turmas e matriculados por modalidade e situação (agrupa por 2 campos)",
                    agregar(turmas,
                            """
                            [{"$group": {"_id": {"modalidade": "$modalidade_turma", "situacao": "$situacao_turma"}, "turmas": {"$sum": 1}, "matriculados": {"$sum": "$total_matriculados"}}}]"""));
            // ----------------------------------------------------------------- escritas
            // Cada escrita tem "preparar" e "limpar" fora da medição, para o banco voltar ao estado original.

            Document modelo = turmas.find(new Document("_id", ID_TURMA_MODELO)).first();
            if (modelo == null) {
                throw new IllegalStateException("Turma modelo " + ID_TURMA_MODELO + " não encontrada. A coleção foi carregada?");
            }
            Document novaTurma = new Document(modelo);       // clone completo: componente, curso, campus e docentes
            novaTurma.put("_id", ID_TURMA_NOVA);

            String nomeOriginalCampus = ((Document) turmas.find(new Document("campus.id", CAMPUS_CIDADE_DE_GOIAS)).first()
                    .get("campus")).getString("nome");

            Passo apagarNova = () -> escrita.deleteOne(new Document("_id", ID_TURMA_NOVA));
            Passo inserirNova = () -> escrita.insertOne(novaTurma);

            medir("E1", "Escrita", "Inserir turma nova (clone da L1, com os 5 docentes)",
                    apagarNova,
                    () -> { escrita.insertOne(novaTurma); return 1; },
                    apagarNova);

            medir("E2", "Escrita", "Atualizar 1 turma por id (+1 matriculado)",
                    null,
                    () -> escrita.updateOne(new Document("_id", ID_TURMA_MODELO),
                            new Document("$inc", new Document("total_matriculados", 1))).getModifiedCount(),
                    () -> escrita.updateOne(new Document("_id", ID_TURMA_MODELO),
                            new Document("$inc", new Document("total_matriculados", -1))));

            medir("E3", "Escrita", "Atualização em massa: +1 vaga em todas as turmas de um campus",
                    null,
                    () -> escrita.updateMany(new Document("campus.id", CAMPUS_SAMAMBAIA),
                            new Document("$inc", new Document("total_vagas_ofertadas", 1))).getModifiedCount(),
                    () -> escrita.updateMany(new Document("campus.id", CAMPUS_SAMAMBAIA),
                            new Document("$inc", new Document("total_vagas_ofertadas", -1))));

            // Aqui aparece o custo da desnormalização: o nome do campus está copiado em cada turma.
            medir("E4", "Escrita", "Renomear um campus (1 linha no SQL x N documentos no Mongo)",
                    null,
                    () -> escrita.updateMany(new Document("campus.id", CAMPUS_CIDADE_DE_GOIAS),
                            new Document("$set", new Document("campus.nome", NOME_TEMPORARIO_CAMPUS))).getModifiedCount(),
                    () -> escrita.updateMany(new Document("campus.id", CAMPUS_CIDADE_DE_GOIAS),
                            new Document("$set", new Document("campus.nome", nomeOriginalCampus))));

            medir("E5", "Escrita", "Remover a turma inserida",
                    inserirNova,
                    () -> escrita.deleteOne(new Document("_id", ID_TURMA_NOVA)).getDeletedCount(),
                    null);

            long depois = turmas.countDocuments(new Document());
            System.out.println();
            System.out.println(depois == antes
                    ? "Conferência: coleção voltou ao estado original (" + depois + " documentos)."
                    : "ATENÇÃO: documentos antes=" + antes + " depois=" + depois + " (verifique a limpeza das escritas).");

            salvarCsv("mongodb", "resultados_mongodb.csv");
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

    // ---------------------------------------------------------------------- construtores de operações

    /** find(): filtro, projeção e ordenação opcionais; percorre todos os documentos devolvidos. */
    private static Operacao find(MongoCollection<Document> c, String filtro, String projecao, String ordem, int limite) {
        BsonDocument f = BsonDocument.parse(filtro);
        BsonDocument p = projecao == null ? null : BsonDocument.parse(projecao);
        BsonDocument o = ordem == null ? null : BsonDocument.parse(ordem);
        return () -> {
            FindIterable<Document> it = c.find(f);
            if (p != null) {
                it = it.projection(p);
            }
            if (o != null) {
                it = it.sort(o);
            }
            if (limite > 0) {
                it = it.limit(limite);
            }
            long n = 0;
            for (Document d : it) {
                n++;
            }
            return n;
        };
    }

    /** countDocuments(): devolve o valor da contagem (para conferir com o COUNT(*) do MySQL). */
    private static Operacao contar(MongoCollection<Document> c, String filtro) {
        BsonDocument f = BsonDocument.parse(filtro);
        return () -> c.countDocuments(f);
    }

    /** aggregate(): pipeline em JSON (array de estágios); percorre todos os documentos do resultado. */
    private static Operacao agregar(MongoCollection<Document> c, String pipelineJson) {
        List<BsonDocument> etapas = BsonDocument.parse("{\"p\": " + pipelineJson + "}")
                .getArray("p").stream().map(BsonValue::asDocument).toList();
        return () -> {
            long n = 0;
            for (Document d : c.aggregate(etapas).allowDiskUse(true)) {
                n++;
            }
            return n;
        };
    }
}
