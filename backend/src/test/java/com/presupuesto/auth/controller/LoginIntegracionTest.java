package com.presupuesto.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.presupuesto.comun.config.RelojDePrueba;
import com.presupuesto.comun.config.RelojDePruebaConfig;
import com.presupuesto.comun.seguridad.JwtService;
import com.presupuesto.usuario.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Import(RelojDePruebaConfig.class)
@Transactional
class LoginIntegracionTest {

    private static final String MENSAJE_CREDENCIALES = "Email o contraseña incorrectos";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RelojDePrueba relojDePrueba;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @AfterEach
    void reiniciarReloj() {
        relojDePrueba.reiniciar();
    }

    @Test
    void credencialesCorrectasDevuelven200ConUnTokenQueExpiraEn24Horas() throws Exception {
        registrarAna("secreta123");

        login("ana@ejemplo.com", "secreta123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expiraEn").value("2026-10-03T12:00:00Z"));
    }

    @Test
    void unEmailConMayusculasYEspaciosIniciaSesionEnLaCuentaNormalizada() throws Exception {
        registrarAna("secreta123");
        Long idDeAna = usuarioRepository.findByEmail("ana@ejemplo.com").orElseThrow().getId();

        String token = tokenDe(login("  Ana@Ejemplo.COM ", "secreta123")
                .andExpect(status().isOk()));

        assertThat(jwtService.validar(token).id()).isEqualTo(idDeAna);
    }

    @Test
    void laContrasenaSeComparaConSusEspacios() throws Exception {
        registrarAna(" secreta123 ");

        esperarCredencialesInvalidas(login("ana@ejemplo.com", "secreta123"));
        login("ana@ejemplo.com", " secreta123 ").andExpect(status().isOk());
    }

    @Test
    void contrasenaIncorrectaYEmailInexistenteRespondenIgual() throws Exception {
        registrarAna("secreta123");

        esperarCredencialesInvalidas(login("ana@ejemplo.com", "incorrecta1"));
        esperarCredencialesInvalidas(login("nadie@ejemplo.com", "secreta123"));
    }

    @Test
    void unaContrasenaDeMasDe72BytesDevuelve401YNo500() throws Exception {
        registrarAna("secreta123");
        String contrasena = "ñ".repeat(40);
        assertThat(contrasena.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(72);

        esperarCredencialesInvalidas(login("ana@ejemplo.com", contrasena));
    }

    @Test
    void unLoginSinContrasenaDevuelve400ConErrorEnContrasena() throws Exception {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("email", "ana@ejemplo.com");

        mockMvc.perform(postJson("/api/v1/auth/login", datos))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.errores.contrasena").exists());
    }

    @Test
    void unLoginCorrectoConUnTokenInvalidoEnElHeaderSeProcesaNormalmente() throws Exception {
        registrarAna("secreta123");

        Map<String, Object> datos = credenciales("ana@ejemplo.com", "secreta123");

        mockMvc.perform(postJson("/api/v1/auth/login", datos)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token-invalido"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    private void registrarAna(String contrasena) throws Exception {
        mockMvc.perform(postJson("/api/v1/auth/registro",
                        RegistroIntegracionTest.datosValidosCon("contrasena", contrasena)))
                .andExpect(status().isCreated());
    }

    private ResultActions login(String email, String contrasena) throws Exception {
        return mockMvc.perform(postJson("/api/v1/auth/login", credenciales(email, contrasena)));
    }

    private static Map<String, Object> credenciales(String email, String contrasena) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("email", email);
        datos.put("contrasena", contrasena);
        return datos;
    }

    private MockHttpServletRequestBuilder postJson(String ruta, Map<String, Object> datos) {
        return post(ruta)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(datos));
    }

    private String tokenDe(ResultActions resultado) throws Exception {
        String cuerpo = resultado.andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo).get("token").asString();
    }

    private static void esperarCredencialesInvalidas(ResultActions resultado) throws Exception {
        resultado
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
                .andExpect(jsonPath("$.detail").value(MENSAJE_CREDENCIALES));
    }
}
