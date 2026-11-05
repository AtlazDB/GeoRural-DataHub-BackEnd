-- US13.T10: o IRL e o IFC foram para o Airflow, em Python/GeoPandas (georural/irl_ifc.py e
-- indicators.py). Quem calcula é a DAG calcular_indicadores, disparada pelo POST
-- /pipeline/indicadores e pela carga (generico.py). Sai o último pacote de PL/SQL, o pkg_indicadores.
--
-- Antes de sair, o Python foi comparado com o pacote em todos os imóveis do PR, com a RL do SICAR e
-- os focos do INPE carregados: 1.115.205 linhas iguais e nenhuma diferente.
--
-- As tabelas ficam (regra_calculo, indicador_imovel, car_reserva_legal, foco_calor): o Python lê e
-- grava nas mesmas.
--
-- O pacote era uma repeatable migration (R__), que não existe mais no repositório: numa instalação do
-- zero ele nem chega a ser criado, por isso o DROP tolera "não existe" (ORA-04043), como na V22. A
-- spring.flyway.ignore-migration-patterns deixa o Flyway aceitar o histórico de quem já o tinha.
BEGIN
  EXECUTE IMMEDIATE 'DROP PACKAGE pkg_indicadores';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE <> -4043 THEN RAISE; END IF;
END;
/
