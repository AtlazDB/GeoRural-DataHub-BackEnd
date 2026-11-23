package geoRural.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/** Grupo de fontes (V31): só organiza os cards na tela de Fontes de dados. */
@Entity
@Table(name = "FONTE_GRUPO")
public class FonteGrupo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "NOME", nullable = false, unique = true)
    private String nome;

    @CreationTimestamp
    @Column(name = "CRIADO_EM", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public OffsetDateTime getCriadoEm() { return criadoEm; }
}
