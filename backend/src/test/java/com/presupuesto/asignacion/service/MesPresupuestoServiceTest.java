package com.presupuesto.asignacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.presupuesto.asignacion.dto.response.MesPresupuestoResponse;
import com.presupuesto.asignacion.service.CalculoMensual.FilaMes;
import com.presupuesto.asignacion.service.CalculoMensual.ResultadoMes;
import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.entity.GrupoCategoria;
import com.presupuesto.cuenta.entity.Cuenta;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.categoria.repository.GrupoCategoriaRepository;
import com.presupuesto.comun.excepcion.DatosInvalidosException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.presupuesto.service.PresupuestoService;
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
class MesPresupuestoServiceTest {

    private static final long USUARIO_ID = 10L;
    private static final long PRESUPUESTO_ID = 20L;
    private static final YearMonth ENERO = YearMonth.of(2026, 1);

    @Mock
    private GrupoCategoriaRepository grupoRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private CalculadoraMes calculadora;

    @Mock
    private PresupuestoService presupuestoService;

    private MesPresupuestoService service;
    private GrupoCategoria hogar;
    private GrupoCategoria viejo;
    private Categoria comida;
    private Categoria ocio;
    private Categoria vieja;

    @BeforeEach
    void preparar() {
        service = new MesPresupuestoService(
                grupoRepository, categoriaRepository, calculadora, presupuestoService);
        hogar = GrupoCategoria.builder().id(1L).nombre("Hogar").orden(0).build();
        viejo = GrupoCategoria.builder().id(2L).nombre("Viejo").orden(1).build();
        viejo.ocultar();
        comida = Categoria.builder().id(10L).grupo(hogar).nombre("Comida").orden(0).build();
        ocio = Categoria.builder().id(11L).grupo(hogar).nombre("Ocio").orden(1).build();
        vieja = Categoria.builder().id(12L).grupo(hogar).nombre("Vieja").orden(2).build();
        vieja.ocultar();
        Categoria deGrupoOculto = Categoria.builder()
                .id(13L).grupo(viejo).nombre("Dentro").orden(0).build();
        when(grupoRepository.findByPresupuestoIdOrderByOrden(PRESUPUESTO_ID))
                .thenReturn(List.of(hogar, viejo));
        when(grupoRepository.findByPresupuestoIdAndOcultoFalseOrderByOrden(PRESUPUESTO_ID))
                .thenReturn(List.of(hogar));
        when(categoriaRepository.findByGrupoPresupuestoIdOrderByOrden(PRESUPUESTO_ID))
                .thenReturn(List.of(comida, ocio, vieja, deGrupoOculto));
        when(categoriaRepository.findByGrupoPresupuestoIdAndOcultaFalseOrderByOrden(PRESUPUESTO_ID))
                .thenReturn(List.of(comida, ocio));
        when(calculadora.calcular(PRESUPUESTO_ID, ENERO)).thenReturn(new ResultadoMes(
                380_000L,
                Map.of(10L, new FilaMes(100_000L, -30_000L, 70_000L),
                        11L, new FilaMes(20_000L, 0L, 20_000L))));
    }

    @Test
    void calcularFilasDelegaEnLaCalculadoraSinValidarElPresupuesto() {
        YearMonth febrero = YearMonth.of(2026, 2);
        Map<YearMonth, Map<Long, FilaMes>> filas = Map.of(
                ENERO, Map.of(10L, new FilaMes(1_000L, -300L, 700L)),
                febrero, Map.of());
        when(calculadora.calcularFilas(PRESUPUESTO_ID, ENERO, febrero)).thenReturn(filas);

        Map<YearMonth, Map<Long, FilaMes>> resultado =
                service.calcularFilas(PRESUPUESTO_ID, ENERO, febrero);

        assertThat(resultado).isSameAs(filas);
        verify(calculadora).calcularFilas(PRESUPUESTO_ID, ENERO, febrero);
        verify(presupuestoService, never()).obtenerDelUsuario(any(), any());
    }

