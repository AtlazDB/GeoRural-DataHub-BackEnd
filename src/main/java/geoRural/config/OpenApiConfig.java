package geoRural.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Documentação da API (springdoc). A tela fica em /api/v1/docs e o JSON do OpenAPI em
 * /api/v1/docs/openapi; os caminhos estão no application.properties. Os endpoints entram
 * sozinhos, lidos dos controllers; @Tag e @Operation só dão nome e descrição em português.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI documentacao() {
        return new OpenAPI()
                .info(new Info()
                        .title("GeoRural DataHub API")
                        .version("v1")
                        .description("Governança e rastreabilidade de dados ambientais de imóveis rurais do Paraná: "
                                + "fontes e zona bruta, mapeamento de colunas, quarentena, imóveis, municípios "
                                + "e indicadores. O processamento e a coleta ficam com o Airflow; aqui eles só "
                                + "entram na fila."))
                // Relativo: vale igual em localhost:8080 e atrás do nginx da VM.
                .servers(List.of(new Server().url("/")));
    }
}
