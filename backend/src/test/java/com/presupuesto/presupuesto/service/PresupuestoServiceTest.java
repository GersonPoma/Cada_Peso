package com.presupuesto.presupuesto.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.presupuesto.comun.excepcion.CodigoError;
import com.presupuesto.comun.excepcion.ConflictoException;
import com.presupuesto.comun.excepcion.NoAutenticadoException;
import com.presupuesto.comun.excepcion.RecursoNoEncontradoException;
import com.presupuesto.presupuesto.dto.request.ActualizarPresupuestoRequest;
import com.presupuesto.presupuesto.dto.request.CrearPresupuestoRequest;
import com.presupuesto.presupuesto.dto.response.PresupuestoResponse;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.evento.PresupuestoCreadoEvento;
import com.presupuesto.presupuesto.repository.PresupuestoRepository;
import com.presupuesto.usuario.entity.Perfil;
import com.presupuesto.usuario.entity.Usuario;
import com.presupuesto.usuario.repository.PerfilRepository;
import com.presupuesto.usuario.repository.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PresupuestoServiceTest {

    private static final long USUARIO_ID = 10L;

    @Mock
    private PresupuestoRepository presupuestoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PerfilRepository perfilRepository;

    @Mock
    private ApplicationEventPublisher publicador;

    private PresupuestoService service;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        service = new PresupuestoService(
                presupuestoRepository, usuarioRepository, perfilRepository, publicador);
        usuario = Usuario.builder().id(USUARIO_ID).email("ana@ejemplo.com").build();
        when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.of(usuario));
        when(presupuestoRepository.saveAndFlush(any(Presupuesto.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    @Test
    void crearSinMonedaUsaLaMonedaPredeterminadaDelPerfil() {
        when(perfilRepository.findByUsuarioId(USUARIO_ID))
                .thenReturn(Optional.of(perfilConMoneda("USD")));

        PresupuestoResponse response =
                service.crear(USUARIO_ID, new CrearPresupuestoRequest("Viajes", null));

        assertThat(response.moneda()).isEqualTo("USD");
        assertThat(response.nombre()).isEqualTo("Viajes");
    }

    @Test
    void crearConMonedaExplicitaNoConsultaElPerfil() {
        PresupuestoResponse response =
                service.crear(USUARIO_ID, new CrearPresupuestoRequest("Viajes", "EUR"));

        assertThat(response.moneda()).isEqualTo("EUR");
        verify(perfilRepository, never()).findByUsuarioId(any());
    }

    @Test
    void crearSinMonedaYSinPerfilLanzaNoAutenticado() {
        when(perfilRepository.findByUsuarioId(USUARIO_ID)).thenReturn(Optional.empty());

        assertThrows(NoAutenticadoException.class,
                () -> service.crear(USUARIO_ID, new CrearPresupuestoRequest("Viajes", null)));
        verify(presupuestoRepository, never()).saveAndFlush(any());
        verify(publicador, never()).publishEvent(any(Object.class));
    }

    @Test
    void crearPublicaElEventoUnaVezConElPresupuestoGuardado() {
        service.crear(USUARIO_ID, new CrearPresupuestoRequest("Viajes", "USD"));

        ArgumentCaptor<PresupuestoCreadoEvento> captor =
                ArgumentCaptor.forClass(PresupuestoCreadoEvento.class);
        verify(publicador, times(1)).publishEvent(captor.capture());
        assertThat(captor.getValue().presupuesto().getNombre()).isEqualTo("Viajes");
    }

    @Test
    void crearInicialPublicaElEventoUnaVez() {
        Presupuesto creado = service.crearInicial(usuario, "BOB");

        ArgumentCaptor<PresupuestoCreadoEvento> captor =
                ArgumentCaptor.forClass(PresupuestoCreadoEvento.class);
        verify(publicador, times(1)).publishEvent(captor.capture());
        assertThat(captor.getValue().presupuesto()).isSameAs(creado);
    }

    @Test
    void renombrarNoPublicaElEvento() {
        Presupuesto existente = presupuesto(7L, "Casa", "BOB");
        when(presupuestoRepository.findByIdAndUsuarioId(7L, USUARIO_ID))
                .thenReturn(Optional.of(existente));

        service.renombrar(7L, USUARIO_ID, new ActualizarPresupuestoRequest("Hogar"));

        verify(publicador, never()).publishEvent(any(Object.class));
    }

    @Test
    void crearConUnUsuarioInexistenteLanzaNoAutenticado() {
        when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.empty());

        assertThrows(NoAutenticadoException.class,
                () -> service.crear(USUARIO_ID, new CrearPresupuestoRequest("Viajes", "BOB")));
    }

    @Test
    void crearGuardaElNombreNormalizadoYElDueno() {
        service.crear(USUARIO_ID, new CrearPresupuestoRequest("Mi CASA", "BOB"));

        ArgumentCaptor<Presupuesto> captor = ArgumentCaptor.forClass(Presupuesto.class);
        verify(presupuestoRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUsuario()).isSameAs(usuario);
        assertThat(captor.getValue().getNombre()).isEqualTo("Mi CASA");
        assertThat(captor.getValue().getNombreNormalizado()).isEqualTo("mi casa");
    }

    @Test
    void crearConUnNombreRepetidoLanzaConflicto() {
        when(presupuestoRepository.existsByUsuarioIdAndNombreNormalizado(USUARIO_ID, "casa"))
                .thenReturn(true);

        ConflictoException excepcion = assertThrows(ConflictoException.class,
                () -> service.crear(USUARIO_ID, new CrearPresupuestoRequest("CASA", "BOB")));

        assertThat(excepcion.getCodigo()).isEqualTo(CodigoError.PRESUPUESTO_YA_EXISTE);
        verify(presupuestoRepository, never()).saveAndFlush(any());
    }

    @Test
    void unaViolacionDeLaRestriccionUnicaSeTraduceAConflicto() {
        when(presupuestoRepository.saveAndFlush(any(Presupuesto.class)))
                .thenThrow(new DataIntegrityViolationException("uk_presupuestos_usuario_nombre"));

        ConflictoException excepcion = assertThrows(ConflictoException.class,
                () -> service.crear(USUARIO_ID, new CrearPresupuestoRequest("Casa", "BOB")));

        assertThat(excepcion.getCodigo()).isEqualTo(CodigoError.PRESUPUESTO_YA_EXISTE);
    }

    @Test
    void crearInicialUsaElNombreMiPresupuestoYLaMonedaRecibida() {
        Presupuesto creado = service.crearInicial(usuario, "USD");

        assertThat(creado.getNombre()).isEqualTo("Mi presupuesto");
        assertThat(creado.getNombreNormalizado()).isEqualTo("mi presupuesto");
        assertThat(creado.getMoneda()).isEqualTo("USD");
        assertThat(creado.getUsuario()).isSameAs(usuario);
    }

    @Test
    void obtenerDelUsuarioDevuelveElPresupuestoPropio() {
        Presupuesto presupuesto = presupuesto(5L, "Casa", "BOB");
        when(presupuestoRepository.findByIdAndUsuarioId(5L, USUARIO_ID))
                .thenReturn(Optional.of(presupuesto));

        assertThat(service.obtenerDelUsuario(5L, USUARIO_ID)).isSameAs(presupuesto);
    }

    @Test
    void obtenerDelUsuarioInexistenteOAjenoLanzaRecursoNoEncontrado() {
        when(presupuestoRepository.findByIdAndUsuarioId(5L, USUARIO_ID))
                .thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.obtenerDelUsuario(5L, USUARIO_ID));
    }

    @Test
    void listarMapeaLosPresupuestosDelUsuario() {
        when(presupuestoRepository.findByUsuarioIdOrderByNombreNormalizado(USUARIO_ID))
                .thenReturn(List.of(
                        presupuesto(1L, "Casa", "BOB"), presupuesto(2L, "Viajes", "USD")));

        assertThat(service.listar(USUARIO_ID))
                .extracting(PresupuestoResponse::nombre)
                .containsExactly("Casa", "Viajes");
    }

    @Test
    void renombrarCambiaElNombreSinTocarLaMoneda() {
        Presupuesto presupuesto = presupuesto(5L, "Casa", "USD");
        when(presupuestoRepository.findByIdAndUsuarioId(5L, USUARIO_ID))
                .thenReturn(Optional.of(presupuesto));

        PresupuestoResponse response =
                service.renombrar(5L, USUARIO_ID, new ActualizarPresupuestoRequest("Hogar"));

        assertThat(response.nombre()).isEqualTo("Hogar");
        assertThat(response.moneda()).isEqualTo("USD");
        assertThat(presupuesto.getNombreNormalizado()).isEqualTo("hogar");
    }

    @Test
    void renombrarAlMismoNombreConOtrasMayusculasEsValido() {
        Presupuesto presupuesto = presupuesto(5L, "casa", "BOB");
        when(presupuestoRepository.findByIdAndUsuarioId(5L, USUARIO_ID))
                .thenReturn(Optional.of(presupuesto));
        // Solo "otro" presupuesto con ese nombre cuenta como duplicado: aquí no hay ninguno.
        when(presupuestoRepository.existsByUsuarioIdAndNombreNormalizadoAndIdNot(
                USUARIO_ID, "casa", 5L)).thenReturn(false);

        PresupuestoResponse response =
                service.renombrar(5L, USUARIO_ID, new ActualizarPresupuestoRequest("Casa"));

        assertThat(response.nombre()).isEqualTo("Casa");
    }

    @Test
    void renombrarAlNombreDeOtroPresupuestoPropioLanzaConflicto() {
        Presupuesto presupuesto = presupuesto(5L, "Viajes", "BOB");
        when(presupuestoRepository.findByIdAndUsuarioId(5L, USUARIO_ID))
                .thenReturn(Optional.of(presupuesto));
        when(presupuestoRepository.existsByUsuarioIdAndNombreNormalizadoAndIdNot(
                USUARIO_ID, "casa", 5L)).thenReturn(true);

        ConflictoException excepcion = assertThrows(ConflictoException.class,
                () -> service.renombrar(
                        5L, USUARIO_ID, new ActualizarPresupuestoRequest("casa")));

        assertThat(excepcion.getCodigo()).isEqualTo(CodigoError.PRESUPUESTO_YA_EXISTE);
        assertThat(presupuesto.getNombre()).isEqualTo("Viajes");
    }

    @Test
    void renombrarUnPresupuestoAjenoLanzaRecursoNoEncontrado() {
        when(presupuestoRepository.findByIdAndUsuarioId(5L, USUARIO_ID))
                .thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class,
                () -> service.renombrar(
                        5L, USUARIO_ID, new ActualizarPresupuestoRequest("Hogar")));
    }

    private static Perfil perfilConMoneda(String moneda) {
        return Perfil.builder().monedaPredeterminada(moneda).build();
    }

    private Presupuesto presupuesto(Long id, String nombre, String moneda) {
        return Presupuesto.builder()
                .id(id)
                .usuario(usuario)
                .nombre(nombre)
                .nombreNormalizado(Presupuesto.normalizar(nombre))
                .moneda(moneda)
                .build();
    }
}
