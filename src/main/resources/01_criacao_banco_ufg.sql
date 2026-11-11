CREATE DATABASE IF NOT EXISTS ufg_turmas;
USE ufg_turmas;

-- . Tabela Campus
CREATE TABLE IF NOT EXISTS campus (
    id_campus INT PRIMARY KEY,
    nome_campus VARCHAR(100) NOT NULL
);

-- . Tabela Curso
CREATE TABLE IF NOT EXISTS curso (
    id_curso INT PRIMARY KEY,
    nome_curso VARCHAR(150) NOT NULL
);

-- . Tabela Componente Curricular
CREATE TABLE IF NOT EXISTS componente_curricular (
    codigo_componente VARCHAR(20) PRIMARY KEY,
    nome_componente VARCHAR(200) NOT NULL,
    tipo_componente VARCHAR(50),
    ch_total INT,
    unidade_academica INT
);

-- . Tabela Docente
CREATE TABLE IF NOT EXISTS docente (
    id_docente INT AUTO_INCREMENT PRIMARY KEY,
    nome_docente VARCHAR(200) NOT NULL UNIQUE
);

-- . Tabela Turma
CREATE TABLE IF NOT EXISTS turma (
    id_turma INT PRIMARY KEY,
    codigo_turma VARCHAR(10) NOT NULL,
    tipo_turma VARCHAR(50),
    modalidade_turma VARCHAR(50),
    ano INT NOT NULL,
    periodo INT NOT NULL,
    total_vagas_ofertadas INT,
    total_matriculados INT,
    data_inicio DATE,
    data_fim DATE,
    matrizes_associadas TEXT,
    situacao_turma VARCHAR(100),
    horario VARCHAR(255),
    codigo_componente VARCHAR(20) NOT NULL,
    id_curso INT NOT NULL,
    id_campus INT NOT NULL,
    FOREIGN KEY (codigo_componente) REFERENCES componente_curricular(codigo_componente),
    FOREIGN KEY (id_curso) REFERENCES curso(id_curso),
    FOREIGN KEY (id_campus) REFERENCES campus(id_campus)
);

-- . Tabela Associativa Turma_Docente
CREATE TABLE IF NOT EXISTS turma_docente (
    id_turma INT NOT NULL,
    id_docente INT NOT NULL,
    PRIMARY KEY (id_turma, id_docente),
    FOREIGN KEY (id_turma) REFERENCES turma(id_turma),
    FOREIGN KEY (id_docente) REFERENCES docente(id_docente)
);
