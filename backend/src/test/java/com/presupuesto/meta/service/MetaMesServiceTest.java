package com.presupuesto.meta.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.presupuesto.meta.dto.response.EstadoMeta;
import com.presupuesto.meta.dto.response.MetaMesResponse;
import com.presupuesto.meta.dto.response.MetasMesResponse;
import com.presupuesto.meta.entity.FrecuenciaMeta;
import com.presupuesto.meta.entity.Meta;
import com.presupuesto.meta.entity.MetaPospuesta;
import com.presupuesto.meta.entity.TipoMeta;
import com.presupuesto.meta.repository.MetaPospuestaRepository;
import com.presupuesto.meta.repository.MetaRepository;
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
class MetaMesServiceTest {

    private static final long USUARIO_ID = 10L;
    private static final long PRESUPUESTO_ID = 20L;
    private static final long COMIDA_ID = 1L;
    private static final long OCIO_ID = 2L;
    private static final long VIEJA_ID = 3L;
    private static final YearMonth OCTUBRE = YearMonth.of(2026, 10);
    private static final LocalDate PRIMERO_OCTUBRE = LocalDate.of(2026, 10, 1);

    @Mock
    private MetaRepository metaRepository;

    @Mock
    private MetaPospuestaRepository pospuestaRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private MesPresupuestoService mesService;

    @Mock
    private PresupuestoService presupuestoService;

    private MetaMesService service;
    private Meta deComida;
    private Meta deOcio;
    private Meta deVieja;

