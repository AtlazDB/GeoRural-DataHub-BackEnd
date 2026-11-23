package geoRural.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

/** Liga os @Scheduled do back só quando a coleta automática está habilitada (ver ColetaAutomatica). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.coleta-automatica.habilitada", havingValue = "true")
public class AgendamentoConfig {

    /** Relógio da coleta automática, no fuso em que as periodicidades são contadas. */
    @Bean
    public Clock relogioColeta() {
        return Clock.system(ZoneId.of("America/Sao_Paulo"));
    }
}
