package com.presupuesto.asignacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.presupuesto.asignacion.dto.request.AsignarRequest;
import com.presupuesto.asignacion.dto.request.MoverDineroRequest;
import com.presupuesto.asignacion.dto.response.AsignacionActualizadaResponse;
import com.presupuesto.asignacion.dto.response.MesPresupuestoResponse;
import com.presupuesto.asignacion.entity.AsignacionMensual;
import com.presupuesto.asignacion.repository.AsignacionMensualRepository;
import com.presupuesto.asignacion.service.CalculoMensual.FilaMes;
import com.presupuesto.asignacion.service.CalculoMensual.ResultadoMes;
import com.presupuesto.categoria.entity.Categoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.comun.excepcion.CodigoError;
import com.presupuesto.comun.excepcion.ConflictoException;
import com.presupuesto.comun.excepcion.DatosInvalidosException;
import com.presupuesto.comun.excepcion.NegocioException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.comun.excepcion.ReglaNegocioException;
import com.presupuesto.presupuesto.service.PresupuestoService;
import java.time.LocalDate;
import java.time.YearMonth;
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
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AsignacionServiceTest {

    private static final long USUARIO_ID = 10L;
    private static final long PRESUPUESTO_ID = 20L;
    private static final long COMIDA_ID = 1L;
    private static final long OCIO_ID = 2L;
    private static final YearMonth ENERO = YearMonth.of(2026, 1);
    private static final LocalDate PRIMERO = LocalDate.of(2026, 1, 1);

    @Mock
    private AsignacionMensualRepository asignacionRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private CalculadoraMes calculadora;

    @Mock
    private MesPresupuestoService mesService;

    @Mock
    private PresupuestoService presupuestoService;

    private AsignacionService service;
    private Categoria comida;
    private Categoria ocio;

    @BeforeEach
    void preparar() {
        service = new AsignacionService(
                asignacionRepository, categoriaRepository, calculadora, mesService,
                presupuestoService);
        comida = Categoria.builder().id(COMIDA_ID).nombre("Comida").build();
        ocio = Categoria.builder().id(OCIO_ID).nombre("Ocio").build();
        when(categoriaRepository.findByIdAndGrupoPresupuestoId(COMIDA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(comida));
        when(categoriaRepository.findByIdAndGrupoPresupuestoId(OCIO_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(ocio));
        when(asignacionRepository.findByCategoriaIdAndMes(any(), any()))
                .thenReturn(Optional.empty());
        when(calculadora.calcular(PRESUPUESTO_ID, ENERO)).thenReturn(new ResultadoMes(
                380_000L,
                Map.of(COMIDA_ID, new FilaMes(100_000L, -30_000L, 70_000L),
                        OCIO_ID, FilaMes.CERO)));
        when(mesService.construir(PRESUPUESTO_ID, ENERO, true))
                .thenReturn(MesPresupuestoResponse.desde(ENERO, 380_000L, List.of()));
    }

    // ---------- asignar ----------

    @Test
    void asignarCreaLaFilaConElBuilderYDevuelveLaFilaCalculadaYElListo() {
        AsignacionActualizadaResponse respuesta = service.asignar(
                PRESUPUESTO_ID, USUARIO_ID, "2026-01", COMIDA_ID, new AsignarRequest(100_000L));

        ArgumentCaptor<List<AsignacionMensual>> captura = capturar();
        verify(asignacionRepository).saveAllAndFlush(captura.capture());
        AsignacionMensual guardada = captura.getValue().get(0);
        assertThat(guardada.getCategoria()).isSameAs(comida);
        assertThat(guardada.getMes()).isEqualTo(PRIMERO);
        assertThat(guardada.getAsignado()).isEqualTo(100_000L);
        assertThat(respuesta.categoria().asignado()).isEqualTo(100_000L);
        assertThat(respuesta.categoria().disponible()).isEqualTo(70_000L);
        assertThat(respuesta.listoParaAsignar()).isEqualTo(380_000L);
        verify(presupuestoService).obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID);
    }

    @Test
    void asignarUnaFilaExistenteLaActualizaSinSumarNiCrearOtra() {
        AsignacionMensual existente = AsignacionMensual.builder()
                .categoria(comida).mes(PRIMERO).asignado(100_000L).build();
        when(asignacionRepository.findByCategoriaIdAndMes(COMIDA_ID, PRIMERO))
                .thenReturn(Optional.of(existente));

        service.asignar(
                PRESUPUESTO_ID, USUARIO_ID, "2026-01", COMIDA_ID, new AsignarRequest(60_000L));

        ArgumentCaptor<List<AsignacionMensual>> captura = capturar();
        verify(asignacionRepository).saveAllAndFlush(captura.capture());
        assertThat(captura.getValue()).containsExactly(existente);
        assertThat(existente.getAsignado()).isEqualTo(60_000L);
    }

    @Test
    void asignarAdmiteCeroYNegativosYCategoriasOcultas() {
        comida.ocultar();

        service.asignar(PRESUPUESTO_ID, USUARIO_ID, "2026-01", COMIDA_ID, new AsignarRequest(0L));
        service.asignar(
                PRESUPUESTO_ID, USUARIO_ID, "2026-01", COMIDA_ID, new AsignarRequest(-5_000L));

        verify(asignacionRepository, org.mockito.Mockito.times(2)).saveAllAndFlush(anyList());
    }

    @Test
    void asignarDa404PorPresupuestoPorCategoriaYNoGuardaNada() {
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.asignar(PRESUPUESTO_ID, USUARIO_ID, "2026-01", 999L,
                        new AsignarRequest(1L)));
        when(presupuestoService.obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID))
                .thenThrow(new RecursoNoEncontradoException("Presupuesto no encontrado"));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.asignar(PRESUPUESTO_ID, USUARIO_ID, "2026-01", COMIDA_ID,
                        new AsignarRequest(1L)));

        verify(asignacionRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void asignarConMesInvalidoDa400DespuesDelPresupuesto() {
        NegocioException error = assertThrows(DatosInvalidosException.class,
                () -> service.asignar(PRESUPUESTO_ID, USUARIO_ID, "2026-13", COMIDA_ID,
                        new AsignarRequest(1L)));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.DATOS_INVALIDOS);
        verify(presupuestoService).obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID);
        verify(asignacionRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void unaViolacionDeLaRestriccionUnicaSeTraduceAConflicto() {
        when(asignacionRepository.saveAllAndFlush(anyList()))
                .thenThrow(new DataIntegrityViolationException("uk_asignaciones"));

        ConflictoException error = assertThrows(ConflictoException.class,
                () -> service.asignar(PRESUPUESTO_ID, USUARIO_ID, "2026-01", COMIDA_ID,
                        new AsignarRequest(1L)));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.CONFLICTO);
    }

    // ---------- mover dinero ----------

    @Test
    void moverDineroRestaAlOrigenSumaAlDestinoYCreaLaFilaQueFalta() {
        AsignacionMensual deComida = AsignacionMensual.builder()
                .categoria(comida).mes(PRIMERO).asignado(100_000L).build();
        when(asignacionRepository.findByCategoriaIdAndMes(COMIDA_ID, PRIMERO))
                .thenReturn(Optional.of(deComida));

        MesPresupuestoResponse mes = service.moverDinero(
                PRESUPUESTO_ID, USUARIO_ID, "2026-01",
                new MoverDineroRequest(COMIDA_ID, OCIO_ID, 30_000L));

        ArgumentCaptor<List<AsignacionMensual>> captura = capturar();
        verify(asignacionRepository).saveAllAndFlush(captura.capture());
        assertThat(deComida.getAsignado()).isEqualTo(70_000L);
        AsignacionMensual deOcio = captura.getValue().get(1);
        assertThat(deOcio.getCategoria()).isSameAs(ocio);
        assertThat(deOcio.getMes()).isEqualTo(PRIMERO);
        assertThat(deOcio.getAsignado()).isEqualTo(30_000L);
        assertThat(mes.mes()).isEqualTo("2026-01");
        verify(presupuestoService).obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID);
    }

    @Test
    void moverTodoElDisponibleSePermite() {
        service.moverDinero(PRESUPUESTO_ID, USUARIO_ID, "2026-01",
                new MoverDineroRequest(COMIDA_ID, OCIO_ID, 70_000L));

        verify(asignacionRepository).saveAllAndFlush(anyList());
    }

    @Test
    void sinDisponibleSuficienteDa422YNoGuardaNada() {
        NegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.moverDinero(PRESUPUESTO_ID, USUARIO_ID, "2026-01",
                        new MoverDineroRequest(COMIDA_ID, OCIO_ID, 70_001L)));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.REGLA_NEGOCIO_VIOLADA);
        verify(asignacionRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void unOrigenSobregastadoODesconocidoSinDatosDa422() {
        when(calculadora.calcular(PRESUPUESTO_ID, ENERO)).thenReturn(new ResultadoMes(
                0L, Map.of(COMIDA_ID, new FilaMes(0L, -5_000L, -5_000L))));

        assertThrows(ReglaNegocioException.class,
                () -> service.moverDinero(PRESUPUESTO_ID, USUARIO_ID, "2026-01",
                        new MoverDineroRequest(COMIDA_ID, OCIO_ID, 1L)));
        assertThrows(ReglaNegocioException.class,
                () -> service.moverDinero(PRESUPUESTO_ID, USUARIO_ID, "2026-01",
                        new MoverDineroRequest(OCIO_ID, COMIDA_ID, 1L)));
        verify(asignacionRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void elMismoOrigenYDestinoDa400() {
        NegocioException error = assertThrows(DatosInvalidosException.class,
                () -> service.moverDinero(PRESUPUESTO_ID, USUARIO_ID, "2026-01",
                        new MoverDineroRequest(COMIDA_ID, COMIDA_ID, 1L)));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.DATOS_INVALIDOS);
        verify(asignacionRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void unaCategoriaAjenaComoOrigenODestinoDa404YNoGuardaNada() {
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.moverDinero(PRESUPUESTO_ID, USUARIO_ID, "2026-01",
                        new MoverDineroRequest(999L, OCIO_ID, 1L)));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.moverDinero(PRESUPUESTO_ID, USUARIO_ID, "2026-01",
                        new MoverDineroRequest(COMIDA_ID, 999L, 1L)));

        verify(asignacionRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void moverConPresupuestoAjenoOMesInvalidoDa404Y400() {
        assertThrows(DatosInvalidosException.class,
                () -> service.moverDinero(PRESUPUESTO_ID, USUARIO_ID, "enero",
                        new MoverDineroRequest(COMIDA_ID, OCIO_ID, 1L)));
        when(presupuestoService.obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID))
                .thenThrow(new RecursoNoEncontradoException("Presupuesto no encontrado"));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.moverDinero(PRESUPUESTO_ID, USUARIO_ID, "enero",
                        new MoverDineroRequest(COMIDA_ID, OCIO_ID, 1L)));

        verify(asignacionRepository, never()).saveAllAndFlush(anyList());
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<AsignacionMensual>> capturar() {
        return ArgumentCaptor.forClass(List.class);
    }
}
