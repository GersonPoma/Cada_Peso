package com.presupuesto.comun.excepcion;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ControladorDePrueba {

    record DatosDePrueba(@NotBlank String nombre, @NotNull LocalDate fecha) {}

    @GetMapping("/prueba/recurso-no-encontrado")
    void lanzarRecursoNoEncontrado() {
        throw new RecursoNoEncontradoException("Recurso de prueba no encontrado");
    }

    @GetMapping("/prueba/conflicto")
    void lanzarConflicto() {
        throw new ConflictoException("Conflicto de prueba");
    }

    @GetMapping("/prueba/conflicto-con-codigo")
    void lanzarConflictoConCodigo() {
        throw new ConflictoException(CodigoError.EMAIL_YA_REGISTRADO, "Email de prueba repetido");
    }

    @GetMapping("/prueba/regla-negocio")
    void lanzarReglaNegocio() {
        throw new ReglaNegocioException("Regla de negocio de prueba violada");
    }

    @GetMapping("/prueba/datos-invalidos")
    void lanzarDatosInvalidos() {
        throw new DatosInvalidosException("Datos de prueba invalidos");
    }

    @GetMapping("/prueba/no-autenticado")
    void lanzarNoAutenticado() {
        throw new NoAutenticadoException(
                CodigoError.CREDENCIALES_INVALIDAS, "Credenciales de prueba incorrectas");
    }

    enum ColorDePrueba { ROJO, AZUL }

    @GetMapping("/prueba/parametro-numero")
    void recibirNumero(@RequestParam Long valor) {}

    @GetMapping("/prueba/parametro-enum")
    void recibirEnum(@RequestParam ColorDePrueba color) {}

    @GetMapping("/prueba/parametro-fecha")
    void recibirFecha(@RequestParam LocalDate fecha) {}

    @PostMapping("/prueba/validacion")
    void validar(@Valid @RequestBody DatosDePrueba datos) {}
}
