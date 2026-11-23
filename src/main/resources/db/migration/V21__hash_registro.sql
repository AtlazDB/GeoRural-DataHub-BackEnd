-- US11.T04: carga incremental sem staging.
--
-- O Airflow grava direto na Zona Tratada e calcula um hash (sha1, 40 caracteres em hexadecimal)
-- de cada linha: os valores das colunas mapeadas, já no tipo do destino, mais a geometria em WKB.
-- Na carga seguinte, o mesmo registro com o mesmo hash não é gravado de novo: só entra o que é
-- novo, só muda o que mudou e, na carga-retrato, só sai o que sumiu da fonte. Linha antiga, sem
-- hash, conta como alterada e ganha o hash na primeira carga depois desta migration.
--
-- Sem índice: o hash é lido junto com a chave (ou o cod_imovel, ou a versão) da carga anterior,
-- e esses já têm índice.
ALTER TABLE imovel_rural      ADD (hash_registro VARCHAR2(40));
ALTER TABLE car_reserva_legal ADD (hash_registro VARCHAR2(40));
ALTER TABLE foco_calor        ADD (hash_registro VARCHAR2(40));
ALTER TABLE municipio         ADD (hash_registro VARCHAR2(40));
ALTER TABLE input_feature     ADD (hash_registro VARCHAR2(40));

COMMENT ON COLUMN imovel_rural.hash_registro      IS 'sha1 dos valores mapeados + geometria (carga incremental, US11.T04)';
COMMENT ON COLUMN car_reserva_legal.hash_registro IS 'sha1 dos valores mapeados + geometria (carga incremental, US11.T04)';
COMMENT ON COLUMN foco_calor.hash_registro        IS 'sha1 dos valores mapeados + geometria (carga incremental, US11.T04)';
COMMENT ON COLUMN municipio.hash_registro         IS 'sha1 dos valores mapeados + geometria (carga incremental, US11.T04)';
COMMENT ON COLUMN input_feature.hash_registro     IS 'sha1 dos valores mapeados + geometria (carga incremental, US11.T04)';
