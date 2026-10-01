package com.presupuesto.comun.excepcion;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ManejadorGlobalExcepciones {

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail manejarRecursoNoEncontrado(RecursoNoEncontradoException excepcion) {
        return construirProblemDetail(HttpStatus.NOT_FOUND, excepcion.getCodigo(), excepcion.getMessage());
    }

    @ExceptionHandler(ConflictoException.class)
    public ProblemDetail manejarConflicto(ConflictoException excepcion) {
        return construirProblemDetail(HttpStatus.CONFLICT, excepcion.getCodigo(), excepcion.getMessage());
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ProblemDetail manejarReglaNegocio(ReglaNegocioException excepcion) {
        return construirProblemDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, excepcion.getCodigo(), excepcion.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail manejarValidacion(MethodArgumentNotValidException excepcion) {
        ProblemDetail problemDetail = construirProblemDetail(
                HttpStatus.UNPROCESSABLE_ENTITY,
                CodigoError.REGLA_NEGOCIO_VIOLADA,
                "Uno o más campos no son válidos");
        problemDetail.setProperty("errores", extraerErroresPorCampo(excepcion));
        return problemDetail;
    }

    private Map<String, String> extraerErroresPorCampo(MethodArgumentNotValidException excepcion) {
        Map<String, String> errores = new LinkedHashMap<>();
        for (FieldError error : excepcion.getBindingResult().getFieldErrors()) {
            errores.put(error.getField(), error.getDefaultMessage());
        }
        return errores;
    }

    private ProblemDetail construirProblemDetail(HttpStatus status, CodigoError codigo, String mensaje) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, mensaje);
        problemDetail.setProperty("codigo", codigo);
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
}
