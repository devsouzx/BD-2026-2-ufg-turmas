USE ufg_turmas;

-- R1 Turma por ID (atributos básicos)
SELECT id_turma, codigo_turma, ano, periodo, total_vagas_ofertadas, total_matriculados
FROM turma
WHERE id_turma = 1001223;

-- R2 Turma por ID com dados relacionados
SELECT t.*, c.nome_componente, c.tipo_componente, c.ch_total, cur.nome_curso, cam.nome_campus, d.nome_docente
FROM turma t
JOIN componente_curricular c ON c.codigo_componente = t.codigo_componente
JOIN curso cur ON cur.id_curso = t.id_curso
JOIN campus cam ON cam.id_campus = t.id_campus
LEFT JOIN turma_docente td ON td.id_turma = t.id_turma
LEFT JOIN docente d ON d.id_docente = td.id_docente
WHERE t.id_turma = 1001223;

-- F1 Abertas por campus (top 50)
SELECT id_turma, codigo_turma, total_vagas_ofertadas, total_matriculados, situacao_turma
FROM turma
WHERE situacao_turma = '1 - ABERTA' AND id_campus = 68700506
ORDER BY total_matriculados DESC, id_turma
LIMIT 50;

-- F2 Abertas com 20+ vagas ociosas
SELECT id_turma, codigo_turma, total_vagas_ofertadas, total_matriculados
FROM turma
WHERE situacao_turma = '1 - ABERTA' AND total_vagas_ofertadas - total_matriculados >= 20;

-- F3 Componente por trecho do nome
SELECT t.id_turma, t.codigo_turma, c.nome_componente
FROM turma t
JOIN componente_curricular c ON c.codigo_componente = t.codigo_componente
WHERE c.nome_componente LIKE '%BANCO DE DADOS%';

-- J1 Turmas de um docente
SELECT t.id_turma, c.codigo_componente, c.nome_componente
FROM docente d
JOIN turma_docente td ON td.id_docente = d.id_docente
JOIN turma t ON t.id_turma = td.id_turma
JOIN componente_curricular c ON c.codigo_componente = t.codigo_componente
WHERE d.nome_docente = 'MARCELLE FIGUEIRA MARQUES DA SILVA SALES';

-- A1 Top docentes por nº de turmas
SELECT d.nome_docente, COUNT(*) AS total_turmas
FROM docente d
JOIN turma_docente td ON td.id_docente = d.id_docente
GROUP BY d.id_docente, d.nome_docente
ORDER BY total_turmas DESC, d.nome_docente
LIMIT 10;

-- A2 Vagas e matriculados por campus
SELECT cam.id_campus, cam.nome_campus, COUNT(*) AS turmas, SUM(t.total_vagas_ofertadas) AS vagas, SUM(t.total_matriculados) AS matriculados
FROM turma t
JOIN campus cam ON cam.id_campus = t.id_campus
GROUP BY cam.id_campus, cam.nome_campus
ORDER BY turmas DESC, cam.id_campus;

-- A3 Distribuição de docentes por turma
SELECT qtd_docentes, COUNT(*) AS turmas
FROM (
    SELECT t.id_turma, COUNT(td.id_docente) AS qtd_docentes
    FROM turma t
    LEFT JOIN turma_docente td ON td.id_turma = t.id_turma
    GROUP BY t.id_turma
) AS por_turma
GROUP BY qtd_docentes
ORDER BY qtd_docentes;

-- E1 Inserir turma com docentes
START TRANSACTION;
INSERT INTO turma (id_turma,
    codigo_turma, tipo_turma, modalidade_turma, ano, periodo,
    total_vagas_ofertadas, total_matriculados, data_inicio, data_fim, matrizes_associadas,
    situacao_turma, horario, codigo_componente, id_curso, id_campus)
SELECT 999999001,
    codigo_turma, tipo_turma, modalidade_turma, ano, periodo,
    total_vagas_ofertadas, total_matriculados, data_inicio, data_fim, matrizes_associadas,
    situacao_turma, horario, codigo_componente, id_curso, id_campus
FROM turma WHERE id_turma = 1001223;

INSERT INTO turma_docente (id_turma, id_docente)
SELECT 999999001, id_docente FROM turma_docente WHERE id_turma = 1001223;
ROLLBACK;

-- E2 Alterar matriculados por turma
START TRANSACTION;
UPDATE turma SET total_matriculados = total_matriculados + 1 WHERE id_turma = 1001223;
ROLLBACK;

-- E3 Renomear campus compartilhado
START TRANSACTION;
UPDATE campus SET nome_campus = 'CAMPUS RENOMEADO' WHERE id_campus = 68700504;
ROLLBACK;

-- E4 Excluir turma com docentes
START TRANSACTION;
INSERT INTO turma (id_turma,
    codigo_turma, tipo_turma, modalidade_turma, ano, periodo,
    total_vagas_ofertadas, total_matriculados, data_inicio, data_fim, matrizes_associadas,
    situacao_turma, horario, codigo_componente, id_curso, id_campus)
SELECT 999999001,
    codigo_turma, tipo_turma, modalidade_turma, ano, periodo,
    total_vagas_ofertadas, total_matriculados, data_inicio, data_fim, matrizes_associadas,
    situacao_turma, horario, codigo_componente, id_curso, id_campus
FROM turma WHERE id_turma = 1001223;

INSERT INTO turma_docente (id_turma, id_docente)
SELECT 999999001, id_docente FROM turma_docente WHERE id_turma = 1001223;

DELETE FROM turma_docente WHERE id_turma = 999999001;
DELETE FROM turma WHERE id_turma = 999999001;
ROLLBACK;
