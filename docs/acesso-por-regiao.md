# Acesso do analista por região (US14)

O gestor escolhe quais cidades cada analista pode consultar. Os outros perfis continuam vendo o Paraná inteiro.

## Regras

- Só restringe quem é **apenas ANALISTA**. Quem tem também GESTOR, OPERADOR_DADOS ou AUDITOR vê tudo.
- Analista **sem nenhuma cidade** na lista vê todas, que era o comportamento antes da US14.
- O município de um imóvel sai do próprio código do CAR: `PR-4102901-...` é de 4102901.
- Imóvel fora da região responde **404**, como se não existisse, para não confirmar que o código existe.
- A mudança de cidades vale na hora. A de perfis vale no próximo login, ou quando o token for renovado.

## Banco

`usuario_municipio` (V26) guarda `usuario_id` (o id do usuário no Keycloak), `cod_ibge` (chave estrangeira para `municipio`), `liberado_por` e `liberado_em`.

## API

| Endpoint | Quem | Para quê |
|---|---|---|
| `POST /api/v1/usuarios` | GESTOR | Cria o usuário no Keycloak com senha provisória, perfis e cidades. Login ou e-mail repetido: 409 |
| `GET /api/v1/usuarios/{id}/municipios` | GESTOR | Cidades do usuário (`todas = true` quando não há restrição) |
| `PUT /api/v1/usuarios/{id}/municipios` | GESTOR | Troca as cidades. `todas = true`, ou lista vazia, libera o Paraná inteiro |
| `GET /api/v1/acesso` | qualquer perfil logado | Cidades que o próprio usuário pode consultar (usado pela tela de Imóveis rurais) |

A lista de usuários (`GET /usuarios`) traz `totalMunicipios`, onde 0 significa todas.

## Onde o filtro é aplicado

O filtro fica centralizado em `AcessoTerritorial`:
- imóveis: lista, página, mapa e detalhe;
- indicadores IRL e IFC do imóvel e o histórico deles;
- resultado de indicador cadastrado por imóvel;
- focos de calor do mapa, pela coluna `foco_calor.cod_ibge`;
- lista de municípios e detalhe do município.

**Quem criar um endpoint novo que lê dados por imóvel ou por município precisa chamar `AcessoTerritorial`.** Os casos pendentes:
- os endpoints `origem` e `comparar` da US09, que ainda estão em PR;
- o dashboard da US12.

## Telas

- **Usuários e perfis:**
  - "Novo usuário" (nome, sobrenome, login, e-mail, senha provisória, perfis e cidades);
  - "Editar acesso" (perfis e cidades);
  - cada analista mostra "Todas as cidades" ou "N cidades".
- **Imóveis rurais:** o analista restrito só vê as cidades dele no filtro. Com uma cidade só, ela já vem selecionada.
