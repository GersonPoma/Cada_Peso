package com.presupuesto.comun.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.presupuesto.comun.config.RelojDePrueba;
import com.presupuesto.comun.config.RelojDePruebaConfig;
import com.presupuesto.comun.excepcion.NoAutenticadoException;
import com.presupuesto.usuario.Usuario;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@Import(RelojDePruebaConfig.class)
class SecurityConfigTest {

    private static final String RUTA_PROTEGIDA = "/api/v1/prueba";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RelojDePrueba relojDePrueba;

    @AfterEach
    void reiniciarReloj() {
        relojDePrueba.reiniciar();
    }

    @Test
    void rutaProtegidaSinTokenDevuelve401ConProblemDetailNoAutenticado() throws Exception {
        esperarNoAutenticado(mockMvc.perform(get(RUTA_PROTEGIDA)))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void el401DeLaCadenaDeSeguridadSeEnviaEnUtf8ConLasTildesIntactas() throws Exception {
        MockHttpServletResponse respuesta = mockMvc.perform(get(RUTA_PROTEGIDA))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_TYPE, "application/problem+json;charset=UTF-8"))
                .andReturn()
                .getResponse();

        String cuerpo = new String(respuesta.getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(cuerpo).contains("\"" + NoAutenticadoException.MENSAJE_NO_AUTENTICADO + "\"");
    }

    @Test
    void actuatorHealthNoRequiereAutenticacion() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void rutaProtegidaConTokenValidoResponde200() throws Exception {
        String token = emitirToken();

        mockMvc.perform(get(RUTA_PROTEGIDA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void rutaProtegidaConTokenAlteradoDevuelve401() throws Exception {
        String token = emitirToken();
        String alterado = token.substring(0, token.length() - 10) + "AAAAAAAAAA";

        esperarNoAutenticado(mockMvc.perform(
                get(RUTA_PROTEGIDA).header(HttpHeaders.AUTHORIZATION, "Bearer " + alterado)));
    }

    @Test
    void rutaProtegidaConTokenExpiradoDevuelve401() throws Exception {
        String token = emitirToken();
        relojDePrueba.avanzar(Duration.ofHours(24).plusSeconds(1));

        esperarNoAutenticado(mockMvc.perform(
                get(RUTA_PROTEGIDA).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)));
    }

    @Test
    void rutaProtegidaConTokenValidoSinPrefijoBearerDevuelve401() throws Exception {
        String token = emitirToken();

        esperarNoAutenticado(mockMvc.perform(
                get(RUTA_PROTEGIDA).header(HttpHeaders.AUTHORIZATION, token)));
    }

    private String emitirToken() {
        Usuario usuario = Usuario.builder().id(1L).email("ana@ejemplo.com").contrasena("x").build();
        return jwtService.emitir(usuario).token();
    }

    private ResultActions esperarNoAutenticado(ResultActions resultado) throws Exception {
        return resultado
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.detail")
                        .value(NoAutenticadoException.MENSAJE_NO_AUTENTICADO));
    }
}
