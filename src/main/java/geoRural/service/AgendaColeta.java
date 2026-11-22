package geoRural.service;

import geoRural.entity.Fonte;
import org.springframework.scheduling.support.CronExpression;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Quando a próxima coleta automática de uma fonte por link vence. Só a regra, sem banco e sem Spring:
 * quem pergunta é o {@link ColetaAutomatica}.
 *
 * <ul>
 *   <li>Com {@code cron} preenchido, vale o cron (5 campos, como no Linux, ou 6, com segundos), no
 *       horário de Brasília. Sem ele, vale a {@code periodicidade}: diária, semanal, mensal...
 *       "eventual" (ou qualquer outro texto) não tem coleta automática.</li>
 *   <li>A conta parte da última coleta ({@code ultimaColetaEm}), que a DAG preenche no fim com
 *       sucesso <b>ou falha</b>: uma fonte que falhou só tenta de novo no próximo período, e não a
 *       cada verificação. Cada disparo de DAG tem custo para o cliente.</li>
 *   <li>A primeira coleta é sempre manual (Executar): fonte que nunca coletou não entra.</li>
 *   <li>Fonte desativada, sem link ou com coleta pedida ou em andamento também não entra.</li>
 * </ul>
 */
public final class AgendaColeta {

    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private static final Map<String, Period> PERIODOS = Map.of(
            "diaria", Period.ofDays(1),
            "semanal", Period.ofWeeks(1),
            "quinzenal", Period.ofDays(15),
            "mensal", Period.ofMonths(1),
            "bimestral", Period.ofMonths(2),
            "trimestral", Period.ofMonths(3),
            "semestral", Period.ofMonths(6),
            "anual", Period.ofYears(1));

    private AgendaColeta() {
    }

    /** true se a fonte deve ser coletada agora. */
    public static boolean vencida(Fonte fonte, OffsetDateTime agora) {
        if (!fonte.isAtiva() || !"LINK".equalsIgnoreCase(fonte.getModalidade())) return false;
        if (fonte.getUrlOrigem() == null || fonte.getUrlOrigem().isBlank()) return false;
        if (coletando(fonte)) return false;
        return proxima(fonte).map(proxima -> !agora.isBefore(proxima)).orElse(false);
    }

    /** Quando vence a próxima coleta, ou vazio se a fonte não tem coleta automática. */
    public static Optional<OffsetDateTime> proxima(Fonte fonte) {
        OffsetDateTime ultima = fonte.getUltimaColetaEm();
        if (ultima == null) return Optional.empty();

        if (fonte.getCron() != null && !fonte.getCron().isBlank()) {
            ZonedDateTime proxima = cron(fonte.getCron()).next(ultima.atZoneSameInstant(FUSO));
            return Optional.ofNullable(proxima).map(ZonedDateTime::toOffsetDateTime);
        }
        Period periodo = PERIODOS.get(normalizar(fonte.getPeriodicidade()));
        return Optional.ofNullable(periodo)
                .map(p -> ultima.atZoneSameInstant(FUSO).plus(p).toOffsetDateTime());
    }

    /** Valida o cron sem calcular nada; lança IllegalArgumentException se for inválido. */
    public static CronExpression cron(String expressao) {
        String limpa = expressao.trim().replaceAll("\\s+", " ");
        // O CronExpression do Spring tem 6 campos (com segundos); o cron do Linux, 5.
        return CronExpression.parse(limpa.split(" ").length == 5 ? "0 " + limpa : limpa);
    }

    /** Mesma regra do campo "coletando" da tela (FonteResponse). */
    static boolean coletando(Fonte fonte) {
        return fonte.getColetaSolicitadaEm() != null
                || (fonte.getUltimaColetaMensagem() != null && fonte.getUltimaColetaEm() == null);
    }

    private static String normalizar(String texto) {
        if (texto == null) return "";
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}
