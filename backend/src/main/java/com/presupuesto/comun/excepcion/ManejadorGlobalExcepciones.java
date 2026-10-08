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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class ManejadorGlobalExcepciones {

    static final String MENSAJE_CAMPOS_INVALIDOS = "Uno o más campos no son válidos";
    static final String MENSAJE_CUERPO_ILEGIBLE = "El cuerpo de la petición no es válido";
    static final String MENSAJE_PARAMETRO_INVALIDO = "Un parámetro de la petición no es válido";
    static final String MENSAJE_ARCHIVO_DEMASIADO_GRANDE = "El archivo supera el tamaño permitido";
    static final String MENSAJE_PARTE_AUSENTE = "Falta una parte obligatoria de la petición";

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

    /**
     * Parámetro de ruta o de consulta con un tipo inválido (enum desconocido, fecha imposible,
     * texto donde va un número). Mensaje fijo: no se expone el valor recibido ni la causa.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail manejarParametroInvalido(MethodArgumentTypeMismatchException excepcion) {
        return construirProblemDetail(
                HttpStatus.BAD_REQUEST, CodigoError.DATOS_INVALIDOS, MENSAJE_PARAMETRO_INVALIDO);
    }

    /**
     * Archivo que supera el techo del transporte multipart. Mensaje fijo, sin el límite ni el
     * nombre del archivo.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail manejarArchivoDemasiadoGrande(MaxUploadSizeExceededException excepcion) {
        return construirProblemDetail(
                HttpStatus.BAD_REQUEST,
                CodigoError.DATOS_INVALIDOS,
                MENSAJE_ARCHIVO_DEMASIADO_GRANDE);
    }

    /** Petición multipart sin una parte obligatoria. Mensaje fijo, sin el nombre de la parte. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ProblemDetail manejarParteAusente(MissingServletRequestPartException excepcion) {
        return construirProblemDetail(
                HttpStatus.BAD_REQUEST, CodigoError.DATOS_INVALIDOS, MENSAJE_PARTE_AUSENTE);
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
