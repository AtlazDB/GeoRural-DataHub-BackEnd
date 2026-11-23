package geoRural.service;

import geoRural.dto.CamadaResponse;
import geoRural.dto.MapaImoveisResponse;
import geoRural.dto.MapaImoveisResponse.Feature;
import geoRural.exception.RequisicaoInvalidaException;
import geoRural.exception.ResourceNotFoundException;
import geoRural.repository.CamadaRepository;
import geoRural.repository.CamadaRepository.FonteInsumo;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class CamadaServiceImpl implements CamadaService {

    static final String RESERVA_LEGAL = "reserva-legal";
    static final String PREFIXO_FONTE = "fonte-";
    static final int LIMITE_MAXIMO = 3000;
    /**
     * Largura máxima da área pedida, em graus (~200 km). O front só pede a partir do zoom 11
     * (~1,3° numa tela larga); mais que isso traria polígonos demais (a vegetação nativa tem
     * feições de vários MB).
     */
    static final double LARGURA_MAXIMA = 2.0;

    /** 6 casas decimais em graus ≈ 10 cm: mais que isso só aumenta o JSON. */
    private static final int CASAS_DECIMAIS = 6;
    /** Largura aproximada da tela em pixels: a simplificação descarta detalhes menores que 1 pixel. */
    private static final double PIXELS_NA_LARGURA = 2000;

    private final CamadaRepository repository;

    public CamadaServiceImpl(CamadaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<CamadaResponse> listar() {
        List<CamadaResponse> camadas = new ArrayList<>();
        if (repository.temReservaLegal()) {
            camadas.add(new CamadaResponse(RESERVA_LEGAL, "Reserva Legal (CAR)", "RESERVA_LEGAL", null));
        }
        for (FonteInsumo fonte : repository.fontesDoInsumo()) {
            camadas.add(new CamadaResponse(PREFIXO_FONTE + fonte.id(), fonte.nome(), "INSUMO", fonte.sigla()));
        }
        return camadas;
    }

    @Override
    public MapaImoveisResponse mapa(String id, Double minLon, Double minLat, Double maxLon, Double maxLat,
                                    int limite) {
        validarArea(minLon, minLat, maxLon, maxLat, limite);
        double tolerancia = (maxLon - minLon) / PIXELS_NA_LARGURA;

        // Pede um a mais que o limite só para saber se a área tinha mais do que cabe
        if (RESERVA_LEGAL.equals(id)) {
            return montar(repository.reservaLegalNaArea(minLon, minLat, maxLon, maxLat, limite + 1), limite,
                    tolerancia, CamadaRepository.ReservaNoMapa::wkb, CamadaServiceImpl::propriedades);
        }
        FonteInsumo fonte = fonteDoId(id);
        return montar(repository.insumoNaArea(fonte.sigla(), minLon, minLat, maxLon, maxLat, limite + 1), limite,
                tolerancia, CamadaRepository.InsumoNoMapa::wkb, CamadaServiceImpl::propriedades);
    }

    private FonteInsumo fonteDoId(String id) {
        if (id != null && id.startsWith(PREFIXO_FONTE)) {
            try {
                long fonteId = Long.parseLong(id.substring(PREFIXO_FONTE.length()));
                return repository.fonteDoInsumo(fonteId)
                        .orElseThrow(() -> new ResourceNotFoundException("Camada não encontrada: " + id));
            } catch (NumberFormatException e) {
                // cai no 404 abaixo
            }
        }
        throw new ResourceNotFoundException("Camada não encontrada: " + id);
    }

    private static <T> MapaImoveisResponse montar(List<T> linhas, int limite, double tolerancia,
                                                  Function<T, byte[]> wkb,
                                                  Function<T, Map<String, Object>> propriedades) {
        boolean truncado = linhas.size() > limite;
        if (truncado) {
            linhas = linhas.subList(0, limite);
        }
        WKBReader leitor = new WKBReader();
        GeoJsonWriter escritor = new GeoJsonWriter(CASAS_DECIMAIS);
        escritor.setEncodeCRS(false);

        List<Feature> features = new ArrayList<>(linhas.size());
        for (T linha : linhas) {
            Geometry geometria = simplificar(ler(leitor, wkb.apply(linha)), tolerancia);
            features.add(Feature.de(propriedades.apply(linha), escritor.write(geometria)));
        }
        return MapaImoveisResponse.de(features, truncado, limite);
    }

    private static Map<String, Object> propriedades(CamadaRepository.ReservaNoMapa r) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("codImovel", r.codImovel());
        p.put("areaHa", r.areaHa());
        p.put("situacao", r.situacao());
        return p;
    }

    private static Map<String, Object> propriedades(CamadaRepository.InsumoNoMapa i) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("categoria", i.categoria());
        p.put("valor", i.valor());
        p.put("dataReferencia", i.dataReferencia() == null ? null : i.dataReferencia().toString());
        return p;
    }

    private static void validarArea(Double minLon, Double minLat, Double maxLon, Double maxLat, int limite) {
        if (minLon == null || minLat == null || maxLon == null || maxLat == null) {
            throw new RequisicaoInvalidaException("Informe a área do mapa: minLon, minLat, maxLon e maxLat.",
                    List.of("minLon", "minLat", "maxLon", "maxLat"));
        }
        if (minLon >= maxLon || minLat >= maxLat
                || minLon < -180 || maxLon > 180 || minLat < -90 || maxLat > 90) {
            throw new RequisicaoInvalidaException("Área do mapa inválida.",
                    List.of("minLon", "minLat", "maxLon", "maxLat"));
        }
        if (maxLon - minLon > LARGURA_MAXIMA || maxLat - minLat > LARGURA_MAXIMA) {
            throw new RequisicaoInvalidaException("Área grande demais para as camadas: aproxime o zoom.",
                    List.of("minLon", "minLat", "maxLon", "maxLat"));
        }
        if (limite < 1 || limite > LIMITE_MAXIMO) {
            throw new RequisicaoInvalidaException("limite deve estar entre 1 e " + LIMITE_MAXIMO + ".",
                    List.of("limite"));
        }
    }

    private static Geometry ler(WKBReader leitor, byte[] wkb) {
        try {
            return leitor.read(wkb);
        } catch (ParseException e) {
            throw new IllegalStateException("Geometria ilegível numa camada do mapa", e);
        }
    }

    /** Com pouco zoom, detalhes menores que um pixel não aparecem e só pesam no JSON. */
    private static Geometry simplificar(Geometry geometria, double tolerancia) {
        Geometry simplificada = DouglasPeuckerSimplifier.simplify(geometria, tolerancia);
        // Feição pequena demais para o zoom pode sumir na simplificação: aí vai a original
        return simplificada.isEmpty() ? geometria : simplificada;
    }
}
