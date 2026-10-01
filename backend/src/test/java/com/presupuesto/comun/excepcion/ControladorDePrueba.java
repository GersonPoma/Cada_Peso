package com.presupuesto.comun.excepcion;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ControladorDePrueba {

    @GetMapping("/prueba/recurso-no-encontrado")
    void lanzarRecursoNoEncontrado() {
        throw new RecursoNoEncontradoException("Recurso de prueba no encontrado");
    }

    @GetMapping("/prueba/conflicto")
    void lanzarConflicto() {
        throw new ConflictoException("Conflicto de prueba");
    }

    @GetMapping("/prueba/regla-negocio")
    void lanzarReglaNegocio() {
        throw new ReglaNegocioException("Regla de negocio de prueba violada");
    }
}
