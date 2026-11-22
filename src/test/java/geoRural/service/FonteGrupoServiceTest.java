package geoRural.service;

import geoRural.dto.FonteGrupoRequest;
import geoRural.dto.FonteGrupoResponse;
import geoRural.entity.Fonte;
import geoRural.entity.FonteGrupo;
import geoRural.exception.GrupoDuplicadoException;
import geoRural.exception.RequisicaoInvalidaException;
import geoRural.repository.FonteGrupoRepository;
import geoRural.repository.FonteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.*;

/** Grupos de fontes (só organizam a tela), sem banco. */
class FonteGrupoServiceTest {

    private final FonteGrupoRepository grupos = mock(FonteGrupoRepository.class);
    private final FonteRepository fontes = mock(FonteRepository.class);
    private final FonteGrupoService service = new FonteGrupoServiceImpl(grupos, fontes);
    private Fonte vegn, vegn2, app;

    @BeforeEach
    void preparar() {
        vegn = fonte(1L, null);
        vegn2 = fonte(2L, null);
        app = fonte(3L, 9L);   // já está em outro grupo
        when(fontes.findAll()).thenReturn(List.of(vegn, vegn2, app));
        when(fontes.findAllById(anyIterable())).thenAnswer(c -> {
            List<Long> ids = new java.util.ArrayList<>();
            ((Iterable<Long>) c.getArgument(0)).forEach(ids::add);
            return List.of(vegn, vegn2, app).stream().filter(f -> ids.contains(f.getId())).toList();
        });
        when(grupos.save(any(FonteGrupo.class))).thenAnswer(c -> {
            FonteGrupo g = c.getArgument(0);
            if (g.getId() == null) g.setId(5L);
            return g;
        });
    }

    @Test
    void criaOGrupoEColocaAsFontesDentro() {
        when(grupos.findByNomeIgnoreCase("Vegetação nativa")).thenReturn(Optional.empty());

        FonteGrupoResponse r = service.criar(new FonteGrupoRequest(" Vegetação nativa ", List.of(1L, 2L)));

        assertThat(r.nome()).isEqualTo("Vegetação nativa");
        assertThat(r.fonteIds()).containsExactly(1L, 2L);
        assertThat(vegn.getGrupoId()).isEqualTo(5L);
        assertThat(vegn2.getGrupoId()).isEqualTo(5L);
        assertThat(app.getGrupoId()).isEqualTo(9L);   // não foi pedida: fica onde estava
    }

    @Test
    void nomeRepetidoERecusado() {
        when(grupos.findByNomeIgnoreCase("APP")).thenReturn(Optional.of(grupo(9L, "APP")));

        assertThatThrownBy(() -> service.criar(new FonteGrupoRequest("APP", List.of())))
                .isInstanceOf(GrupoDuplicadoException.class);
        verify(grupos, never()).save(any());
    }

    @Test
    void fonteQueNaoExisteERecusada() {
        when(grupos.findByNomeIgnoreCase("X")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.criar(new FonteGrupoRequest("X", List.of(1L, 99L))))
                .isInstanceOf(RequisicaoInvalidaException.class)
                .hasMessageContaining("99");
    }

    @Test
    void editarTiraAsQueSairamEMoveAsQueEntraram() {
        vegn.setGrupoId(5L);
        vegn2.setGrupoId(5L);
        when(grupos.findById(5L)).thenReturn(Optional.of(grupo(5L, "Vegetação")));
        when(grupos.findByNomeIgnoreCase("Vegetação")).thenReturn(Optional.of(grupo(5L, "Vegetação")));

        FonteGrupoResponse r = service.atualizar(5L, new FonteGrupoRequest("Vegetação", List.of(1L, 3L)));

        assertThat(r.fonteIds()).containsExactly(1L, 3L);
        assertThat(vegn.getGrupoId()).isEqualTo(5L);
        assertThat(vegn2.getGrupoId()).isNull();     // saiu
        assertThat(app.getGrupoId()).isEqualTo(5L);  // veio do grupo 9
    }

    @Test
    void excluirDeixaAsFontesSemGrupo() {
        vegn.setGrupoId(5L);
        when(grupos.findById(5L)).thenReturn(Optional.of(grupo(5L, "Vegetação")));

        service.excluir(5L);

        assertThat(vegn.getGrupoId()).isNull();
        assertThat(app.getGrupoId()).isEqualTo(9L);
        verify(grupos).deleteById(5L);
    }

    @Test
    void listarMostraAsFontesDeCadaGrupo() {
        vegn.setGrupoId(9L);
        when(grupos.findAllByOrderByNomeAsc()).thenReturn(List.of(grupo(9L, "APP"), grupo(5L, "Vazio")));

        List<FonteGrupoResponse> lista = service.listar();

        assertThat(lista.get(0).fonteIds()).containsExactlyInAnyOrder(1L, 3L);
        assertThat(lista.get(1).fonteIds()).isEmpty();
    }

    private static Fonte fonte(Long id, Long grupoId) {
        Fonte f = new Fonte();
        f.setId(id);
        f.setGrupoId(grupoId);
        return f;
    }

    private static FonteGrupo grupo(Long id, String nome) {
        FonteGrupo g = new FonteGrupo();
        g.setId(id);
        g.setNome(nome);
        return g;
    }
}
