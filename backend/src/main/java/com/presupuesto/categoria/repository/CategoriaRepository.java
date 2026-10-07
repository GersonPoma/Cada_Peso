package com.presupuesto.categoria.repository;

import com.presupuesto.categoria.entity.Categoria;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    Optional<Categoria> findByIdAndGrupoPresupuestoId(Long id, Long presupuestoId);

    List<Categoria> findByGrupoIdOrderByOrden(Long grupoId);

    List<Categoria> findByGrupoPresupuestoIdOrderByOrden(Long presupuestoId);

    List<Categoria> findByGrupoPresupuestoIdAndOcultaFalseOrderByOrden(Long presupuestoId);

    /** Todas las del presupuesto en el orden del árbol: grupo y luego categoría. */
    List<Categoria> findByGrupoPresupuestoIdOrderByGrupoOrdenAscOrdenAsc(Long presupuestoId);

    /** Las no ocultas del presupuesto en el orden del árbol. */
    List<Categoria> findByGrupoPresupuestoIdAndOcultaFalseOrderByGrupoOrdenAscOrdenAsc(
            Long presupuestoId);

    /** Las del presupuesto con esos ids (las ajenas no aparecen), en el orden del árbol. */
    List<Categoria> findByGrupoPresupuestoIdAndIdInOrderByGrupoOrdenAscOrdenAsc(
            Long presupuestoId, Collection<Long> ids);

    long countByGrupoId(Long grupoId);

    boolean existsByGrupoIdAndNombreNormalizado(Long grupoId, String nombreNormalizado);

    boolean existsByGrupoIdAndNombreNormalizadoAndIdNot(
            Long grupoId, String nombreNormalizado, Long id);
}
