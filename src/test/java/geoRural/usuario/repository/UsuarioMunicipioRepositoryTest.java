package geoRural.usuario.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.oracle.bmc.objectstorage.ObjectStorage;

import geoRural.usuario.dto.MunicipioLiberado;

/** Contra o Oracle do docker-compose, desfeito no fim. Londrina (4113700) vem da V5. */
@SpringBootTest
@Transactional
class UsuarioMunicipioRepositoryTest {

    private static final String USUARIO = "teste-usuario-municipio";

    @MockitoBean
    private ObjectStorage objectStorage;

    @Autowired
    private UsuarioMunicipioRepository repository;

    @Test
    void substituirTrocaAListaInteira() {
        repository.substituir(USUARIO, Set.of("4113700"), "gestor");

        assertThat(repository.listar(USUARIO)).containsExactly(new MunicipioLiberado("4113700", "Londrina"));
        assertThat(repository.contarPorUsuario(List.of(USUARIO, "outro"))).isEqualTo(Map.of(USUARIO, 1));

        repository.substituir(USUARIO, Set.of(), "gestor");

        assertThat(repository.codigos(USUARIO)).isEmpty();
    }

    @Test
    void inexistentesSoTrazOQueNaoEstaNaMalha() {
        assertThat(repository.inexistentes(Set.of("4113700", "9999999"))).containsExactly("9999999");
    }
}
