--------------------------------------------------------------------------------
-- Geometria também em WKB, gravada na carga, para o cálculo dos indicadores.
--------------------------------------------------------------------------------
-- O GeoPandas só lê WKB. Sem esta coluna, cada cálculo de indicador pede ao Oracle
-- SDO_UTIL.TO_WKBGEOMETRY de todas as geometrias, e só essa conversão leva ~7 min
-- para os 557 mil imóveis (Oracle local, 17/11/2026). Lendo o WKB pronto, ~55 s.
--
-- Quem grava: a carga no Airflow (gravacao.py no CAR, tratada.py no genérico e na
-- revalidação), junto com a geometria, no mesmo MERGE/INSERT. Sem trigger: a regra
-- fica no Python. A coluna geometria continua sendo a referência (índice espacial,
-- SDO_FILTER, back e mapa).
--
-- As linhas que já existem são preenchidas fora desta migration (leva minutos e não
-- pode travar a subida do back): DAG preencher_geometria_wkb, uma vez por ambiente.
-- Enquanto isso, o cálculo lê COALESCE(geometria_wkb, SDO_UTIL.TO_WKBGEOMETRY(geometria)):
-- continua certo, só lento nas linhas que ainda não têm o WKB.
--------------------------------------------------------------------------------

ALTER TABLE imovel_rural ADD (
  geometria_wkb BLOB
);

ALTER TABLE input_feature ADD (
  geometria_wkb BLOB
);

COMMENT ON COLUMN imovel_rural.geometria_wkb IS 'A mesma geometria em WKB 2D, gravada pela carga junto com a geometria (leitura rápida no cálculo dos indicadores)';
COMMENT ON COLUMN input_feature.geometria_wkb IS 'A mesma geometria em WKB 2D, gravada pela carga junto com a geometria (leitura rápida no cálculo dos indicadores)';
