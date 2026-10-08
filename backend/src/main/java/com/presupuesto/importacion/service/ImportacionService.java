package com.presupuesto.importacion.service;

import com.presupuesto.comun.excepcion.DatosInvalidosException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.comun.excepcion.ReglaNegocioException;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.repository.CuentaRepository;
import com.presupuesto.importacion.dto.request.ParametrosImportacion;
import com.presupuesto.importacion.dto.response.EstadoFila;
import com.presupuesto.importacion.dto.response.FilaPreviaResponse;
import com.presupuesto.importacion.dto.response.ImportacionResponse;
import com.presupuesto.importacion.dto.response.VistaPreviaResponse;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.service.PresupuestoService;
import com.presupuesto.transaccion.dto.request.CrearTransaccionRequest;
import com.presupuesto.transaccion.entity.Transaccion;
import com.presupuesto.transaccion.service.ClaveMovimiento;
import com.presupuesto.transaccion.service.TransaccionService;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Importa transacciones a una cuenta desde un CSV. El archivo se procesa en memoria y se
 * interpreta de nuevo en cada llamada: la importación nunca confía en una vista previa anterior.
 * Orden de errores: presupuesto 404, cuenta 404, archivo y parámetros 400, cuenta cerrada 422 y
 * filas inválidas 422.
 */
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(ImportacionProperties.class)
public class ImportacionService {

    static final String MENSAJE_CUENTA_NO_ENCONTRADA = "Cuenta no encontrada";
    static final String MENSAJE_CUENTA_CERRADA = "La cuenta está cerrada";
    static final String MENSAJE_SIN_ARCHIVO = "Falta el archivo";
    static final String MENSAJE_ARCHIVO_VACIO = "El archivo está vacío";
    static final String MENSAJE_NO_SE_PUDO_LEER = "No se pudo leer el archivo";
    static final String MENSAJE_DEMASIADAS_FILAS =
            "El archivo tiene más filas de datos que el máximo permitido";
    static final String MENSAJE_FILAS_INVALIDAS =
            "El archivo tiene %d filas inválidas; revisa la vista previa o usa omitirInvalidas";

    private static final long BYTES_POR_MB = 1024L * 1024L;

    private final PresupuestoService presupuestoService;
    private final CuentaRepository cuentaRepository;
    private final TransaccionService transaccionService;
    private final ImportacionProperties propiedades;

    /** Filas ya interpretadas y clasificadas; {@code estados} va en el orden de {@code filas}. */
    private record Analisis(
            Presupuesto presupuesto,
            Cuenta cuenta,
            ParametrosImportacion parametros,
            List<FilaInterpretada> filas,
            List<EstadoFila> estados) {}

    @Transactional(readOnly = true)
    public VistaPreviaResponse vistaPrevia(
            Long presupuestoId,
            Long usuarioId,
            Long cuentaId,
            MultipartFile archivo,
            Map<String, String> parametros) {
        Analisis analisis =
                analizar(presupuestoId, usuarioId, cuentaId, archivo, parametros, false);
        List<FilaPreviaResponse> filas = new ArrayList<>(analisis.filas().size());
        for (int i = 0; i < analisis.filas().size(); i++) {
            filas.add(FilaPreviaResponse.desde(analisis.filas().get(i), analisis.estados().get(i)));
        }
        return VistaPreviaResponse.desde(filas);
    }

    /**
     * Todo o nada: las filas {@code NUEVA} se crean en esta transacción, con la cuenta bloqueada
     * para que dos importaciones simultáneas del mismo archivo no dupliquen.
     */
    @Transactional
    public ImportacionResponse importar(
            Long presupuestoId,
            Long usuarioId,
            Long cuentaId,
            MultipartFile archivo,
            Map<String, String> parametros) {
        Analisis analisis =
                analizar(presupuestoId, usuarioId, cuentaId, archivo, parametros, true);
        int invalidas = contar(analisis.estados(), EstadoFila.INVALIDA);
        if (invalidas > 0 && !analisis.parametros().omitirInvalidas()) {
            throw new ReglaNegocioException(MENSAJE_FILAS_INVALIDAS.formatted(invalidas));
        }
        List<CrearTransaccionRequest> nuevas = new ArrayList<>();
        for (int i = 0; i < analisis.filas().size(); i++) {
            if (analisis.estados().get(i) == EstadoFila.NUEVA) {
                nuevas.add(aSolicitud(analisis.cuenta(), analisis.filas().get(i)));
            }
        }
        int sinCategoria = 0;
        if (!nuevas.isEmpty()) {
            List<Transaccion> creadas = transaccionService.crearLote(
                    analisis.presupuesto(), analisis.cuenta(), nuevas);
            sinCategoria = (int) creadas.stream().filter(t -> t.getCategoria() == null).count();
        }
        return ImportacionResponse.desde(
                nuevas.size(),
                contar(analisis.estados(), EstadoFila.DUPLICADA),
                invalidas,
                sinCategoria);
    }

