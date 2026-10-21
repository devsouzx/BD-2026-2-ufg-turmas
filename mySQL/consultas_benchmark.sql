USE ufg_turmas;

SELECT * 
FROM turma 
WHERE id_turma = 1001119;

SELECT 
    t.id_turma,
    t.codigo_turma,
    c.nome_componente AS disciplina,
    cur.nome_curso AS curso,
    cam.nome_campus AS campus,
    t.total_vagas_ofertadas,
    t.total_matriculados
FROM turma t
JOIN componente_curricular c ON t.codigo_componente = c.codigo_componente
JOIN curso cur ON t.id_curso = cur.id_curso
JOIN campus cam ON t.id_campus = cam.id_campus
LIMIT 100;

SELECT 
    t.id_turma,
    t.codigo_turma,
    c.nome_componente AS disciplina,
    d.nome_docente AS professor
FROM turma_docente td
JOIN turma t ON td.id_turma = t.id_turma
JOIN componente_curricular c ON t.codigo_componente = c.codigo_componente
JOIN docente d ON td.id_docente = d.id_docente
LIMIT 100;

SELECT 
    d.nome_docente,
    COUNT(td.id_turma) AS total_turmas
FROM docente d
JOIN turma_docente td ON d.id_docente = td.id_docente
GROUP BY d.id_docente, d.nome_docente
ORDER BY total_turmas DESC
LIMIT 10;
