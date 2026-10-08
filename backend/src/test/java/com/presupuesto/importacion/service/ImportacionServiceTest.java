package com.presupuesto.importacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.presupuesto.comun.excepcion.DatosInvalidosException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.comun.excepcion.ReglaNegocioException;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.cuenta.entity.TipoCuenta;
import com.presupuesto.cuenta.repository.CuentaRepository;
import com.presupuesto.importacion.dto.response.EstadoFila;
import com.presupuesto.importacion.dto.response.ImportacionResponse;
import com.presupuesto.importacion.dto.response.VistaPreviaResponse;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.service.PresupuestoService;
import com.presupuesto.transaccion.dto.request.CrearTransaccionRequest;
import com.presupuesto.transaccion.entity.Transaccion;
import com.presupuesto.transaccion.service.ClaveMovimiento;
import com.presupuesto.transaccion.service.TransaccionService;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

/**
 * Orden de errores, límites y, sobre todo, que sin filas válidas no se consulte la base y que la
 * importación cree solo las nuevas.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImportacionServiceTest {

    private static final long USUARIO_ID = 10L;
    private static final long PRESUPUESTO_ID = 20L;
    private static final long CUENTA_ID = 30L;
    private static final LocalDate MARZO_5 = LocalDate.of(2026, 3, 5);

    @Mock
    private PresupuestoService presupuestoService;

    @Mock
    private CuentaRepository cuentaRepository;

    @Mock
    private TransaccionService transaccionService;

    private ImportacionService service;
    private Presupuesto presupuesto;
    private Cuenta banco;

    @BeforeEach
    void preparar() {
        service = new ImportacionService(
                presupuestoService,
                cuentaRepository,
                transaccionService,
                new ImportacionProperties(2 * 1024 * 1024, 5));
        presupuesto = Presupuesto.builder().id(PRESUPUESTO_ID).build();
        banco = Cuenta.builder().id(CUENTA_ID).tipo(TipoCuenta.CORRIENTE).build();
        when(presupuestoService.obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID))
                .thenReturn(presupuesto);
        when(cuentaRepository.findByIdAndPresupuestoId(CUENTA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(banco));
        when(cuentaRepository.findByIdAndPresupuestoIdParaActualizar(CUENTA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(banco));
        when(transaccionService.contarExistentesPorClave(anyLong(), any(), any()))
                .thenReturn(Map.of());
    }

    private static Map<String, String> parametros() {
        return new java.util.HashMap<>(Map.of(
                "separador", "COMA",
                "columnaFecha", "0",
                "formatoFecha", "yyyy-MM-dd",
                "columnaMonto", "1",
                "separadorDecimal", "PUNTO",
                "columnaDescripcion", "2"));
    }

    private static MockMultipartFile csv(String contenido) {
        return new MockMultipartFile(
                "archivo", "x.csv", "text/csv", contenido.getBytes(StandardCharsets.UTF_8));
    }

    private VistaPreviaResponse vistaPrevia(String contenido, Map<String, String> p) {
        return service.vistaPrevia(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID, csv(contenido), p);
    }

    private ImportacionResponse importar(String contenido, Map<String, String> p) {
        return service.importar(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID, csv(contenido), p);
    }

    private void sinConsultarExistentes() {
        verify(transaccionService, never()).contarExistentesPorClave(anyLong(), any(), any());
    }

    // ---------- sin filas válidas no se consulta la base ----------

    @Test
    void soloEncabezadoNoConsultaExistentesNiCreaNada() {
        Map<String, String> p = parametros();
        p.put("tieneEncabezado", "true");

        VistaPreviaResponse previa = vistaPrevia("fecha,monto,desc\n", p);
        ImportacionResponse resultado = importar("fecha,monto,desc\n", p);

        assertThat(previa.totales().total()).isZero();
        assertThat(previa.totales().nuevas()).isZero();
        assertThat(previa.totales().sinCategoria()).isZero();
        assertThat(resultado).isEqualTo(new ImportacionResponse(0, 0, 0, 0));
        sinConsultarExistentes();
        verify(transaccionService, never()).crearLote(any(), any(), any());
    }

    @Test
    void sinFilasDeDatosNoConsultaExistentes() {
        assertThat(vistaPrevia("\n\n", parametros()).filas()).isEmpty();
        assertThat(importar("\n\n", parametros())).isEqualTo(new ImportacionResponse(0, 0, 0, 0));
        sinConsultarExistentes();
    }

    @Test
    void soloFilasInvalidasNoConsultaExistentes() {
        String contenido = "31/02/2026,10,a\nayer,10,b\n";

        VistaPreviaResponse previa = vistaPrevia(contenido, parametros());

        assertThat(previa.totales().invalidas()).isEqualTo(2);
        assertThat(previa.totales().nuevas()).isZero();
        assertThat(previa.totales().duplicadas()).isZero();
        assertThat(previa.totales().sinCategoria()).isZero();
        sinConsultarExistentes();
        Map<String, String> omitiendo = parametros();
        omitiendo.put("omitirInvalidas", "true");
        assertThat(importar(contenido, omitiendo)).isEqualTo(new ImportacionResponse(0, 0, 2, 0));
        sinConsultarExistentes();
    }

    @Test
    void conAlMenosUnaFilaValidaConsultaExistentesUnaVezConElRangoDeFechas() {
        vistaPrevia("2026-03-05,10,a\n2026-03-01,10,b\n2026-03-09,10,c\nmala,10,d\n", parametros());

        verify(transaccionService, times(1)).contarExistentesPorClave(
                CUENTA_ID, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 9));
    }

    // ---------- importación: solo las nuevas ----------

    @Test
    void importaSoloLasNuevasConLosValoresDeCadaFila() {
        when(transaccionService.contarExistentesPorClave(anyLong(), any(), any()))
                .thenReturn(Map.of(ClaveMovimiento.de(MARZO_5, -4500, "Cafe Luna"), 1));
        when(transaccionService.crearLote(any(), any(), any())).thenAnswer(invocacion -> {
            List<CrearTransaccionRequest> pedidas = invocacion.getArgument(2);
            return pedidas.stream().map(r -> Transaccion.builder().build()).toList();
        });

        ImportacionResponse resultado = importar(
                "2026-03-05,-4.50,CAFE LUNA\n2026-03-05,-4.50,Cafe Luna\n2026-03-06,10,Sueldo\n",
                parametros());

        assertThat(resultado).isEqualTo(new ImportacionResponse(2, 1, 0, 2));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CrearTransaccionRequest>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(transaccionService).crearLote(
                org.mockito.ArgumentMatchers.eq(presupuesto),
                org.mockito.ArgumentMatchers.eq(banco),
                captor.capture());
        assertThat(captor.getValue()).hasSize(2).allSatisfy(r -> {
            assertThat(r.cuentaId()).isEqualTo(CUENTA_ID);
            assertThat(r.categoriaId()).isNull();
            assertThat(r.aprobada()).isFalse();
            assertThat(r.subtransacciones()).isEmpty();
        });
        assertThat(captor.getValue().get(0).monto()).isEqualTo(-4500L);
        assertThat(captor.getValue().get(0).beneficiario()).isEqualTo("Cafe Luna");
        assertThat(captor.getValue().get(1).monto()).isEqualTo(10_000L);
    }

    @Test
    void conInvalidasSinOmitirResponde422YNoCreaNada() {
        ReglaNegocioException e = assertThrows(ReglaNegocioException.class,
                () -> importar("2026-03-05,10,a\nmala,10,b\n", parametros()));

        assertThat(e.getMessage()).contains("1 filas inválidas");
        verify(transaccionService, never()).crearLote(any(), any(), any());
    }

    @Test
    void laImportacionBloqueaLaCuentaYLaVistaPreviaNo() {
        vistaPrevia("2026-03-05,10,a", parametros());

        verify(cuentaRepository).findByIdAndPresupuestoId(CUENTA_ID, PRESUPUESTO_ID);
        verify(cuentaRepository, never())
                .findByIdAndPresupuestoIdParaActualizar(anyLong(), anyLong());

        importar("2026-03-05,10,a", parametros());

        verify(cuentaRepository)
                .findByIdAndPresupuestoIdParaActualizar(CUENTA_ID, PRESUPUESTO_ID);
    }

    // ---------- orden de errores ----------

    @Test
    void presupuestoAjenoEs404AntesQueCualquierOtraCosa() {
        when(presupuestoService.obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID))
                .thenThrow(new RecursoNoEncontradoException("Presupuesto no encontrado"));

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.vistaPrevia(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID, null, Map.of()));
        verify(cuentaRepository, never()).findByIdAndPresupuestoId(anyLong(), anyLong());
    }

    @Test
    void cuentaInexistenteEs404AunqueElArchivoYLosParametrosSeanInvalidos() {
        when(cuentaRepository.findByIdAndPresupuestoId(CUENTA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.empty());
        when(cuentaRepository.findByIdAndPresupuestoIdParaActualizar(CUENTA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.vistaPrevia(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID, null, Map.of()));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.importar(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID, null,
                        Map.of("omitirInvalidas", "quizas")));
    }

    @Test
    void sinArchivoOVacioEs400() {
        assertThrows(DatosInvalidosException.class,
                () -> service.vistaPrevia(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID, null, parametros()));
        assertThrows(DatosInvalidosException.class, () -> vistaPrevia("", parametros()));
    }

    @Test
    void parametrosInvalidosSon400() {
        Map<String, String> p = parametros();
        p.put("tieneEncabezado", "quizas");

        assertThrows(DatosInvalidosException.class, () -> vistaPrevia("2026-03-05,1,a", p));
    }

    @Test
    void archivoMasGrandeQueElLimiteEs400ConElMaximoEnElMensaje() {
        byte[] grande = new byte[2 * 1024 * 1024 + 1];
        MockMultipartFile archivo = new MockMultipartFile("archivo", "x.csv", "text/csv", grande);

        DatosInvalidosException e = assertThrows(DatosInvalidosException.class,
                () -> service.vistaPrevia(
                        PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID, archivo, parametros()));

        assertThat(e.getMessage()).contains("2 MB");
    }

    @Test
    void masFilasQueElMaximoEs400YExactamenteElMaximoNo() {
        StringBuilder cinco = new StringBuilder();
        for (int i = 1; i <= 5; i++) {
            cinco.append("2026-03-0").append(i).append(",10,a\n");
        }

        assertThat(vistaPrevia(cinco.toString(), parametros()).totales().total()).isEqualTo(5);
        assertThrows(DatosInvalidosException.class,
                () -> vistaPrevia(cinco + "2026-03-09,10,a\n", parametros()));
    }

    @Test
    void elEncabezadoNoCuentaParaElMaximoDeFilasDeDatos() {
        Map<String, String> p = parametros();
        p.put("tieneEncabezado", "true");

        assertThat(vistaPrevia(
                "f,m,d\n2026-03-01,1,a\n2026-03-02,1,a\n2026-03-03,1,a\n2026-03-04,1,a\n"
                        + "2026-03-05,1,a\n", p).totales().total()).isEqualTo(5);
    }

    @Test
    void cuentaCerradaEs422PeroDespuesDeLosErrores400() {
        banco.cerrar();

        assertThrows(ReglaNegocioException.class, () -> vistaPrevia("2026-03-05,1,a", parametros()));
        assertThrows(ReglaNegocioException.class, () -> importar("mala,1,a", parametros()));
        assertThrows(DatosInvalidosException.class,
                () -> service.vistaPrevia(PRESUPUESTO_ID, USUARIO_ID, CUENTA_ID,
                        new MockMultipartFile("archivo", "x.csv", "text/csv",
                                new byte[] {(byte) 0xE9}),
                        parametros()));
        sinConsultarExistentes();
    }

    @Test
    void estadosDeLaVistaPrevia() {
        when(transaccionService.contarExistentesPorClave(anyLong(), any(), any()))
                .thenReturn(Map.of(ClaveMovimiento.de(MARZO_5, 10_000, "a"), 1));

        VistaPreviaResponse previa = vistaPrevia(
                "2026-03-05,10,a\n2026-03-05,10,a\nmala,10,a\n", parametros());

        assertThat(previa.filas()).extracting(f -> f.estado())
                .containsExactly(EstadoFila.DUPLICADA, EstadoFila.NUEVA, EstadoFila.INVALIDA);
        assertThat(previa.filas().get(2).motivo()).isEqualTo("La fecha no es válida");
        assertThat(previa.filas().get(2).monto()).isNull();
        assertThat(previa.filas()).extracting(f -> f.fila()).containsExactly(1, 2, 3);
        assertThat(previa.totales().sinCategoria()).isEqualTo(1);
    }
}
