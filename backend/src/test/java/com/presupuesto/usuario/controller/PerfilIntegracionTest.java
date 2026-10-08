package com.presupuesto.usuario.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.presupuesto.comun.config.RelojDePruebaConfig;
import com.presupuesto.comun.excepcion.NoAutenticadoException;
import com.presupuesto.comun.seguridad.JwtService;
import com.presupuesto.comun.seguridad.Rol;
import com.presupuesto.usuario.entity.Usuario;
import com.presupuesto.usuario.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Flujos de {@code PUT /usuarios/yo} y {@code POST /usuarios/yo/contrasena}. Cada test registra a
 * sus propias personas con un email único, así que no dependen entre sí ni del orden.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(RelojDePruebaConfig.class)
@Transactional
class PerfilIntegracionTest {

    private static final String RUTA_YO = "/api/v1/usuarios/yo";
    private static final String RUTA_CONTRASENA = "/api/v1/usuarios/yo/contrasena";
    private static final String MENSAJE_ACTUAL_INCORRECTA = "La contraseña actual es incorrecta";
    private static final String CONTRASENA_INICIAL = "secreta123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // --- 4.1 Cambio de nombre

    @Test
    void cambiaElNombreRecortadoYLaConsultaPosteriorLoMuestra() throws Exception {
        Persona ana = registrar("Ana");

        mockMvc.perform(put(RUTA_YO).headers(ana.cabecera())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("nombre", "  María José  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("María José"))
                .andExpect(jsonPath("$.email").value(ana.email()));

        mockMvc.perform(get(RUTA_YO).headers(ana.cabecera()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("María José"));
    }

    @Test
    void soloCambiaElNombreAunqueElCuerpoTraigaOtrosCampos() throws Exception {
        Persona ana = registrar(Map.of("nombre", "Ana", "telefono", "70012345",
                "monedaPredeterminada", "USD"));

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre", "Beatriz");
        cuerpo.put("apellido", "Otra");
        cuerpo.put("email", "otro@ejemplo.com");
        cuerpo.put("rol", "ADMIN");
        cuerpo.put("monedaPredeterminada", "EUR");
        cuerpo.put("telefono", "99999999");
        mockMvc.perform(put(RUTA_YO).headers(ana.cabecera())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuerpo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Beatriz"))
                .andExpect(jsonPath("$.apellido").value("Rojas"))
                .andExpect(jsonPath("$.email").value(ana.email()))
                .andExpect(jsonPath("$.rol").value("USUARIO"))
                .andExpect(jsonPath("$.telefono").value("70012345"))
                .andExpect(jsonPath("$.monedaPredeterminada").value("USD"));
    }

    @Test
    void unNombreVacioOSoloDeEspaciosEs400ConErrorEnNombreYNoCambiaNada() throws Exception {
        Persona ana = registrar("Ana");

        for (String nombre : new String[] {"", "    "}) {
            mockMvc.perform(put(RUTA_YO).headers(ana.cabecera())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("nombre", nombre)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                    .andExpect(jsonPath("$.errores.nombre", notNullValue()));
        }
        esperarNombre(ana, "Ana");
    }

    @Test
    void unNombreDeUnCaracterTrasRecortarOMuyLargoEs400YNoSeReflejaEnElError() throws Exception {
        Persona ana = registrar("Ana");
        String reconocible = "ZZREFLEJADO" + "x".repeat(90);

        for (String nombre : new String[] {" A ", reconocible}) {
            String cuerpo = mockMvc.perform(put(RUTA_YO).headers(ana.cabecera())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("nombre", nombre)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                    .andExpect(jsonPath("$.errores.nombre", notNullValue()))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

            assertThat(cuerpo).doesNotContain("ZZREFLEJADO");
        }
        assertThat("ZZREFLEJADO" + "x".repeat(90)).hasSize(101);
        esperarNombre(ana, "Ana");
    }

    @Test
    void unNombreDeExactamente100CaracteresRodeadoDeEspaciosSeGuardaSinLosEspacios()
            throws Exception {
        Persona ana = registrar("Ana");
        String nombre = "n".repeat(100);

        mockMvc.perform(put(RUTA_YO).headers(ana.cabecera())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("nombre", "  " + nombre + "  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value(nombre));
    }

    @Test
    void sinNombreConCuerpoVacioOJsonMalFormadoEs400() throws Exception {
        Persona ana = registrar("Ana");

        for (String cuerpo : new String[] {"{}", "", "{\"nombre\": "}) {
            mockMvc.perform(put(RUTA_YO).headers(ana.cabecera())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        }
        esperarNombre(ana, "Ana");
    }

    // --- 4.2 Cambio de contraseña

    @Test
    void cambiarLaContrasenaResponde204SinCuerpoYElHashNoEsElTextoPlano() throws Exception {
        Persona ana = registrar("Ana");

        String cuerpo = cambiarContrasena(ana, CONTRASENA_INICIAL, "otraClave456")
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).isEmpty();
        Usuario guardado = usuarioRepository.findByEmail(ana.email()).orElseThrow();
        assertThat(guardado.getContrasena()).isNotEqualTo("otraClave456");
        assertThat(passwordEncoder.matches("otraClave456", guardado.getContrasena())).isTrue();
    }

    @Test
    void conLaContrasenaNuevaElLoginFuncionaYConLaViejaFalla() throws Exception {
        Persona ana = registrar("Ana");
        cambiarContrasena(ana, CONTRASENA_INICIAL, "otraClave456")
                .andExpect(status().isNoContent());

        login(ana.email(), "otraClave456")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()));
        login(ana.email(), CONTRASENA_INICIAL)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    void laActualIncorrectaEs422ConMensajeFijoSinReflejarNadaYNoCambiaLaContrasena()
            throws Exception {
        Persona ana = registrar("Ana");

        String cuerpo = cambiarContrasena(ana, "equivocada1", "otraClave456")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO_VIOLADA"))
                .andExpect(jsonPath("$.detail").value(MENSAJE_ACTUAL_INCORRECTA))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(cuerpo).doesNotContain("equivocada1").doesNotContain("otraClave456");
        login(ana.email(), CONTRASENA_INICIAL).andExpect(status().isOk());
    }

    @Test
    void unaActualDe37CaracteresDeDosBytesEs422ConElMismoMensajeFijo() throws Exception {
        Persona ana = registrar("Ana");
        String actual = "ñ".repeat(37);

        assertThat(actual).hasSize(37);
        assertThat(actual.getBytes(StandardCharsets.UTF_8)).hasSize(74);
        cambiarContrasena(ana, actual, "otraClave456")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO_VIOLADA"))
                .andExpect(jsonPath("$.detail").value(MENSAJE_ACTUAL_INCORRECTA));
        login(ana.email(), CONTRASENA_INICIAL).andExpect(status().isOk());
    }

    @Test
    void unaNuevaDebilEs400ConErrorEnContrasenaNuevaSinReflejarlaYNoCambiaNada()
            throws Exception {
        Persona ana = registrar("Ana");

        for (String nueva : new String[] {"corta12", "a".repeat(73), "ñ".repeat(40)}) {
            String cuerpo = cambiarContrasena(ana, CONTRASENA_INICIAL, nueva)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                    .andExpect(jsonPath("$.errores.contrasenaNueva", notNullValue()))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

            assertThat(cuerpo).doesNotContain(nueva).doesNotContain(CONTRASENA_INICIAL);
        }
        login(ana.email(), CONTRASENA_INICIAL).andExpect(status().isOk());
    }

    @Test
    void laNuevaIgualALaActualEs422YNoCambiaLaContrasena() throws Exception {
        Persona ana = registrar("Ana");

        String cuerpo = cambiarContrasena(ana, CONTRASENA_INICIAL, CONTRASENA_INICIAL)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO_VIOLADA"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(cuerpo).doesNotContain(CONTRASENA_INICIAL);
        login(ana.email(), CONTRASENA_INICIAL).andExpect(status().isOk());
    }

    @Test
    void conOtraCapitalizacionLaNuevaEsDistintaYSeAcepta() throws Exception {
        Persona ana = registrar("Ana");

        cambiarContrasena(ana, CONTRASENA_INICIAL, "Secreta123").andExpect(status().isNoContent());

        login(ana.email(), "Secreta123").andExpect(status().isOk());
    }

    @Test
    void conLaActualIncorrectaIgualALaNuevaGanaElMensajeDeActualIncorrecta() throws Exception {
        Persona ana = registrar("Ana");

        cambiarContrasena(ana, "equivocada1", "equivocada1")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(MENSAJE_ACTUAL_INCORRECTA));
    }

    @Test
    void laNuevaConEspaciosSeGuardaTalCualYPermiteIniciarSesionConEllos() throws Exception {
        Persona ana = registrar("Ana");

        cambiarContrasena(ana, CONTRASENA_INICIAL, "  clave nueva 9 ")
                .andExpect(status().isNoContent());

        login(ana.email(), "  clave nueva 9 ").andExpect(status().isOk());
        login(ana.email(), "clave nueva 9").andExpect(status().isUnauthorized());
    }

    @Test
    void faltanDatosOElCuerpoEsIlegibleEs400() throws Exception {
        Persona ana = registrar("Ana");

        enviarContrasena(ana, json("contrasenaNueva", "otraClave456"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.contrasenaActual", notNullValue()));
        enviarContrasena(ana, json("contrasenaActual", CONTRASENA_INICIAL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.contrasenaNueva", notNullValue()));
        cambiarContrasena(ana, "", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        for (String cuerpo : new String[] {"", "{\"contrasenaActual\": "}) {
            enviarContrasena(ana, cuerpo)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        }
        login(ana.email(), CONTRASENA_INICIAL).andExpect(status().isOk());
    }

    @Test
    void nadaDeLoQueSeEnviaNiElHashApareceEnLasRespuestas() throws Exception {
        Persona ana = registrar("Ana");
        String hash = usuarioRepository.findByEmail(ana.email()).orElseThrow().getContrasena();

        String[] respuestas = {
            cambiarContrasena(ana, "equivocada1", "otraClave456")
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8),
            cambiarContrasena(ana, CONTRASENA_INICIAL, CONTRASENA_INICIAL)
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8),
            cambiarContrasena(ana, CONTRASENA_INICIAL, "corta12")
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8),
            cambiarContrasena(ana, CONTRASENA_INICIAL, "otraClave456")
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8),
            mockMvc.perform(put(RUTA_YO).headers(ana.cabecera())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json("nombre", "Ana B")))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)
        };
        for (String respuesta : respuestas) {
            assertThat(respuesta)
                    .doesNotContain("equivocada1")
                    .doesNotContain("otraClave456")
                    .doesNotContain("corta12")
                    .doesNotContain(CONTRASENA_INICIAL)
                    .doesNotContain(hash);
        }
    }

    // --- 4.3 Seguridad y aislamiento

    @Test
    void sinTokenAmbasRutasResponden401() throws Exception {
        esperarNoAutenticado(mockMvc.perform(put(RUTA_YO)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("nombre", "Beto"))));
        esperarNoAutenticado(mockMvc.perform(post(RUTA_CONTRASENA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(contrasenas(CONTRASENA_INICIAL, "otraClave456"))));
    }

    @Test
    void conUnTokenInvalidoAmbasRutasResponden401() throws Exception {
        esperarNoAutenticado(mockMvc.perform(put(RUTA_YO)
                .header(HttpHeaders.AUTHORIZATION, "Bearer no.es.un.token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("nombre", "Beto"))));
        esperarNoAutenticado(mockMvc.perform(post(RUTA_CONTRASENA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer no.es.un.token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(contrasenas(CONTRASENA_INICIAL, "otraClave456"))));
    }

    @Test
    void conUnTokenDeUnaPersonaInexistenteAmbasRutasResponden401() throws Exception {
        String token = jwtService.emitir(Long.MAX_VALUE, Rol.USUARIO).token();

        esperarNoAutenticado(mockMvc.perform(put(RUTA_YO)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json("nombre", "Beto"))));
        esperarNoAutenticado(mockMvc.perform(post(RUTA_CONTRASENA)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(contrasenas(CONTRASENA_INICIAL, "otraClave456"))));
    }

    @Test
    void anaNoAfectaALaCuentaDeBeto() throws Exception {
        Persona ana = registrar("Ana");
        Persona beto = registrar("Beto");

        mockMvc.perform(put(RUTA_YO).headers(ana.cabecera())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("nombre", "Ana Maria")))
                .andExpect(status().isOk());
        cambiarContrasena(ana, CONTRASENA_INICIAL, "otraClave456")
                .andExpect(status().isNoContent());

        esperarNombre(beto, "Beto");
        login(beto.email(), CONTRASENA_INICIAL).andExpect(status().isOk());
        login(beto.email(), "otraClave456").andExpect(status().isUnauthorized());
        esperarNombre(ana, "Ana Maria");
    }

    @Test
    void elTokenAnteriorAlCambioSigueSiendoValido() throws Exception {
        Persona ana = registrar("Ana");

        cambiarContrasena(ana, CONTRASENA_INICIAL, "otraClave456")
                .andExpect(status().isNoContent());

        mockMvc.perform(get(RUTA_YO).headers(ana.cabecera()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ana.email()));
    }

    // --- Ayudas

    private record Persona(String email, String token) {

        HttpHeaders cabecera() {
            HttpHeaders cabecera = new HttpHeaders();
            cabecera.setBearerAuth(token);
            return cabecera;
        }
    }

    private Persona registrar(String nombre) throws Exception {
        return registrar(Map.of("nombre", nombre));
    }

    /** Registra a una persona con un email único; {@code extras} pisa los datos por defecto. */
    private Persona registrar(Map<String, Object> extras) throws Exception {
        String email = "perfil-" + UUID.randomUUID() + "@ejemplo.com";
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("email", email);
        datos.put("contrasena", CONTRASENA_INICIAL);
        datos.put("nombre", "Ana");
        datos.put("apellido", "Rojas");
        datos.put("fechaNacimiento", "1990-05-20");
        datos.putAll(extras);
        String cuerpo = mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(datos)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new Persona(email, objectMapper.readTree(cuerpo).get("token").asString());
    }

    private ResultActions cambiarContrasena(Persona persona, String actual, String nueva)
            throws Exception {
        return enviarContrasena(persona, contrasenas(actual, nueva));
    }

    private ResultActions enviarContrasena(Persona persona, String cuerpo) throws Exception {
        return mockMvc.perform(post(RUTA_CONTRASENA).headers(persona.cabecera())
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private ResultActions login(String email, String contrasena) throws Exception {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("email", email);
        datos.put("contrasena", contrasena);
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(datos)));
    }

    private void esperarNombre(Persona persona, String nombre) throws Exception {
        mockMvc.perform(get(RUTA_YO).headers(persona.cabecera()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value(nombre));
    }

    private String json(String campo, String valor) throws Exception {
        return objectMapper.writeValueAsString(Map.of(campo, valor));
    }

    private String contrasenas(String actual, String nueva) throws Exception {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("contrasenaActual", actual);
        datos.put("contrasenaNueva", nueva);
        return objectMapper.writeValueAsString(datos);
    }

    private static void esperarNoAutenticado(ResultActions resultado) throws Exception {
        resultado
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.detail")
                        .value(NoAutenticadoException.MENSAJE_NO_AUTENTICADO));
    }
}
