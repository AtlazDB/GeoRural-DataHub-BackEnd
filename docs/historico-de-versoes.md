# Histórico de versões e origem dos números (US09)

Responde à pergunta do Auditor: de onde veio este número, e o que mudou desde a versão anterior?

## Como o histórico é gravado

A carga do Airflow (`tratada.py`) só regrava um imóvel quando o `hash_registro` dele muda (V21). Antes de a linha ser sobrescrita ou apagada, o trigger `trg_imovel_rural_historico` (V25) copia a versão anterior para `imovel_rural_historico`:

| Coluna | O que guarda |
|---|---|
| `dataset_versao_id` | a versão a que a linha pertencia |
| `substituida_por_versao_id` | a versão que a substituiu (no `DELETE`, a última versão do mesmo conjunto) |
| `operacao` | `ALTERADO` ou `REMOVIDO` |
| `atributos` | município, UF, área declarada, situação e código do município, em JSON |
| `geometria`, `hash_registro` | o contorno e o hash daquela versão |

Decisões:
- **Trigger no banco, e não na DAG.** Vale para qualquer caminho que grave no `imovel_rural`: a carga, o Revalidar da quarentena ou um ajuste manual. Também não exige mudança no repositório do Airflow.
- **Só grava quando a versão e o hash mudam.** Regravar a mesma linha dentro da mesma carga não é uma versão nova.
- **O histórico é imutável.** O trigger `trg_imovel_historico_imutavel` recusa qualquer `UPDATE` (ORA-20030). A única exceção é o `ON DELETE SET NULL` da `substituida_por_versao_id`.
- **Apagar uma `dataset_versao`** (limpeza administrativa ou de teste) leva junto o histórico dela (`ON DELETE CASCADE`).

## Endpoints (tag "Versões" no Swagger)

| Endpoint | Para quê |
|---|---|
| `GET /api/v1/fontes/{id}/versoes` | Versões de cada conjunto da fonte: data, arquivo, hash, origem (upload/coleta), execução e registros. No CAR, também novos, alterados e removidos |
| `GET /api/v1/imoveis/{cod}/origem` | Versão atual do imóvel com a trilha (fonte → arquivo/hash → execução), todas as versões dele e os indicadores com a regra e as versões de dados usadas |
| `GET /api/v1/imoveis/{cod}/comparar?de=&para=` | Campos e contorno que mudaram entre duas versões e, em cada indicador, se a diferença veio do dado, da regra ou dos dois. Sem `de` e `para`, compara a mais nova com a anterior |

Na comparação, o motivo de cada indicador é:

| Motivo | Quando |
|---|---|
| `DADO` | mudaram os campos ou o contorno do imóvel, ou a versão do insumo (RL, focos) |
| `REGRA` | mudou a regra de cálculo (código ou versão em `regra_calculo`) |
| `DADO_E_REGRA` | as duas coisas |
| `NENHUM` | nada que entra no cálculo mudou |
| `SEM_CALCULO` | o indicador não foi calculado numa das versões |

Nos indicadores cadastrados (`indicator_result`), a regra não é versionada, então a diferença só pode aparecer como `DADO`.

Contagem por versão do CAR:
- **novo** é o imóvel cuja primeira versão (no histórico ou na linha atual) é aquela;
- **alterado** e **removido** vêm de `substituida_por_versao_id`.

## Telas

- **Fontes de dados → página da fonte → aba Versões:** linha do tempo das versões, com a atual em destaque e as anteriores marcadas como imutáveis.
- **Imóveis rurais → card de indicadores → "De onde veio este número?":** abre o modal com a trilha até o arquivo de origem e a comparação com a versão anterior. Os seletores permitem comparar qualquer par de versões. "Histórico completo do cálculo" leva à tela de Rastreabilidade.

## Dados de demonstração

O script `db/seed-demo/historico_imoveis.sql` roda à mão, fora do Flyway, depois de pelo menos uma carga do CAR. Ele simula uma segunda carga para 3 imóveis:
- o primeiro muda a área declarada;
- o segundo muda a situação;
- o terceiro muda o contorno.

Depois, "Calcular indicadores" de novo para a comparação mostrar o IRL e o IFC nas duas versões.

## Limitações

- Só o `imovel_rural` tem histórico. Reserva Legal, focos e `input_feature` mostram as versões na aba, mas sem novos, alterados e removidos.
- Os `atributos` guardados são os 5 campos do imóvel listados acima. Se o `imovel_rural` ganhar coluna nova, o trigger precisa ser atualizado numa migration nova.
- Imóveis gravados antes da V25 não têm histórico: a primeira versão comparável é a primeira carga depois dela.
