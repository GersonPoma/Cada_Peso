package com.presupuesto.transaccion.service;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.comun.excepcion.DatosInvalidosException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.comun.excepcion.ReglaNegocioException;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.repository.CuentaRepository;
import com.presupuesto.transaccion.dto.request.SubTransaccionRequest;
import com.presupuesto.transaccion.entity.SubTransaccion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resuelve las cuentas y categorías que una transacción referencia, siempre dentro del
 * presupuesto validado, y aplica las reglas de la división en subtransacciones.
 */
@Component
@RequiredArgsConstructor
class TransaccionReferencias {

    static final int MINIMO_SUBTRANSACCIONES = 2;
    static final int MAXIMO_SUBTRANSACCIONES = 20;

    static final String MENSAJE_CUENTA_NO_ENCONTRADA = "Cuenta no encontrada";
    static final String MENSAJE_CATEGORIA_NO_ENCONTRADA = "Categoría no encontrada";
    static final String MENSAJE_CUENTA_CERRADA = "La cuenta está cerrada";
    static final String MENSAJE_CANTIDAD_SUBTRANSACCIONES =
            "Una división lleva entre 2 y 20 subtransacciones";
    static final String MENSAJE_CATEGORIA_Y_DIVISION =
            "Una transacción dividida no lleva categoría propia";
    static final String MENSAJE_SUMA_INCORRECTA =
            "La suma de las subtransacciones debe ser igual al monto de la transacción";

    private final CuentaRepository cuentaRepository;
    private final CategoriaRepository categoriaRepository;

    /** Categoría de la transacción ({@code null} si no tiene) y sus partes ya armadas. */
    record Division(Categoria categoria, List<SubTransaccion> partes) {}

    Cuenta cuenta(Long cuentaId, Long presupuestoId) {
        return cuentaRepository.findByIdAndPresupuestoId(cuentaId, presupuestoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(MENSAJE_CUENTA_NO_ENCONTRADA));
    }

    /** {@code null} si no se indica categoría; 404 si no es del presupuesto (oculta sí sirve). */
    Categoria categoria(Long categoriaId, Long presupuestoId) {
        if (categoriaId == null) {
            return null;
        }
        return categoriaRepository.findByIdAndGrupoPresupuestoId(categoriaId, presupuestoId)
                .orElseThrow(
                        () -> new RecursoNoEncontradoException(MENSAJE_CATEGORIA_NO_ENCONTRADA));
    }

    void exigirAbierta(Cuenta cuenta) {
        if (cuenta.isCerrada()) {
            throw new ReglaNegocioException(MENSAJE_CUENTA_CERRADA);
        }
    }

    /**
     * Orden: 404 por categorías ajenas, 400 por cantidad o por categoría junto a la división, y
     * 422 si la suma no coincide con el monto. Sin subtransacciones solo resuelve la categoría.
     */
    Division dividir(
            Long presupuestoId,
            Long categoriaId,
            long monto,
            List<SubTransaccionRequest> subtransacciones) {
        Categoria categoria = categoria(categoriaId, presupuestoId);
        if (subtransacciones.isEmpty()) {
            return new Division(categoria, List.of());
        }
        Map<Long, Categoria> categorias = new HashMap<>();
        List<SubTransaccion> partes = new ArrayList<>(subtransacciones.size());
        for (SubTransaccionRequest parte : subtransacciones) {
            Long idCategoria = parte.categoriaId();
            Categoria deLaParte = idCategoria == null
                    ? null
                    : categorias.computeIfAbsent(idCategoria, id -> categoria(id, presupuestoId));
            partes.add(SubTransaccion.builder()
                    .categoria(deLaParte)
                    .monto(parte.monto())
                    .memo(parte.memo())
                    .build());
        }
        if (partes.size() < MINIMO_SUBTRANSACCIONES || partes.size() > MAXIMO_SUBTRANSACCIONES) {
            throw new DatosInvalidosException(MENSAJE_CANTIDAD_SUBTRANSACCIONES);
        }
        if (categoria != null) {
            throw new DatosInvalidosException(MENSAJE_CATEGORIA_Y_DIVISION);
        }
        exigirSuma(monto, partes);
        return new Division(null, partes);
    }

    private static void exigirSuma(long monto, List<SubTransaccion> partes) {
        long suma = 0L;
        try {
            for (SubTransaccion parte : partes) {
                suma = Math.addExact(suma, parte.getMonto());
            }
        } catch (ArithmeticException desbordamiento) {
            throw new ReglaNegocioException(MENSAJE_SUMA_INCORRECTA);
        }
        if (suma != monto) {
            throw new ReglaNegocioException(MENSAJE_SUMA_INCORRECTA);
        }
    }
}
