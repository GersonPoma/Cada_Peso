package com.presupuesto.categoria.repository;

import com.presupuesto.categoria.entity.Categoria;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    Optional<Categoria> findByIdAndGrupoPresupuestoId(Long id, Long presupuestoId);

    List<Categoria> findByGrupoIdOrderByOrden(Long grupoId);

    List<Categoria> findByGrupoPresupuestoIdOrderByOrden(Long presupuestoId);

    List<Categoria> findByGrupoPresupuestoIdAndOcultaFalseOrderByOrden(Long presupuestoId);

    long countByGrupoId(Long grupoId);

    boolean existsByGrupoIdAndNombreNormalizado(Long grupoId, String nombreNormalizado);

    boolean existsByGrupoIdAndNombreNormalizadoAndIdNot(
            Long grupoId, String nombreNormalizado, Long id);
}
