package com.presupuesto.importacion.controller;

import com.presupuesto.comun.seguridad.UsuarioAutenticado;
import com.presupuesto.importacion.dto.response.ImportacionResponse;
import com.presupuesto.importacion.dto.response.VistaPreviaResponse;
import com.presupuesto.importacion.service.ImportacionService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * El archivo y los parámetros del mapeo son opcionales aquí, y se reciben como texto, para que
 * el service responda 400 después de los 404 de presupuesto y cuenta.
 */
@RestController
@RequestMapping("/api/v1/presupuestos/{presupuestoId}/cuentas/{cuentaId}/importacion")
@RequiredArgsConstructor
public class ImportacionController {

    private final ImportacionService importacionService;

    @PostMapping(value = "/vista-previa", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VistaPreviaResponse vistaPrevia(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long cuentaId,
            @RequestPart(name = "archivo", required = false) MultipartFile archivo,
            @RequestParam Map<String, String> parametros) {
        return importacionService.vistaPrevia(
                presupuestoId, usuario.id(), cuentaId, archivo, parametros);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ImportacionResponse importar(
            @AuthenticationPrincipal UsuarioAutenticado usuario,
            @PathVariable Long presupuestoId,
            @PathVariable Long cuentaId,
            @RequestPart(name = "archivo", required = false) MultipartFile archivo,
            @RequestParam Map<String, String> parametros) {
        return importacionService.importar(
                presupuestoId, usuario.id(), cuentaId, archivo, parametros);
    }
}
