--------------------------------------------------------------------------------
-- US14.T01: municípios que cada usuário pode consultar
--------------------------------------------------------------------------------
-- Vale só para o perfil ANALISTA. Analista sem nenhuma linha aqui vê todos os
-- municípios (o comportamento de antes desta migration); com linhas, só os
-- municípios listados. Os outros perfis veem tudo.
--
-- usuario_id é o id (sub) do usuário no Keycloak: os usuários não ficam no
-- Oracle, então não há FK para ele.
--------------------------------------------------------------------------------

CREATE TABLE usuario_municipio (
  usuario_id    VARCHAR2(36 CHAR)   NOT NULL,
  cod_ibge      VARCHAR2(7 CHAR)    NOT NULL,
  liberado_por  VARCHAR2(100 CHAR),
  liberado_em   TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_usuario_municipio PRIMARY KEY (usuario_id, cod_ibge),
  CONSTRAINT fk_usuario_municipio_ibge FOREIGN KEY (cod_ibge)
    REFERENCES municipio (cod_ibge) ON DELETE CASCADE
);

CREATE INDEX ix_usuario_municipio_ibge ON usuario_municipio (cod_ibge);

COMMENT ON TABLE usuario_municipio IS 'Municípios liberados para cada analista (US14). Sem linhas: acesso a todos';
COMMENT ON COLUMN usuario_municipio.usuario_id IS 'id (sub) do usuário no Keycloak';
COMMENT ON COLUMN usuario_municipio.liberado_por IS 'username do gestor que liberou';
