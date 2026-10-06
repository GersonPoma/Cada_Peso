package com.presupuesto.comun.excepcion;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ManejadorGlobalExcepciones {

    static final String MENSAJE_CAMPOS_INVALIDOS = "Uno o más campos no son válidos";
    static final String MENSAJE_CUERPO_ILEGIBLE = "El cuerpo de la petición no es válido";

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail manejarRecursoNoEncontrado(RecursoNoEncontradoException excepcion) {
        return construirProblemDetail(
                HttpStatus.NOT_FOUND, excepcion.getCodigo(), excepcion.getMessage());
    }

    @ExceptionHandler(ConflictoException.class)
    public ProblemDetail manejarConflicto(ConflictoException excepcion) {
        return construirProblemDetail(
                HttpStatus.CONFLICT, excepcion.getCodigo(), excepcion.getMessage());
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ProblemDetail manejarReglaNegocio(ReglaNegocioException excepcion) {
        return construirProblemDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, excepcion.getCodigo(), excepcion.getMessage());
    }

    @ExceptionHandler(DatosInvalidosException.class)
    public ProblemDetail manejarDatosInvalidos(DatosInvalidosException excepcion) {
        return construirProblemDetail(
                HttpStatus.BAD_REQUEST, excepcion.getCodigo(), excepcion.getMessage());
    }

    @ExceptionHandler(NoAutenticadoException.class)
    public ProblemDetail manejarNoAutenticado(NoAutenticadoException excepcion) {
        return construirProblemDetail(
                HttpStatus.UNAUTHORIZED, excepcion.getCodigo(), excepcion.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail manejarValidacion(MethodArgumentNotValidException excepcion) {
        ProblemDetail problemDetail = construirProblemDetail(
                HttpStatus.BAD_REQUEST, CodigoError.DATOS_INVALIDOS, MENSAJE_CAMPOS_INVALIDOS);
        problemDetail.setProperty("errores", extraerErroresPorCampo(excepcion));
        return problemDetail;
    }

    /**
     * Cuerpo que no se puede deserializar (JSON mal formado, cuerpo vacío, tipo incorrecto,
     * fecha imposible). Nunca se usa el mensaje ni la causa de la excepción: el de Jackson
     * incluye clases internas, posiciones y fragmentos del cuerpo recibido.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail manejarCuerpoIlegible(HttpMessageNotReadableException excepcion) {
        return construirProblemDetail(
                HttpStatus.BAD_REQUEST, CodigoError.DATOS_INVALIDOS, MENSAJE_CUERPO_ILEGIBLE);
    }

    private Map<String, String> extraerErroresPorCampo(MethodArgumentNotValidException excepcion) {
        Map<String, String> errores = new LinkedHashMap<>();
        for (FieldError error : excepcion.getBindingResult().getFieldErrors()) {
            errores.put(error.getField(), error.getDefaultMessage());
        }
        return errores;
    }

    private ProblemDetail construirProblemDetail(
            HttpStatus status, CodigoError codigo, String mensaje) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, mensaje);
        problemDetail.setProperty("codigo", codigo);
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
}
