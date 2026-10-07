package com.presupuesto.meta.controller;

import com.presupuesto.comun.seguridad.UsuarioAutenticado;
import com.presupuesto.meta.dto.request.AutoAsignarRequest;
import com.presupuesto.meta.dto.response.AutoAsignarResponse;
import com.presupuesto.meta.dto.response.MetaMesResponse;
import com.presupuesto.meta.dto.response.MetasMesResponse;
import com.presupuesto.meta.service.AutoAsignarService;
import com.presupuesto.meta.service.MetaMesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/presupuestos/{presupuestoId}/meses/{mes}")
@RequiredArgsConstructor
public class MetaMesController {

    private final MetaMesService metaMesService;
    private final AutoAsignarService autoAsignarService;

    @GetMapping("/metas")
    public MetasMesResponse obtener(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable String mes,
            @RequestParam(defaultValue = "false") boolean incluirOcultas) {
        return metaMesService.obtener(presupuestoId, usuario.id(), mes, incluirOcultas);
    }

    @PostMapping("/metas/{categoriaId}/posponer")
    public MetaMesResponse posponer(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable String mes,
            @PathVariable Long categoriaId) {
        return metaMesService.posponer(presupuestoId, usuario.id(), mes, categoriaId);
    }

    @PostMapping("/metas/{categoriaId}/reanudar")
    public MetaMesResponse reanudar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable String mes,
            @PathVariable Long categoriaId) {
        return metaMesService.reanudar(presupuestoId, usuario.id(), mes, categoriaId);
    }

    @PostMapping("/auto-asignar")
    public AutoAsignarResponse autoAsignar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable String mes,
            @Valid @RequestBody AutoAsignarRequest request) {
        return autoAsignarService.autoAsignar(presupuestoId, usuario.id(), mes, request);
    }
}
