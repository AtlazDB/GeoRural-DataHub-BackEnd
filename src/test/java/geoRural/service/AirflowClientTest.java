package geoRural.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import geoRural.exception.AirflowIndisponivelException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Sem Spring e sem banco: um servidor HTTP local faz o papel da API REST do Airflow. */
class AirflowClientTest {

    private static final String DAG_RUNS = "/api/v2/dags/revalidar_registro/dagRuns";

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<String> corpos = Collections.synchronizedList(new ArrayList<>());
    private final List<String> tokens = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger consultas = new AtomicInteger();
    private volatile String estadoFinal = "success";
    private volatile boolean pausada = false;
    private HttpServer servidor;
    private String base;

    @BeforeEach
    void subirServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/auth/token", troca -> responder(troca, 200, "{\"access_token\": \"abc\"}"));
        servidor.createContext("/api/v2/dags/revalidar_registro", troca ->
                responder(troca, 200, "{\"is_paused\": " + pausada + "}"));
        servidor.createContext(DAG_RUNS, troca -> {
            tokens.add(troca.getRequestHeaders().getFirst("Authorization"));
            String caminho = troca.getRequestURI().getRawPath();
            if (troca.getRequestMethod().equals("POST")) {
                corpos.add(new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                responder(troca, 200, "{\"dag_run_id\": \"manual__2026-11-02T00:00:00+00:00\"}");
            } else if (caminho.contains("/xcomEntries/return_value")) {
                assertThat(troca.getRequestURI().getQuery()).isEqualTo("deserialize=true");
                responder(troca, 200, "{\"value\": {\"status\": \"APROVADO\", \"erros\": []}}");
            } else {
                // Primeira consulta: ainda rodando; depois, terminou.
                String estado = consultas.incrementAndGet() < 2 ? "running" : estadoFinal;
                responder(troca, 200, "{\"state\": \"" + estado + "\"}");
            }
        });
        servidor.start();
        base = "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    @AfterEach
    void derrubarServidor() {
        servidor.stop(0);
    }

    @Test
    void dispararNaoEsperaTerminar() {
        cliente("", 30).disparar("revalidar_registro", Map.of("quarentena_id", 7));

        assertThat(corpos).hasSize(1);
        assertThat(consultas.get()).isZero();
    }

    @Test
    void dagPausadaNaoEDisparada() {
        pausada = true;

        assertThatThrownBy(() -> cliente("", 30).disparar("revalidar_registro", Map.of("quarentena_id", 7)))
                .isInstanceOf(AirflowIndisponivelException.class).hasMessageContaining("pausada");
        assertThat(corpos).isEmpty();
    }

    @Test
    void disparaEsperaEDevolveOQueATarefaRetornou() throws Exception {
        JsonNode resposta = cliente("", 30).executar("revalidar_registro", "revalidar", Map.of("quarentena_id", 7));

        assertThat(resposta.path("status").asText()).isEqualTo("APROVADO");
        JsonNode disparo = mapper.readTree(corpos.get(0));
        assertThat(disparo.path("conf").path("quarentena_id").asInt()).isEqualTo(7);
        assertThat(disparo.path("logical_date").isNull()).isTrue();
        assertThat(consultas.get()).isEqualTo(2);
        assertThat(tokens).containsOnlyNulls();  // sem usuário, sem token (Airflow local)
    }

    @Test
    void comUsuarioPedeOTokenEUsaEmTudo() {
        cliente("admin", 30).executar("revalidar_registro", "revalidar", Map.of("quarentena_id", 7));

        assertThat(tokens).isNotEmpty().containsOnly("Bearer abc");
    }

    @Test
    void dagQueFalhaViraErroClaro() {
        estadoFinal = "failed";

        assertThatThrownBy(() -> cliente("", 30).executar("revalidar_registro", "revalidar", Map.of()))
                .isInstanceOf(AirflowIndisponivelException.class)
                .hasMessageContaining("falhou");
    }

    @Test
    void dagQueNaoTerminaNoPrazoViraErroClaro() {
        estadoFinal = "running";

        assertThatThrownBy(() -> cliente("", 1).executar("revalidar_registro", "revalidar", Map.of()))
                .isInstanceOf(AirflowIndisponivelException.class)
                .hasMessageContaining("não terminou em 1 segundos");
    }

    @Test
    void airflowForaDoArViraErroClaro() {
        servidor.stop(0);

        assertThatThrownBy(() -> cliente("", 30).executar("revalidar_registro", "revalidar", Map.of()))
                .isInstanceOf(AirflowIndisponivelException.class)
                .hasMessageContaining("Não foi possível falar com o Airflow");
    }

    private AirflowClient cliente(String usuario, long espera) {
        return new AirflowClient(mapper, base + "/", usuario, "senha", espera);
    }

    private static void responder(HttpExchange troca, int codigo, String corpo) throws IOException {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().add("Content-Type", "application/json");
        troca.sendResponseHeaders(codigo, bytes.length);
        try (OutputStream out = troca.getResponseBody()) {
            out.write(bytes);
        }
    }
}
