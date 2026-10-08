package com.presupuesto.reporte.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.presupuesto.asignacion.repository.ActividadMensualRepository;
import com.presupuesto.asignacion.repository.ActividadMensualRepository.ActividadPorMes;
import com.presupuesto.asignacion.repository.ActividadMensualRepository.SinCategoriaPorMes;
import com.presupuesto.asignacion.repository.ActividadMensualRepository.TotalPorMes;
import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.entity.GrupoCategoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.categoria.repository.GrupoCategoriaRepository;
import com.presupuesto.comun.excepcion.DatosInvalidosException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.presupuesto.service.PresupuestoService;
import com.presupuesto.reporte.dto.response.GastoPorCategoriaResponse;
import com.presupuesto.reporte.dto.response.GrupoGastoResponse;
import com.presupuesto.reporte.dto.response.IngresosGastosResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GastoReporteServiceTest {

    private static final long PRESUPUESTO_ID = 20L;
    private static final long USUARIO_ID = 5L;

    @Mock
    private PresupuestoService presupuestoService;

    @Mock
    private ActividadMensualRepository actividadRepository;

    @Mock
    private GrupoCategoriaRepository grupoRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    private GastoReporteService service;
    private Categoria comida;
    private Categoria hogar;
    private Categoria metasDeAhorro;
    private GrupoCategoria vidaDiaria;
    private GrupoCategoria ahorro;

    @BeforeEach
    void preparar() {
        service = new GastoReporteService(
                presupuestoService, actividadRepository, grupoRepository, categoriaRepository,
                new ReporteProperties(60));
        vidaDiaria = grupo(1L, "Vida diaria");
        ahorro = grupo(2L, "Ahorro");
        comida = categoria(10L, "Comida", vidaDiaria);
        hogar = categoria(11L, "Hogar", vidaDiaria);
        metasDeAhorro = categoria(12L, "Metas de ahorro", ahorro);
        when(grupoRepository.findByPresupuestoIdOrderByOrden(PRESUPUESTO_ID))
                .thenReturn(List.of(vidaDiaria, ahorro));
        when(categoriaRepository.findByGrupoPresupuestoIdOrderByOrden(PRESUPUESTO_ID))
                .thenReturn(List.of(comida, hogar, metasDeAhorro));
    }

    @Test
    void conUnPresupuestoAjenoResponde404SinConsultarNiValidarElRango() {
        doThrow(new RecursoNoEncontradoException("Presupuesto no encontrado"))
                .when(presupuestoService).obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID);

        assertThatThrownBy(() -> service.gastoPorCategoria(
                PRESUPUESTO_ID, USUARIO_ID, "2026-13", null))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> service.ingresosGastos(
                PRESUPUESTO_ID, USUARIO_ID, "2026-13", null))
                .isInstanceOf(RecursoNoEncontradoException.class);

        verifyNoInteractions(actividadRepository, grupoRepository, categoriaRepository);
    }

    @Test
    void unRangoInvalidoNoConsultaDatos() {
        assertThatThrownBy(() -> service.gastoPorCategoria(
                PRESUPUESTO_ID, USUARIO_ID, "2026-10", "2026-09"))
                .isInstanceOf(DatosInvalidosException.class);
        assertThatThrownBy(() -> service.ingresosGastos(
                PRESUPUESTO_ID, USUARIO_ID, "ayer", "2026-09"))
                .isInstanceOf(DatosInvalidosException.class);

        verifyNoInteractions(actividadRepository, grupoRepository, categoriaRepository);
    }

    @Test
    void elEjemploDeOctubreDaLosTotalesYLosPorcentajes() {
        datosDeOctubre();

        GastoPorCategoriaResponse respuesta =
                service.gastoPorCategoria(PRESUPUESTO_ID, USUARIO_ID, "2026-10", "2026-10");

        assertThat(respuesta.total()).isEqualTo(265_000L);
        assertThat(respuesta.sinCategoria().total()).isEqualTo(5_000L);
        assertThat(respuesta.sinCategoria().porcentaje()).isEqualTo(189L);
        assertThat(respuesta.grupos()).extracting(GrupoGastoResponse::nombre)
                .containsExactly("Vida diaria", "Ahorro");
        GrupoGastoResponse vida = respuesta.grupos().get(0);
        assertThat(vida.total()).isEqualTo(160_000L);
        assertThat(vida.categorias()).extracting("nombre", "total", "porcentaje")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Comida", 140_000L, 5283L),
                        org.assertj.core.groups.Tuple.tuple("Hogar", 20_000L, 755L));
        assertThat(respuesta.grupos().get(1).categorias().get(0).porcentaje()).isEqualTo(3774L);
    }

    @Test
    void loQueTieneMovimientosAntesDelRangoNoApareceNiSuma() {
        List<ActividadPorMes> antiguas = List.of(actividad(comida, 2026, 8, -999_000L));
        when(actividadRepository.actividadSimple(any(), any())).thenReturn(antiguas);

        GastoPorCategoriaResponse respuesta =
                service.gastoPorCategoria(PRESUPUESTO_ID, USUARIO_ID, "2026-10", "2026-10");

        assertThat(respuesta.total()).isZero();
        assertThat(respuesta.grupos()).isEmpty();
        assertThat(respuesta.sinCategoria().total()).isZero();
        assertThat(respuesta.sinCategoria().porcentaje()).isZero();
    }

    @Test
    void ingresosGastosDevuelveUnElementoPorMesYGastosIgualesAlReporteDeCategorias() {
        datosDeOctubre();

        IngresosGastosResponse respuesta =
                service.ingresosGastos(PRESUPUESTO_ID, USUARIO_ID, "2026-10", "2026-11");

        assertThat(respuesta.meses()).hasSize(2);
        assertThat(respuesta.meses().get(0).ingresos()).isEqualTo(500_000L);
        assertThat(respuesta.meses().get(0).gastos()).isEqualTo(265_000L);
        assertThat(respuesta.meses().get(0).neto()).isEqualTo(235_000L);
        assertThat(respuesta.meses().get(1).ingresos()).isZero();
        assertThat(respuesta.meses().get(1).gastos()).isZero();
        assertThat(respuesta.gastos()).isEqualTo(
                service.gastoPorCategoria(PRESUPUESTO_ID, USUARIO_ID, "2026-10", "2026-11")
                        .total());
    }

    @Test
    void elNumeroDeConsultasNoDependeDelRango() {
        datosDeOctubre();

        service.gastoPorCategoria(PRESUPUESTO_ID, USUARIO_ID, "2026-10", "2026-10");
        service.gastoPorCategoria(PRESUPUESTO_ID, USUARIO_ID, "2024-11", "2026-10");

        verify(actividadRepository, times(2)).actividadSimple(eq(PRESUPUESTO_ID), any());
        verify(actividadRepository, times(2)).actividadDividida(eq(PRESUPUESTO_ID), any());
        verify(actividadRepository, times(2))
                .sinCategoriaPorMes(eq(PRESUPUESTO_ID), any(), any());
        verify(actividadRepository, times(2))
                .salidasDivididasSinCategoria(eq(PRESUPUESTO_ID), any(), any());
        verify(grupoRepository, times(2)).findByPresupuestoIdOrderByOrden(PRESUPUESTO_ID);
        verify(categoriaRepository, times(2))
                .findByGrupoPresupuestoIdOrderByOrden(PRESUPUESTO_ID);
    }

    @Test
    void ingresosGastosNoConsultaGruposNiCategorias() {
        datosDeOctubre();

        service.ingresosGastos(PRESUPUESTO_ID, USUARIO_ID, "2025-01", "2026-10");

        verifyNoInteractions(grupoRepository, categoriaRepository);
        verify(actividadRepository, times(1)).actividadSimple(eq(PRESUPUESTO_ID), any());
        verify(actividadRepository, times(1))
                .sinCategoriaPorMes(eq(PRESUPUESTO_ID), eq(LocalDate.of(2025, 1, 1)),
                        eq(LocalDate.of(2026, 10, 31)));
    }

    private void datosDeOctubre() {
        List<ActividadPorMes> simples = List.of(
                actividad(comida, 2026, 10, -110_000L),
                actividad(comida, 2026, 10, 10_000L),
                actividad(metasDeAhorro, 2026, 10, -100_000L));
        List<ActividadPorMes> divididas = List.of(
                actividad(comida, 2026, 10, -40_000L),
                actividad(hogar, 2026, 10, -20_000L));
        List<SinCategoriaPorMes> sinCategoria =
                List.of(sinCategoria(2026, 10, 500_000L, -5_000L));
        when(actividadRepository.actividadSimple(eq(PRESUPUESTO_ID), any())).thenReturn(simples);
        when(actividadRepository.actividadDividida(eq(PRESUPUESTO_ID), any()))
                .thenReturn(divididas);
        when(actividadRepository.sinCategoriaPorMes(eq(PRESUPUESTO_ID), any(), any()))
                .thenReturn(sinCategoria);
        when(actividadRepository.salidasDivididasSinCategoria(eq(PRESUPUESTO_ID), any(), any()))
                .thenReturn(List.of());
    }

    private static ActividadPorMes actividad(Categoria categoria, int anio, int mes, long total) {
        ActividadPorMes fila = mock(ActividadPorMes.class);
        when(fila.getCategoriaId()).thenReturn(categoria.getId());
        when(fila.getAnio()).thenReturn(anio);
        when(fila.getMes()).thenReturn(mes);
        when(fila.getTotal()).thenReturn(total);
        return fila;
    }

    private static SinCategoriaPorMes sinCategoria(
            int anio, int mes, long ingresos, long salidas) {
        SinCategoriaPorMes fila = mock(SinCategoriaPorMes.class);
        when(fila.getAnio()).thenReturn(anio);
        when(fila.getMes()).thenReturn(mes);
        when(fila.getIngresos()).thenReturn(ingresos);
        when(fila.getSalidas()).thenReturn(salidas);
        return fila;
    }

    @SuppressWarnings("unused")
    private static TotalPorMes total(int anio, int mes, long total) {
        TotalPorMes fila = mock(TotalPorMes.class);
        when(fila.getAnio()).thenReturn(anio);
        when(fila.getMes()).thenReturn(mes);
        when(fila.getTotal()).thenReturn(total);
        return fila;
    }

    private static GrupoCategoria grupo(long id, String nombre) {
        GrupoCategoria grupo = GrupoCategoria.builder().nombre(nombre).build();
        grupo.setId(id);
        return grupo;
    }

    private static Categoria categoria(long id, String nombre, GrupoCategoria grupo) {
        Categoria categoria = Categoria.builder().nombre(nombre).grupo(grupo).build();
        categoria.setId(id);
        return categoria;
    }
}
