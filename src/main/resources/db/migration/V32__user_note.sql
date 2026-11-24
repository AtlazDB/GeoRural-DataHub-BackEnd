--------------------------------------------------------------------------------
-- US13.T26: anotações do usuário na tela inicial ("Minhas anotações")
--------------------------------------------------------------------------------
-- Uma anotação por usuário, que ele mesmo escreve e lê. Aparece para o Analista
-- (no lugar do dashboard) e para o Auditor (abaixo do dashboard).
--
-- user_id é o id (sub) do usuário no Keycloak: os usuários não ficam no Oracle,
-- então não há FK para ele (mesmo esquema da usuario_municipio, V27).
--------------------------------------------------------------------------------

CREATE TABLE user_note (
  user_id     VARCHAR2(36 CHAR)   NOT NULL,
  content     CLOB,
  updated_at  TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL,
  CONSTRAINT pk_user_note PRIMARY KEY (user_id)
);

COMMENT ON TABLE user_note IS 'Anotações livres de cada usuário na tela inicial (US13.T26)';
COMMENT ON COLUMN user_note.user_id IS 'id (sub) do usuário no Keycloak';
COMMENT ON COLUMN user_note.content IS 'Texto da anotação, como o usuário digitou';
