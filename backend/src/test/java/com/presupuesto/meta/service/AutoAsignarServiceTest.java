package com.presupuesto.meta.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.presupuesto.asignacion.service.AsignacionService;
import com.presupuesto.asignacion.service.CalculoMensual.FilaMes;
import com.presupuesto.asignacion.service.CalculoMensual.ResultadoMes;
import com.presupuesto.asignacion.service.MesPresupuestoService;
import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.comun.excepcion.CodigoError;
import com.presupuesto.comun.excepcion.ConflictoException;
import com.presupuesto.comun.excepcion.DatosInvalidosException;
import com.presupuesto.comun.excepcion.NegocioException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.meta.dto.request.AutoAsignarRequest;
import com.presupuesto.meta.dto.request.EstrategiaAutoAsignar;
import com.presupuesto.meta.dto.response.AutoAsignarResponse;
import com.presupuesto.meta.dto.response.CambioAsignacionResponse;
import com.presupuesto.meta.entity.FrecuenciaMeta;
import com.presupuesto.meta.entity.Meta;
import com.presupuesto.meta.entity.TipoMeta;
import com.presupuesto.meta.repository.MetaPospuestaRepository;
import com.presupuesto.meta.repository.MetaRepository;
import com.presupuesto.presupuesto.service.PresupuestoService;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AutoAsignarServiceTest {

    private static final long USUARIO_ID = 10L;
    private static final long PRESUPUESTO_ID = 20L;
    private static final long COMIDA_ID = 1L;
    private static final long OCIO_ID = 2L;
    private static final long VIEJA_ID = 3L;
    private static final YearMonth OCTUBRE = YearMonth.of(2026, 10);
    private static final YearMonth SEPTIEMBRE = YearMonth.of(2026, 9);
    private static final YearMonth AGOSTO = YearMonth.of(2026, 8);
    private static final YearMonth JULIO = YearMonth.of(2026, 7);

    @Mock
    private MetaRepository metaRepository;

    @Mock
    private MetaPospuestaRepository pospuestaRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private MesPresupuestoService mesService;

    @Mock
    private AsignacionService asignacionService;

    @Mock
    private PresupuestoService presupuestoService;

    private AutoAsignarService service;
    private Categoria comida;
    private Categoria ocio;
    private Categoria vieja;

    @BeforeEach
    void preparar() {
        service = new AutoAsignarService(metaRepository, pospuestaRepository, categoriaRepository,
                mesService, asignacionService, presupuestoService);
        comida = Categoria.builder().id(COMIDA_ID).nombre("Comida").build();
        ocio = Categoria.builder().id(OCIO_ID).nombre("Ocio").build();
        vieja = Categoria.builder().id(VIEJA_ID).nombre("Vieja").build();
        vieja.ocultar();
        when(categoriaRepository
                .findByGrupoPresupuestoIdAndOcultaFalseOrderByGrupoOrdenAscOrdenAsc(PRESUPUESTO_ID))
                .thenReturn(List.of(comida, ocio));
        when(metaRepository.findDelPresupuestoEnOrdenDelArbol(PRESUPUESTO_ID))
                .thenReturn(List.of());
        when(pospuestaRepository.findMetaIdsPospuestas(any(), any())).thenReturn(List.of());
        // Sin datos en ningún mes salvo que el test diga otra cosa.
        when(mesService.calcular(any(), any())).thenReturn(new ResultadoMes(0L, Map.of()));
    }

    private void mes(YearMonth mes, long listo, Map<Long, FilaMes> filas) {
        when(mesService.calcular(PRESUPUESTO_ID, mes)).thenReturn(new ResultadoMes(listo, filas));
    }

    private static FilaMes asignado(long asignado) {
        return new FilaMes(asignado, 0L, asignado);
    }

    private static FilaMes actividad(long actividad) {
        return new FilaMes(0L, actividad, actividad);
    }

    private AutoAsignarResponse autoAsignar(EstrategiaAutoAsignar estrategia) {
        return service.autoAsignar(PRESUPUESTO_ID, USUARIO_ID, "2026-10",
                new AutoAsignarRequest(estrategia, null, false));
    }

    private void metaMensualDeComida() {
        Meta meta = Meta.builder().id(50L).categoria(comida).build();
        meta.reemplazar(TipoMeta.MONTO_MENSUAL, 100_000L, FrecuenciaMeta.MENSUAL,
                null, null, null, null);
        when(metaRepository.findDelPresupuestoEnOrdenDelArbol(PRESUPUESTO_ID))
                .thenReturn(List.of(meta));
    }

    // ---------- estrategias ----------

    @Test
    void faltanteMetaSumaElFaltanteAlAsignadoYDejaLasCategoriasSinMetaIgual() {
        metaMensualDeComida();
        mes(OCTUBRE, 500_000L, Map.of(COMIDA_ID, asignado(30_000L), OCIO_ID, asignado(7_000L)));

        AutoAsignarResponse respuesta = autoAsignar(EstrategiaAutoAsignar.FALTANTE_META);

        assertThat(respuesta.cambios()).containsExactly(
                new CambioAsignacionResponse(COMIDA_ID, "Comida", 30_000L, 100_000L));
        assertThat(respuesta.listoParaAsignarDespues()).isEqualTo(430_000L);
        verify(asignacionService).fijarAsignados(PRESUPUESTO_ID, OCTUBRE,
                Map.of(COMIDA_ID, 100_000L));
    }

    @Test
    void faltanteMetaSumaElFaltanteSobreLoYaAsignadoYNoTocaUnaMetaFinanciada() {
        metaMensualDeComida();
        mes(OCTUBRE, 0L, Map.of(COMIDA_ID, asignado(100_000L)));

        AutoAsignarResponse respuesta = autoAsignar(EstrategiaAutoAsignar.FALTANTE_META);

        assertThat(respuesta.cambios()).isEmpty();
        verify(asignacionService, never()).fijarAsignados(any(), any(), anyMap());
    }

    @Test
    void faltanteMetaIgnoraUnaMetaPospuestaEnElMes() {
        metaMensualDeComida();
        when(pospuestaRepository.findMetaIdsPospuestas(PRESUPUESTO_ID, OCTUBRE.atDay(1)))
                .thenReturn(List.of(50L));
        mes(OCTUBRE, 0L, Map.of(COMIDA_ID, asignado(30_000L)));

        assertThat(autoAsignar(EstrategiaAutoAsignar.FALTANTE_META).cambios()).isEmpty();
    }

    @Test
    void asignadoMesPasadoCopiaLoAsignadoEnSeptiembre() {
        mes(SEPTIEMBRE, 0L, Map.of(COMIDA_ID, asignado(50_000L)));
        mes(OCTUBRE, 300_000L, Map.of());

        AutoAsignarResponse respuesta = autoAsignar(EstrategiaAutoAsignar.ASIGNADO_MES_PASADO);

        assertThat(respuesta.cambios()).containsExactly(
                new CambioAsignacionResponse(COMIDA_ID, "Comida", 0L, 50_000L));
        assertThat(respuesta.listoParaAsignarDespues()).isEqualTo(250_000L);
        verify(asignacionService).fijarAsignados(PRESUPUESTO_ID, OCTUBRE,
                Map.of(COMIDA_ID, 50_000L));
    }

    @Test
    void gastadoMesPasadoUsaLaActividadNegativaYUnIngresoDejaCero() {
        mes(SEPTIEMBRE, 0L, Map.of(COMIDA_ID, actividad(-35_000L), OCIO_ID, actividad(4_000L)));
        mes(OCTUBRE, 0L, Map.of(OCIO_ID, asignado(7_000L)));

        AutoAsignarResponse respuesta = autoAsignar(EstrategiaAutoAsignar.GASTADO_MES_PASADO);

        assertThat(respuesta.cambios()).containsExactly(
                new CambioAsignacionResponse(COMIDA_ID, "Comida", 0L, 35_000L),
                new CambioAsignacionResponse(OCIO_ID, "Ocio", 7_000L, 0L));
    }

    @Test
    void promedioAsignadoDeTresMesesConUnoEnCero() {
        mes(SEPTIEMBRE, 0L, Map.of(COMIDA_ID, asignado(30_000L)));
        mes(AGOSTO, 0L, Map.of(COMIDA_ID, asignado(60_000L)));
        mes(JULIO, 0L, Map.of());

        AutoAsignarResponse respuesta = autoAsignar(EstrategiaAutoAsignar.PROMEDIO_ASIGNADO);

        assertThat(respuesta.cambios()).containsExactly(
                new CambioAsignacionResponse(COMIDA_ID, "Comida", 0L, 30_000L));
    }

    @Test
    void promedioGastadoRedondeaHaciaAbajoYLosIngresosCuentanCero() {
        mes(SEPTIEMBRE, 0L, Map.of(COMIDA_ID, actividad(-10_000L), OCIO_ID, actividad(5_000L)));
        mes(AGOSTO, 0L, Map.of(COMIDA_ID, actividad(-10_000L)));
        mes(JULIO, 0L, Map.of(COMIDA_ID, actividad(-11L)));

        AutoAsignarResponse respuesta = autoAsignar(EstrategiaAutoAsignar.PROMEDIO_GASTADO);

        assertThat(respuesta.cambios()).containsExactly(
                new CambioAsignacionResponse(COMIDA_ID, "Comida", 0L, 6_670L));
    }

    @Test
    void unMesAnteriorFueraDelRangoCuentaCeroYNoSeCalcula() {
        mes(YearMonth.of(2000, 1), 0L, Map.of(COMIDA_ID, asignado(90_000L)));

        AutoAsignarResponse respuesta = service.autoAsignar(PRESUPUESTO_ID, USUARIO_ID, "2000-02",
                new AutoAsignarRequest(EstrategiaAutoAsignar.PROMEDIO_ASIGNADO, null, false));

        assertThat(respuesta.cambios()).containsExactly(
                new CambioAsignacionResponse(COMIDA_ID, "Comida", 0L, 30_000L));
        verify(mesService, never()).calcular(PRESUPUESTO_ID, YearMonth.of(1999, 12));
        verify(mesService, never()).calcular(PRESUPUESTO_ID, YearMonth.of(1999, 11));
    }

    // ---------- simular, sin cambios y listo para asignar ----------

    @Test
    void simularDevuelveLosCambiosSinGuardarNadaYConAplicadoFalso() {
        mes(SEPTIEMBRE, 0L, Map.of(COMIDA_ID, asignado(50_000L), OCIO_ID, asignado(20_000L)));
        mes(OCTUBRE, 100_000L, Map.of());

        AutoAsignarResponse respuesta = service.autoAsignar(PRESUPUESTO_ID, USUARIO_ID, "2026-10",
                new AutoAsignarRequest(EstrategiaAutoAsignar.ASIGNADO_MES_PASADO, null, true));

        assertThat(respuesta.aplicado()).isFalse();
        assertThat(respuesta.cambios()).hasSize(2);
        assertThat(respuesta.listoParaAsignarDespues()).isEqualTo(30_000L);
        verify(asignacionService, never()).fijarAsignados(any(), any(), anyMap());
    }

    @Test
    void sinCambiosLaListaVaVaciaLosDosListoSonIgualesYNoSeEscribe() {
        mes(OCTUBRE, 123_000L, Map.of());

        AutoAsignarResponse respuesta = autoAsignar(EstrategiaAutoAsignar.ASIGNADO_MES_PASADO);

        assertThat(respuesta.aplicado()).isTrue();
        assertThat(respuesta.cambios()).isEmpty();
        assertThat(respuesta.listoParaAsignarAntes()).isEqualTo(123_000L);
        assertThat(respuesta.listoParaAsignarDespues()).isEqualTo(123_000L);
        verify(asignacionService, never()).fijarAsignados(any(), any(), anyMap());
    }

    @Test
    void elListoParaAsignarPuedeQuedarNegativo() {
        mes(SEPTIEMBRE, 0L, Map.of(COMIDA_ID, asignado(80_000L)));
        mes(OCTUBRE, 10_000L, Map.of());

        AutoAsignarResponse respuesta = autoAsignar(EstrategiaAutoAsignar.ASIGNADO_MES_PASADO);

        assertThat(respuesta.listoParaAsignarDespues()).isEqualTo(-70_000L);
        verify(asignacionService).fijarAsignados(PRESUPUESTO_ID, OCTUBRE,
                Map.of(COMIDA_ID, 80_000L));
    }

    // ---------- categorías ----------

    @Test
    void sinCategoriaIdsNoSeProcesanLasOcultas() {
        mes(SEPTIEMBRE, 0L, Map.of(VIEJA_ID, asignado(9_000L)));

        assertThat(autoAsignar(EstrategiaAutoAsignar.ASIGNADO_MES_PASADO).cambios()).isEmpty();
    }

    @Test
    void conCategoriaIdsExplicitosSeProcesaUnaOcultaYSoloLasListadas() {
        when(categoriaRepository.findByGrupoPresupuestoIdAndIdInOrderByGrupoOrdenAscOrdenAsc(
                any(), any())).thenReturn(List.of(vieja));
        mes(SEPTIEMBRE, 0L, Map.of(VIEJA_ID, asignado(9_000L), COMIDA_ID, asignado(1_000L)));

        AutoAsignarResponse respuesta = service.autoAsignar(PRESUPUESTO_ID, USUARIO_ID, "2026-10",
                new AutoAsignarRequest(EstrategiaAutoAsignar.ASIGNADO_MES_PASADO,
                        List.of(VIEJA_ID, VIEJA_ID), false));

        assertThat(respuesta.cambios()).containsExactly(
                new CambioAsignacionResponse(VIEJA_ID, "Vieja", 0L, 9_000L));
        verify(asignacionService).fijarAsignados(PRESUPUESTO_ID, OCTUBRE,
                Map.of(VIEJA_ID, 9_000L));
    }

    @Test
    void unaCategoriaAjenaResponde404SinAplicarNada() {
        when(categoriaRepository.findByGrupoPresupuestoIdAndIdInOrderByGrupoOrdenAscOrdenAsc(
                any(), any())).thenReturn(List.of(comida));
        mes(SEPTIEMBRE, 0L, Map.of(COMIDA_ID, asignado(50_000L)));

        NegocioException error = assertThrows(RecursoNoEncontradoException.class,
                () -> service.autoAsignar(PRESUPUESTO_ID, USUARIO_ID, "2026-10",
                        new AutoAsignarRequest(EstrategiaAutoAsignar.ASIGNADO_MES_PASADO,
                                List.of(COMIDA_ID, 99L), false)));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.RECURSO_NO_ENCONTRADO);
        verify(asignacionService, never()).fijarAsignados(any(), any(), anyMap());
    }

    // ---------- orden de errores y atomicidad ----------

    @Test
    void unMesInvalidoResponde400DespuesDeValidarElPresupuestoYSinCalcularNada() {
        NegocioException error = assertThrows(DatosInvalidosException.class,
                () -> service.autoAsignar(PRESUPUESTO_ID, USUARIO_ID, "2026-13",
                        new AutoAsignarRequest(EstrategiaAutoAsignar.FALTANTE_META, null, false)));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.DATOS_INVALIDOS);
        verify(presupuestoService).obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID);
        verify(mesService, never()).calcular(any(), any());
    }

    @Test
    void unPresupuestoAjenoResponde404AunqueElMesSeaInvalido() {
        when(presupuestoService.obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID))
                .thenThrow(new RecursoNoEncontradoException("Presupuesto no encontrado"));

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.autoAsignar(PRESUPUESTO_ID, USUARIO_ID, "2026-13",
                        new AutoAsignarRequest(EstrategiaAutoAsignar.FALTANTE_META, null, false)));
        verify(asignacionService, never()).fijarAsignados(any(), any(), anyMap());
    }

    @Test
    void todoSeEscribeConUnaSolaLlamadaYUnFalloSePropaga() throws Exception {
        Map<Long, Long> esperado = new HashMap<>();
        esperado.put(COMIDA_ID, 50_000L);
        esperado.put(OCIO_ID, 20_000L);
        mes(SEPTIEMBRE, 0L, Map.of(COMIDA_ID, asignado(50_000L), OCIO_ID, asignado(20_000L)));
        org.mockito.Mockito.doThrow(new ConflictoException("carrera"))
                .when(asignacionService).fijarAsignados(any(), any(), anyMap());

        assertThrows(ConflictoException.class,
                () -> autoAsignar(EstrategiaAutoAsignar.ASIGNADO_MES_PASADO));

        verify(asignacionService, times(1)).fijarAsignados(PRESUPUESTO_ID, OCTUBRE, esperado);
        assertThat(AutoAsignarService.class.getMethod("autoAsignar", Long.class, Long.class,
                String.class, AutoAsignarRequest.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }
}
