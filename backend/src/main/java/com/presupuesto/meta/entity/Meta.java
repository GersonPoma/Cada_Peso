package com.presupuesto.meta.entity;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.comun.EntidadBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
 * Meta de una categoría (una sola por categoría, también si está oculta). Los campos de
 * frecuencia y fechas son nulos cuando no aplican al tipo; los descarta el request antes de
 * llegar aquí.
 */
@Entity
@Table(
        name = "metas",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_metas_categoria", columnNames = {"categoria_id"}))
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Meta extends EntidadBase {

    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    /** Sin setter: toda la meta cambia junta con {@link #reemplazar}. */
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoMeta tipo;

    /** Milésimas, mayor que 0. */
    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private long monto;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private FrecuenciaMeta frecuencia;

    /** 1 (lunes) a 7 (domingo), solo con frecuencia semanal. */
    @Setter(AccessLevel.NONE)
    private Integer diaSemana;

    /** De 2 a 365 días, solo con frecuencia personalizada. */
    @Setter(AccessLevel.NONE)
    private Integer intervaloDias;

    @Setter(AccessLevel.NONE)
    private LocalDate fechaInicio;

    @Setter(AccessLevel.NONE)
    private LocalDate fechaObjetivo;

    /** Cambia todos los datos de la meta a la vez; la categoría no cambia nunca. */
    public void reemplazar(
            TipoMeta nuevoTipo,
            long nuevoMonto,
            FrecuenciaMeta nuevaFrecuencia,
            Integer nuevoDiaSemana,
            Integer nuevoIntervaloDias,
            LocalDate nuevaFechaInicio,
            LocalDate nuevaFechaObjetivo) {
        this.tipo = nuevoTipo;
        this.monto = nuevoMonto;
        this.frecuencia = nuevaFrecuencia;
        this.diaSemana = nuevoDiaSemana;
        this.intervaloDias = nuevoIntervaloDias;
        this.fechaInicio = nuevaFechaInicio;
        this.fechaObjetivo = nuevaFechaObjetivo;
    }
}
