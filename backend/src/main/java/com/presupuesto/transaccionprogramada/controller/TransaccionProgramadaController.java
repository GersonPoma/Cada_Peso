package com.presupuesto.transaccionprogramada.controller;

import com.presupuesto.comun.seguridad.UsuarioAutenticado;
import com.presupuesto.transaccionprogramada.dto.request.ActualizarProgramadaRequest;
import com.presupuesto.transaccionprogramada.dto.request.CrearProgramadaRequest;
import com.presupuesto.transaccionprogramada.dto.response.GeneracionResponse;
import com.presupuesto.transaccionprogramada.dto.response.TransaccionProgramadaResponse;
import com.presupuesto.transaccionprogramada.service.TransaccionProgramadaService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/presupuestos/{presupuestoId}/transacciones-programadas")
@RequiredArgsConstructor
public class TransaccionProgramadaController {

    private final TransaccionProgramadaService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransaccionProgramadaResponse crear(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @Valid @RequestBody CrearProgramadaRequest request) {
        return service.crear(presupuestoId, usuario.id(), request);
    }

    @GetMapping
    public List<TransaccionProgramadaResponse> listar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @RequestParam(defaultValue = "false") boolean soloActivas) {
        return service.listar(presupuestoId, usuario.id(), soloActivas);
    }

    @PostMapping("/generar")
    public GeneracionResponse generar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId) {
        return service.generar(presupuestoId, usuario.id());
    }

    @GetMapping("/{id}")
    public TransaccionProgramadaResponse obtener(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long id) {
        return service.obtener(presupuestoId, usuario.id(), id);
    }

    @PutMapping("/{id}")
    public TransaccionProgramadaResponse actualizar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long id,
            @Valid @RequestBody ActualizarProgramadaRequest request) {
        return service.actualizar(presupuestoId, usuario.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long id) {
        service.borrar(presupuestoId, usuario.id(), id);
    }

    @PostMapping("/{id}/pausar")
    public TransaccionProgramadaResponse pausar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long id) {
        return service.pausar(presupuestoId, usuario.id(), id);
    }

    @PostMapping("/{id}/reanudar")
    public TransaccionProgramadaResponse reanudar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long id) {
        return service.reanudar(presupuestoId, usuario.id(), id);
    }
}
