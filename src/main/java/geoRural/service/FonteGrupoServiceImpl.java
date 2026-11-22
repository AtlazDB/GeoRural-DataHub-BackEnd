package geoRural.service;

import geoRural.dto.FonteGrupoRequest;
import geoRural.dto.FonteGrupoResponse;
import geoRural.entity.Fonte;
import geoRural.entity.FonteGrupo;
import geoRural.exception.GrupoDuplicadoException;
import geoRural.exception.RequisicaoInvalidaException;
import geoRural.exception.ResourceNotFoundException;
import geoRural.repository.FonteGrupoRepository;
import geoRural.repository.FonteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FonteGrupoServiceImpl implements FonteGrupoService {

    private final FonteGrupoRepository grupos;
    private final FonteRepository fontes;

    public FonteGrupoServiceImpl(FonteGrupoRepository grupos, FonteRepository fontes) {
        this.grupos = grupos;
        this.fontes = fontes;
    }

    @Override
    public List<FonteGrupoResponse> listar() {
        Map<Long, List<Long>> membros = fontes.findAll().stream()
                .filter(f -> f.getGrupoId() != null)
                .collect(Collectors.groupingBy(Fonte::getGrupoId,
                        Collectors.mapping(Fonte::getId, Collectors.toList())));
        return grupos.findAllByOrderByNomeAsc().stream()
                .map(g -> new FonteGrupoResponse(g.getId(), g.getNome(), membros.getOrDefault(g.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional
    public FonteGrupoResponse criar(FonteGrupoRequest pedido) {
        String nome = pedido.nome().trim();
        if (grupos.findByNomeIgnoreCase(nome).isPresent()) {
            throw new GrupoDuplicadoException(nome);
        }
        FonteGrupo grupo = new FonteGrupo();
        grupo.setNome(nome);
        grupo = grupos.save(grupo);
        return new FonteGrupoResponse(grupo.getId(), grupo.getNome(), definirMembros(grupo.getId(), pedido.fonteIds()));
    }

    @Override
    @Transactional
    public FonteGrupoResponse atualizar(Long id, FonteGrupoRequest pedido) {
        FonteGrupo grupo = buscar(id);
        String nome = pedido.nome().trim();
        grupos.findByNomeIgnoreCase(nome)
                .filter(outro -> !outro.getId().equals(id))
                .ifPresent(outro -> { throw new GrupoDuplicadoException(nome); });
        grupo.setNome(nome);
        grupos.save(grupo);
        return new FonteGrupoResponse(id, nome, definirMembros(id, pedido.fonteIds()));
    }

    @Override
    @Transactional
    public void excluir(Long id) {
        buscar(id);
        definirMembros(id, List.of());   // as fontes ficam sem grupo
        grupos.deleteById(id);
    }

    private FonteGrupo buscar(Long id) {
        return grupos.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Grupo " + id + " não encontrado."));
    }

    /** Deixa no grupo exatamente as fontes pedidas: as que saíram ficam sem grupo. */
    private List<Long> definirMembros(Long grupoId, List<Long> pedidas) {
        Set<Long> ids = new LinkedHashSet<>();
        if (pedidas != null) {
            pedidas.stream().filter(Objects::nonNull).forEach(ids::add);
        }
        List<Fonte> escolhidas = fontes.findAllById(ids);
        if (escolhidas.size() != ids.size()) {
            Set<Long> achadas = escolhidas.stream().map(Fonte::getId).collect(Collectors.toSet());
            ids.removeAll(achadas);
            throw new RequisicaoInvalidaException("Fonte(s) não encontrada(s): " + ids + ".", List.of("fonteIds"));
        }
        for (Fonte f : fontes.findAll()) {
            boolean dentro = ids.contains(f.getId());
            if (dentro && !grupoId.equals(f.getGrupoId())) {
                f.setGrupoId(grupoId);
                fontes.save(f);
            } else if (!dentro && grupoId.equals(f.getGrupoId())) {
                f.setGrupoId(null);
                fontes.save(f);
            }
        }
        return List.copyOf(ids);
    }
}
