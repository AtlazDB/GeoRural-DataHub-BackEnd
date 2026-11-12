package geoRural.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import geoRural.dto.ComparacaoImovelResponse;
import geoRural.dto.IndicadorCadastradoResultado;
import geoRural.dto.IndicadorResponse;
import geoRural.dto.OrigemImovelResponse;
import geoRural.dto.VersaoDadosResponse;
import geoRural.dto.VersaoFonteResponse;
import geoRural.dto.VersaoImovelResponse;
import geoRural.exception.FonteNaoEncontradaException;
import geoRural.exception.ImovelNaoEncontradoException;
import geoRural.exception.RequisicaoInvalidaException;
import geoRural.repository.FonteRepository;
import geoRural.repository.IndicadorRepository;
import geoRural.repository.VersaoRepository;
import geoRural.repository.VersaoRepository.Retrato;

@Service
@Transactional(readOnly = true)
public class VersaoService {

    private final VersaoRepository versoes;
    private final IndicadorRepository indicadores;
    private final FonteRepository fontes;

    public VersaoService(VersaoRepository versoes, IndicadorRepository indicadores, FonteRepository fontes) {
        this.versoes = versoes;
        this.indicadores = indicadores;
        this.fontes = fontes;
    }

    public List<VersaoFonteResponse> versoesDaFonte(Long fonteId) {
        if (!fontes.existsById(fonteId)) {
            throw new FonteNaoEncontradaException(fonteId);
        }
        return versoes.versoesDaFonte(fonteId);
    }

    public OrigemImovelResponse origem(String codImovel) {
        List<VersaoImovelResponse> lista = versoesDoImovel(codImovel);
        VersaoImovelResponse atual = lista.stream().filter(v -> "ATUAL".equals(v.situacao())).findFirst().orElse(null);
        return new OrigemImovelResponse(
                codImovel,
                atual,
                lista,
                indicadores.buscarAtuais(codImovel),
                versoes.indicadoresCadastrados(codImovel, null));
    }

    /** Sem de/para, compara a versão mais nova com a anterior. */
    public ComparacaoImovelResponse comparar(String codImovel, Long de, Long para) {
        List<VersaoImovelResponse> lista = versoesDoImovel(codImovel);
        VersaoImovelResponse versaoPara = para == null ? lista.get(0) : buscar(lista, para, "para");
        VersaoImovelResponse versaoDe;
        if (de != null) {
            versaoDe = buscar(lista, de, "de");
        } else {
            int i = lista.indexOf(versaoPara);
            versaoDe = i + 1 < lista.size() ? lista.get(i + 1) : null;
        }
        if (versaoDe == null) {
            return new ComparacaoImovelResponse(codImovel, null, versaoPara, List.of(), false, null,
                    retrato(codImovel, versaoPara).areaGeometriaHa(), List.of());
        }

        Retrato antes = retrato(codImovel, versaoDe);
        Retrato depois = retrato(codImovel, versaoPara);
        List<ComparacaoImovelResponse.Campo> campos = new ArrayList<>();
        for (Map.Entry<String, Object> campo : antes.campos().entrySet()) {
            Object valorDepois = depois.campos().get(campo.getKey());
            campos.add(new ComparacaoImovelResponse.Campo(campo.getKey(), campo.getValue(), valorDepois,
                    !iguais(campo.getValue(), valorDepois)));
        }
        boolean geometriaMudou = !versoes.geometriaIgual(codImovel, versaoDe.datasetVersaoId(), versaoPara.datasetVersaoId());
        boolean imovelMudou = geometriaMudou || campos.stream().anyMatch(ComparacaoImovelResponse.Campo::mudou);

        List<ComparacaoImovelResponse.Indicador> comparados = new ArrayList<>();
        compararIndicadores(codImovel, versaoDe, versaoPara, imovelMudou, comparados);
        compararCadastrados(codImovel, versaoDe, versaoPara, imovelMudou, comparados);

        return new ComparacaoImovelResponse(codImovel, versaoDe, versaoPara, campos, geometriaMudou,
                antes.areaGeometriaHa(), depois.areaGeometriaHa(), comparados);
    }

