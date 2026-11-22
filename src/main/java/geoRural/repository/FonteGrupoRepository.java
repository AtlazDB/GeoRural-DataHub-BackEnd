package geoRural.repository;

import geoRural.entity.FonteGrupo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FonteGrupoRepository extends JpaRepository<FonteGrupo, Long> {

    List<FonteGrupo> findAllByOrderByNomeAsc();

    Optional<FonteGrupo> findByNomeIgnoreCase(String nome);
}
