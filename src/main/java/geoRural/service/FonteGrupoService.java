package geoRural.service;

import geoRural.dto.FonteGrupoRequest;
import geoRural.dto.FonteGrupoResponse;

import java.util.List;

/** Grupos de fontes: só organizam a tela (V31). Não mudam processamento nem indicadores. */
public interface FonteGrupoService {

    List<FonteGrupoResponse> listar();

    FonteGrupoResponse criar(FonteGrupoRequest pedido);

    /** Renomeia e troca as fontes do grupo (as que saíram ficam sem grupo). */
    FonteGrupoResponse atualizar(Long id, FonteGrupoRequest pedido);

    /** Apaga o grupo; as fontes dele ficam sem grupo, nada é apagado. */
    void excluir(Long id);
}