    @Test
    void armaLosGruposYCategoriasEnOrdenConSusCifrasYTotales() {
        MesPresupuestoResponse mes = service.obtener(PRESUPUESTO_ID, USUARIO_ID, "2026-01", false);

        assertThat(mes.mes()).isEqualTo("2026-01");
        assertThat(mes.listoParaAsignar()).isEqualTo(380_000L);
        assertThat(mes.totalAsignado()).isEqualTo(120_000L);
        assertThat(mes.totalActividad()).isEqualTo(-30_000L);
        assertThat(mes.totalDisponible()).isEqualTo(90_000L);
        assertThat(mes.grupos()).hasSize(1);
        assertThat(mes.grupos().get(0).categorias())
                .extracting(c -> c.nombre())
                .containsExactly("Comida", "Ocio");
        assertThat(mes.grupos().get(0).categorias().get(0).disponible()).isEqualTo(70_000L);
        verify(presupuestoService).obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID);
    }

    @Test
    void conIncluirOcultasTraeGruposYCategoriasOcultosConCero() {
        MesPresupuestoResponse mes = service.obtener(PRESUPUESTO_ID, USUARIO_ID, "2026-01", true);

        assertThat(mes.grupos()).extracting(g -> g.nombre()).containsExactly("Hogar", "Viejo");
        assertThat(mes.grupos().get(0).categorias()).hasSize(3);
        assertThat(mes.grupos().get(0).categorias().get(2).oculta()).isTrue();
        assertThat(mes.grupos().get(0).categorias().get(2).disponible()).isZero();
        assertThat(mes.grupos().get(1).categorias()).hasSize(1);
    }

    @Test
    void unPresupuestoSinDatosDaTodoEnCero() {
        when(calculadora.calcular(PRESUPUESTO_ID, ENERO))
                .thenReturn(new ResultadoMes(0L, Map.of()));

        MesPresupuestoResponse mes = service.obtener(PRESUPUESTO_ID, USUARIO_ID, "2026-01", false);

        assertThat(mes.listoParaAsignar()).isZero();
        assertThat(mes.totalAsignado()).isZero();
        assertThat(mes.grupos().get(0).categorias())
                .allSatisfy(c -> assertThat(c.sobregastada()).isFalse());
    }

    @Test
    void elPresupuestoAjenoDa404AntesQueElMesInvalido() {
        when(presupuestoService.obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID))
                .thenThrow(new RecursoNoEncontradoException("Presupuesto no encontrado"));

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.obtener(PRESUPUESTO_ID, USUARIO_ID, "basura", false));
        verify(calculadora, never()).calcular(any(), any());
    }

    @Test
    void unMesInvalidoDa400SinCalcular() {
        assertThrows(DatosInvalidosException.class,
                () -> service.obtener(PRESUPUESTO_ID, USUARIO_ID, "2026-13", false));
        verify(calculadora, never()).calcular(any(), any());
    }

    @Test
    void calcularDevuelveElResultadoDeLaCalculadoraSinValidarElPresupuesto() {
        ResultadoMes resultado = service.calcular(PRESUPUESTO_ID, ENERO);

        assertThat(resultado.listoParaAsignar()).isEqualTo(380_000L);
        verify(presupuestoService, never()).obtenerDelUsuario(any(), any());
    }

    @Test
    void laFilaDeUnaCategoriaDePagoTraeLaMarcaYLaTarjeta() {
        Categoria pago = Categoria.builder().id(14L).grupo(hogar).nombre("Pago: Visa").orden(3)
                .cuentaTarjeta(Cuenta.builder().id(44L).build()).build();
        when(categoriaRepository.findByGrupoPresupuestoIdAndOcultaFalseOrderByOrden(PRESUPUESTO_ID))
                .thenReturn(List.of(comida, pago));
        when(calculadora.calcular(PRESUPUESTO_ID, ENERO)).thenReturn(new ResultadoMes(
                350_000L,
                Map.of(10L, new FilaMes(100_000L, -30_000L, 70_000L),
                        14L, new FilaMes(0L, 30_000L, 30_000L))));

        MesPresupuestoResponse mes = service.obtener(PRESUPUESTO_ID, USUARIO_ID, "2026-01", false);

        assertThat(mes.grupos().get(0).categorias()).hasSize(2);
        assertThat(mes.grupos().get(0).categorias().get(0).esPagoTarjeta()).isFalse();
        assertThat(mes.grupos().get(0).categorias().get(0).cuentaId()).isNull();
        assertThat(mes.grupos().get(0).categorias().get(1).esPagoTarjeta()).isTrue();
        assertThat(mes.grupos().get(0).categorias().get(1).cuentaId()).isEqualTo(44L);
        assertThat(mes.grupos().get(0).categorias().get(1).disponible()).isEqualTo(30_000L);
        assertThat(mes.totalDisponible()).isEqualTo(100_000L);
    }
}
