USE ufg_turmas;

-- L1 | Leitura pontual | Turma por id (registro cru)
-- esperado: 1 linha
SELECT * FROM turma WHERE id_turma = 1001223;

-- L2 | Leitura pontual | Turma por id (completa: componente, curso, campus e docentes)
-- esperado: 5 linhas (1 por docente)
-- obs: SQL devolve 1 linha por docente (5); Mongo devolve 1 documento já completo
SELECT t.id_turma, t.codigo_turma, c.nome_componente, cur.nome_curso, cam.nome_campus, d.nome_docente
FROM turma t
JOIN componente_curricular c ON c.codigo_componente = t.codigo_componente
JOIN curso cur ON cur.id_curso = t.id_curso
JOIN campus cam ON cam.id_campus = t.id_campus
LEFT JOIN turma_docente td ON td.id_turma = t.id_turma
LEFT JOIN docente d ON d.id_docente = td.id_docente
WHERE t.id_turma = 1001223;

-- F1 | Filtro | Turmas abertas de um campus, top 50 por matriculados
-- esperado: 50 linhas (1ª: id 1008259, 104 matriculados)
SELECT id_turma, codigo_turma, total_vagas_ofertadas, total_matriculados, situacao_turma
FROM turma WHERE situacao_turma = '1 - ABERTA' AND id_campus = 68700506
ORDER BY total_matriculados DESC, id_turma LIMIT 50;

-- F2 | Filtro | Contagem de turmas abertas com 20+ vagas ociosas (expressão entre colunas)
-- esperado: valor 493
SELECT COUNT(*) FROM turma WHERE situacao_turma = '1 - ABERTA' AND total_vagas_ofertadas - total_matriculados >= 20;

-- F3 | Filtro | Busca textual no nome da disciplina (contém 'BANCO DE DADOS')
-- esperado: 9 linhas
SELECT t.id_turma, t.codigo_turma, c.nome_componente
FROM turma t JOIN componente_curricular c ON c.codigo_componente = t.codigo_componente
WHERE c.nome_componente LIKE '%BANCO DE DADOS%' ORDER BY t.id_turma;

-- F4 | Filtro | Contagem de turmas a distância iniciadas a partir de 01/10/2023 (intervalo de data)
-- esperado: valor 75
SELECT COUNT(*) FROM turma WHERE modalidade_turma = 'À Distância' AND data_inicio >= '2023-10-01';

-- J1 | Junção N:N | Turmas de um docente
-- esperado: 38 linhas
SELECT t.id_turma, c.codigo_componente, c.nome_componente
FROM docente d
JOIN turma_docente td ON td.id_docente = d.id_docente
JOIN turma t ON t.id_turma = td.id_turma
JOIN componente_curricular c ON c.codigo_componente = t.codigo_componente
WHERE d.nome_docente = 'MARCELLE FIGUEIRA MARQUES DA SILVA SALES' ORDER BY t.id_turma;

-- J2 | Junção N:N | Docentes de um curso com nº de turmas (top 20)
-- esperado: 20 linhas (1ª: BRUNO OLIVEIRA SILVESTRE, 6)
SELECT d.nome_docente, COUNT(*) AS qtd
FROM turma t
JOIN turma_docente td ON td.id_turma = t.id_turma
JOIN docente d ON d.id_docente = td.id_docente
WHERE t.id_curso = 69673254
GROUP BY d.id_docente, d.nome_docente ORDER BY qtd DESC, d.nome_docente LIMIT 20;

-- J3 | Junção N:N | Todos os vínculos turma x docente x disciplina (junção completa)
-- esperado: 8768 linhas
SELECT t.id_turma, c.nome_componente, d.nome_docente
FROM turma t
JOIN componente_curricular c ON c.codigo_componente = t.codigo_componente
JOIN turma_docente td ON td.id_turma = t.id_turma
JOIN docente d ON d.id_docente = td.id_docente;

-- A1 | Agregação | Top 10 docentes com mais turmas
-- esperado: 10 linhas (1ª: MARCELLE FIGUEIRA MARQUES DA SILVA SALES, 38)
SELECT d.nome_docente, COUNT(*) AS total_turmas
FROM docente d JOIN turma_docente td ON td.id_docente = d.id_docente
GROUP BY d.id_docente, d.nome_docente ORDER BY total_turmas DESC, d.nome_docente LIMIT 10;

