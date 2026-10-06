package com.presupuesto.categoria.controller;

import com.presupuesto.categoria.dto.request.ActualizarGrupoCategoriaRequest;
import com.presupuesto.categoria.dto.request.CrearGrupoCategoriaRequest;
import com.presupuesto.categoria.dto.request.MoverGrupoCategoriaRequest;
import com.presupuesto.categoria.dto.response.GrupoCategoriaResponse;
import com.presupuesto.categoria.service.GrupoCategoriaService;
import com.presupuesto.comun.seguridad.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/presupuestos/{presupuestoId}/grupos-categorias")
@RequiredArgsConstructor
public class GrupoCategoriaController {

    private final GrupoCategoriaService grupoCategoriaService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GrupoCategoriaResponse crear(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @Valid @RequestBody CrearGrupoCategoriaRequest request) {
        return grupoCategoriaService.crear(presupuestoId, usuario.id(), request);
    }

    @PutMapping("/{id}")
    public GrupoCategoriaResponse renombrar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long id,
            @Valid @RequestBody ActualizarGrupoCategoriaRequest request) {
        return grupoCategoriaService.renombrar(presupuestoId, usuario.id(), id, request);
    }

    @PostMapping("/{id}/ocultar")
    public GrupoCategoriaResponse ocultar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long id) {
        return grupoCategoriaService.ocultar(presupuestoId, usuario.id(), id);
    }

    @PostMapping("/{id}/mostrar")
    public GrupoCategoriaResponse mostrar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long id) {
        return grupoCategoriaService.mostrar(presupuestoId, usuario.id(), id);
    }

    @PostMapping("/{id}/mover")
    public GrupoCategoriaResponse mover(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long id,
            @Valid @RequestBody MoverGrupoCategoriaRequest request) {
        return grupoCategoriaService.mover(presupuestoId, usuario.id(), id, request);
    }
}
