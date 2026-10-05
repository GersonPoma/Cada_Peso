package com.presupuesto.usuario.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.presupuesto.comun.config.RelojDePrueba;
import com.presupuesto.comun.config.RelojDePruebaConfig;
import com.presupuesto.comun.excepcion.NoAutenticadoException;
import com.presupuesto.comun.seguridad.JwtService;
import com.presupuesto.comun.seguridad.Rol;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(RelojDePruebaConfig.class)
@Transactional
class UsuarioActualIntegracionTest {

    private static final String RUTA_YO = "/api/v1/usuarios/yo";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RelojDePrueba relojDePrueba;

    @Autowired
    private JwtService jwtService;

    @AfterEach
    void reiniciarReloj() {
        relojDePrueba.reiniciar();
    }

    @Test
    void devuelveLosDatosDelUsuarioAutenticadoSinLaContrasena() throws Exception {
        String token = registrarAna();

        String cuerpo = mockMvc
                .perform(get(RUTA_YO).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ana@ejemplo.com"))
                .andExpect(jsonPath("$.rol").value("USUARIO"))
                .andExpect(jsonPath("$.nombre").value("Ana"))
                .andExpect(jsonPath("$.apellido").value("Rojas"))
                .andExpect(jsonPath("$.fechaNacimiento").value("1990-05-20"))
                .andExpect(jsonPath("$.telefono").isEmpty())
                .andExpect(jsonPath("$.monedaPredeterminada").value("BOB"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode respuesta = objectMapper.readTree(cuerpo);
        assertThat(Set.copyOf(respuesta.propertyNames())).containsExactlyInAnyOrder(
                "email", "rol", "nombre", "apellido", "fechaNacimiento", "telefono",
                "monedaPredeterminada");
        assertThat(cuerpo).doesNotContainIgnoringCase("contrasena");
    }

    @Test
    void sinTokenDevuelve401NoAutenticado() throws Exception {
        esperarNoAutenticado(mockMvc.perform(get(RUTA_YO)));
    }

    @Test
    void conUnTokenValidoDeUnUsuarioInexistenteDevuelve401ConElMismoMensaje() throws Exception {
        String token = jwtService.emitir(Long.MAX_VALUE, Rol.USUARIO).token();

        esperarNoAutenticado(
                mockMvc.perform(get(RUTA_YO).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)));
    }

    private String registrarAna() throws Exception {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("email", "ana@ejemplo.com");
        datos.put("contrasena", "secreta123");
        datos.put("nombre", "Ana");
        datos.put("apellido", "Rojas");
        datos.put("fechaNacimiento", "1990-05-20");
        String cuerpo = mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(datos)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(cuerpo).get("token").asString();
    }

    private static void esperarNoAutenticado(ResultActions resultado) throws Exception {
        resultado
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.detail")
                        .value(NoAutenticadoException.MENSAJE_NO_AUTENTICADO));
    }
}
