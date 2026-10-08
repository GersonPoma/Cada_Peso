package com.presupuesto.usuario.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.presupuesto.comun.excepcion.CodigoError;
import com.presupuesto.comun.excepcion.NoAutenticadoException;
import com.presupuesto.comun.excepcion.ReglaNegocioException;
import com.presupuesto.comun.seguridad.Rol;
import com.presupuesto.usuario.dto.request.ActualizarNombreRequest;
import com.presupuesto.usuario.dto.request.CambiarContrasenaRequest;
import com.presupuesto.usuario.dto.response.UsuarioActualResponse;
import com.presupuesto.usuario.entity.Perfil;
import com.presupuesto.usuario.entity.Usuario;
import com.presupuesto.usuario.repository.PerfilRepository;
import com.presupuesto.usuario.repository.UsuarioRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsuarioServiceTest {

    private static final long USUARIO_ID = 10L;
    private static final String HASH_GUARDADO = "hash-guardado";

    @Mock
    private PerfilRepository perfilRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UsuarioService service;
    private Usuario usuario;
    private Perfil perfil;

    @BeforeEach
    void preparar() {
        service = new UsuarioService(perfilRepository, usuarioRepository, passwordEncoder);
        usuario = Usuario.builder()
                .email("ana@ejemplo.com")
                .contrasena(HASH_GUARDADO)
                .rol(Rol.USUARIO)
                .build();
        perfil = Perfil.builder()
                .usuario(usuario)
                .nombre("Ana")
                .apellido("Rojas")
                .fechaNacimiento(LocalDate.parse("1990-05-20"))
                .monedaPredeterminada("BOB")
                .build();
        when(perfilRepository.findByUsuarioId(USUARIO_ID)).thenReturn(Optional.of(perfil));
        when(perfilRepository.save(any(Perfil.class))).thenAnswer(
                invocacion -> invocacion.getArgument(0));
        when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.of(usuario));
    }

    // --- cambiarNombre

    @Test
    void cambiarNombreGuardaElNombreYDevuelveLosDatosActualizados() {
        UsuarioActualResponse respuesta =
                service.cambiarNombre(USUARIO_ID, new ActualizarNombreRequest("María José"));

        assertThat(perfil.getNombre()).isEqualTo("María José");
        assertThat(respuesta.nombre()).isEqualTo("María José");
        assertThat(respuesta.apellido()).isEqualTo("Rojas");
        assertThat(respuesta.email()).isEqualTo("ana@ejemplo.com");
        verify(perfilRepository).save(perfil);
    }

    @Test
    void cambiarNombreDeUnUsuarioInexistenteEs401() {
        when(perfilRepository.findByUsuarioId(USUARIO_ID)).thenReturn(Optional.empty());

        NoAutenticadoException error = assertThrows(NoAutenticadoException.class,
                () -> service.cambiarNombre(USUARIO_ID, new ActualizarNombreRequest("Beto")));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.NO_AUTENTICADO);
        assertThat(error.getMessage()).isEqualTo(NoAutenticadoException.MENSAJE_NO_AUTENTICADO);
        verify(perfilRepository, never()).save(any(Perfil.class));
    }

    // --- cambiarContrasena

    @Test
    void cambiarContrasenaConLaActualCorrectaGuardaElHashDeLaNueva() {
        when(passwordEncoder.matches("secreta123", HASH_GUARDADO)).thenReturn(true);
        when(passwordEncoder.encode("otraClave456")).thenReturn("hash-nuevo");

        service.cambiarContrasena(USUARIO_ID, new CambiarContrasenaRequest("secreta123",
                "otraClave456"));

        assertThat(usuario.getContrasena()).isEqualTo("hash-nuevo");
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void cambiarContrasenaGuardaUnHashBcryptRealQueValidaLaNueva() {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
        UsuarioService conBcrypt =
                new UsuarioService(perfilRepository, usuarioRepository, bcrypt);
        usuario.setContrasena(bcrypt.encode("secreta123"));

        conBcrypt.cambiarContrasena(USUARIO_ID, new CambiarContrasenaRequest("secreta123",
                "otraClave456"));

        assertThat(usuario.getContrasena()).isNotEqualTo("otraClave456");
        assertThat(bcrypt.matches("otraClave456", usuario.getContrasena())).isTrue();
        assertThat(bcrypt.matches("secreta123", usuario.getContrasena())).isFalse();
    }

    @Test
    void laActualIncorrectaEs422ConMensajeFijoYNoGuardaNada() {
        when(passwordEncoder.matches("equivocada1", HASH_GUARDADO)).thenReturn(false);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.cambiarContrasena(USUARIO_ID,
                        new CambiarContrasenaRequest("equivocada1", "otraClave456")));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.REGLA_NEGOCIO_VIOLADA);
        assertThat(error.getMessage()).isEqualTo("La contraseña actual es incorrecta");
        assertThat(error.getMessage()).doesNotContain("equivocada1").doesNotContain("otraClave456");
        assertThat(usuario.getContrasena()).isEqualTo(HASH_GUARDADO);
        verify(passwordEncoder, never()).encode(anyString());
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void unaActualDe37CaracteresDeDosBytesEs422SinLlamarAMatches() {
        String actual = "ñ".repeat(37);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.cambiarContrasena(USUARIO_ID,
                        new CambiarContrasenaRequest(actual, "otraClave456")));

        assertThat(actual).hasSize(37);
        assertThat(error.getMessage()).isEqualTo("La contraseña actual es incorrecta");
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void unaActualDe36CaracteresDeDosBytesSiSeComparaConElHash() {
        String actual = "ñ".repeat(36);
        when(passwordEncoder.matches(actual, HASH_GUARDADO)).thenReturn(true);
        when(passwordEncoder.encode("otraClave456")).thenReturn("hash-nuevo");

        service.cambiarContrasena(USUARIO_ID, new CambiarContrasenaRequest(actual,
                "otraClave456"));

        verify(passwordEncoder).matches(actual, HASH_GUARDADO);
        assertThat(usuario.getContrasena()).isEqualTo("hash-nuevo");
    }

    @Test
    void laNuevaIgualALaActualEs422YNoGuardaNada() {
        when(passwordEncoder.matches("secreta123", HASH_GUARDADO)).thenReturn(true);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.cambiarContrasena(USUARIO_ID,
                        new CambiarContrasenaRequest("secreta123", "secreta123")));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.REGLA_NEGOCIO_VIOLADA);
        assertThat(error.getMessage())
                .isEqualTo("La contraseña nueva debe ser distinta de la actual");
        assertThat(error.getMessage()).doesNotContain("secreta123");
        assertThat(usuario.getContrasena()).isEqualTo(HASH_GUARDADO);
        verify(passwordEncoder, never()).encode(anyString());
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void laIgualidadEsExactaYDistingueMayusculas() {
        when(passwordEncoder.matches("secreta123", HASH_GUARDADO)).thenReturn(true);
        when(passwordEncoder.encode("Secreta123")).thenReturn("hash-nuevo");

        service.cambiarContrasena(USUARIO_ID, new CambiarContrasenaRequest("secreta123",
                "Secreta123"));

        assertThat(usuario.getContrasena()).isEqualTo("hash-nuevo");
    }

    @Test
    void conLaActualIncorrectaGanaElMensajeDeActualIncorrectaAunqueSeanIguales() {
        when(passwordEncoder.matches("equivocada1", HASH_GUARDADO)).thenReturn(false);

        ReglaNegocioException error = assertThrows(ReglaNegocioException.class,
                () -> service.cambiarContrasena(USUARIO_ID,
                        new CambiarContrasenaRequest("equivocada1", "equivocada1")));

        assertThat(error.getMessage()).isEqualTo("La contraseña actual es incorrecta");
    }

    @Test
    void cambiarContrasenaDeUnUsuarioInexistenteEs401SinTocarElEncoder() {
        when(usuarioRepository.findById(USUARIO_ID)).thenReturn(Optional.empty());

        NoAutenticadoException error = assertThrows(NoAutenticadoException.class,
                () -> service.cambiarContrasena(USUARIO_ID,
                        new CambiarContrasenaRequest("secreta123", "otraClave456")));

        assertThat(error.getCodigo()).isEqualTo(CodigoError.NO_AUTENTICADO);
        assertThat(error.getMessage()).isEqualTo(NoAutenticadoException.MENSAJE_NO_AUTENTICADO);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(passwordEncoder, never()).encode(anyString());
    }
}
