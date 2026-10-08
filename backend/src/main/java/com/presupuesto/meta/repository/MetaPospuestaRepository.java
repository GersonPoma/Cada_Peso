package com.presupuesto.meta.repository;

import com.presupuesto.meta.entity.MetaPospuesta;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MetaPospuestaRepository extends JpaRepository<MetaPospuesta, Long> {

    Optional<MetaPospuesta> findByMetaIdAndMes(Long metaId, LocalDate mes);

    long countByMetaId(Long metaId);

    void deleteByMetaId(Long metaId);

    /** Ids de las metas del presupuesto pospuestas en el mes (primer día del mes). */
    @Query("""
            select p.meta.id from MetaPospuesta p
            where p.meta.categoria.grupo.presupuesto.id = :presupuestoId and p.mes = :mes
            """)
    List<Long> findMetaIdsPospuestas(
            @Param("presupuestoId") Long presupuestoId, @Param("mes") LocalDate mes);

    /**
     * Las metas del presupuesto pospuestas en algún mes del rango (primer día de cada mes, ambos
     * extremos inclusive), una fila por meta y mes: lo mismo que {@link #findMetaIdsPospuestas}
     * para todos los meses del rango con una sola consulta.
     */
    @Query("""
            select p.meta.id as metaId, p.mes as mes from MetaPospuesta p
            where p.meta.categoria.grupo.presupuesto.id = :presupuestoId
              and p.mes between :desde and :hasta
            """)
    List<PospuestaEnMes> findPospuestasEnRango(
            @Param("presupuestoId") Long presupuestoId,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);

    interface PospuestaEnMes {

        Long getMetaId();

        LocalDate getMes();
    }
}
