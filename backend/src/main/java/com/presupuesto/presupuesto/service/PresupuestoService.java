package com.presupuesto.presupuesto.service;

import com.presupuesto.comun.excepcion.CodigoError;
import com.presupuesto.comun.excepcion.ConflictoException;
import com.presupuesto.comun.excepcion.NoAutenticadoException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.presupuesto.dto.request.ActualizarPresupuestoRequest;
import com.presupuesto.presupuesto.dto.request.CrearPresupuestoRequest;
import com.presupuesto.presupuesto.dto.response.PresupuestoResponse;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.evento.PresupuestoCreadoEvento;
import com.presupuesto.presupuesto.repository.PresupuestoRepository;
import com.presupuesto.usuario.entity.Perfil;
import com.presupuesto.usuario.entity.Usuario;
import com.presupuesto.usuario.repository.PerfilRepository;
import com.presupuesto.usuario.repository.UsuarioRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PresupuestoService {

    public static final String NOMBRE_PRESUPUESTO_INICIAL = "Mi presupuesto";

    static final String MENSAJE_NO_ENCONTRADO = "Presupuesto no encontrado";
    static final String MENSAJE_YA_EXISTE = "Ya tienes un presupuesto con ese nombre";

    private final PresupuestoRepository presupuestoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PerfilRepository perfilRepository;
    private final ApplicationEventPublisher publicador;

    /**
     * Devuelve el presupuesto si pertenece al usuario. Inexistente y ajeno responden igual
     * (404), para no revelar qué ids existen. Toda feature que cuelgue de un presupuesto debe
     * llamarlo antes de operar con los datos de la URL.
     */
    @Transactional(readOnly = true)
    public Presupuesto obtenerDelUsuario(Long presupuestoId, Long usuarioId) {
        return presupuestoRepository.findByIdAndUsuarioId(presupuestoId, usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException(MENSAJE_NO_ENCONTRADO));
    }

    /** Sin moneda en el request, usa la moneda predeterminada del perfil del usuario. */
    @Transactional
    public PresupuestoResponse crear(Long usuarioId, CrearPresupuestoRequest request) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(PresupuestoService::noAutenticado);
        String moneda = request.moneda() != null
                ? request.moneda()
                : perfilRepository.findByUsuarioId(usuarioId)
                        .map(Perfil::getMonedaPredeterminada)
                        .orElseThrow(PresupuestoService::noAutenticado);
        return PresupuestoResponse.desde(guardarNuevo(usuario, request.nombre(), moneda));
    }

    /** Presupuesto que recibe toda persona al registrarse; se une a la transacción del alta. */
    @Transactional
    public Presupuesto crearInicial(Usuario usuario, String moneda) {
        return guardarNuevo(usuario, NOMBRE_PRESUPUESTO_INICIAL, moneda);
    }

    @Transactional(readOnly = true)
    public List<PresupuestoResponse> listar(Long usuarioId) {
        return presupuestoRepository.findByUsuarioIdOrderByNombreNormalizado(usuarioId).stream()
                .map(PresupuestoResponse::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public PresupuestoResponse obtener(Long presupuestoId, Long usuarioId) {
        return PresupuestoResponse.desde(obtenerDelUsuario(presupuestoId, usuarioId));
    }

    /** Solo cambia el nombre; la moneda nunca se modifica. */
    @Transactional
    public PresupuestoResponse renombrar(
            Long presupuestoId, Long usuarioId, ActualizarPresupuestoRequest request) {
        Presupuesto presupuesto = obtenerDelUsuario(presupuestoId, usuarioId);
        String normalizado = Presupuesto.normalizar(request.nombre());
        if (presupuestoRepository.existsByUsuarioIdAndNombreNormalizadoAndIdNot(
                usuarioId, normalizado, presupuestoId)) {
            throw yaExiste();
        }
        presupuesto.renombrar(request.nombre());
        return PresupuestoResponse.desde(guardar(presupuesto));
    }

    private Presupuesto guardarNuevo(Usuario usuario, String nombre, String moneda) {
        String normalizado = Presupuesto.normalizar(nombre);
        if (presupuestoRepository.existsByUsuarioIdAndNombreNormalizado(
                usuario.getId(), normalizado)) {
            throw yaExiste();
        }
        Presupuesto presupuesto = guardar(Presupuesto.builder()
                .usuario(usuario)
                .nombre(nombre)
                .nombreNormalizado(normalizado)
                .moneda(moneda)
                .build());
        // Síncrono: los oyentes corren en esta transacción y un fallo los revierte a todos.
        publicador.publishEvent(new PresupuestoCreadoEvento(presupuesto));
        return presupuesto;
    }

    /** Red de seguridad ante dos peticiones simultáneas con el mismo nombre. */
    private Presupuesto guardar(Presupuesto presupuesto) {
        try {
            return presupuestoRepository.saveAndFlush(presupuesto);
        } catch (DataIntegrityViolationException nombreDuplicado) {
            throw yaExiste();
        }
    }

    private static ConflictoException yaExiste() {
        return new ConflictoException(CodigoError.PRESUPUESTO_YA_EXISTE, MENSAJE_YA_EXISTE);
    }

    private static NoAutenticadoException noAutenticado() {
        return new NoAutenticadoException(
                CodigoError.NO_AUTENTICADO, NoAutenticadoException.MENSAJE_NO_AUTENTICADO);
    }
}
