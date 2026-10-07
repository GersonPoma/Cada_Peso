package com.presupuesto.beneficiario.controller;

import com.presupuesto.beneficiario.dto.request.ActualizarBeneficiarioRequest;
import com.presupuesto.beneficiario.dto.request.CrearBeneficiarioRequest;
import com.presupuesto.beneficiario.dto.response.BeneficiarioResponse;
import com.presupuesto.beneficiario.service.BeneficiarioService;
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
@RequestMapping("/api/v1/presupuestos/{presupuestoId}/beneficiarios")
@RequiredArgsConstructor
public class BeneficiarioController {

    private final BeneficiarioService beneficiarioService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BeneficiarioResponse crear(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @Valid @RequestBody CrearBeneficiarioRequest request) {
        return beneficiarioService.crear(presupuestoId, usuario.id(), request);
    }

    @GetMapping
    public List<BeneficiarioResponse> listar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer limite) {
        return beneficiarioService.listar(presupuestoId, usuario.id(), q, limite);
    }

    @GetMapping("/{id}")
    public BeneficiarioResponse obtener(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long id) {
        return beneficiarioService.obtener(presupuestoId, usuario.id(), id);
    }

    @PutMapping("/{id}")
    public BeneficiarioResponse actualizar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long id,
            @Valid @RequestBody ActualizarBeneficiarioRequest request) {
        return beneficiarioService.actualizar(presupuestoId, usuario.id(), id, request);
    }
}
