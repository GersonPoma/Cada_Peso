package com.presupuesto.transaccion.repository;

import com.presupuesto.transaccion.entity.EstadoTransaccion;
import com.presupuesto.transaccion.entity.SubTransaccion;
import com.presupuesto.transaccion.entity.Transaccion;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDate;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Filtros de la lista de transacciones; todos se combinan con AND. */
public final class TransaccionSpecifications {

    static final char ESCAPE = '\\';

    private TransaccionSpecifications() {}

    public static Specification<Transaccion> delPresupuesto(Long presupuestoId) {
        return (raiz, consulta, cb) ->
                cb.equal(raiz.get("cuenta").get("presupuesto").get("id"), presupuestoId);
    }

    public static Specification<Transaccion> deLaCuenta(Long cuentaId) {
        return (raiz, consulta, cb) -> cb.equal(raiz.get("cuenta").get("id"), cuentaId);
    }

    /** Coincide con la categoría de la transacción o con la de alguna subtransacción. */
    public static Specification<Transaccion> deLaCategoria(Long categoriaId) {
        return (raiz, consulta, cb) -> {
            Subquery<Long> sub = consulta.subquery(Long.class);
            Root<SubTransaccion> parte = sub.from(SubTransaccion.class);
            sub.select(parte.get("id"))
                    .where(
                            cb.equal(parte.get("transaccion"), raiz),
                            cb.equal(parte.get("categoria").get("id"), categoriaId));
            return cb.or(cb.equal(raiz.get("categoria").get("id"), categoriaId), cb.exists(sub));
        };
    }

    public static Specification<Transaccion> desde(LocalDate desde) {
        return (raiz, consulta, cb) -> cb.greaterThanOrEqualTo(raiz.get("fecha"), desde);
    }

    public static Specification<Transaccion> hasta(LocalDate hasta) {
        return (raiz, consulta, cb) -> cb.lessThanOrEqualTo(raiz.get("fecha"), hasta);
    }

    public static Specification<Transaccion> conEstado(EstadoTransaccion estado) {
        return (raiz, consulta, cb) -> cb.equal(raiz.get("estado"), estado);
    }

    public static Specification<Transaccion> sinAprobar() {
        return (raiz, consulta, cb) -> cb.isFalse(raiz.get("aprobada"));
    }

    /** Busca {@code texto} en beneficiario y memo sin distinguir mayúsculas, sin comodines. */
    public static Specification<Transaccion> contiene(String texto) {
        return (raiz, consulta, cb) -> {
            String patron = "%" + escaparComodines(texto.toLowerCase(Locale.ROOT)) + "%";
            Predicate enBeneficiario =
                    cb.like(cb.lower(raiz.get("beneficiario")), patron, ESCAPE);
            Predicate enMemo = cb.like(cb.lower(raiz.get("memo")), patron, ESCAPE);
            return cb.or(enBeneficiario, enMemo);
        };
    }

    /** Antepone el escape a {@code \}, {@code %} y {@code _} para que valgan como texto. */
    public static String escaparComodines(String texto) {
        StringBuilder resultado = new StringBuilder(texto.length());
        for (char c : texto.toCharArray()) {
            if (c == ESCAPE || c == '%' || c == '_') {
                resultado.append(ESCAPE);
            }
            resultado.append(c);
        }
        return resultado.toString();
    }
}
