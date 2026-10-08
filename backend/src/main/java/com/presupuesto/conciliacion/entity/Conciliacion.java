package com.presupuesto.conciliacion.entity;

import com.presupuesto.comun.EntidadBase;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.presupuesto.entity.Presupuesto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Registro de historial de una conciliación de cuenta: no se edita ni se borra, por eso no tiene
 * setters. Los montos van en milésimas.
 */
@Entity
@Table(
        name = "conciliaciones",
        indexes = @Index(name = "ix_conciliaciones_cuenta_fecha", columnList = "cuenta_id, fecha"))
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Conciliacion extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presupuesto_id", nullable = false)
    private Presupuesto presupuesto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cuenta_id", nullable = false)
    private Cuenta cuenta;

    /** Fecha del extracto. */
    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private long saldoExtracto;

    /** Monto del ajuste creado; 0 si no hubo. */
    @Column(nullable = false)
    private long ajuste;

    /** Id de la transacción de ajuste, o {@code null}. Sin relación JPA ni clave foránea. */
    @Column(name = "transaccion_ajuste_id")
    private Long transaccionAjusteId;

    @Column(nullable = false)
    private int cantidadReconciliadas;
}
