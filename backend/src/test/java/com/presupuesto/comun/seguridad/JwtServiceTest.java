package com.presupuesto.comun.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.presupuesto.comun.config.RelojDePrueba;
import com.presupuesto.usuario.Rol;
import com.presupuesto.usuario.Usuario;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.WeakKeyException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRETO_POR_DEFECTO =
            "cada-peso-secreto-solo-para-desarrollo-local-no-usar-en-produccion";
    private static final Duration VEINTICUATRO_HORAS = Duration.ofHours(24);

    private RelojDePrueba reloj;
    private JwtService jwtService;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        reloj = new RelojDePrueba();
        jwtService = new JwtService(
                new JwtProperties(SECRETO_POR_DEFECTO, VEINTICUATRO_HORAS), reloj);
        usuario = Usuario.builder().id(42L).email("ana@ejemplo.com").contrasena("hash").build();
    }

    @Test
    void unTokenRecienEmitidoSeValidaConSuIdYRol() {
        TokenEmitido emitido = jwtService.emitir(usuario);

        UsuarioAutenticado autenticado = jwtService.validar(emitido.token());

        assertThat(autenticado).isEqualTo(new UsuarioAutenticado(42L, Rol.USUARIO));
        assertThat(emitido.expiraEn())
                .isEqualTo(RelojDePrueba.INSTANTE_INICIAL.plus(VEINTICUATRO_HORAS));
    }

    @Test
    void elTokenSeFirmaConHs256AunqueElSecretoTenga66Bytes() {
        assertThat(SECRETO_POR_DEFECTO.getBytes(StandardCharsets.UTF_8)).hasSize(66);
        String token = jwtService.emitir(usuario).token();

        String header = new String(
                Base64.getUrlDecoder().decode(token.split("\\.")[0]), StandardCharsets.UTF_8);

        assertThat(header).contains("\"alg\":\"HS256\"");
    }

    @Test
    void unTokenAlteradoSeRechaza() {
        String token = jwtService.emitir(usuario).token();
        String[] partes = token.split("\\.");
        char[] payload = partes[1].toCharArray();
        int medio = payload.length / 2;
        payload[medio] = payload[medio] == 'A' ? 'B' : 'A';
        String alterado = partes[0] + "." + new String(payload) + "." + partes[2];

        assertThatThrownBy(() -> jwtService.validar(alterado)).isInstanceOf(JwtException.class);
    }

    @Test
    void unTokenSeRechazaTras24HorasYUnSegundo() {
        String token = jwtService.emitir(usuario).token();

        reloj.avanzar(VEINTICUATRO_HORAS.plusSeconds(1));

        assertThatThrownBy(() -> jwtService.validar(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void unSecretoDe31BytesImpideCrearElServicio() {
        JwtProperties propiedades = new JwtProperties("a".repeat(31), VEINTICUATRO_HORAS);

        assertThatThrownBy(() -> new JwtService(propiedades, reloj))
                .isInstanceOf(WeakKeyException.class);
    }

    @Test
    void unSecretoDeExactamente32BytesSeAcepta() {
        JwtProperties propiedades = new JwtProperties("a".repeat(32), VEINTICUATRO_HORAS);

        assertThatCode(() -> new JwtService(propiedades, reloj).emitir(usuario))
                .doesNotThrowAnyException();
    }
}