    private Analisis analizar(
            Long presupuestoId,
            Long usuarioId,
            Long cuentaId,
            MultipartFile archivo,
            Map<String, String> parametrosCrudos,
            boolean bloquearCuenta) {
        Presupuesto presupuesto = presupuestoService.obtenerDelUsuario(presupuestoId, usuarioId);
        Cuenta cuenta = (bloquearCuenta
                        ? cuentaRepository.findByIdAndPresupuestoIdParaActualizar(
                                cuentaId, presupuestoId)
                        : cuentaRepository.findByIdAndPresupuestoId(cuentaId, presupuestoId))
                .orElseThrow(() -> new RecursoNoEncontradoException(MENSAJE_CUENTA_NO_ENCONTRADA));
        ParametrosImportacion parametros = ParametrosImportacion.de(parametrosCrudos);
        byte[] bytes = leerBytes(archivo);
        String texto = DecodificadorUtf8.decodificar(bytes);
        int maxRegistros = propiedades.maxFilas() + (parametros.tieneEncabezado() ? 1 : 0);
        List<List<String>> registros =
                LectorCsv.leer(texto, parametros.separador().caracter(), maxRegistros);
        if (parametros.tieneEncabezado() && !registros.isEmpty()) {
            registros = registros.subList(1, registros.size());
        }
        if (cuenta.isCerrada()) {
            throw new ReglaNegocioException(MENSAJE_CUENTA_CERRADA);
        }
        List<FilaInterpretada> filas = new ArrayList<>(registros.size());
        for (int i = 0; i < registros.size(); i++) {
            filas.add(InterpreteFilas.interpretar(i + 1, registros.get(i), parametros));
        }
        return new Analisis(presupuesto, cuenta, parametros, filas, clasificar(cuenta, filas));
    }

    /**
     * Sin filas válidas no se consulta la base: el rango de fechas de la consulta sale de ellas.
     */
    private List<EstadoFila> clasificar(Cuenta cuenta, List<FilaInterpretada> filas) {
        List<ClaveMovimiento> claves = new ArrayList<>(filas.size());
        LocalDate desde = null;
        LocalDate hasta = null;
        for (FilaInterpretada fila : filas) {
            if (!fila.valida()) {
                claves.add(null);
                continue;
            }
            claves.add(ClaveMovimiento.de(fila.fecha(), fila.monto(), fila.beneficiario()));
            desde = desde == null || fila.fecha().isBefore(desde) ? fila.fecha() : desde;
            hasta = hasta == null || fila.fecha().isAfter(hasta) ? fila.fecha() : hasta;
        }
        Map<ClaveMovimiento, Integer> existentes = desde == null
                ? Map.of()
                : transaccionService.contarExistentesPorClave(cuenta.getId(), desde, hasta);
        List<Boolean> duplicadas = ClasificadorDuplicados.clasificar(claves, existentes);
        List<EstadoFila> estados = new ArrayList<>(filas.size());
        for (int i = 0; i < filas.size(); i++) {
            if (!filas.get(i).valida()) {
                estados.add(EstadoFila.INVALIDA);
            } else {
                estados.add(duplicadas.get(i) ? EstadoFila.DUPLICADA : EstadoFila.NUEVA);
            }
        }
        return estados;
    }

    private byte[] leerBytes(MultipartFile archivo) {
        if (archivo == null) {
            throw new DatosInvalidosException(MENSAJE_SIN_ARCHIVO);
        }
        if (archivo.getSize() == 0) {
            throw new DatosInvalidosException(MENSAJE_ARCHIVO_VACIO);
        }
        if (archivo.getSize() > propiedades.maxBytes()) {
            throw new DatosInvalidosException(
                    "El archivo supera el máximo de " + enMb(propiedades.maxBytes()));
        }
        try {
            return archivo.getBytes();
        } catch (IOException e) {
            throw new DatosInvalidosException(MENSAJE_NO_SE_PUDO_LEER);
        }
    }

    private static String enMb(long bytes) {
        return bytes % BYTES_POR_MB == 0
                ? (bytes / BYTES_POR_MB) + " MB"
                : String.format(Locale.ROOT, "%.1f MB", (double) bytes / BYTES_POR_MB);
    }

    private static CrearTransaccionRequest aSolicitud(Cuenta cuenta, FilaInterpretada fila) {
        return new CrearTransaccionRequest(
                cuenta.getId(),
                fila.fecha(),
                fila.monto(),
                null,
                fila.beneficiario(),
                fila.memo(),
                false,
                null);
    }

    private static int contar(List<EstadoFila> estados, EstadoFila buscado) {
        return (int) estados.stream().filter(estado -> estado == buscado).count();
    }
}
