package com.presupuesto.cuenta.entity;

import com.presupuesto.comun.EntidadBase;
import com.presupuesto.presupuesto.entity.Presupuesto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Cuenta financiera de un presupuesto. {@code enPresupuesto} y {@code saldoInicial} se fijan al
 * crearla y no se pueden cambiar; las cuentas no se borran, se cierran.
 */
@Entity
@Table(
        name = "cuentas",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cuentas_presupuesto_nombre",
                columnNames = {"presupuesto_id", "nombre_normalizado"}))
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Cuenta extends EntidadBase {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presupuesto_id", nullable = false)
    private Presupuesto presupuesto;

    /** Sin setter: solo cambia con {@link #renombrar(String)}, que mantiene el campo derivado. */
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, length = 100)
    private String nombre;

    /** {@link #nombre} en minúsculas: sostiene la unicidad sin distinguir mayúsculas. */
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, length = 100)
    private String nombreNormalizado;

    /** Sin setter: solo cambia con {@link #cambiarTipo(TipoCuenta)}. */
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoCuenta tipo;

    /** {@code false} para cuentas de seguimiento. Se define al crear. */
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, updatable = false)
    @Builder.Default
    private boolean enPresupuesto = true;

    /** En milésimas. Se define al crear. */
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, updatable = false)
    @Builder.Default
    private long saldoInicial = 0L;

    /** Sin setter: solo cambia con {@link #cerrar()} y {@link #reabrir()}. */
    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    @Builder.Default
    private boolean cerrada = false;

    /** Forma comparable de un nombre ya recortado ({@code Locale.ROOT}, sin depender de la JVM). */
    public static String normalizar(String nombre) {
        return nombre.toLowerCase(Locale.ROOT);
    }

    /** Cambia el nombre (ya recortado) y mantiene sincronizado {@link #nombreNormalizado}. */
    public void renombrar(String nuevoNombre) {
        this.nombre = nuevoNombre;
        this.nombreNormalizado = normalizar(nuevoNombre);
    }

    public void cambiarTipo(TipoCuenta nuevoTipo) {
        this.tipo = nuevoTipo;
    }

    public void cerrar() {
        this.cerrada = true;
    }

    public void reabrir() {
        this.cerrada = false;
    }
}
