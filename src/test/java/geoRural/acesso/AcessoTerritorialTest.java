package geoRural.acesso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.oracle.bmc.objectstorage.ObjectStorage;

import geoRural.dto.FiltroImoveis;
import geoRural.entity.Municipio;
import geoRural.exception.ImovelNaoEncontradoException;
import geoRural.exception.MunicipioNaoEncontradoException;
import geoRural.repository.FocoRepository;
import geoRural.repository.ImovelRuralConsultaRepository;
import geoRural.service.AirflowClient;
import geoRural.service.ImovelRuralService;
import geoRural.service.IndicadorService;
import geoRural.service.MunicipioService;

/**
 * Contra o Oracle do docker-compose, desfeito no fim. Dois imóveis e dois focos: um par em
 * Londrina (4113700, da V5) e outro num município de outro código. O analista só tem Londrina.
 */
@SpringBootTest
@Transactional
class AcessoTerritorialTest {

    private static final String ANALISTA = "teste-acesso-analista";
    private static final String LONDRINA = "PR-4113700-TESTE-ACESSO-1";
    private static final String FORA = "PR-4119905-TESTE-ACESSO-2";

    @MockitoBean
    private ObjectStorage objectStorage;

    @MockitoBean
    private AirflowClient airflow;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ImovelRuralConsultaRepository consulta;

    @Autowired
    private ImovelRuralService imoveis;

    @Autowired
    private IndicadorService indicadores;

    @Autowired
    private MunicipioService municipios;

    @Autowired
    private FocoRepository focos;

    @Autowired
    private AcessoController acessoController;

    @BeforeEach
    void cenario() {
        jdbc.update("INSERT INTO fonte (nome, sigla, orgao, formato) VALUES ('Teste do acesso', 'TESTE_AC', 'Teste', 'SHP')");
        jdbc.update("""
                INSERT INTO arquivo_bruto (fonte_id, chave_objeto, nome_original, tamanho_bytes, hash_sha256)
                SELECT id, 'teste/acesso/car.zip', 'car.zip', 1, 'teste' FROM fonte WHERE sigla = 'TESTE_AC'""");
        jdbc.update("""
                INSERT INTO execucao (arquivo_bruto_id, tipo)
                SELECT id, 'PUBLICACAO' FROM arquivo_bruto WHERE chave_objeto = 'teste/acesso/car.zip'""");
        jdbc.update("""
                INSERT INTO dataset_versao (conjunto, versao, execucao_id, zona)
                SELECT 'teste_acesso', 1, e.id, 'TRATADA'
                  FROM execucao e JOIN arquivo_bruto a ON a.id = e.arquivo_bruto_id
                 WHERE a.chave_objeto = 'teste/acesso/car.zip'""");
        long versao = jdbc.queryForObject("SELECT id FROM dataset_versao WHERE conjunto = 'teste_acesso'", Long.class);

        imovel(LONDRINA, versao, -40.00);
        imovel(FORA, versao, -40.01);
        foco("teste-acesso-foco-1", "4113700", versao, -40.002);
        foco("teste-acesso-foco-2", "4119905", versao, -40.008);
    }

    @AfterEach
    void sair() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void analistaComCidadeSoVeOQueEDaRegiao() {
        liberar(ANALISTA, "4113700");
        logar(ANALISTA, "ANALISTA");

        assertThat(codigosNaPagina()).containsExactly(LONDRINA);
        assertThat(consulta.buscarNaArea(-40.1, -30.1, -39.9, -29.9, filtro(), 100))
                .extracting(ImovelRuralConsultaRepository.ImovelNoMapa::codImovel).containsExactly(LONDRINA);
        assertThat(imoveis.buscarPorCodImovel(LONDRINA).getCodImovel()).isEqualTo(LONDRINA);
        assertThatThrownBy(() -> imoveis.buscarPorCodImovel(FORA)).isInstanceOf(ImovelNaoEncontradoException.class);
        assertThatThrownBy(() -> indicadores.buscarPorImovel(FORA)).isInstanceOf(ImovelNaoEncontradoException.class);
        assertThat(municipios.listar(null)).extracting(Municipio::getCodIbge).containsExactly("4113700");
        assertThatThrownBy(() -> municipios.buscarPorCodIbge("4119905")).isInstanceOf(MunicipioNaoEncontradoException.class);
        assertThat(focosNaArea()).containsExactly("teste-acesso-foco-1");
        assertThat(acessoController.meuAcesso().todas()).isFalse();
    }

