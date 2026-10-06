package com.presupuesto.asignacion.repository;

import com.presupuesto.asignacion.entity.AsignacionMensual;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AsignacionMensualRepository extends JpaRepository<AsignacionMensual, Long> {

    Optional<AsignacionMensual> findByCategoriaIdAndMes(Long categoriaId, LocalDate mes);

    /** Lo asignado en el presupuesto hasta un mes (inclusive), una fila por categoría y mes. */
    @Query("""
            select a.categoria.id as categoriaId, a.mes as mes, a.asignado as asignado
            from AsignacionMensual a
            where a.categoria.grupo.presupuesto.id = :presupuestoId and a.mes <= :hasta
            """)
    List<AsignadoPorMes> asignadosHasta(
            @Param("presupuestoId") Long presupuestoId, @Param("hasta") LocalDate hasta);

    interface AsignadoPorMes {

        Long getCategoriaId();

        LocalDate getMes();

        Long getAsignado();
    }
}
