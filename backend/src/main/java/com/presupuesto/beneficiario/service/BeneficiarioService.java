package com.presupuesto.beneficiario.service;

import com.presupuesto.beneficiario.dto.request.ActualizarBeneficiarioRequest;
import com.presupuesto.beneficiario.dto.request.CrearBeneficiarioRequest;
import com.presupuesto.beneficiario.dto.response.BeneficiarioResponse;
import com.presupuesto.beneficiario.entity.Beneficiario;
import com.presupuesto.beneficiario.repository.BeneficiarioRepository;
import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.comun.excepcion.CodigoError;
import com.presupuesto.comun.excepcion.ConflictoException;
import com.presupuesto.comun.excepcion.DatosInvalidosException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.service.PresupuestoService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Toda operación HTTP valida primero que el presupuesto sea del usuario. */
@Service
@RequiredArgsConstructor
public class BeneficiarioService {

    public static final int LIMITE_POR_DEFECTO = 10;
    public static final int LIMITE_MAXIMO = 50;

    static final String MENSAJE_NO_ENCONTRADO = "Beneficiario no encontrado";
    static final String MENSAJE_CATEGORIA_NO_ENCONTRADA = "Categoría no encontrada";
    static final String MENSAJE_YA_EXISTE = "Ya existe un beneficiario con ese nombre";
    static final String MENSAJE_LIMITE_INVALIDO = "El límite debe estar entre 1 y 50";

    private final BeneficiarioRepository beneficiarioRepository;
    private final CategoriaRepository categoriaRepository;
    private final PresupuestoService presupuestoService;

    @Transactional
    public BeneficiarioResponse crear(
            Long presupuestoId, Long usuarioId, CrearBeneficiarioRequest request) {
        Presupuesto presupuesto = presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        String normalizado = Beneficiario.normalizar(request.nombre());
        if (beneficiarioRepository.existsByPresupuestoIdAndNombreNormalizado(
                presupuestoId, normalizado)) {
            throw yaExiste();
        }
        Categoria categoria = categoria(request.categoriaId(), presupuestoId);
        return BeneficiarioResponse.desde(guardar(Beneficiario.builder()
                .presupuesto(presupuesto)
                .nombre(request.nombre())
                .nombreNormalizado(normalizado)
                .categoriaPredeterminada(categoria)
                .build()));
    }

    /**
     * Sin {@code q} devuelve todos, ordenados por nombre; con {@code q}, solo los que empiezan por
     * ese texto, hasta {@code limite} (10 por defecto). {@code limite} se valida siempre que se
     * envíe, pero solo se aplica con {@code q}.
     */
    @Transactional(readOnly = true)
    public List<BeneficiarioResponse> listar(
            Long presupuestoId, Long usuarioId, String q, Integer limite) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        if (limite != null && (limite < 1 || limite > LIMITE_MAXIMO)) {
            throw new DatosInvalidosException(MENSAJE_LIMITE_INVALIDO);
        }
        String prefijo = q == null ? "" : q.strip();
        List<Beneficiario> beneficiarios;
        if (prefijo.isEmpty()) {
            beneficiarios = beneficiarioRepository
                    .findByPresupuestoIdOrderByNombreNormalizado(presupuestoId);
        } else {
            int tope = limite == null ? LIMITE_POR_DEFECTO : limite;
            beneficiarios = beneficiarioRepository.buscarPorPrefijo(
                    presupuestoId, patronDePrefijo(prefijo), PageRequest.of(0, tope));
        }
        return beneficiarios.stream().map(BeneficiarioResponse::desde).toList();
    }

    @Transactional(readOnly = true)
    public BeneficiarioResponse obtener(Long presupuestoId, Long usuarioId, Long id) {
        return BeneficiarioResponse.desde(buscar(presupuestoId, usuarioId, id));
    }

    /** Renombra y fija la categoría predeterminada; {@code categoriaId} nulo la quita. */
    @Transactional
    public BeneficiarioResponse actualizar(
            Long presupuestoId, Long usuarioId, Long id, ActualizarBeneficiarioRequest request) {
        Beneficiario beneficiario = buscar(presupuestoId, usuarioId, id);
        String normalizado = Beneficiario.normalizar(request.nombre());
        if (beneficiarioRepository.existsByPresupuestoIdAndNombreNormalizadoAndIdNot(
                presupuestoId, normalizado, id)) {
            throw yaExiste();
        }
        Categoria categoria = categoria(request.categoriaId(), presupuestoId);
        beneficiario.renombrar(request.nombre());
        beneficiario.cambiarCategoriaPredeterminada(categoria);
        return BeneficiarioResponse.desde(guardar(beneficiario));
    }

    /**
     * Para las transacciones: el beneficiario del presupuesto con ese nombre (sin distinguir
     * mayúsculas) o uno nuevo sin categoría. El presupuesto ya está validado por el llamador y
     * {@code nombre} llega recortado y no vacío.
     */
    @Transactional
    public Beneficiario obtenerOCrear(Presupuesto presupuesto, String nombre) {
        String normalizado = Beneficiario.normalizar(nombre);
        return beneficiarioRepository
                .findByPresupuestoIdAndNombreNormalizado(presupuesto.getId(), normalizado)
                .orElseGet(() -> guardar(Beneficiario.builder()
                        .presupuesto(presupuesto)
                        .nombre(nombre)
                        .nombreNormalizado(normalizado)
                        .build()));
    }

    private Beneficiario buscar(Long presupuestoId, Long usuarioId, Long id) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        return beneficiarioRepository.findByIdAndPresupuestoId(id, presupuestoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(MENSAJE_NO_ENCONTRADO));
    }

    /** {@code null} si no se indica categoría; 404 si no es del presupuesto (oculta sí sirve). */
    private Categoria categoria(Long categoriaId, Long presupuestoId) {
        if (categoriaId == null) {
            return null;
        }
        return categoriaRepository.findByIdAndGrupoPresupuestoId(categoriaId, presupuestoId)
                .orElseThrow(
                        () -> new RecursoNoEncontradoException(MENSAJE_CATEGORIA_NO_ENCONTRADA));
    }

    /** Minúsculas, con {@code !}, {@code %} y {@code _} escapados con {@code !}; termina en %. */
    static String patronDePrefijo(String prefijo) {
        String escapado = Beneficiario.normalizar(prefijo)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return escapado + "%";
    }

    /** Red de seguridad ante dos peticiones simultáneas con el mismo nombre. */
    private Beneficiario guardar(Beneficiario beneficiario) {
        try {
            return beneficiarioRepository.saveAndFlush(beneficiario);
        } catch (DataIntegrityViolationException nombreDuplicado) {
            throw yaExiste();
        }
    }

    private static ConflictoException yaExiste() {
        return new ConflictoException(CodigoError.BENEFICIARIO_YA_EXISTE, MENSAJE_YA_EXISTE);
    }
}
