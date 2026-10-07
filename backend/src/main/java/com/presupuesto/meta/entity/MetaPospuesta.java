package com.presupuesto.meta.entity;

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

/** Una meta pospuesta en un mes. {@code mes} es siempre el primer día; una fila por meta y mes. */
@Entity
@Table(
        name = "metas_pospuestas",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_metas_pospuestas_meta_mes", columnNames = {"meta_id", "mes"}))
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class MetaPospuesta extends EntidadBase {

    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meta_id", nullable = false)
    private Meta meta;

    /** Primer día del mes. */
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, updatable = false)
    private LocalDate mes;

    @PrePersist
    @PreUpdate
    void validarMes() {
        if (mes == null || mes.getDayOfMonth() != 1) {
            throw new IllegalStateException("El mes de una meta pospuesta es el primer día");
        }
    }
}
