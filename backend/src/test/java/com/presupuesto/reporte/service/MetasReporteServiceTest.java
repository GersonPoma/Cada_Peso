package com.presupuesto.reporte.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.presupuesto.asignacion.service.CalculoMensual.FilaMes;
import com.presupuesto.asignacion.service.MesPresupuestoService;
import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.entity.GrupoCategoria;
import com.presupuesto.categoria.entity.TipoGrupoCategoria;
import com.presupuesto.comun.excepcion.DatosInvalidosException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.meta.dto.response.EstadoMeta;
import com.presupuesto.meta.entity.FrecuenciaMeta;
import com.presupuesto.meta.entity.Meta;
import com.presupuesto.meta.entity.TipoMeta;
import com.presupuesto.meta.repository.MetaPospuestaRepository;
import com.presupuesto.meta.repository.MetaPospuestaRepository.PospuestaEnMes;
import com.presupuesto.meta.repository.MetaRepository;
import com.presupuesto.presupuesto.service.PresupuestoService;
import com.presupuesto.reporte.dto.response.CumplimientoMetasResponse;
import com.presupuesto.reporte.dto.response.MesMetaResponse;
import com.presupuesto.reporte.dto.response.MetaCumplimientoResponse;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MetasReporteServiceTest {

    private static final long PRESUPUESTO_ID = 20L;
    private static final long USUARIO_ID = 5L;
    private static final long COMIDA_ID = 10L;
    private static final long PAGO_ID = 11L;
    private static final YearMonth SEPTIEMBRE = YearMonth.of(2026, 9);
    private static final YearMonth OCTUBRE = YearMonth.of(2026, 10);

    @Mock
    private PresupuestoService presupuestoService;

    @Mock
    private MesPresupuestoService mesService;

    @Mock
    private MetaRepository metaRepository;

    @Mock
    private MetaPospuestaRepository pospuestaRepository;

    private MetasReporteService service;
    private Meta deComida;
    private Meta dePago;

    @BeforeEach
    void preparar() {
        service = new MetasReporteService(
                presupuestoService, mesService, metaRepository, pospuestaRepository,
                new ReporteProperties(60));
        GrupoCategoria vida = GrupoCategoria.builder().nombre("Vida").build();
        GrupoCategoria pagos = GrupoCategoria.builder()
                .nombre("Pagos").tipo(TipoGrupoCategoria.PAGOS_TARJETA).build();
        Categoria comida = Categoria.builder().nombre("Comida").grupo(vida).build();
        comida.setId(COMIDA_ID);
        Categoria pagoVisa = Categoria.builder().nombre("Pago: Visa").grupo(pagos).build();
        pagoVisa.setId(PAGO_ID);
        deComida = meta(1L, comida, 100_000L);
        dePago = meta(2L, pagoVisa, 40_000L);
        when(metaRepository.findDelPresupuestoEnOrdenDelArbol(PRESUPUESTO_ID))
                .thenReturn(List.of(deComida, dePago));
        when(pospuestaRepository.findPospuestasEnRango(any(), any(), any())).thenReturn(List.of());
    }

    private static Meta meta(long id, Categoria categoria, long monto) {
        Meta meta = Meta.builder()
                .categoria(categoria)
                .tipo(TipoMeta.MONTO_MENSUAL)
                .monto(monto)
                .frecuencia(FrecuenciaMeta.MENSUAL)
                .build();
        meta.setId(id);
        return meta;
    }

    private void filas(YearMonth desde, YearMonth hasta, Map<YearMonth, Map<Long, FilaMes>> filas) {
        when(mesService.calcularFilas(PRESUPUESTO_ID, desde, hasta)).thenReturn(filas);
    }

    @Test
    void conUnPresupuestoAjenoResponde404SinConsultarNiValidarElRango() {
        doThrow(new RecursoNoEncontradoException("Presupuesto no encontrado"))
                .when(presupuestoService).obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID);

        assertThatThrownBy(() -> service.cumplimiento(
                PRESUPUESTO_ID, USUARIO_ID, "2026-13", null))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(mesService, metaRepository, pospuestaRepository);
    }

    @Test
    void unRangoInvalidoNoConsultaDatos() {
        assertThatThrownBy(() -> service.cumplimiento(
                PRESUPUESTO_ID, USUARIO_ID, "2026-10", "2026-09"))
                .isInstanceOf(DatosInvalidosException.class);
        assertThatThrownBy(() -> service.cumplimiento(PRESUPUESTO_ID, USUARIO_ID, null, null))
                .isInstanceOf(DatosInvalidosException.class);

        verifyNoInteractions(mesService, metaRepository, pospuestaRepository);
    }

    @Test
    void elEjemploDeUnMesDaNecesidad100000Asignado80000Gastado60000Y8000() {
        filas(OCTUBRE, OCTUBRE, Map.of(OCTUBRE, Map.of(
                COMIDA_ID, new FilaMes(80_000L, -60_000L, 20_000L))));

        CumplimientoMetasResponse respuesta =
                service.cumplimiento(PRESUPUESTO_ID, USUARIO_ID, "2026-10", null);

        assertThat(respuesta.desde()).isEqualTo("2026-10");
        assertThat(respuesta.hasta()).isEqualTo("2026-10");
        MetaCumplimientoResponse comida = respuesta.metas().get(0);
        assertThat(comida.categoriaId()).isEqualTo(COMIDA_ID);
        MesMetaResponse mes = comida.meses().get(0);
        assertThat(mes.necesidad()).isEqualTo(100_000L);
        assertThat(mes.asignado()).isEqualTo(80_000L);
        assertThat(mes.gastado()).isEqualTo(60_000L);
        assertThat(mes.disponible()).isEqualTo(20_000L);
        assertThat(mes.faltante()).isEqualTo(20_000L);
        assertThat(mes.estado()).isEqualTo(EstadoMeta.FALTA);
        assertThat(mes.porcentaje()).isEqualTo(8_000L);
    }

    @Test
    void elTotalDeUnRangoDeDosMesesSumaLasCifras() {
        filas(SEPTIEMBRE, OCTUBRE, Map.of(
                SEPTIEMBRE, Map.of(COMIDA_ID, new FilaMes(100_000L, -60_000L, 40_000L)),
                OCTUBRE, Map.of(COMIDA_ID, new FilaMes(80_000L, 0L, 120_000L))));

        MetaCumplimientoResponse comida = service
                .cumplimiento(PRESUPUESTO_ID, USUARIO_ID, "2026-09", "2026-10").metas().get(0);

        assertThat(comida.meses()).hasSize(2);
        assertThat(comida.necesidad()).isEqualTo(200_000L);
        assertThat(comida.asignado()).isEqualTo(180_000L);
        assertThat(comida.gastado()).isEqualTo(60_000L);
        assertThat(comida.porcentaje()).isEqualTo(9_000L);
        assertThat(comida.monto()).isEqualTo(100_000L);
    }

    @Test
    void unaMetaPospuestaTieneNecesidadCeroPorcentajeNuloYEstadoPospuesta() {
        filas(OCTUBRE, OCTUBRE, Map.of(OCTUBRE, Map.of(
                COMIDA_ID, new FilaMes(0L, 0L, 0L))));
        PospuestaEnMes pospuesta = mock(PospuestaEnMes.class);
        when(pospuesta.getMetaId()).thenReturn(1L);
        when(pospuesta.getMes()).thenReturn(LocalDate.of(2026, 10, 1));
        List<PospuestaEnMes> pospuestas = List.of(pospuesta);
        when(pospuestaRepository.findPospuestasEnRango(
                PRESUPUESTO_ID, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 1)))
                .thenReturn(pospuestas);

        MesMetaResponse mes = service.cumplimiento(PRESUPUESTO_ID, USUARIO_ID, "2026-10", null)
                .metas().get(0).meses().get(0);

        assertThat(mes.necesidad()).isZero();
        assertThat(mes.porcentaje()).isNull();
        assertThat(mes.estado()).isEqualTo(EstadoMeta.POSPUESTA);
    }

    @Test
    void enUnaCategoriaDePagoDeTarjetaLoGastadoEsCeroAunqueHayaReserva() {
        filas(OCTUBRE, OCTUBRE, Map.of(OCTUBRE, Map.of(
                PAGO_ID, new FilaMes(0L, 30_000L, 30_000L))));

        MetaCumplimientoResponse pago = service
                .cumplimiento(PRESUPUESTO_ID, USUARIO_ID, "2026-10", null).metas().get(1);

        assertThat(pago.categoriaId()).isEqualTo(PAGO_ID);
        assertThat(pago.meses().get(0).gastado()).isZero();
        assertThat(pago.gastado()).isZero();
    }

    @Test
    void unaCategoriaSinFilaEnElMesSeTrataComoCeros() {
        filas(OCTUBRE, OCTUBRE, Map.of(OCTUBRE, Map.of()));

        MesMetaResponse mes = service.cumplimiento(PRESUPUESTO_ID, USUARIO_ID, "2026-10", null)
                .metas().get(0).meses().get(0);

        assertThat(mes.asignado()).isZero();
        assertThat(mes.gastado()).isZero();
        assertThat(mes.necesidad()).isEqualTo(100_000L);
        assertThat(mes.porcentaje()).isZero();
        assertThat(mes.estado()).isEqualTo(EstadoMeta.FALTA);
    }

    @Test
    void sinMetasDevuelveLaListaVacia() {
        when(metaRepository.findDelPresupuestoEnOrdenDelArbol(PRESUPUESTO_ID))
                .thenReturn(List.of());
        filas(OCTUBRE, OCTUBRE, Map.of(OCTUBRE, Map.of()));

        assertThat(service.cumplimiento(PRESUPUESTO_ID, USUARIO_ID, "2026-10", null).metas())
                .isEmpty();
    }

    @Test
    void noHayConsultasPorMesNiPorMeta() {
        filas(OCTUBRE, OCTUBRE, Map.of(OCTUBRE, Map.of()));
        YearMonth desde = YearMonth.of(2024, 11);
        java.util.Map<YearMonth, Map<Long, FilaMes>> largo = new java.util.LinkedHashMap<>();
        for (YearMonth mes = desde; !mes.isAfter(OCTUBRE); mes = mes.plusMonths(1)) {
            largo.put(mes, Map.of());
        }
        filas(desde, OCTUBRE, largo);

        service.cumplimiento(PRESUPUESTO_ID, USUARIO_ID, "2026-10", null);
        service.cumplimiento(PRESUPUESTO_ID, USUARIO_ID, "2024-11", "2026-10");

        verify(mesService, times(2)).calcularFilas(any(), any(), any());
        verify(metaRepository, times(2)).findDelPresupuestoEnOrdenDelArbol(PRESUPUESTO_ID);
        verify(pospuestaRepository, times(2)).findPospuestasEnRango(any(), any(), any());
    }
}
