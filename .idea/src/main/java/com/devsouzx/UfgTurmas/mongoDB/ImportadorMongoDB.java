package com.devsouzx.UfgTurmas.mongoDB;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Collation;
import com.mongodb.client.model.CollationStrength;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.InsertManyOptions;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.io.BufferedReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ImportadorMongoDB {
    private static final String URI = System.getenv().getOrDefault("MONGO_URI", "mongodb://localhost:27017");
    private static final String BANCO = "ufg_turmas";
    private static final String COLECAO = "turma";
    private static final int TAMANHO_LOTE = 1000;

    private static final String[] CABECALHOS_OBRIGATORIOS = {
            "id_turma", "codigo_componente_curricular",
            "nome_componente_curricular", "tipo_do_componente",
            "ch_total_componente", "unidade_academica", "codigo_turma",
            "tipo_turma", "modalidade_turma", "ano", "periodo",
            "id_curso_responsavel_oferta", "curso_responsavel_pela_oferta",
            "id_campus_ies", "campus", "total_vagas_ofertadas",
            "data_inicio", "matrizes_associadas",
            "situacao_turma", "horario", "total_matriculados", "docentes"
    };

    private static String colunaDataFim = "data_fim";

    private static final DateTimeFormatter[] FORMATOS_DATA = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/uuuu")
    };

    // Ignora acento e maiúscula ao agrupar
    private static final Collation COLACAO = Collation.builder()
            .locale("pt")
            .collationStrength(CollationStrength.PRIMARY)
            .build();

    public static void main(String[] args) throws Exception {
        Path arquivo = Path.of(args.length > 0 ? args[0] : "da_turmas_2023_2.csv");

        try (MongoClient cliente = MongoClients.create(URI)) {
            MongoDatabase banco = cliente.getDatabase(BANCO);
            MongoCollection<Document> turmas = banco.getCollection(COLECAO);

            turmas.drop();

            List<Document> lote = new ArrayList<>();
            Set<Integer> turmasVistas = new HashSet<>();
            long linhasLidas = 0, linhasSemId = 0, linhasDuplicadas = 0, inseridas = 0;

            long inicio = System.nanoTime();

            try (BufferedReader leitor = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
                leitor.mark(1);
                if (leitor.read() != '\uFEFF') {
                    leitor.reset();
                }

                try (CSVParser parser = CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setIgnoreEmptyLines(true)
                        .setTrim(true)
                        .get()
                        .parse(leitor)) {

                    validarCabecalho(parser);

                    for (CSVRecord linha : parser) {
                        linhasLidas++;

                        Integer idTurma = inteiroOuNulo(campo(linha, "id_turma"));
                        if (idTurma == null) {
                            linhasSemId++;
                            continue;
                        }
                        if (!turmasVistas.add(idTurma)) {
                            linhasDuplicadas++;
                            continue;
                        }

                        Document componente = new Document("codigo",
                                textoOuNulo(campo(linha, "codigo_componente_curricular")))
                                .append("nome", campo(linha, "nome_componente_curricular"))
                                .append("tipo", campo(linha, "tipo_do_componente"))
                                .append("ch_total", inteiroOuNulo(campo(linha, "ch_total_componente")))
                                .append("unidade_academica",
                                        inteiroOuNulo(campo(linha, "unidade_academica")));

                        Document curso = new Document("id",
                                inteiroOuNulo(campo(linha, "id_curso_responsavel_oferta")))
                                .append("nome", campo(linha, "curso_responsavel_pela_oferta"));

                        Document campus = new Document("id",
                                inteiroOuNulo(campo(linha, "id_campus_ies")))
                                .append("nome", campo(linha, "campus"));

                        lote.add(new Document("_id", idTurma)
                                .append("codigo_turma", campo(linha, "codigo_turma"))
                                .append("tipo_turma", campo(linha, "tipo_turma"))
                                .append("modalidade_turma", campo(linha, "modalidade_turma"))
                                .append("ano", inteiroOuNulo(campo(linha, "ano")))
                                .append("periodo", inteiroOuNulo(campo(linha, "periodo")))
                                .append("total_vagas_ofertadas",
                                        inteiroOuNulo(campo(linha, "total_vagas_ofertadas")))
                                .append("total_matriculados",
                                        inteiroOuNulo(campo(linha, "total_matriculados")))
                                .append("data_inicio", dataOuNula(campo(linha, "data_inicio")))
                                .append("data_fim", dataOuNula(campo(linha, colunaDataFim)))
                                .append("matrizes_associadas", campo(linha, "matrizes_associadas"))
                                .append("situacao_turma", campo(linha, "situacao_turma"))
                                .append("horario", campo(linha, "horario"))
                                .append("componente", componente)
                                .append("curso", curso)
                                .append("campus", campus)
                                .append("docentes", separarDocentes(campo(linha, "docentes"))));

                        if (lote.size() >= TAMANHO_LOTE) {
                            inseridas += enviar(turmas, lote);
                        }
                    }
                }
            }
            inseridas += enviar(turmas, lote);

            long msCarga = (System.nanoTime() - inicio) / 1_000_000;

            long inicioIdx = System.nanoTime();
            criarIndices(turmas);
            long msIndices = (System.nanoTime() - inicioIdx) / 1_000_000;

            System.out.println("=== Importação concluída ===");
            System.out.println("Linhas lidas do CSV:       " + linhasLidas);
            System.out.println("Ignoradas (sem id_turma):  " + linhasSemId);
            System.out.println("Ignoradas (id repetido):   " + linhasDuplicadas);
            System.out.println("Turmas inseridas:          " + inseridas);
            System.out.println();
            System.out.println("Distintos (para conferir com o MySQL):");
            System.out.println("  campi:       " + contarDistintos(turmas, "$campus.id", false));
            System.out.println("  cursos:      " + contarDistintos(turmas, "$curso.id", false));
            System.out.println("  componentes: " + contarDistintos(turmas, "$componente.codigo", false));
            System.out.println("  docentes:    " + contarDistintos(turmas, "$docentes", true));
            System.out.println();
            System.out.println("Tempo de carga:   " + msCarga + " ms");
            System.out.println("Tempo de índices: " + msIndices + " ms");
        }
    }

    // Envio em lote e índices
    private static int enviar(MongoCollection<Document> colecao, List<Document> lote) {
        if (lote.isEmpty()) {
            return 0;
        }
        int n = lote.size();
        colecao.insertMany(lote, new InsertManyOptions().ordered(false));
        lote.clear();
        return n;
    }

    private static void criarIndices(MongoCollection<Document> turmas) {
        turmas.createIndex(Indexes.ascending("campus.id"));
        turmas.createIndex(Indexes.ascending("curso.id"));
        turmas.createIndex(Indexes.ascending("componente.codigo"));
        turmas.createIndex(Indexes.ascending("ano", "periodo"));
        turmas.createIndex(Indexes.ascending("situacao_turma"));
        turmas.createIndex(Indexes.ascending("docentes"));
    }

    private static long contarDistintos(MongoCollection<Document> colecao,
                                        String campo, boolean desdobrar) {
        List<Bson> etapas = new ArrayList<>();
        if (desdobrar) {
            etapas.add(Aggregates.unwind(campo));
        }
        etapas.add(Aggregates.match(Filters.ne(campo.substring(1), null)));
        etapas.add(Aggregates.group(campo));
        etapas.add(Aggregates.count("total"));

        Document resultado = colecao.aggregate(etapas).collation(COLACAO).first();
        return resultado == null ? 0 : ((Number) resultado.get("total")).longValue();
    }

    private static void validarCabecalho(CSVParser parser) {
        Map<String, Integer> encontrados = parser.getHeaderMap();
        List<String> faltando = new ArrayList<>();
        for (String h : CABECALHOS_OBRIGATORIOS) {
            if (!encontrados.containsKey(h)) {
                faltando.add(h);
            }
        }
        if (encontrados.containsKey("data_fim")) {
            colunaDataFim = "data_fim";
        } else if (encontrados.containsKey("data_fima")) {
            colunaDataFim = "data_fima";
        } else {
            faltando.add("data_fim (ou data_fima)");
        }
        if (!faltando.isEmpty()) {
            throw new IllegalStateException(
                    "Colunas não encontradas no CSV: " + faltando
                            + "\nColunas existentes: " + encontrados.keySet());
        }
    }

    private static String campo(CSVRecord linha, String nome) {
        String valor = linha.get(nome);
        return valor == null ? "" : valor.trim();
    }

    private static String textoOuNulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor;
    }

    private static Integer inteiroOuNulo(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(valor.trim()).intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    private static Date dataOuNula(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String texto = valor.trim();
        if (texto.length() > 10) {
            texto = texto.substring(0, 10);
        }
        for (DateTimeFormatter formato : FORMATOS_DATA) {
            try {
                return Date.from(LocalDate.parse(texto, formato)
                        .atStartOfDay(ZoneOffset.UTC).toInstant());
            } catch (DateTimeParseException ignorado) {
                // tenta o próximo formato
            }
        }
        return null;
    }

    private static List<String> separarDocentes(String texto) {
        List<String> nomes = new ArrayList<>();
        if (texto == null || texto.isBlank()) {
            return nomes;
        }
        Set<String> chaves = new HashSet<>();
        for (String parte : texto.split(",")) {
            String nome = parte.trim().replaceAll("\\s+", " ");
            if (!nome.isBlank() && chaves.add(normalizar(nome))) {
                nomes.add(nome);
            }
        }
        return nomes;
    }

    private static String normalizar(String nome) {
        String semAcento = Normalizer.normalize(nome, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return semAcento.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}