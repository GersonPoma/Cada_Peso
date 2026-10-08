package com.presupuesto.categoria.service;

import com.presupuesto.categoria.dto.request.ActualizarCategoriaRequest;
import com.presupuesto.categoria.dto.request.CrearCategoriaRequest;
import com.presupuesto.categoria.dto.request.MoverCategoriaRequest;
import com.presupuesto.categoria.dto.response.CategoriaResponse;
import com.presupuesto.categoria.dto.response.GrupoCategoriaConCategoriasResponse;
import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.entity.GrupoCategoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.categoria.repository.GrupoCategoriaRepository;
import com.presupuesto.comun.excepcion.CodigoError;
import com.presupuesto.comun.excepcion.ConflictoException;
import com.presupuesto.comun.excepcion.DatosInvalidosException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.comun.excepcion.ReglaNegocioException;
import com.presupuesto.presupuesto.service.PresupuestoService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Toda operación valida primero que el presupuesto sea del usuario. */
@Service
@RequiredArgsConstructor
public class CategoriaService {

    static final String MENSAJE_NO_ENCONTRADA = "Categoría no encontrada";
    static final String MENSAJE_YA_EXISTE = "Ya existe una categoría con ese nombre en el grupo";
    static final String MENSAJE_POSICION_INVALIDA = "La posición está fuera de rango";
    static final String MENSAJE_CATEGORIA_DE_PAGO =
            "Las categorías de pago de tarjeta las gestiona el sistema";
    static final String MENSAJE_GRUPO_DE_PAGOS =
            "No se pueden agregar categorías al grupo de pagos de tarjetas";

    private final CategoriaRepository categoriaRepository;
    private final GrupoCategoriaRepository grupoRepository;
    private final PresupuestoService presupuestoService;