    private void compararIndicadores(String codImovel, VersaoImovelResponse de, VersaoImovelResponse para,
                                     boolean imovelMudou, List<ComparacaoImovelResponse.Indicador> saida) {
        Map<String, IndicadorResponse> antes = porIndicador(indicadores.buscarPorVersaoImovel(codImovel, de.datasetVersaoId()));
        Map<String, IndicadorResponse> depois = porIndicador(indicadores.buscarPorVersaoImovel(codImovel, para.datasetVersaoId()));
        for (String nome : unir(antes.keySet(), depois.keySet())) {
            IndicadorResponse a = antes.get(nome);
            IndicadorResponse d = depois.get(nome);
            String regraDe = a == null ? null : a.regra().codigo() + " v" + a.regra().versao();
            String regraPara = d == null ? null : d.regra().codigo() + " v" + d.regra().versao();
            boolean insumoMudou = a != null && d != null && !mesmaVersao(a.versaoInsumo(), d.versaoInsumo());
            boolean dado = imovelMudou || insumoMudou;
            boolean regra = a != null && d != null && !Objects.equals(regraDe, regraPara);
            IndicadorResponse ref = d != null ? d : a;
            saida.add(new ComparacaoImovelResponse.Indicador(
                    nome,
                    ref.regra().nome(),
                    ref.unidade(),
                    a == null ? null : a.valor(),
                    a == null ? null : a.status(),
                    d == null ? null : d.valor(),
                    d == null ? null : d.status(),
                    regraDe,
                    regraPara,
                    dado,
                    regra,
                    motivo(a == null || d == null, dado, regra)));
        }
    }

    // A regra dos indicadores cadastrados não tem versão: a diferença só pode vir do dado.
    private void compararCadastrados(String codImovel, VersaoImovelResponse de, VersaoImovelResponse para,
                                     boolean imovelMudou, List<ComparacaoImovelResponse.Indicador> saida) {
        Map<String, IndicadorCadastradoResultado> antes = new LinkedHashMap<>();
        versoes.indicadoresCadastrados(codImovel, de.datasetVersaoId()).forEach(r -> antes.put(r.codigo(), r));
        Map<String, IndicadorCadastradoResultado> depois = new LinkedHashMap<>();
        versoes.indicadoresCadastrados(codImovel, para.datasetVersaoId()).forEach(r -> depois.put(r.codigo(), r));
        for (String codigo : unir(antes.keySet(), depois.keySet())) {
            IndicadorCadastradoResultado a = antes.get(codigo);
            IndicadorCadastradoResultado d = depois.get(codigo);
            boolean dado = imovelMudou || (a != null && d != null && !Objects.equals(a.versaoInsumoId(), d.versaoInsumoId()));
            IndicadorCadastradoResultado ref = d != null ? d : a;
            saida.add(new ComparacaoImovelResponse.Indicador(
                    codigo,
                    ref.nome(),
                    ref.unidade(),
                    a == null ? null : a.valor(),
                    a == null ? null : a.status(),
                    d == null ? null : d.valor(),
                    d == null ? null : d.status(),
                    null,
                    null,
                    dado,
                    false,
                    motivo(a == null || d == null, dado, false)));
        }
    }

    private List<VersaoImovelResponse> versoesDoImovel(String codImovel) {
        if (!versoes.imovelExiste(codImovel)) {
            throw new ImovelNaoEncontradoException(codImovel);
        }
        return versoes.versoesDoImovel(codImovel);
    }

    private Retrato retrato(String codImovel, VersaoImovelResponse versao) {
        return versoes.retrato(codImovel, versao.datasetVersaoId())
                .orElseThrow(() -> new ImovelNaoEncontradoException(codImovel));
    }

    private static VersaoImovelResponse buscar(List<VersaoImovelResponse> lista, long id, String parametro) {
        return lista.stream().filter(v -> v.datasetVersaoId() == id).findFirst()
                .orElseThrow(() -> new RequisicaoInvalidaException(
                        "O imóvel não tem a versão " + id + " (parâmetro " + parametro + ").", List.of(parametro)));
    }

    private static Map<String, IndicadorResponse> porIndicador(List<IndicadorResponse> lista) {
        Map<String, IndicadorResponse> mapa = new LinkedHashMap<>();
        lista.forEach(i -> mapa.put(i.indicador(), i));
        return mapa;
    }

    private static List<String> unir(Iterable<String> a, Iterable<String> b) {
        List<String> todos = new ArrayList<>();
        a.forEach(todos::add);
        b.forEach(k -> {
            if (!todos.contains(k)) {
                todos.add(k);
            }
        });
        return todos;
    }

    private static boolean mesmaVersao(VersaoDadosResponse a, VersaoDadosResponse b) {
        if (a == null || b == null) {
            return a == b;
        }
        return Objects.equals(a.conjunto(), b.conjunto()) && a.versao() == b.versao();
    }

    private static String motivo(boolean faltaCalculo, boolean dado, boolean regra) {
        if (faltaCalculo) {
            return "SEM_CALCULO";
        }
        if (dado && regra) {
            return "DADO_E_REGRA";
        }
        return dado ? "DADO" : regra ? "REGRA" : "NENHUM";
    }

    private static boolean iguais(Object a, Object b) {
        if (a instanceof BigDecimal x && b instanceof BigDecimal y) {
            return x.compareTo(y) == 0;
        }
        if (a instanceof Number x && b instanceof Number y) {
            return new BigDecimal(x.toString()).compareTo(new BigDecimal(y.toString())) == 0;
        }
        return Objects.equals(a, b);
    }
}
