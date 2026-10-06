package com.presupuesto.categoria.controller;

import com.presupuesto.categoria.dto.request.ActualizarCategoriaRequest;
import com.presupuesto.categoria.dto.request.CrearCategoriaRequest;
import com.presupuesto.categoria.dto.request.MoverCategoriaRequest;
import com.presupuesto.categoria.dto.response.CategoriaResponse;
import com.presupuesto.categoria.dto.response.GrupoCategoriaConCategoriasResponse;
import com.presupuesto.categoria.service.CategoriaService;
import com.presupuesto.comun.seguridad.UsuarioAutenticado;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/presupuestos/{presupuestoId}/categorias")
@RequiredArgsConstructor
public class CategoriaController {

    private final CategoriaService categoriaService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoriaResponse crear(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @Valid @RequestBody CrearCategoriaRequest request) {
        return categoriaService.crear(presupuestoId, usuario.id(), request);
    }

    @GetMapping
    public List<GrupoCategoriaConCategoriasResponse> arbol(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @RequestParam(defaultValue = "false") boolean incluirOcultas) {
        return categoriaService.arbol(presupuestoId, usuario.id(), incluirOcultas);
    }

    @GetMapping("/{id}")
    public CategoriaResponse obtener(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long id) {
        return categoriaService.obtener(presupuestoId, usuario.id(), id);
    }

    @PutMapping("/{id}")
    public CategoriaResponse actualizar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long id,
            @Valid @RequestBody ActualizarCategoriaRequest request) {
        return categoriaService.actualizar(presupuestoId, usuario.id(), id, request);
    }

    @PostMapping("/{id}/ocultar")
    public CategoriaResponse ocultar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long id) {
        return categoriaService.ocultar(presupuestoId, usuario.id(), id);
    }

    @PostMapping("/{id}/mostrar")
    public CategoriaResponse mostrar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long id) {
        return categoriaService.mostrar(presupuestoId, usuario.id(), id);
    }

    @PostMapping("/{id}/mover")
    public CategoriaResponse mover(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long id,
            @Valid @RequestBody MoverCategoriaRequest request) {
        return categoriaService.mover(presupuestoId, usuario.id(), id, request);
    }
}