    @Transactional
    public CategoriaResponse crear(
            Long presupuestoId, Long usuarioId, CrearCategoriaRequest request) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        GrupoCategoria grupo = buscarGrupo(presupuestoId, request.grupoId());
        exigirGrupoNormal(grupo);
        String normalizado = Categoria.normalizar(request.nombre());
        if (categoriaRepository.existsByGrupoIdAndNombreNormalizado(grupo.getId(), normalizado)) {
            throw yaExiste();
        }
        int orden = (int) categoriaRepository.countByGrupoId(grupo.getId());
        Categoria categoria = Categoria.builder()
                .grupo(grupo)
                .nombre(request.nombre())
                .nombreNormalizado(normalizado)
                .orden(orden)
                .nota(request.nota())
                .build();
        return CategoriaResponse.desde(guardar(categoria));
    }

    @Transactional(readOnly = true)
    public CategoriaResponse obtener(Long presupuestoId, Long usuarioId, Long id) {
        return CategoriaResponse.desde(buscar(presupuestoId, usuarioId, id));
    }

    /** Solo nombre y nota; el grupo y el orden nunca cambian por aquí. */
    @Transactional
    public CategoriaResponse actualizar(
            Long presupuestoId, Long usuarioId, Long id, ActualizarCategoriaRequest request) {
        Categoria categoria = buscar(presupuestoId, usuarioId, id);
        exigirNoEsDePago(categoria);
        String normalizado = Categoria.normalizar(request.nombre());
        if (categoriaRepository.existsByGrupoIdAndNombreNormalizadoAndIdNot(
                categoria.getGrupo().getId(), normalizado, id)) {
            throw yaExiste();
        }
        categoria.renombrar(request.nombre());
        categoria.cambiarNota(request.nota());
        return CategoriaResponse.desde(guardar(categoria));
    }

    @Transactional
    public CategoriaResponse ocultar(Long presupuestoId, Long usuarioId, Long id) {
        Categoria categoria = buscar(presupuestoId, usuarioId, id);
        exigirNoEsDePago(categoria);
        categoria.ocultar();
        return CategoriaResponse.desde(categoriaRepository.saveAndFlush(categoria));
    }

    @Transactional
    public CategoriaResponse mostrar(Long presupuestoId, Long usuarioId, Long id) {
        Categoria categoria = buscar(presupuestoId, usuarioId, id);
        exigirNoEsDePago(categoria);
        categoria.mostrar();
        return CategoriaResponse.desde(categoriaRepository.saveAndFlush(categoria));
    }

    /**
     * Mueve la categoría dentro de su grupo ({@code posicion} en [0, n-1]) o a otro grupo del
     * presupuesto ({@code posicion} en [0, m], con m las categorías del destino) y renumera sin
     * huecos el origen y el destino.
     */
    @Transactional
    public CategoriaResponse mover(
            Long presupuestoId, Long usuarioId, Long id, MoverCategoriaRequest request) {
        Categoria categoria = buscar(presupuestoId, usuarioId, id);
        GrupoCategoria destino = buscarGrupo(presupuestoId, request.grupoId());
        exigirNoEsDePago(categoria);
        exigirGrupoNormal(destino);
        Long origenId = categoria.getGrupo().getId();
        boolean mismoGrupo = destino.getId().equals(origenId);
        int posicion = request.posicion();

        List<Categoria> categoriasDestino =
                new ArrayList<>(categoriaRepository.findByGrupoIdOrderByOrden(destino.getId()));
        int maximo = mismoGrupo ? categoriasDestino.size() - 1 : categoriasDestino.size();
        if (posicion > maximo) {
            throw new DatosInvalidosException(MENSAJE_POSICION_INVALIDA);
        }

        List<Categoria> cambiadas = new ArrayList<>();
        if (mismoGrupo) {
            categoriasDestino.remove(categoria);
        } else {
            if (categoriaRepository.existsByGrupoIdAndNombreNormalizado(
                    destino.getId(), categoria.getNombreNormalizado())) {
                throw yaExiste();
            }
            List<Categoria> categoriasOrigen =
                    new ArrayList<>(categoriaRepository.findByGrupoIdOrderByOrden(origenId));
            categoriasOrigen.remove(categoria);
            renumerar(categoriasOrigen, cambiadas);
            categoria.moverAGrupo(destino);
            cambiadas.add(categoria);
        }
        categoriasDestino.add(posicion, categoria);
        renumerar(categoriasDestino, cambiadas);

        try {
            categoriaRepository.saveAllAndFlush(cambiadas.stream().distinct().toList());
        } catch (DataIntegrityViolationException nombreDuplicado) {
            throw yaExiste();
        }
        return CategoriaResponse.desde(categoria);
    }

    /** Grupos y categorías por {@code orden}; sin {@code incluirOcultas} omite los ocultos. */
    @Transactional(readOnly = true)
    public List<GrupoCategoriaConCategoriasResponse> arbol(
            Long presupuestoId, Long usuarioId, boolean incluirOcultas) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        List<GrupoCategoria> grupos = incluirOcultas
                ? grupoRepository.findByPresupuestoIdOrderByOrden(presupuestoId)
                : grupoRepository.findByPresupuestoIdAndOcultoFalseOrderByOrden(presupuestoId);
        List<Categoria> categorias = incluirOcultas
                ? categoriaRepository.findByGrupoPresupuestoIdOrderByOrden(presupuestoId)
                : categoriaRepository
                        .findByGrupoPresupuestoIdAndOcultaFalseOrderByOrden(presupuestoId);
        Map<Long, List<Categoria>> porGrupo = new HashMap<>();
        for (Categoria categoria : categorias) {
            porGrupo.computeIfAbsent(categoria.getGrupo().getId(), clave -> new ArrayList<>())
                    .add(categoria);
        }
        return grupos.stream()
                .map(grupo -> GrupoCategoriaConCategoriasResponse.desde(
                        grupo, porGrupo.getOrDefault(grupo.getId(), List.of())))
                .toList();
    }

    private static void renumerar(List<Categoria> categorias, List<Categoria> cambiadas) {
        for (int i = 0; i < categorias.size(); i++) {
            Categoria categoria = categorias.get(i);
            if (categoria.getOrden() != i) {
                categoria.asignarOrden(i);
                cambiadas.add(categoria);
            }
        }
    }

    /** Las categorías de pago de tarjeta no se editan, ocultan, muestran ni mueven a mano. */
    private static void exigirNoEsDePago(Categoria categoria) {
        if (categoria.esPagoTarjeta()) {
            throw new ReglaNegocioException(MENSAJE_CATEGORIA_DE_PAGO);
        }
    }

    /** Al grupo de pagos de tarjetas no se crean ni se mueven categorías. */
    private static void exigirGrupoNormal(GrupoCategoria grupo) {
        if (grupo.esPagosTarjeta()) {
            throw new ReglaNegocioException(MENSAJE_GRUPO_DE_PAGOS);
        }
    }

    private Categoria buscar(Long presupuestoId, Long usuarioId, Long id) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        return categoriaRepository.findByIdAndGrupoPresupuestoId(id, presupuestoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(MENSAJE_NO_ENCONTRADA));
    }

    private GrupoCategoria buscarGrupo(Long presupuestoId, Long grupoId) {
        return grupoRepository.findByIdAndPresupuestoId(grupoId, presupuestoId)
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        GrupoCategoriaService.MENSAJE_NO_ENCONTRADO));
    }

    /** Red de seguridad ante dos peticiones simultáneas con el mismo nombre. */
    private Categoria guardar(Categoria categoria) {
        try {
            return categoriaRepository.saveAndFlush(categoria);
        } catch (DataIntegrityViolationException nombreDuplicado) {
            throw yaExiste();
        }
    }

    private static ConflictoException yaExiste() {
        return new ConflictoException(CodigoError.CATEGORIA_YA_EXISTE, MENSAJE_YA_EXISTE);
    }
}
