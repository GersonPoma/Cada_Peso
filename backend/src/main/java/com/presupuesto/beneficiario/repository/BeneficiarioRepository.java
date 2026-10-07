package com.presupuesto.beneficiario.repository;

import com.presupuesto.beneficiario.entity.Beneficiario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BeneficiarioRepository extends JpaRepository<Beneficiario, Long> {

    Optional<Beneficiario> findByIdAndPresupuestoId(Long id, Long presupuestoId);

    Optional<Beneficiario> findByPresupuestoIdAndNombreNormalizado(
            Long presupuestoId, String nombreNormalizado);

    List<Beneficiario> findByPresupuestoIdOrderByNombreNormalizado(Long presupuestoId);

    boolean existsByPresupuestoIdAndNombreNormalizado(Long presupuestoId, String nombreNormalizado);

    boolean existsByPresupuestoIdAndNombreNormalizadoAndIdNot(
            Long presupuestoId, String nombreNormalizado, Long id);

    /**
     * Los que empiezan por el prefijo. {@code patron} ya viene en minúsculas, con {@code !},
     * {@code %} y {@code _} escapados con {@code !} y un {@code %} al final.
     */
    @Query("""
            select b from Beneficiario b
            where b.presupuesto.id = :presupuestoId
              and b.nombreNormalizado like :patron escape '!'
            order by b.nombreNormalizado
            """)
    List<Beneficiario> buscarPorPrefijo(
            @Param("presupuestoId") Long presupuestoId,
            @Param("patron") String patron,
            Pageable limite);
}
