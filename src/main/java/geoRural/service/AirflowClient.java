package geoRural.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import geoRural.exception.AirflowIndisponivelException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * A API REST do Airflow (v2): dispara uma DAG, espera terminar e lê o que a tarefa devolveu (XCom).
 * É o caminho do "Revalidar" da quarentena: a regra fica em um lugar só (no Airflow), e a tela
 * recebe a resposta na hora, em uns 3 segundos.
 *
 * Com usuário e senha configurados, pede um token em /auth/token (o Airflow da VM). Sem eles, chama
 * sem token: o Airflow local da equipe roda sem login.
 */
@Component
public class AirflowClient {

    private static final Duration TEMPO_POR_PEDIDO = Duration.ofSeconds(15);
    private static final long INTERVALO_MS = 300;

    private final ObjectMapper objectMapper;
    private final HttpClient http;
    private final String url;
    private final String usuario;
    private final String senha;
    private final Duration espera;

    public AirflowClient(ObjectMapper objectMapper,
                         @Value("${app.airflow.url}") String url,
                         @Value("${app.airflow.usuario:}") String usuario,
                         @Value("${app.airflow.senha:}") String senha,
                         @Value("${app.airflow.espera-segundos:60}") long esperaSegundos) {
        this.objectMapper = objectMapper;
        // HTTP/1.1: por padrão o cliente pede o upgrade para HTTP/2, e a API do Airflow responde 400.
        this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(TEMPO_POR_PEDIDO).build();
        this.url = url.replaceAll("/+$", "");
        this.usuario = usuario;
        this.senha = senha;
        this.espera = Duration.ofSeconds(esperaSegundos);
    }

    /** Dispara a DAG com `conf`, espera terminar e devolve o que a tarefa retornou. */
    public JsonNode executar(String dagId, String tarefa, Map<String, Object> conf) {
        String token = token();
        ObjectNode corpo = objectMapper.createObjectNode();
        corpo.putNull("logical_date");
        corpo.set("conf", objectMapper.valueToTree(conf));
        String base = "/api/v2/dags/" + dagId + "/dagRuns";
        String execucao = pedir("POST", base, corpo.toString(), token).path("dag_run_id").asText();
        String caminho = base + "/" + URLEncoder.encode(execucao, StandardCharsets.UTF_8).replace("+", "%20");

        long limite = System.nanoTime() + espera.toNanos();
        String estado = pedir("GET", caminho, null, token).path("state").asText();
        while (!estado.equals("success") && !estado.equals("failed")) {
            if (System.nanoTime() > limite) {
                throw new AirflowIndisponivelException("O Airflow não terminou em " + espera.toSeconds()
                        + " segundos: confira se a DAG " + dagId + " está ativa e tente de novo.");
            }
            dormir();
            estado = pedir("GET", caminho, null, token).path("state").asText();
        }
        if (estado.equals("failed")) {
            throw new AirflowIndisponivelException("A DAG " + dagId + " falhou: veja o log da execução no Airflow.");
        }
        return pedir("GET", caminho + "/taskInstances/" + tarefa + "/xcomEntries/return_value?deserialize=true",
                null, token).path("value");
    }

    private String token() {
        if (usuario.isBlank()) {
            return null;
        }
        ObjectNode corpo = objectMapper.createObjectNode().put("username", usuario).put("password", senha);
        return pedir("POST", "/auth/token", corpo.toString(), null).path("access_token").asText();
    }

    private JsonNode pedir(String metodo, String caminho, String corpo, String token) {
        HttpRequest.Builder pedido = HttpRequest.newBuilder(URI.create(url + caminho))
                .timeout(TEMPO_POR_PEDIDO)
                .header("Content-Type", "application/json")
                .method(metodo, corpo == null ? HttpRequest.BodyPublishers.noBody()
                                              : HttpRequest.BodyPublishers.ofString(corpo));
        if (token != null) {
            pedido.header("Authorization", "Bearer " + token);
        }
        try {
            HttpResponse<String> resposta = http.send(pedido.build(), HttpResponse.BodyHandlers.ofString());
            if (resposta.statusCode() >= 400) {
                throw new AirflowIndisponivelException("O Airflow respondeu HTTP " + resposta.statusCode()
                        + " em " + caminho + ".");
            }
            return objectMapper.readTree(resposta.body());
        } catch (JsonProcessingException e) {
            throw new AirflowIndisponivelException("O Airflow não respondeu JSON em " + caminho + ".", e);
        } catch (IOException e) {
            throw new AirflowIndisponivelException("Não foi possível falar com o Airflow em " + url + ".", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AirflowIndisponivelException("A chamada ao Airflow foi interrompida.", e);
        }
    }

    private void dormir() {
        try {
            Thread.sleep(INTERVALO_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AirflowIndisponivelException("A chamada ao Airflow foi interrompida.", e);
        }
    }
}
