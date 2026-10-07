package com.presupuesto.meta.controller;

import com.presupuesto.comun.seguridad.UsuarioAutenticado;
import com.presupuesto.meta.dto.request.GuardarMetaRequest;
import com.presupuesto.meta.dto.response.MetaResponse;
import com.presupuesto.meta.service.MetaService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/presupuestos/{presupuestoId}")
@RequiredArgsConstructor
public class MetaController {

    private final MetaService metaService;

    @PutMapping("/categorias/{categoriaId}/meta")
    public MetaResponse guardar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long categoriaId,
            @Valid @RequestBody GuardarMetaRequest request) {
        return metaService.guardar(presupuestoId, usuario.id(), categoriaId, request);
    }

    @GetMapping("/categorias/{categoriaId}/meta")
    public MetaResponse obtener(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long categoriaId) {
        return metaService.obtener(presupuestoId, usuario.id(), categoriaId);
    }

    @DeleteMapping("/categorias/{categoriaId}/meta")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long categoriaId) {
        metaService.borrar(presupuestoId, usuario.id(), categoriaId);
    }

    @GetMapping("/metas")
    public List<MetaResponse> listar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId) {
        return metaService.listar(presupuestoId, usuario.id());
    }
}
