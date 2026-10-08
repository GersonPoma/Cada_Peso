package com.presupuesto.conciliacion.repository;

import com.presupuesto.conciliacion.entity.Conciliacion;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConciliacionRepository extends JpaRepository<Conciliacion, Long> {

    /** Historial de la cuenta, de la más reciente a la más antigua (fecha y luego id). */
    List<Conciliacion> findByCuentaIdOrderByFechaDescIdDesc(Long cuentaId, Pageable limite);
}
