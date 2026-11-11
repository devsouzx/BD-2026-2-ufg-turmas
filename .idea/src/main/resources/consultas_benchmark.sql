USE ufg_turmas;

SELECT id_turma, codigo_turma, ano, periodo, total_vagas_ofertadas, total_matriculados
FROM turma
WHERE id_turma = 1001223;

SELECT t.*, c.codigo_componente, c.nome_componente, c.tipo_componente, c.ch_total,
       cur.id_curso, cur.nome_curso, cam.id_campus, cam.nome_campus, d.nome_docente
FROM turma t
JOIN componente_curricular c ON c.codigo_componente = t.codigo_componente
JOIN curso cur ON cur.id_curso = t.id_curso
JOIN campus cam ON cam.id_campus = t.id_campus
LEFT JOIN turma_docente td ON td.id_turma = t.id_turma
LEFT JOIN docente d ON d.id_docente = td.id_docente
WHERE t.id_turma = 1001223;

SELECT id_turma, codigo_turma, total_vagas_ofertadas, total_matriculados, situacao_turma
FROM turma
WHERE situacao_turma = '1 - ABERTA' AND id_campus = 68700506
ORDER BY total_matriculados DESC, id_turma
LIMIT 50;

SELECT COUNT(*)
FROM turma
WHERE situacao_turma = '1 - ABERTA'
  AND COALESCE(total_vagas_ofertadas, 0) - COALESCE(total_matriculados, 0) >= 20;

SELECT t.id_turma, t.codigo_turma, c.nome_componente
FROM turma t
JOIN componente_curricular c ON c.codigo_componente = t.codigo_componente
WHERE c.nome_componente LIKE '%BANCO DE DADOS%'
ORDER BY t.id_turma;

SELECT COUNT(*)
FROM turma
WHERE modalidade_turma = 'À Distância'
  AND data_inicio >= '2023-10-01';

SELECT t.id_turma, c.codigo_componente, c.nome_componente
FROM docente d
JOIN turma_docente td ON td.id_docente = d.id_docente
JOIN turma t ON t.id_turma = td.id_turma
JOIN componente_curricular c ON c.codigo_componente = t.codigo_componente
WHERE d.nome_docente = 'MARCELLE FIGUEIRA MARQUES DA SILVA SALES'
ORDER BY t.id_turma;

SELECT d.nome_docente, COUNT(*) AS total_turmas
FROM docente d
JOIN turma_docente td ON td.id_docente = d.id_docente
GROUP BY d.id_docente, d.nome_docente
ORDER BY total_turmas DESC, d.nome_docente
LIMIT 10;

SELECT cam.id_campus, cam.nome_campus, COUNT(*) AS turmas,
       SUM(t.total_vagas_ofertadas) AS vagas,
       SUM(t.total_matriculados) AS matriculados
FROM turma t
JOIN campus cam ON cam.id_campus = t.id_campus
GROUP BY cam.id_campus, cam.nome_campus
ORDER BY turmas DESC, cam.id_campus;

SELECT qtd_docentes, COUNT(*) AS turmas
FROM (
    SELECT t.id_turma, COUNT(td.id_docente) AS qtd_docentes
    FROM turma t
    LEFT JOIN turma_docente td ON td.id_turma = t.id_turma
    GROUP BY t.id_turma
) AS distribuicao
GROUP BY qtd_docentes
ORDER BY qtd_docentes;

START TRANSACTION;
INSERT INTO turma (
    id_turma, codigo_turma, tipo_turma, modalidade_turma, ano, periodo,
    total_vagas_ofertadas, total_matriculados, data_inicio, data_fim,
    matrizes_associadas, situacao_turma, horario, codigo_componente,
    id_curso, id_campus
)
SELECT
    999999001, codigo_turma, tipo_turma, modalidade_turma, ano, periodo,
    total_vagas_ofertadas, total_matriculados, data_inicio, data_fim,
    matrizes_associadas, situacao_turma, horario, codigo_componente,
    id_curso, id_campus
FROM turma
WHERE id_turma = 1001223;

INSERT INTO turma_docente (id_turma, id_docente)
SELECT 999999001, id_docente
FROM turma_docente
WHERE id_turma = 1001223;
ROLLBACK;

START TRANSACTION;
UPDATE turma
SET total_matriculados = total_matriculados + 1
WHERE id_turma = 1001223;
ROLLBACK;

START TRANSACTION;
UPDATE campus
SET nome_campus = 'CAMPUS RENOMEADO (TESTE)'
WHERE id_campus = 68700504;
ROLLBACK;

START TRANSACTION;
INSERT INTO turma (
    id_turma, codigo_turma, tipo_turma, modalidade_turma, ano, periodo,
    total_vagas_ofertadas, total_matriculados, data_inicio, data_fim,
    matrizes_associadas, situacao_turma, horario, codigo_componente,
    id_curso, id_campus
)
SELECT
    999999001, codigo_turma, tipo_turma, modalidade_turma, ano, periodo,
    total_vagas_ofertadas, total_matriculados, data_inicio, data_fim,
    matrizes_associadas, situacao_turma, horario, codigo_componente,
    id_curso, id_campus
FROM turma
WHERE id_turma = 1001223;

INSERT INTO turma_docente (id_turma, id_docente)
SELECT 999999001, id_docente
FROM turma_docente
WHERE id_turma = 1001223;

DELETE FROM turma_docente WHERE id_turma = 999999001;
DELETE FROM turma WHERE id_turma = 999999001;
ROLLBACK;
