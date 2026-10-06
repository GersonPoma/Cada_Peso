package com.presupuesto.categoria.repository;

import com.presupuesto.categoria.entity.GrupoCategoria;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GrupoCategoriaRepository extends JpaRepository<GrupoCategoria, Long> {

    Optional<GrupoCategoria> findByIdAndPresupuestoId(Long id, Long presupuestoId);

    List<GrupoCategoria> findByPresupuestoIdOrderByOrden(Long presupuestoId);

    List<GrupoCategoria> findByPresupuestoIdAndOcultoFalseOrderByOrden(Long presupuestoId);

    long countByPresupuestoId(Long presupuestoId);

    boolean existsByPresupuestoIdAndNombreNormalizado(Long presupuestoId, String nombreNormalizado);

    boolean existsByPresupuestoIdAndNombreNormalizadoAndIdNot(
            Long presupuestoId, String nombreNormalizado, Long id);
}
