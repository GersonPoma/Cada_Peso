package com.presupuesto.meta.repository;

import com.presupuesto.meta.entity.Meta;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MetaRepository extends JpaRepository<Meta, Long> {

    Optional<Meta> findByCategoriaId(Long categoriaId);

    /** Las metas del presupuesto (también de categorías ocultas) en el orden del árbol. */
    @Query("""
            select m from Meta m
            join fetch m.categoria c
            join fetch c.grupo g
            where g.presupuesto.id = :presupuestoId
            order by g.orden, c.orden
            """)
    List<Meta> findDelPresupuestoEnOrdenDelArbol(@Param("presupuestoId") Long presupuestoId);
}
