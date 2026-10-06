package com.presupuesto.presupuesto.controller;

import com.presupuesto.comun.seguridad.UsuarioAutenticado;
import com.presupuesto.presupuesto.dto.request.ActualizarPresupuestoRequest;
import com.presupuesto.presupuesto.dto.request.CrearPresupuestoRequest;
import com.presupuesto.presupuesto.dto.response.PresupuestoResponse;
import com.presupuesto.presupuesto.service.PresupuestoService;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/presupuestos")
@RequiredArgsConstructor
public class PresupuestoController {

    private final PresupuestoService presupuestoService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PresupuestoResponse crear(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @Valid @RequestBody CrearPresupuestoRequest request) {
        return presupuestoService.crear(usuario.id(), request);
    }

    @GetMapping
    public List<PresupuestoResponse> listar(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return presupuestoService.listar(usuario.id());
    }

    @GetMapping("/{id}")
    public PresupuestoResponse obtener(
            @AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable Long id) {
        return presupuestoService.obtener(id, usuario.id());
    }

    @PutMapping("/{id}")
    public PresupuestoResponse renombrar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long id,
            @Valid @RequestBody ActualizarPresupuestoRequest request) {
        return presupuestoService.renombrar(id, usuario.id(), request);
    }
}
