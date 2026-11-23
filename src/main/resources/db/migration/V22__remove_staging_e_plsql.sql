-- US11.T04: as regras e a gravação foram para o Airflow, em Python (georural/regras.py, tratada.py e
-- revalidacao.py). O "Revalidar" da quarentena também (DAG revalidar_registro, pela API REST).
-- Saem a staging, o recorte em grade do "fora do PR" e os dois pacotes de PL/SQL.
--
-- Fica o pkg_indicadores: os indicadores IRL e IFC continuam no banco.
--
-- Os pacotes eram repeatable migrations (R__), que não existem mais no repositório: numa instalação
-- do zero eles nem chegam a ser criados, por isso o DROP tolera "não existe" (ORA-04043). A
-- spring.flyway.ignore-migration-patterns deixa o Flyway aceitar o histórico de quem já os tinha.
BEGIN
  EXECUTE IMMEDIATE 'DROP PACKAGE pkg_tratada';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE <> -4043 THEN RAISE; END IF;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'DROP PACKAGE pkg_quality';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE <> -4043 THEN RAISE; END IF;
END;
/

DROP TABLE staging_erro;
DROP TABLE staging_registro;

DROP TABLE recorte_territorial;
DELETE FROM USER_SDO_GEOM_METADATA WHERE TABLE_NAME = 'RECORTE_TERRITORIAL';
COMMIT;

COMMENT ON TABLE tipo_erro IS 'Os códigos de erro das regras de qualidade (Airflow: georural/regras.py).';