    @BeforeEach
    void preparar() {
        service = new MetaMesService(metaRepository, pospuestaRepository, categoriaRepository,
                mesService, presupuestoService);
        Categoria comida = Categoria.builder().id(COMIDA_ID).nombre("Comida").build();
        Categoria ocio = Categoria.builder().id(OCIO_ID).nombre("Ocio").build();
        Categoria vieja = Categoria.builder().id(VIEJA_ID).nombre("Vieja").build();
        vieja.ocultar();
        deComida = meta(10L, comida, TipoMeta.MONTO_MENSUAL, 100_000L);
        deOcio = meta(11L, ocio, TipoMeta.SALDO_OBJETIVO, 300_000L);
        deVieja = meta(12L, vieja, TipoMeta.SALDO_OBJETIVO, 50_000L);
        when(categoriaRepository.findByIdAndGrupoPresupuestoId(COMIDA_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(comida));
        when(categoriaRepository.findByIdAndGrupoPresupuestoId(OCIO_ID, PRESUPUESTO_ID))
                .thenReturn(Optional.of(ocio));
        when(categoriaRepository.findByIdAndGrupoPresupuestoId(4L, PRESUPUESTO_ID))
                .thenReturn(Optional.of(Categoria.builder().id(4L).nombre("Sin meta").build()));
        when(metaRepository.findByCategoriaId(COMIDA_ID)).thenReturn(Optional.of(deComida));
        when(metaRepository.findByCategoriaId(OCIO_ID)).thenReturn(Optional.of(deOcio));
        when(metaRepository.findByCategoriaId(4L)).thenReturn(Optional.empty());
        when(metaRepository.findDelPresupuestoEnOrdenDelArbol(PRESUPUESTO_ID))
                .thenReturn(List.of(deComida, deOcio, deVieja));
        when(pospuestaRepository.findMetaIdsPospuestas(PRESUPUESTO_ID, PRIMERO_OCTUBRE))
                .thenReturn(List.of());
        when(pospuestaRepository.findByMetaIdAndMes(any(), any())).thenReturn(Optional.empty());
        // Comida: asignado 60000. Ocio: inicial 120000, asignado 0. Vieja: inicial 20000.
        when(mesService.calcular(PRESUPUESTO_ID, OCTUBRE)).thenReturn(new ResultadoMes(0L, Map.of(
                COMIDA_ID, new FilaMes(60_000L, 0L, 60_000L),
                OCIO_ID, new FilaMes(0L, 0L, 120_000L),
                VIEJA_ID, new FilaMes(0L, 0L, 20_000L))));
    }

    private static Meta meta(long id, Categoria categoria, TipoMeta tipo, long monto) {
        Meta meta = Meta.builder().id(id).categoria(categoria).build();
        meta.reemplazar(tipo, monto,
                tipo == TipoMeta.MONTO_MENSUAL ? FrecuenciaMeta.MENSUAL : null,
                null, null, null, null);
        return meta;
    }

    // ---------- estado del mes ----------

    @Test
    void elEstadoDelMesCalculaNecesidadFaltanteYEstadoDeCadaMeta() {
        MetasMesResponse respuesta = service.obtener(PRESUPUESTO_ID, USUARIO_ID, "2026-10", true);

        assertThat(respuesta.mes()).isEqualTo("2026-10");
        assertThat(respuesta.metas()).extracting(MetaMesResponse::categoriaId)
                .containsExactly(COMIDA_ID, OCIO_ID, VIEJA_ID);
        MetaMesResponse comida = respuesta.metas().get(0);
        assertThat(comida.necesidad()).isEqualTo(100_000L);
        assertThat(comida.asignado()).isEqualTo(60_000L);
        assertThat(comida.faltante()).isEqualTo(40_000L);
        assertThat(comida.estado()).isEqualTo(EstadoMeta.FALTA);
        MetaMesResponse ocio = respuesta.metas().get(1);
        assertThat(ocio.necesidad()).isEqualTo(180_000L);
        assertThat(ocio.faltante()).isEqualTo(180_000L);
        assertThat(ocio.disponible()).isEqualTo(120_000L);
    }

    @Test
    void sinIncluirOcultasOmiteLasMetasDeCategoriasOcultasYSuTotal() {
        MetasMesResponse sin = service.obtener(PRESUPUESTO_ID, USUARIO_ID, "2026-10", false);
        MetasMesResponse con = service.obtener(PRESUPUESTO_ID, USUARIO_ID, "2026-10", true);

        assertThat(sin.metas()).extracting(MetaMesResponse::categoriaId)
                .containsExactly(COMIDA_ID, OCIO_ID);
        assertThat(sin.totalFaltante()).isEqualTo(220_000L);
        assertThat(con.metas()).hasSize(3);
        assertThat(con.totalFaltante()).isEqualTo(220_000L + 30_000L);
    }

    @Test
    void unaMetaPospuestaEnElMesTieneNecesidadCeroYEstadoPospuesta() {
        when(pospuestaRepository.findMetaIdsPospuestas(PRESUPUESTO_ID, PRIMERO_OCTUBRE))
                .thenReturn(List.of(deComida.getId()));

        MetasMesResponse respuesta = service.obtener(PRESUPUESTO_ID, USUARIO_ID, "2026-10", false);

        MetaMesResponse comida = respuesta.metas().get(0);
        assertThat(comida.necesidad()).isZero();
        assertThat(comida.faltante()).isZero();
        assertThat(comida.estado()).isEqualTo(EstadoMeta.POSPUESTA);
        assertThat(respuesta.totalFaltante()).isEqualTo(180_000L);
    }

    @Test
    void sinMetasElEstadoDelMesEstaVacio() {
        when(metaRepository.findDelPresupuestoEnOrdenDelArbol(PRESUPUESTO_ID))
                .thenReturn(List.of());

        MetasMesResponse respuesta = service.obtener(PRESUPUESTO_ID, USUARIO_ID, "2026-10", false);

        assertThat(respuesta.metas()).isEmpty();
        assertThat(respuesta.totalFaltante()).isZero();
    }

    @Test
    void elMesInvalidoResponde400DespuesDeValidarElPresupuesto() {
        NegocioException error = assertThrows(DatosInvalidosException.class,
                () -> service.obtener(PRESUPUESTO_ID, USUARIO_ID, "2026-13", false));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.DATOS_INVALIDOS);
        verify(presupuestoService).obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID);
    }

    @Test
    void unPresupuestoAjenoResponde404AunqueElMesSeaInvalido() {
        when(presupuestoService.obtenerDelUsuario(PRESUPUESTO_ID, USUARIO_ID))
                .thenThrow(new RecursoNoEncontradoException("Presupuesto no encontrado"));

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.obtener(PRESUPUESTO_ID, USUARIO_ID, "2026-13", false));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.posponer(PRESUPUESTO_ID, USUARIO_ID, "2026-13", COMIDA_ID));
        assertThrows(RecursoNoEncontradoException.class,
                () -> service.reanudar(PRESUPUESTO_ID, USUARIO_ID, "2026-13", COMIDA_ID));
    }

    // ---------- posponer ----------

    @Test
    void posponerCreaLaFilaDelMesYDevuelveElElementoPospuesto() {
        MetaMesResponse respuesta = service.posponer(
                PRESUPUESTO_ID, USUARIO_ID, "2026-10", COMIDA_ID);

        ArgumentCaptor<MetaPospuesta> guardada = ArgumentCaptor.forClass(MetaPospuesta.class);
        verify(pospuestaRepository).saveAndFlush(guardada.capture());
        assertThat(guardada.getValue().getMeta()).isSameAs(deComida);
        assertThat(guardada.getValue().getMes()).isEqualTo(PRIMERO_OCTUBRE);
        assertThat(respuesta.estado()).isEqualTo(EstadoMeta.POSPUESTA);
        assertThat(respuesta.necesidad()).isZero();
    }

    @Test
    void posponerDeNuevoEsIdempotenteYNoCreaOtraFila() {
        when(pospuestaRepository.findByMetaIdAndMes(deComida.getId(), PRIMERO_OCTUBRE))
                .thenReturn(Optional.of(MetaPospuesta.builder()
                        .meta(deComida).mes(PRIMERO_OCTUBRE).build()));

        MetaMesResponse respuesta = service.posponer(
                PRESUPUESTO_ID, USUARIO_ID, "2026-10", COMIDA_ID);

        verify(pospuestaRepository, never()).saveAndFlush(any(MetaPospuesta.class));
        assertThat(respuesta.estado()).isEqualTo(EstadoMeta.POSPUESTA);
    }

    @Test
    void posponerConUnaCarreraDeCreacionResponde409() {
        when(pospuestaRepository.saveAndFlush(any(MetaPospuesta.class)))
                .thenThrow(new DataIntegrityViolationException("duplicada"));

        assertThrows(ConflictoException.class,
                () -> service.posponer(PRESUPUESTO_ID, USUARIO_ID, "2026-10", COMIDA_ID));
    }

    @Test
    void posponerOReanudarUnaCategoriaSinMetaOAjenaResponde404() {
        for (long categoria : new long[] {4L, 99L}) {
            assertThrows(RecursoNoEncontradoException.class,
                    () -> service.posponer(PRESUPUESTO_ID, USUARIO_ID, "2026-10", categoria));
            assertThrows(RecursoNoEncontradoException.class,
                    () -> service.reanudar(PRESUPUESTO_ID, USUARIO_ID, "2026-10", categoria));
        }
        verify(pospuestaRepository, never()).saveAndFlush(any(MetaPospuesta.class));
    }

    // ---------- reanudar ----------

    @Test
    void reanudarBorraLaFilaDelMesYElMesSeCalculaConNormalidad() {
        MetaPospuesta pospuesta = MetaPospuesta.builder()
                .meta(deComida).mes(PRIMERO_OCTUBRE).build();
        when(pospuestaRepository.findByMetaIdAndMes(deComida.getId(), PRIMERO_OCTUBRE))
                .thenReturn(Optional.of(pospuesta));

        MetaMesResponse respuesta = service.reanudar(
                PRESUPUESTO_ID, USUARIO_ID, "2026-10", COMIDA_ID);

        verify(pospuestaRepository).delete(pospuesta);
        assertThat(respuesta.estado()).isEqualTo(EstadoMeta.FALTA);
        assertThat(respuesta.necesidad()).isEqualTo(100_000L);
    }

    @Test
    void reanudarUnMesNoPospuestoNoBorraNadaYResponde200() {
        MetaMesResponse respuesta = service.reanudar(
                PRESUPUESTO_ID, USUARIO_ID, "2026-10", COMIDA_ID);

        verify(pospuestaRepository, never()).delete(any(MetaPospuesta.class));
        assertThat(respuesta.estado()).isEqualTo(EstadoMeta.FALTA);
    }

    @Test
    void posponerSoloAfectaAlMesPedido() {
        service.posponer(PRESUPUESTO_ID, USUARIO_ID, "2026-10", COMIDA_ID);

        ArgumentCaptor<MetaPospuesta> guardada = ArgumentCaptor.forClass(MetaPospuesta.class);
        verify(pospuestaRepository).saveAndFlush(guardada.capture());
        assertThat(guardada.getValue().getMes()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(guardada.getValue().getMes()).isNotEqualTo(LocalDate.of(2026, 11, 1));
    }
}
