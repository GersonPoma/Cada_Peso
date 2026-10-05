package com.presupuesto.usuario.repository;

import com.presupuesto.usuario.entity.Perfil;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PerfilRepository extends JpaRepository<Perfil, Long> {

    /** Carga el perfil junto con su usuario en una sola consulta ({@code open-in-view=false}). */
    @EntityGraph(attributePaths = "usuario")
    Optional<Perfil> findByUsuarioId(Long usuarioId);
}
