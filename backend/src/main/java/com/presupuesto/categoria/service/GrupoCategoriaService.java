package com.presupuesto.categoria.service;

import com.presupuesto.categoria.dto.request.ActualizarGrupoCategoriaRequest;
import com.presupuesto.categoria.dto.request.CrearGrupoCategoriaRequest;
import com.presupuesto.categoria.dto.request.MoverGrupoCategoriaRequest;
import com.presupuesto.categoria.dto.response.GrupoCategoriaResponse;
import com.presupuesto.categoria.entity.GrupoCategoria;
import com.presupuesto.categoria.repository.GrupoCategoriaRepository;
import com.presupuesto.comun.excepcion.CodigoError;
import com.presupuesto.comun.excepcion.ConflictoException;
import com.presupuesto.comun.excepcion.DatosInvalidosException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.service.PresupuestoService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Toda operación valida primero que el presupuesto sea del usuario. */
@Service
@RequiredArgsConstructor
public class GrupoCategoriaService {

    static final String MENSAJE_NO_ENCONTRADO = "Grupo de categorías no encontrado";
    static final String MENSAJE_YA_EXISTE = "Ya existe un grupo de categorías con ese nombre";
    static final String MENSAJE_POSICION_INVALIDA = "La posición está fuera de rango";

    private final GrupoCategoriaRepository grupoRepository;
    private final PresupuestoService presupuestoService;

    @Transactional
    public GrupoCategoriaResponse crear(
            Long presupuestoId, Long usuarioId, CrearGrupoCategoriaRequest request) {
        Presupuesto presupuesto = presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        String normalizado = GrupoCategoria.normalizar(request.nombre());
        if (grupoRepository.existsByPresupuestoIdAndNombreNormalizado(
                presupuestoId, normalizado)) {
            throw yaExiste();
        }
        int orden = (int) grupoRepository.countByPresupuestoId(presupuestoId);
        return GrupoCategoriaResponse.desde(guardar(GrupoCategoria.builder()
                .presupuesto(presupuesto)
                .nombre(request.nombre())
                .nombreNormalizado(normalizado)
                .orden(orden)
                .build()));
    }

    @Transactional
    public GrupoCategoriaResponse renombrar(
            Long presupuestoId, Long usuarioId, Long id, ActualizarGrupoCategoriaRequest request) {
        GrupoCategoria grupo = buscar(presupuestoId, usuarioId, id);
        String normalizado = GrupoCategoria.normalizar(request.nombre());
        if (grupoRepository.existsByPresupuestoIdAndNombreNormalizadoAndIdNot(
                presupuestoId, normalizado, id)) {
            throw yaExiste();
        }
        grupo.renombrar(request.nombre());
        return GrupoCategoriaResponse.desde(guardar(grupo));
    }

    @Transactional
    public GrupoCategoriaResponse ocultar(Long presupuestoId, Long usuarioId, Long id) {
        GrupoCategoria grupo = buscar(presupuestoId, usuarioId, id);
        grupo.ocultar();
        return GrupoCategoriaResponse.desde(grupoRepository.saveAndFlush(grupo));
    }

    @Transactional
    public GrupoCategoriaResponse mostrar(Long presupuestoId, Long usuarioId, Long id) {
        GrupoCategoria grupo = buscar(presupuestoId, usuarioId, id);
        grupo.mostrar();
        return GrupoCategoriaResponse.desde(grupoRepository.saveAndFlush(grupo));
    }

    /** Reubica el grupo y renumera todos (ocultos incluidos) de 0 a n-1 sin huecos. */
    @Transactional
    public GrupoCategoriaResponse mover(
            Long presupuestoId, Long usuarioId, Long id, MoverGrupoCategoriaRequest request) {
        GrupoCategoria grupo = buscar(presupuestoId, usuarioId, id);
        List<GrupoCategoria> grupos =
                new ArrayList<>(grupoRepository.findByPresupuestoIdOrderByOrden(presupuestoId));
        int posicion = request.posicion();
        if (posicion >= grupos.size()) {
            throw new DatosInvalidosException(MENSAJE_POSICION_INVALIDA);
        }
        grupos.remove(grupo);
        grupos.add(posicion, grupo);
        List<GrupoCategoria> cambiados = new ArrayList<>();
        for (int i = 0; i < grupos.size(); i++) {
            if (grupos.get(i).getOrden() != i) {
                grupos.get(i).asignarOrden(i);
                cambiados.add(grupos.get(i));
            }
        }
        grupoRepository.saveAllAndFlush(cambiados);
        return GrupoCategoriaResponse.desde(grupo);
    }

    private GrupoCategoria buscar(Long presupuestoId, Long usuarioId, Long id) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        return grupoRepository.findByIdAndPresupuestoId(id, presupuestoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(MENSAJE_NO_ENCONTRADO));
    }

    /** Red de seguridad ante dos peticiones simultáneas con el mismo nombre. */
    private GrupoCategoria guardar(GrupoCategoria grupo) {
        try {
            return grupoRepository.saveAndFlush(grupo);
        } catch (DataIntegrityViolationException nombreDuplicado) {
            throw yaExiste();
        }
    }

    private static ConflictoException yaExiste() {
        return new ConflictoException(CodigoError.GRUPO_CATEGORIA_YA_EXISTE, MENSAJE_YA_EXISTE);
    }
}
