package com.presupuesto.comun.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import com.presupuesto.comun.excepcion.NoAutenticadoException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Los 401 y 403 de la cadena de seguridad se escriben a mano (no pasan por el
 * {@code @RestControllerAdvice}): deben declarar y usar UTF-8, porque el cliente decodifica el
 * JSON como UTF-8 y con ISO-8859-1 las tildes del detail llegarían rotas.
 */
class RespuestasSeguridadUtf8Test {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void el401SeEnviaEnUtf8YSuDetailConTildesSeDecodificaBien() throws Exception {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        new AutenticacionEntryPointPersonalizado(objectMapper).commence(
                new MockHttpServletRequest(),
                respuesta,
                new InsufficientAuthenticationException("sin token"));

        assertThat(respuesta.getStatus()).isEqualTo(401);
        assertThat(NoAutenticadoException.MENSAJE_NO_AUTENTICADO).contains("ó");
        assertThat(detailDecodificadoComoUtf8(respuesta))
                .isEqualTo(NoAutenticadoException.MENSAJE_NO_AUTENTICADO);
        assertThat(respuesta.getContentType()).isEqualTo(contentTypeUtf8());
    }

    @Test
    void el403SeEnviaEnUtf8YSuDetailSeDecodificaBien() throws Exception {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();

        new AccesoDenegadoHandlerPersonalizado(objectMapper).handle(
                new MockHttpServletRequest(), respuesta, new AccessDeniedException("sin permiso"));

        assertThat(respuesta.getStatus()).isEqualTo(403);
        assertThat(detailDecodificadoComoUtf8(respuesta))
                .isEqualTo("No tiene permisos para acceder a este recurso");
        assertThat(respuesta.getContentType()).isEqualTo(contentTypeUtf8());
    }

    private String detailDecodificadoComoUtf8(MockHttpServletResponse respuesta) {
        String cuerpo = new String(respuesta.getContentAsByteArray(), StandardCharsets.UTF_8);
        JsonNode json = objectMapper.readTree(cuerpo);
        return json.get("detail").asString();
    }

    private static String contentTypeUtf8() {
        return MediaType.APPLICATION_PROBLEM_JSON_VALUE + ";charset=UTF-8";
    }
}
