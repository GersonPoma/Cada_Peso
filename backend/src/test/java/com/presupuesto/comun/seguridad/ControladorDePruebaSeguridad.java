package com.presupuesto.comun.seguridad;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ControladorDePruebaSeguridad {

    @GetMapping("/api/v1/prueba")
    String rutaProtegidaDePrueba() {
        return "ok";
    }
}
