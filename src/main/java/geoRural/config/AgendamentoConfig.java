package geoRural.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Liga os @Scheduled do back só quando a coleta automática está habilitada (ver ColetaAutomatica). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.coleta-automatica.habilitada", havingValue = "true")
public class AgendamentoConfig {
}
