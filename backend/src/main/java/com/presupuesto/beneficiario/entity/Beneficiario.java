package com.presupuesto.beneficiario.entity;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.comun.EntidadBase;
import com.presupuesto.presupuesto.entity.Presupuesto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Persona o comercio con quien se mueve dinero en un presupuesto. Recuerda la categoría con que se
 * usó por última vez ({@code categoriaPredeterminada}) para sugerirla; los beneficiarios no se
 * borran.
 */
@Entity
@Table(
        name = "beneficiarios",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_beneficiarios_presupuesto_nombre",
                columnNames = {"presupuesto_id", "nombre_normalizado"}))
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Beneficiario extends EntidadBase {

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

    /**
     * Última categoría usada con este beneficiario, o {@code null}. Sin setter: solo cambia con
     * {@link #cambiarCategoriaPredeterminada(Categoria)} y {@link #recordarCategoria(Categoria)}.
     */
    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_predeterminada_id")
    private Categoria categoriaPredeterminada;

    /** Forma comparable de un nombre ya recortado ({@code Locale.ROOT}, sin depender de la JVM). */
    public static String normalizar(String nombre) {
        return nombre.toLowerCase(Locale.ROOT);
    }

    /** Cambia el nombre (ya recortado) y mantiene sincronizado {@link #nombreNormalizado}. */
    public void renombrar(String nuevoNombre) {
        this.nombre = nuevoNombre;
        this.nombreNormalizado = normalizar(nuevoNombre);
    }

    /** Fija la categoría predeterminada o la quita con {@code null}. */
    public void cambiarCategoriaPredeterminada(Categoria nuevaCategoria) {
        this.categoriaPredeterminada = nuevaCategoria;
    }

    /** Recuerda la última categoría usada con este beneficiario. */
    public void recordarCategoria(Categoria categoria) {
        this.categoriaPredeterminada = categoria;
    }
}
