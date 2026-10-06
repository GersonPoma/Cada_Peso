package com.presupuesto.asignacion.entity;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.comun.EntidadBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Dinero asignado a una categoría en un mes, en milésimas (puede ser 0 o negativo). {@code mes}
 * es siempre el primer día del mes; hay una sola fila por categoría y mes.
 */
@Entity
@Table(
        name = "asignaciones_mensuales",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_asignaciones_mensuales_categoria_mes",
                columnNames = {"categoria_id", "mes"}))
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class AsignacionMensual extends EntidadBase {

    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    /** Primer día del mes. */
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, updatable = false)
    private LocalDate mes;

    /** Sin setter: solo cambia con {@link #fijarAsignado(long)} y {@link #sumarAsignado(long)}. */
    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private long asignado;

    public void fijarAsignado(long nuevoAsignado) {
        this.asignado = nuevoAsignado;
    }

    public void sumarAsignado(long delta) {
        this.asignado += delta;
    }

    @PrePersist
    @PreUpdate
    void validarMes() {
        if (mes == null || mes.getDayOfMonth() != 1) {
            throw new IllegalStateException("El mes de una asignación es el primer día del mes");
        }
    }
}
