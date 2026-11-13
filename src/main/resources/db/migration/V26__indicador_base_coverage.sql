-- Terceiro modelo de cálculo dos indicadores cadastrados: BASE_COVERAGE (IAPP, APP conservada).
--
-- Quanto de uma camada base dentro do imóvel está coberto pela fonte do indicador, em %:
-- (imóvel ∩ APP ∩ vegetação nativa) ÷ (imóvel ∩ APP) × 100. A camada base vem de
-- parameters.baseSources (ids de fonte); a cobertura, da fonte_id (+ parameters.extraSources).
-- No indicator_result, numerator é a APP com vegetação e area_imovel_ha é a APP total do imóvel
-- (o passivo a recuperar é a diferença). Imóvel sem APP não ganha resultado.
--
-- Quem calcula é a DAG calcular_indicador (georural/custom_indicators.py); aqui só se libera o
-- modelo no CHECK. Os outros parâmetros (since, extraSources, baseSources) vivem no JSON de
-- parameters e não precisam de coluna.

ALTER TABLE indicator_definition DROP CONSTRAINT ck_indicator_def_model;

ALTER TABLE indicator_definition ADD CONSTRAINT ck_indicator_def_model
  CHECK (calculation_model IN ('POINT_DENSITY', 'AREA_COVERAGE', 'BASE_COVERAGE'));

COMMENT ON COLUMN indicator_definition.parameters IS
  'JSON com os parâmetros do modelo (factor, category, since, extraSources, baseSources).';
