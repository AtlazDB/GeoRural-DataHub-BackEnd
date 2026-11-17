--------------------------------------------------------------------------------
-- Progresso do processamento de um arquivo, para a barra na tela Fonte de dados
--------------------------------------------------------------------------------
-- O DAG atualiza durante o processamento:
--   progress       0 a 100
--   progress_step  etapa atual (ex.: LEITURA, GRAVACAO, INDICADORES)
--------------------------------------------------------------------------------

ALTER TABLE execucao ADD (
  progress       NUMBER(3)          DEFAULT 0 NOT NULL,
  progress_step  VARCHAR2(30 CHAR),
  CONSTRAINT ck_execucao_progress CHECK (progress BETWEEN 0 AND 100)
);