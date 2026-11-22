package geoRural.service;

import geoRural.entity.Fonte;
import geoRural.exception.AirflowIndisponivelException;
import geoRural.repository.FonteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Coleta automática das fontes por link, conforme a periodicidade (ou o cron) de cada uma.
 *
 * A verificação roda aqui no back, e não numa DAG agendada, de propósito: cada disparo de DAG tem
 * custo para o cliente. Olhar quais fontes venceram é só uma consulta no banco; o Airflow só é
 * chamado para a fonte que venceu, pelo mesmo caminho do botão Executar
 * ({@link ColetaLinkService#executar}). Uma fonte mensal gera um disparo por mês.
 *
 * Liga com {@code app.coleta-automatica.habilitada=true} (desligada por padrão, para o back no PC
 * de quem desenvolve não sair coletando dos serviços públicos). A regra de vencimento está em
 * {@link AgendaColeta}.
 */
@Component
@ConditionalOnProperty(name = "app.coleta-automatica.habilitada", havingValue = "true")
public class ColetaAutomatica {

    private static final Logger log = LoggerFactory.getLogger(ColetaAutomatica.class);

    private final FonteRepository fonteRepository;
    private final ColetaLinkService coletaLinkService;
    private final Clock relogio;

    // Um construtor só: o Spring usa este sem precisar de anotação. O relógio vem do
    // AgendamentoConfig (fuso de Brasília); os testes passam um relógio fixo.
    public ColetaAutomatica(FonteRepository fonteRepository, ColetaLinkService coletaLinkService, Clock relogio) {
        this.fonteRepository = fonteRepository;
        this.coletaLinkService = coletaLinkService;
        this.relogio = relogio;
    }

    /** Dispara a coleta das fontes vencidas. Devolve quantas foram disparadas. */
    @Scheduled(cron = "${app.coleta-automatica.verificacao:0 0 * * * *}", zone = "America/Sao_Paulo")
    public int verificar() {
        OffsetDateTime agora = OffsetDateTime.now(relogio);
        List<Fonte> vencidas = fonteRepository.findAll().stream()
                .filter(fonte -> vencidaSemErro(fonte, agora))
                .toList();

        int disparadas = 0;
        for (Fonte fonte : vencidas) {
            try {
                coletaLinkService.executar(fonte.getId());
                disparadas++;
                log.info("Coleta automática: fonte {} ({}) disparada", fonte.getSigla(), fonte.getPeriodicidade());
            } catch (AirflowIndisponivelException e) {
                // As outras também falhariam. O pedido já foi desfeito pelo executar: a próxima
                // verificação tenta de novo, sem ter criado nenhuma execução no Airflow.
                log.warn("Coleta automática adiada: Airflow indisponível ({})", e.getMessage());
                break;
            } catch (RuntimeException e) {
                log.warn("Coleta automática da fonte {} não disparada: {}", fonte.getSigla(), e.getMessage());
            }
        }
        if (!vencidas.isEmpty()) {
            log.info("Coleta automática: {} de {} fonte(s) vencida(s) disparada(s)", disparadas, vencidas.size());
        }
        return disparadas;
    }

    /** Um cron inválido numa fonte não pode parar a verificação das outras. */
    private boolean vencidaSemErro(Fonte fonte, OffsetDateTime agora) {
        try {
            return AgendaColeta.vencida(fonte, agora);
        } catch (IllegalArgumentException e) {
            log.warn("Fonte {}: cron inválido ({}), sem coleta automática", fonte.getSigla(), fonte.getCron());
            return false;
        }
    }
}
