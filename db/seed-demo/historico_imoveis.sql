--------------------------------------------------------------------------------
-- GeoRural DataHub — Seed de demonstração: segunda versão de alguns imóveis (US09)
--------------------------------------------------------------------------------
-- NÃO é migration: fica fora do Flyway e roda à mão, no banco onde a demo
-- acontece, depois de pelo menos uma carga do CAR. Rodar de novo não faz nada
-- (procura o arquivo demo_segunda_carga_car.zip).
--
-- Simula uma segunda carga do CAR para 3 imóveis, como a DAG faria: cria o
-- arquivo, a execução e a versão nova do conjunto imoveis_rurais, e regrava os
-- imóveis com hash novo. O trigger da V25 guarda a versão anterior de cada um.
--
--   1º imóvel: área declarada 5% maior
--   2º imóvel: situação muda para PE (pendente)
--   3º imóvel: contorno simplificado (vértices a menos de 10 m somem: área e geometria mudam)
--
-- Escolhe imóveis que já têm IRL ou IFC calculado, se houver. Depois de rodar,
-- "Calcular indicadores" de novo para a comparação mostrar o indicador nas duas
-- versões.
--------------------------------------------------------------------------------

DECLARE
  v_fonte    NUMBER;
  v_arquivo  NUMBER;
  v_execucao NUMBER;
  v_versao   NUMBER;
  v_numero   NUMBER;
  v_ordem    NUMBER := 0;
BEGIN
  SELECT COUNT(*) INTO v_numero FROM arquivo_bruto WHERE nome_original = 'demo_segunda_carga_car.zip';
  IF v_numero > 0 THEN
    DBMS_OUTPUT.PUT_LINE('Seed já aplicado: nada a fazer.');
    RETURN;
  END IF;

  SELECT MAX(a.fonte_id) INTO v_fonte
    FROM dataset_versao dv
    JOIN execucao e      ON e.id = dv.execucao_id
    JOIN arquivo_bruto a ON a.id = e.arquivo_bruto_id
   WHERE dv.conjunto = 'imoveis_rurais'
     AND dv.id = (SELECT MAX(id) FROM dataset_versao WHERE conjunto = 'imoveis_rurais');
  IF v_fonte IS NULL THEN
    RAISE_APPLICATION_ERROR(-20000, 'Nenhuma carga do CAR no banco: processe um arquivo do CAR antes do seed.');
  END IF;

  INSERT INTO arquivo_bruto (fonte_id, chave_objeto, nome_original, tamanho_bytes, hash_sha256, status, origem, processado_em)
  VALUES (v_fonte, 'bruta/demo/demo_segunda_carga_car.zip', 'demo_segunda_carga_car.zip', 1,
          'demo-segunda-carga-car', 'PROCESSADO', 'UPLOAD', SYSTIMESTAMP)
  RETURNING id INTO v_arquivo;

  INSERT INTO execucao (arquivo_bruto_id, tipo, status, finalizada_em)
  VALUES (v_arquivo, 'PUBLICACAO', 'CONCLUIDA', SYSTIMESTAMP)
  RETURNING id INTO v_execucao;

  SELECT NVL(MAX(versao), 0) + 1 INTO v_numero FROM dataset_versao WHERE conjunto = 'imoveis_rurais';
  INSERT INTO dataset_versao (conjunto, versao, execucao_id, zona, registros, publicada_em)
  VALUES ('imoveis_rurais', v_numero, v_execucao, 'TRATADA', 3, SYSTIMESTAMP)
  RETURNING id INTO v_versao;

  FOR im IN (SELECT ir.cod_imovel
               FROM imovel_rural ir
              ORDER BY CASE WHEN EXISTS (SELECT 1 FROM indicador_imovel ii WHERE ii.cod_imovel = ir.cod_imovel)
                            THEN 0 ELSE 1 END, ir.cod_imovel
              FETCH FIRST 3 ROWS ONLY) LOOP
    v_ordem := v_ordem + 1;
    -- Um UPDATE só: o trigger guarda a linha como estava antes de tudo mudar.
    UPDATE imovel_rural
       SET area_ha   = CASE WHEN v_ordem = 1 THEN ROUND(area_ha * 1.05, 4) ELSE area_ha END,
           situacao  = CASE WHEN v_ordem = 2 THEN 'PE' ELSE situacao END,
           geometria = CASE WHEN v_ordem = 3 THEN SDO_UTIL.SIMPLIFY(geometria, 10, 0.05) ELSE geometria END,
           dataset_versao_id = v_versao,
           hash_registro = 'demo-' || v_numero || '-' || v_ordem
     WHERE cod_imovel = im.cod_imovel;
    DBMS_OUTPUT.PUT_LINE('Versão ' || v_numero || ' do imóvel ' || im.cod_imovel);
  END LOOP;

  COMMIT;
END;
/
