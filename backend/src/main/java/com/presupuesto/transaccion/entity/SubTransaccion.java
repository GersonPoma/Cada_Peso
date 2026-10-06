package com.presupuesto.transaccion.entity;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.comun.EntidadBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Parte de una transacción dividida. Se crea con el builder, la enlaza a su transacción
 * {@link Transaccion#reemplazarSubtransacciones(java.util.List)} y no se modifica después.
 */
@Entity
@Table(name = "subtransacciones")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class SubTransaccion extends EntidadBase {

    /** Sin setter: la enlaza {@link Transaccion#reemplazarSubtransacciones(java.util.List)}. */
    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaccion_id", nullable = false)
    private Transaccion transaccion;

    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    /** En milésimas, con signo y distinto de 0. */
    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private long monto;

    @Setter(AccessLevel.NONE)
    @Column(length = 500)
    private String memo;

    void vincular(Transaccion duena) {
        this.transaccion = duena;
    }
}
