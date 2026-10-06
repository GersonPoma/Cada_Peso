package com.presupuesto.asignacion.service;

import com.presupuesto.asignacion.dto.response.CategoriaMesResponse;
import com.presupuesto.asignacion.dto.response.GrupoMesResponse;
import com.presupuesto.asignacion.dto.response.MesPresupuestoResponse;
import com.presupuesto.asignacion.service.CalculoMensual.FilaMes;
import com.presupuesto.asignacion.service.CalculoMensual.ResultadoMes;
import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.entity.GrupoCategoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.categoria.repository.GrupoCategoriaRepository;
import com.presupuesto.presupuesto.service.PresupuestoService;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Toda operación valida primero que el presupuesto sea del usuario. */
@Service
@RequiredArgsConstructor
public class MesPresupuestoService {

    private final GrupoCategoriaRepository grupoRepository;
    private final CategoriaRepository categoriaRepository;
    private final CalculadoraMes calculadora;
    private final PresupuestoService presupuestoService;

    @Transactional(readOnly = true)
    public MesPresupuestoResponse obtener(
            Long presupuestoId, Long usuarioId, String mes, boolean incluirOcultas) {
        presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        return construir(presupuestoId, MesParametro.interpretar(mes), incluirOcultas);
    }

    /** El mes ya calculado, con grupos y categorías en su orden (con o sin las ocultas). */
    MesPresupuestoResponse construir(
            Long presupuestoId, YearMonth mes, boolean incluirOcultas) {
        ResultadoMes resultado = calculadora.calcular(presupuestoId, mes);
        List<GrupoCategoria> grupos = incluirOcultas
                ? grupoRepository.findByPresupuestoIdOrderByOrden(presupuestoId)
                : grupoRepository.findByPresupuestoIdAndOcultoFalseOrderByOrden(presupuestoId);
        List<Categoria> categorias = incluirOcultas
                ? categoriaRepository.findByGrupoPresupuestoIdOrderByOrden(presupuestoId)
                : categoriaRepository
                        .findByGrupoPresupuestoIdAndOcultaFalseOrderByOrden(presupuestoId);
        Map<Long, List<CategoriaMesResponse>> porGrupo = new HashMap<>();
        for (Categoria categoria : categorias) {
            porGrupo.computeIfAbsent(categoria.getGrupo().getId(), clave -> new ArrayList<>())
                    .add(fila(categoria, resultado));
        }
        List<GrupoMesResponse> respuestaGrupos = grupos.stream()
                .map(grupo -> GrupoMesResponse.desde(
                        grupo, porGrupo.getOrDefault(grupo.getId(), List.of())))
                .toList();
        return MesPresupuestoResponse.desde(mes, resultado.listoParaAsignar(), respuestaGrupos);
    }

    static CategoriaMesResponse fila(Categoria categoria, ResultadoMes resultado) {
        FilaMes fila = resultado.fila(categoria.getId());
        return CategoriaMesResponse.desde(
                categoria, fila.asignado(), fila.actividad(), fila.disponible());
    }
}
