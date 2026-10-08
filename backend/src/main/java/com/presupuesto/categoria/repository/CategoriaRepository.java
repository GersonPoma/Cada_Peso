package com.presupuesto.categoria.repository;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.cuenta.entity.Cuenta;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** La categoría de pago de una tarjeta, si la tiene. */
    Optional<Categoria> findByCuentaTarjetaId(Long cuentaId);

    /** Cuenta (tarjeta) y categoría de pago de cada tarjeta del presupuesto. */
    @Query("""
            select c.cuentaTarjeta.id as cuentaId, c.id as categoriaId
            from Categoria c
            where c.grupo.presupuesto.id = :presupuestoId
              and c.cuentaTarjeta is not null
            """)
    List<PagoDeTarjeta> pagosDeTarjetas(@Param("presupuestoId") Long presupuestoId);

    /** Presupuestos con alguna tarjeta del presupuesto que aún no tiene categoría de pago. */
    @Query("""
            select distinct t.presupuesto.id
            from Cuenta t
            where t.tipo = com.presupuesto.cuenta.entity.TipoCuenta.TARJETA_CREDITO
              and t.enPresupuesto = true
              and not exists (select 1 from Categoria c where c.cuentaTarjeta = t)
            order by t.presupuesto.id
            """)
    List<Long> presupuestosConTarjetasSinCategoria();

    /** Las tarjetas del presupuesto sin categoría de pago, por id. */
    @Query("""
            select t
            from Cuenta t
            where t.presupuesto.id = :presupuestoId
              and t.tipo = com.presupuesto.cuenta.entity.TipoCuenta.TARJETA_CREDITO
              and t.enPresupuesto = true
              and not exists (select 1 from Categoria c where c.cuentaTarjeta = t)
            order by t.id
            """)
    List<Cuenta> tarjetasSinCategoria(@Param("presupuestoId") Long presupuestoId);

    long countByGrupoId(Long grupoId);

    boolean existsByGrupoIdAndNombreNormalizado(Long grupoId, String nombreNormalizado);

    boolean existsByGrupoIdAndNombreNormalizadoAndIdNot(
            Long grupoId, String nombreNormalizado, Long id);

    interface PagoDeTarjeta {

        Long getCuentaId();

        Long getCategoriaId();
    }
}
