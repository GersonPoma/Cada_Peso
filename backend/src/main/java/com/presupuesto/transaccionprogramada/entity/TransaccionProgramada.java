package com.presupuesto.transaccionprogramada.entity;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.comun.EntidadBase;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.presupuesto.entity.Presupuesto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Plantilla de una transacción recurrente: en cada ocurrencia el generador crea una transacción
 * real. {@code proximaFecha} es la siguiente ocurrencia pendiente (o {@code null} si ya no quedan)
 * y {@code ultimaOcurrenciaGenerada} la última que se creó. Las reglas las aplica el service; la
 * entidad solo expone los cambios permitidos.
 */
@Entity
@Table(
        name = "transacciones_programadas",
        indexes = @Index(
                name = "ix_transacciones_programadas_vencimiento",
                columnList = "activa, proxima_fecha"))
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TransaccionProgramada extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presupuesto_id", nullable = false)
    private Presupuesto presupuesto;

    /** Sin setter: la cuenta no se cambia, se crea otra plantilla. */
    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cuenta_id", nullable = false)
    private Cuenta cuenta;

    /** Sin setter: el día de pago no se cambia, se crea otra plantilla. */
    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private LocalDate fechaInicio;

    /** Sin setter: solo cambia con {@link #editar}. */
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FrecuenciaProgramada frecuencia;

    /** Sin setter: solo cambia con {@link #editar}. {@code null} si no termina. */
    @Setter(AccessLevel.NONE)
    private LocalDate fechaFin;

    /** Milésimas con signo, distinto de 0. Sin setter: solo cambia con {@link #editar}. */
    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private long monto;

    /** Sin setter: solo cambia con {@link #editar}. */
    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    /** Texto ya recortado, o {@code null}. Sin setter: solo cambia con {@link #editar}. */
    @Setter(AccessLevel.NONE)
    @Column(length = 100)
    private String beneficiario;

    @Setter(AccessLevel.NONE)
    @Column(length = 500)
    private String memo;

    /** Sin setter: solo cambia con {@link #pausar()} y {@link #activar(LocalDate)}. */
    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    @Builder.Default
    private boolean activa = true;

    /** Siguiente ocurrencia pendiente, o {@code null} si finalizó. Sin setter. */
    @Setter(AccessLevel.NONE)
    private LocalDate proximaFecha;

    /** Última ocurrencia que generó una transacción, o {@code null}. Sin setter. */
    @Setter(AccessLevel.NONE)
    private LocalDate ultimaOcurrenciaGenerada;

    /** Motivo del último fallo de generación, o {@code null}. Sin setter. */
    @Setter(AccessLevel.NONE)
    @Column(length = 500)
    private String ultimoError;

    /** Cambia lo que afecta a las ocurrencias futuras; texto ya recortado y nulo si vacío. */
    public void editar(
            long nuevoMonto,
            Categoria nuevaCategoria,
            String nuevoBeneficiario,
            String nuevoMemo,
            FrecuenciaProgramada nuevaFrecuencia,
            LocalDate nuevaFechaFin) {
        this.monto = nuevoMonto;
        this.categoria = nuevaCategoria;
        this.beneficiario = nuevoBeneficiario;
        this.memo = nuevoMemo;
        this.frecuencia = nuevaFrecuencia;
        this.fechaFin = nuevaFechaFin;
    }

    /** Fija la siguiente ocurrencia pendiente ({@code null} si ya no quedan). */
    public void reprogramar(LocalDate nuevaProximaFecha) {
        this.proximaFecha = nuevaProximaFecha;
    }

    public void pausar() {
        this.activa = false;
    }

    /** Vuelve a activarla en {@code nuevaProximaFecha} y limpia el error anterior. */
    public void activar(LocalDate nuevaProximaFecha) {
        this.activa = true;
        this.proximaFecha = nuevaProximaFecha;
        this.ultimoError = null;
    }

    /** Anota hasta qué ocurrencia se generó y la siguiente pendiente; borra el error. */
    public void registrarGeneracion(LocalDate ultima, LocalDate nuevaProximaFecha) {
        if (ultima != null) {
            this.ultimaOcurrenciaGenerada = ultima;
        }
        this.proximaFecha = nuevaProximaFecha;
        this.ultimoError = null;
    }

    public void registrarError(String motivo) {
        this.ultimoError = motivo == null || motivo.length() <= 500
                ? motivo
                : motivo.substring(0, 500);
    }
}
