package com.presupuesto.transaccionprogramada.repository;

import com.presupuesto.transaccionprogramada.entity.TransaccionProgramada;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransaccionProgramadaRepository
        extends JpaRepository<TransaccionProgramada, Long> {

    Optional<TransaccionProgramada> findByIdAndPresupuestoId(Long id, Long presupuestoId);

    /** Por próxima fecha ascendente (las finalizadas al final) y luego por id. */
    @Query("""
            select p from TransaccionProgramada p
            where p.presupuesto.id = :presupuestoId
              and (:soloActivas = false or p.activa = true)
            order by case when p.proximaFecha is null then 1 else 0 end,
                     p.proximaFecha, p.id
            """)
    List<TransaccionProgramada> listar(
            @Param("presupuestoId") Long presupuestoId,
            @Param("soloActivas") boolean soloActivas);

    /** Ids de las plantillas activas con una ocurrencia pendiente hasta {@code hasta}. */
    @Query("""
            select p.id from TransaccionProgramada p
            where p.activa = true and p.proximaFecha <= :hasta
            order by p.id
            """)
    List<Long> idsVencidas(@Param("hasta") LocalDate hasta);

    @Query("""
            select p.id from TransaccionProgramada p
            where p.presupuesto.id = :presupuestoId
              and p.activa = true and p.proximaFecha <= :hasta
            order by p.id
            """)
    List<Long> idsVencidasDelPresupuesto(
            @Param("presupuestoId") Long presupuestoId, @Param("hasta") LocalDate hasta);

    /** Carga la plantilla bloqueando su fila hasta el fin de la transacción. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from TransaccionProgramada p where p.id = :id")
    Optional<TransaccionProgramada> bloquear(@Param("id") Long id);
}
