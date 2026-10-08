package com.presupuesto.categoria.service;

import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.entity.GrupoCategoria;
import com.presupuesto.categoria.entity.TipoGrupoCategoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.categoria.repository.GrupoCategoriaRepository;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.entity.TipoCuenta;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.repository.PresupuestoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Mantiene el grupo {@code Pagos de tarjetas de crédito} y la categoría {@code Pago: <tarjeta>}
 * de cada tarjeta del presupuesto. Lo usan el oyente de los eventos de cuenta y la migración, y
 * no valida pertenencia: quien lo llama ya tiene la cuenta. No lo alcanzan las protecciones 422,
 * que viven en los services de grupos y categorías.
 */
@Component
@RequiredArgsConstructor
class CategoriasPagoTarjeta {

    static final String NOMBRE_GRUPO = "Pagos de tarjetas de crédito";
    static final String PREFIJO_CATEGORIA = "Pago: ";
    static final int LARGO_MAXIMO = 100;
    static final String MENSAJE_PRESUPUESTO_NO_ENCONTRADO = "Presupuesto no encontrado";

    private final GrupoCategoriaRepository grupoRepository;
    private final CategoriaRepository categoriaRepository;
    private final PresupuestoRepository presupuestoRepository;

    /** Solo las tarjetas de crédito del presupuesto tienen categoría de pago. */
    static boolean aplica(Cuenta cuenta) {
        return cuenta.getTipo() == TipoCuenta.TARJETA_CREDITO && cuenta.isEnPresupuesto();
    }

    /**
     * El grupo de pagos del presupuesto, localizado por su tipo y nunca por su nombre; si falta
     * lo crea al final. Si otro grupo ya usa el nombre, toma {@code (2)}, {@code (3)}... Bloquea
     * la fila del presupuesto (uno solo por transacción) para que dos transacciones no creen
     * cada una su grupo.
     */
    GrupoCategoria asegurarGrupo(Long presupuestoId) {
        Presupuesto presupuesto = presupuestoRepository.findByIdParaActualizar(presupuestoId)
                .orElseThrow(
                        () -> new RecursoNoEncontradoException(MENSAJE_PRESUPUESTO_NO_ENCONTRADO));
        return grupoRepository
                .findFirstByPresupuestoIdAndTipo(presupuestoId, TipoGrupoCategoria.PAGOS_TARJETA)
                .orElseGet(() -> crearGrupo(presupuesto));
    }

    /** Crea la categoría de la tarjeta (oculta si está cerrada); si ya existe, la devuelve. */
    Categoria crear(Cuenta cuenta) {
        return categoriaRepository.findByCuentaTarjetaId(cuenta.getId())
                .orElseGet(() -> nueva(cuenta));
    }

    /** Alinea el nombre de la categoría con el de la tarjeta. */
    void renombrar(Cuenta cuenta) {
        Categoria categoria = crear(cuenta);
        categoria.renombrar(nombreDeLaCategoria(cuenta, categoria.getGrupo().getId(),
                categoria.getId()));
        categoriaRepository.saveAndFlush(categoria);
    }

    void ocultar(Cuenta cuenta) {
        Categoria categoria = crear(cuenta);
        categoria.ocultar();
        categoriaRepository.saveAndFlush(categoria);
    }

    void mostrar(Cuenta cuenta) {
        Categoria categoria = crear(cuenta);
        categoria.mostrar();
        categoriaRepository.saveAndFlush(categoria);
    }

    private Categoria nueva(Cuenta cuenta) {
        GrupoCategoria grupo = asegurarGrupo(cuenta.getPresupuesto().getId());
        String nombre = nombreDeLaCategoria(cuenta, grupo.getId(), null);
        Categoria categoria = Categoria.builder()
                .grupo(grupo)
                .nombre(nombre)
                .nombreNormalizado(Categoria.normalizar(nombre))
                .orden((int) categoriaRepository.countByGrupoId(grupo.getId()))
                .cuentaTarjeta(cuenta)
                .build();
        if (cuenta.isCerrada()) {
            categoria.ocultar();
        }
        return categoriaRepository.saveAndFlush(categoria);
    }

    private GrupoCategoria crearGrupo(Presupuesto presupuesto) {
        Long presupuestoId = presupuesto.getId();
        String nombre = nombreLibreDelGrupo(presupuestoId);
        return grupoRepository.saveAndFlush(GrupoCategoria.builder()
                .presupuesto(presupuesto)
                .nombre(nombre)
                .nombreNormalizado(GrupoCategoria.normalizar(nombre))
                .orden((int) grupoRepository.countByPresupuestoId(presupuestoId))
                .tipo(TipoGrupoCategoria.PAGOS_TARJETA)
                .build());
    }

    private String nombreLibreDelGrupo(Long presupuestoId) {
        String nombre = NOMBRE_GRUPO;
        for (int numero = 2; grupoRepository.existsByPresupuestoIdAndNombreNormalizado(
                presupuestoId, GrupoCategoria.normalizar(nombre)); numero++) {
            nombre = NOMBRE_GRUPO + " (" + numero + ")";
        }
        return nombre;
    }

    /**
     * {@code Pago: <tarjeta>} recortado para no pasar de 100 caracteres. Si otra categoría del
     * grupo ya tiene ese nombre (la propia, {@code propiaId}, no cuenta), se recorta para dejar
     * lugar a ` (<id de la cuenta>)` y se lo agrega.
     */
    private String nombreDeLaCategoria(Cuenta cuenta, Long grupoId, Long propiaId) {
        String completo = PREFIJO_CATEGORIA + cuenta.getNombre();
        String nombre = recortar(completo, LARGO_MAXIMO);
        if (existe(grupoId, nombre, propiaId)) {
            String sufijo = " (" + cuenta.getId() + ")";
            nombre = recortar(completo, LARGO_MAXIMO - sufijo.length()) + sufijo;
        }
        return nombre;
    }

    private boolean existe(Long grupoId, String nombre, Long propiaId) {
        String normalizado = Categoria.normalizar(nombre);
        return propiaId == null
                ? categoriaRepository.existsByGrupoIdAndNombreNormalizado(grupoId, normalizado)
                : categoriaRepository.existsByGrupoIdAndNombreNormalizadoAndIdNot(
                        grupoId, normalizado, propiaId);
    }

    /** Recorta por caracteres completos (code points), sin partir un par sustituto. */
    static String recortar(String texto, int maximo) {
        if (texto.codePointCount(0, texto.length()) <= maximo) {
            return texto;
        }
        return texto.substring(0, texto.offsetByCodePoints(0, maximo));
    }
}
