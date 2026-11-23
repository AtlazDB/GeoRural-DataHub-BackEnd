-- US11.T04: hash na quarentena.
--
-- O Airflow calcula um hash (sha1, 40 caracteres em hexadecimal) de cada registro reprovado: o que
-- veio no arquivo, a geometria e o motivo. Na carga seguinte, o reprovado igual não é gravado de
-- novo: o que está em aberto passa para a carga nova, e o que o analista já decidiu continua decidido.
-- Registro antigo, sem hash, é substituído na próxima carga, como antes, e ganha o hash.
--
-- Sem índice: a carga lê os hashes do conjunto de uma vez, pela execução.
ALTER TABLE registro_quarentena ADD (hash_registro VARCHAR2(40));

COMMENT ON COLUMN registro_quarentena.hash_registro IS 'sha1 dos dados, da geometria e do motivo do reprovado (US11.T04)';