    @Test
    void analistaSemCidadeDefinidaVeTudo() {
        logar(ANALISTA, "ANALISTA");

        assertThat(codigosNaPagina()).containsExactlyInAnyOrder(LONDRINA, FORA);
        assertThat(focosNaArea()).hasSize(2);
        assertThat(acessoController.meuAcesso().todas()).isTrue();
    }

    @Test
    void quemTemOutroPerfilAlemDeAnalistaVeTudo() {
        liberar(ANALISTA, "4113700");
        logar(ANALISTA, "ANALISTA", "GESTOR");

        assertThat(codigosNaPagina()).containsExactlyInAnyOrder(LONDRINA, FORA);
        assertThat(imoveis.buscarPorCodImovel(FORA).getCodImovel()).isEqualTo(FORA);
    }

    @Test
    void foraDeUmaRequisicaoNaoHaRestricao() {
        liberar(ANALISTA, "4113700");

        assertThat(codigosNaPagina()).containsExactlyInAnyOrder(LONDRINA, FORA);
    }

    @Test
    void codigoDoMunicipioSaiDoCodigoDoCar() {
        assertThat(AcessoTerritorial.codIbgeDoImovel("PR-4102901-944EADD4")).isEqualTo("4102901");
        assertThat(AcessoTerritorial.codIbgeDoImovel("sem-padrao")).isNull();
    }

    private List<String> codigosNaPagina() {
        return consulta.buscarPagina(filtro(), 1, 50).stream().map(i -> i.codImovel()).toList();
    }

    private List<String> focosNaArea() {
        return focos.buscarNaArea(-40.1, -30.1, -39.9, -29.9, "TESTE-AC",
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2027, 1, 1, 0, 0), 100)
                .stream().map(FocoRepository.FocoNoMapa::idInpe).sorted().toList();
    }

    private static FiltroImoveis filtro() {
        return new FiltroImoveis(null, "TESTE-ACESSO", null);
    }

    private void liberar(String usuario, String codIbge) {
        jdbc.update("INSERT INTO usuario_municipio (usuario_id, cod_ibge) VALUES (?, ?)", usuario, codIbge);
    }

    private static void logar(String usuario, String... perfis) {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "none"), Map.of("sub", usuario));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                Arrays.stream(perfis).map(p -> new SimpleGrantedAuthority("ROLE_" + p)).toList()));
    }

    private void imovel(String cod, long versao, double lon) {
        jdbc.update("INSERT INTO imovel_rural (cod_imovel, municipio, estado, area_ha, dataset_versao_id, geometria)"
                + " VALUES (?, 'Teste', 'PR', 1, ?, " + quadrado(lon) + ")", cod, versao);
    }

    private void foco(String id, String codIbge, long versao, double lon) {
        jdbc.update("""
                INSERT INTO foco_calor (id_inpe, data_hora, satelite, cod_ibge, geometria, dataset_versao_id)
                VALUES (?, TIMESTAMP '2026-06-01 12:00:00', 'TESTE-AC', ?,
                        SDO_GEOMETRY(2001, 4326, SDO_POINT_TYPE(?, -29.995, NULL), NULL, NULL), ?)""",
                id, codIbge, lon, versao);
    }

    // Em alto-mar, longe de qualquer imóvel de verdade; anel anti-horário, como o Oracle exige.
    private static String quadrado(double lon) {
        return String.format(java.util.Locale.ROOT,
                "SDO_GEOMETRY(2003, 4326, NULL, SDO_ELEM_INFO_ARRAY(1, 1003, 1), "
                        + "SDO_ORDINATE_ARRAY(%f, -30, %f, -30, %f, -29.99, %f, -29.99, %f, -30))",
                lon, lon + 0.005, lon + 0.005, lon, lon);
    }
}
