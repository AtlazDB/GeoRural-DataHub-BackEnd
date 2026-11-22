package geoRural.service;

import geoRural.entity.Fonte;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sem Spring e sem banco: quando a coleta automática de uma fonte vence. */
class AgendaColetaTest {

    private static final ZoneOffset BRASILIA = ZoneOffset.ofHours(-3);
    private static final OffsetDateTime ULTIMA = OffsetDateTime.of(2026, 11, 10, 3, 0, 0, 0, BRASILIA);

    private static Fonte fonte(String periodicidade, OffsetDateTime ultimaColeta) {
        Fonte fonte = new Fonte();
        fonte.setSigla("FOCOS-IAT");
        fonte.setModalidade("LINK");
        fonte.setUrlOrigem("https://geopr.iat.pr.gov.br/server/rest/services/Focos/FeatureServer/0/query");
        fonte.setPeriodicidade(periodicidade);
        ReflectionTestUtils.setField(fonte, "ultimaColetaEm", ultimaColeta);
        ReflectionTestUtils.setField(fonte, "ultimaColetaMensagem", "Coleta concluída");
        return fonte;
    }

    @Test
    void diariaVenceVinteEQuatroHorasDepoisDaUltima() {
        Fonte diaria = fonte("diária", ULTIMA);

        assertThat(AgendaColeta.vencida(diaria, ULTIMA.plusHours(23).plusMinutes(59))).isFalse();
        assertThat(AgendaColeta.vencida(diaria, ULTIMA.plusDays(1))).isTrue();
    }

    @Test
    void mensalVenceNoMesmoDiaDoMesSeguinte() {
        Fonte mensal = fonte("mensal", ULTIMA);

        assertThat(AgendaColeta.proxima(mensal)).contains(OffsetDateTime.of(2026, 12, 10, 3, 0, 0, 0, BRASILIA));
        assertThat(AgendaColeta.vencida(mensal, ULTIMA.plusDays(29))).isFalse();
        assertThat(AgendaColeta.vencida(mensal, ULTIMA.plusMonths(1))).isTrue();
    }

    @Test
    void periodicidadeSemAcentoEComMaiusculaVale() {
        assertThat(AgendaColeta.vencida(fonte("  DIARIA ", ULTIMA), ULTIMA.plusDays(1))).isTrue();
        assertThat(AgendaColeta.vencida(fonte("Semanal", ULTIMA), ULTIMA.plusWeeks(1))).isTrue();
    }

    @Test
    void eventualOuTextoDesconhecidoNuncaVence() {
        assertThat(AgendaColeta.proxima(fonte("eventual", ULTIMA))).isEmpty();
        assertThat(AgendaColeta.vencida(fonte("quando der", ULTIMA), ULTIMA.plusYears(5))).isFalse();
    }

    @Test
    void primeiraColetaEhManual() {
        Fonte nuncaColetou = fonte("diária", null);
        ReflectionTestUtils.setField(nuncaColetou, "ultimaColetaMensagem", null);

        assertThat(AgendaColeta.vencida(nuncaColetou, ULTIMA.plusYears(1))).isFalse();
    }

    @Test
    void coletaPedidaOuEmAndamentoNaoDisparaDeNovo() {
        Fonte pedida = fonte("diária", ULTIMA);
        pedida.setColetaSolicitadaEm(ULTIMA.plusDays(2));
        assertThat(AgendaColeta.vencida(pedida, ULTIMA.plusDays(3))).isFalse();

        // A DAG zera ultimaColetaEm ao reservar e escreve o andamento na mensagem.
        Fonte emAndamento = fonte("diária", null);
        ReflectionTestUtils.setField(emAndamento, "ultimaColetaMensagem", "Coletando: 1.000 de 5.000 registros.");
        assertThat(AgendaColeta.vencida(emAndamento, ULTIMA.plusDays(3))).isFalse();
    }

    @Test
    void falhaEsperaOProximoPeriodo() {
        // No fim com falha a DAG também grava ultimaColetaEm: nada de tentar de novo a cada hora.
        Fonte falhou = fonte("mensal", ULTIMA);
        ReflectionTestUtils.setField(falhou, "ultimaColetaMensagem", "A coleta falhou: HTTP 500");

        assertThat(AgendaColeta.vencida(falhou, ULTIMA.plusHours(1))).isFalse();
        assertThat(AgendaColeta.vencida(falhou, ULTIMA.plusMonths(1))).isTrue();
    }

    @Test
    void desativadaUploadOuSemLinkNaoEntram() {
        Fonte desativada = fonte("diária", ULTIMA);
        desativada.setAtiva(false);
        Fonte upload = fonte("diária", ULTIMA);
        upload.setModalidade("MANUAL");
        Fonte semLink = fonte("diária", ULTIMA);
        semLink.setUrlOrigem(" ");

        OffsetDateTime depois = ULTIMA.plusDays(2);
        assertThat(AgendaColeta.vencida(desativada, depois)).isFalse();
        assertThat(AgendaColeta.vencida(upload, depois)).isFalse();
        assertThat(AgendaColeta.vencida(semLink, depois)).isFalse();
    }

    @Test
    void cronDeCincoCamposValeNoHorarioDeBrasiliaEPassaNaFrenteDaPeriodicidade() {
        Fonte comCron = fonte("anual", ULTIMA);
        comCron.setCron("0 3 1 * *"); // todo dia 1, às 3 h

        assertThat(AgendaColeta.proxima(comCron)).contains(OffsetDateTime.of(2026, 12, 1, 3, 0, 0, 0, BRASILIA));
        assertThat(AgendaColeta.vencida(comCron, OffsetDateTime.of(2026, 12, 1, 2, 59, 0, 0, BRASILIA))).isFalse();
        assertThat(AgendaColeta.vencida(comCron, OffsetDateTime.of(2026, 12, 1, 3, 0, 0, 0, BRASILIA))).isTrue();
    }

    @Test
    void cronDeSeisCamposTambemVale() {
        Fonte comCron = fonte("eventual", ULTIMA);
        comCron.setCron("0 0 6 * * MON");

        assertThat(AgendaColeta.proxima(comCron)).contains(OffsetDateTime.of(2026, 11, 16, 6, 0, 0, 0, BRASILIA));
    }

    @Test
    void cronInvalidoLancaErro() {
        assertThatThrownBy(() -> AgendaColeta.cron("todo dia")).isInstanceOf(IllegalArgumentException.class);
    }
}
