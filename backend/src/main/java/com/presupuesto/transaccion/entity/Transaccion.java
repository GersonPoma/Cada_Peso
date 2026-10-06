package com.presupuesto.transaccion.entity;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.comun.EntidadBase;
import com.presupuesto.cuenta.entity.Cuenta;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.BatchSize;

/**
 * Movimiento de dinero de una cuenta. El {@code monto} va en milésimas con signo (entrada
 * positiva, salida negativa). Si tiene subtransacciones no lleva categoría propia. Las reglas de
 * negocio las aplica el service; la entidad solo expone los cambios permitidos.
 */
@Entity
@Table(
        name = "transacciones",
        indexes = @Index(name = "ix_transacciones_cuenta_fecha", columnList = "cuenta_id, fecha"))
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class Transaccion extends EntidadBase {

    /** Sin setter: solo cambia con {@link #moverA(Cuenta)}. */
    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cuenta_id", nullable = false)
    private Cuenta cuenta;

    /** Sin setter: solo cambia con {@link #editar}. */
    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private LocalDate fecha;

    /** Sin setter: solo cambia con {@link #editar}. */
    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    private long monto;

    /** {@code null} si no tiene categoría o si está dividida. */
    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private Categoria categoria;

    @Setter(AccessLevel.NONE)
    @Column(length = 100)
    private String beneficiario;

    @Setter(AccessLevel.NONE)
    @Column(length = 500)
    private String memo;

    /** Sin setter: solo cambia con {@link #cambiarEstado(EstadoTransaccion)}. */
    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoTransaccion estado = EstadoTransaccion.NO_CONCILIADA;

    /** Sin setter: solo cambia con {@link #aprobar()}. */
    @Setter(AccessLevel.NONE)
    @Column(nullable = false)
    @Builder.Default
    private boolean aprobada = true;

    /** Sin setter: solo cambia con {@link #reemplazarSubtransacciones(List)}. */
    @Setter(AccessLevel.NONE)
    @OneToMany(mappedBy = "transaccion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    @BatchSize(size = 50)
    @Builder.Default
    private List<SubTransaccion> subtransacciones = new ArrayList<>();

    /** Beneficiario y memo ya llegan recortados y en {@code null} si quedaron vacíos. */
    public void editar(
            LocalDate nuevaFecha,
            long nuevoMonto,
            Categoria nuevaCategoria,
            String nuevoBeneficiario,
            String nuevoMemo) {
        this.fecha = nuevaFecha;
        this.monto = nuevoMonto;
        this.categoria = nuevaCategoria;
        this.beneficiario = nuevoBeneficiario;
        this.memo = nuevoMemo;
    }

    /** Reemplaza todas las subtransacciones conservando la misma colección (orphanRemoval). */
    public void reemplazarSubtransacciones(List<SubTransaccion> nuevas) {
        List<SubTransaccion> copia = List.copyOf(nuevas);
        subtransacciones.clear();
        for (SubTransaccion sub : copia) {
            sub.vincular(this);
            subtransacciones.add(sub);
        }
    }

    public void moverA(Cuenta nuevaCuenta) {
        this.cuenta = nuevaCuenta;
    }

    public void categorizar(Categoria nuevaCategoria) {
        this.categoria = nuevaCategoria;
    }

    public void aprobar() {
        this.aprobada = true;
    }

    public void cambiarEstado(EstadoTransaccion nuevoEstado) {
        this.estado = nuevoEstado;
    }

    public boolean estaReconciliada() {
        return estado == EstadoTransaccion.RECONCILIADA;
    }

    public boolean tieneSubtransacciones() {
        return !subtransacciones.isEmpty();
    }
}