-- A2 | Agregação | Turmas, vagas e matriculados por campus
-- esperado: 5 linhas (1ª: 68700506 SAMAMBAIA, 3765 turmas, 95255 vagas, 56339 matriculados)
SELECT cam.id_campus, cam.nome_campus, COUNT(*) AS turmas, SUM(t.total_vagas_ofertadas) AS vagas, SUM(t.total_matriculados) AS matriculados
FROM turma t JOIN campus cam ON cam.id_campus = t.id_campus
GROUP BY cam.id_campus, cam.nome_campus ORDER BY turmas DESC, cam.id_campus;

-- A3 | Agregação | Top 10 cursos por total de matriculados
-- esperado: 10 linhas (1º: AGRONOMIA, 199 turmas, 4684 matriculados)
SELECT cur.id_curso, cur.nome_curso, COUNT(*) AS turmas, SUM(t.total_matriculados) AS matriculados
FROM turma t JOIN curso cur ON cur.id_curso = t.id_curso
GROUP BY cur.id_curso, cur.nome_curso ORDER BY matriculados DESC, cur.id_curso LIMIT 10;

-- A4 | Agregação | Top 10 disciplinas mais ofertadas
-- esperado: 10 linhas (1ª: EMA0969, 31 turmas)
SELECT c.codigo_componente, c.nome_componente, COUNT(*) AS turmas
FROM turma t JOIN componente_curricular c ON c.codigo_componente = t.codigo_componente
GROUP BY c.codigo_componente, c.nome_componente ORDER BY turmas DESC, c.codigo_componente LIMIT 10;

-- A5 | Agregação | Distribuição de turmas por nº de docentes
-- esperado: 28 linhas (1ª: n=0, 245 turmas)
SELECT n, COUNT(*) AS turmas FROM (
  SELECT t.id_turma, COUNT(td.id_docente) AS n FROM turma t LEFT JOIN turma_docente td ON td.id_turma = t.id_turma GROUP BY t.id_turma) x
GROUP BY n ORDER BY n;

-- A6 | Agregação | Turmas e matriculados por modalidade e situação (agrupa por 2 campos)
-- esperado: 7 linhas (ex.: Presencial / 1 - ABERTA = 2903 turmas, 50131 matriculados; ordem livre)
SELECT modalidade_turma, situacao_turma, COUNT(*) AS turmas, SUM(total_matriculados) AS matriculados
FROM turma GROUP BY modalidade_turma, situacao_turma;

-- =====================================================================
-- ESCRITAS (cada uma é desfeita em seguida; o Java faz isso fora da medição)
-- =====================================================================

-- E1 | Inserir turma nova (clone da turma 1001223 com seus docentes) - uma transação   esperado: 1 turma + 5 vínculos
START TRANSACTION;
INSERT INTO turma (id_turma, codigo_turma, tipo_turma, modalidade_turma, ano, periodo, total_vagas_ofertadas, total_matriculados, data_inicio, data_fim, matrizes_associadas, situacao_turma, horario, codigo_componente, id_curso, id_campus)
  SELECT 999999001, codigo_turma, tipo_turma, modalidade_turma, ano, periodo, total_vagas_ofertadas, total_matriculados, data_inicio, data_fim, matrizes_associadas, situacao_turma, horario, codigo_componente, id_curso, id_campus FROM turma WHERE id_turma = 1001223;
INSERT INTO turma_docente (id_turma, id_docente) SELECT 999999001, id_docente FROM turma_docente WHERE id_turma = 1001223;
COMMIT;

-- E5 | Remover a turma inserida (também desfaz a E1)
START TRANSACTION;
DELETE FROM turma_docente WHERE id_turma = 999999001;
DELETE FROM turma WHERE id_turma = 999999001;
COMMIT;

-- E2 | Atualizar 1 turma por id (+1 matriculado)   esperado: 1 linha alterada
UPDATE turma SET total_matriculados = total_matriculados + 1 WHERE id_turma = 1001223;
UPDATE turma SET total_matriculados = total_matriculados - 1 WHERE id_turma = 1001223;  -- desfaz

-- E3 | Atualização em massa: +1 vaga em todas as turmas do campus SAMAMBAIA   esperado: 3765 linhas alteradas
UPDATE turma SET total_vagas_ofertadas = total_vagas_ofertadas + 1 WHERE id_campus = 68700506;
UPDATE turma SET total_vagas_ofertadas = total_vagas_ofertadas - 1 WHERE id_campus = 68700506;  -- desfaz

-- E4 | Renomear um campus   esperado: 1 linha alterada (no Mongo são 295 documentos)
UPDATE campus SET nome_campus = 'CAMPUS RENOMEADO (TESTE)' WHERE id_campus = 68700504;
UPDATE campus SET nome_campus = 'CIDADE DE GOIÁS' WHERE id_campus = 68700504;  -- desfaz
