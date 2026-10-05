package com.presupuesto.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.presupuesto.comun.config.RelojDePrueba;
import com.presupuesto.comun.config.RelojDePruebaConfig;
import com.presupuesto.usuario.Perfil;
import com.presupuesto.usuario.PerfilRepository;
import com.presupuesto.usuario.Rol;
import com.presupuesto.usuario.Usuario;
import com.presupuesto.usuario.UsuarioRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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

@SpringBootTest
@AutoConfigureMockMvc
@Import(RelojDePruebaConfig.class)
@Transactional
class RegistroIntegracionTest {

    private static final String RUTA_REGISTRO = "/api/v1/auth/registro";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RelojDePrueba relojDePrueba;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PerfilRepository perfilRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void reiniciarReloj() {
        relojDePrueba.reiniciar();
    }

    @Test
    void registroExitosoDevuelve201ConUnTokenUsable() throws Exception {
        String respuesta = registrar(datosValidos())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andExpect(jsonPath("$.expiraEn").value("2026-10-03T12:00:00Z"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String token = objectMapper.readTree(respuesta).get("token").asString();

        mockMvc.perform(get("/api/v1/prueba").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
        assertThat(usuarioGuardado("ana@ejemplo.com").getRol()).isEqualTo(Rol.USUARIO);
    }

    @Test
    void sinMonedaNiTelefonoSeGuardaBobYSinTelefono() throws Exception {
        registrar(datosValidos()).andExpect(status().isCreated());

        Perfil perfil = perfilGuardado("ana@ejemplo.com");
        assertThat(perfil.getMonedaPredeterminada()).isEqualTo("BOB");
        assertThat(perfil.getTelefono()).isNull();
    }

    @Test
    void conMonedaExplicitaSeGuardaEsaMoneda() throws Exception {
        registrar(datosValidosCon("monedaPredeterminada", "USD")).andExpect(status().isCreated());

        assertThat(perfilGuardado("ana@ejemplo.com").getMonedaPredeterminada()).isEqualTo("USD");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ZZZ", "bob"})
    void unaMonedaInexistenteOEnMinusculasSeRechaza(String moneda) throws Exception {
        esperarDatosInvalidos(
                registrar(datosValidosCon("monedaPredeterminada", moneda)), "monedaPredeterminada");
    }

    @Test
    void elEmailSeGuardaRecortadoYEnMinusculas() throws Exception {
        registrar(datosValidosCon("email", "  Ana@Ejemplo.COM  ")).andExpect(status().isCreated());

        assertThat(usuarioRepository.findByEmail("ana@ejemplo.com")).isPresent();
    }

    @Test
    void nombreYApellidoSeGuardanRecortados() throws Exception {
        Map<String, Object> datos = datosValidosCon("nombre", "  María José ");
        datos.put("apellido", " Rojas  ");

        registrar(datos).andExpect(status().isCreated());

        Perfil perfil = perfilGuardado("ana@ejemplo.com");
        assertThat(perfil.getNombre()).isEqualTo("María José");
        assertThat(perfil.getApellido()).isEqualTo("Rojas");
    }

    @Test
    void unNombreQueSoloCumpleLaLongitudPorLosEspaciosSeRechaza() throws Exception {
        esperarDatosInvalidos(registrar(datosValidosCon("nombre", "  A  ")), "nombre");
    }

    @Test
    void laContrasenaSeGuardaConSusEspacios() throws Exception {
        registrar(datosValidosCon("contrasena", " secreta123 ")).andExpect(status().isCreated());

        String hash = usuarioGuardado("ana@ejemplo.com").getContrasena();
        assertThat(passwordEncoder.matches(" secreta123 ", hash)).isTrue();
        assertThat(passwordEncoder.matches("secreta123", hash)).isFalse();
    }

    @Test
    void unEmailYaRegistradoConOtrasMayusculasYEspaciosDevuelve409() throws Exception {
        registrar(datosValidos()).andExpect(status().isCreated());
        long usuariosAntes = usuarioRepository.count();

        registrar(datosValidosCon("email", " ANA@ejemplo.com"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EMAIL_YA_REGISTRADO"));

        assertThat(usuarioRepository.count()).isEqualTo(usuariosAntes);
    }

    @ParameterizedTest
    @CsvSource({
        "contrasena, secret7",
        "email, ana-arroba-ejemplo.com",
        "telefono, 123456789012345678901"
    })
    void unCampoInvalidoDevuelve400DatosInvalidos(String campo, String valor) throws Exception {
        esperarDatosInvalidos(registrar(datosValidosCon(campo, valor)), campo);
    }

    @Test
    void unaContrasenaDe72CaracteresQueSuperaLos72BytesDevuelve400() throws Exception {
        String contrasena = "ñ" + "a".repeat(71);

        esperarDatosInvalidos(registrar(datosValidosCon("contrasena", contrasena)), "contrasena");
    }

    @Test
    void variosCamposInvalidosSeInformanALaVez() throws Exception {
        Map<String, Object> datos = datosValidosCon("telefono", "123456789012345678901");
        datos.remove("nombre");

        esperarDatosInvalidos(registrar(datos), "nombre")
                .andExpect(jsonPath("$.errores.telefono", notNullValue()));
    }

    @Test
    void unJsonMalFormadoDevuelve400ConElMensajeGenerico() throws Exception {
        esperarCuerpoIlegible(mockMvc.perform(post(RUTA_REGISTRO)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"ana@ejemplo.com\"")));
    }

    @Test
    void unaFechaDeNacimientoQueNoExisteDevuelve400ConElMensajeGenerico() throws Exception {
        esperarCuerpoIlegible(registrar(datosValidosCon("fechaNacimiento", "2000-13-01")));
    }

    @ParameterizedTest
    @CsvSource({
        "2026-10-02T12:00:00Z, 2008-10-02",
        "2026-03-01T12:00:00Z, 2008-02-29"
    })
    void seAceptaAQuienYaCumplio18(String ahora, String fechaNacimiento) throws Exception {
        relojDePrueba.fijar(Instant.parse(ahora));

        registrar(datosValidosCon("fechaNacimiento", fechaNacimiento))
                .andExpect(status().isCreated());
    }

    @ParameterizedTest
    @CsvSource({
        "2026-10-02T12:00:00Z, 2008-10-03",
        "2026-02-28T12:00:00Z, 2008-02-29",
        "2026-10-02T12:00:00Z, 2030-01-01"
    })
    void seRechazaAQuienTodaviaNoCumplio18(String ahora, String fechaNacimiento)
            throws Exception {
        relojDePrueba.fijar(Instant.parse(ahora));

        esperarDatosInvalidos(
                registrar(datosValidosCon("fechaNacimiento", fechaNacimiento)), "fechaNacimiento");
    }

    private ResultActions registrar(Map<String, Object> datos) throws Exception {
        return mockMvc.perform(post(RUTA_REGISTRO)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(datos)));
    }

    private static ResultActions esperarDatosInvalidos(ResultActions resultado, String campo)
            throws Exception {
        return resultado
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.errores." + campo, notNullValue()));
    }

    private static void esperarCuerpoIlegible(ResultActions resultado) throws Exception {
        resultado
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.detail").value("El cuerpo de la petición no es válido"))
                .andExpect(jsonPath("$.errores").doesNotExist());
    }

    private Usuario usuarioGuardado(String email) {
        return usuarioRepository.findByEmail(email).orElseThrow();
    }

    private Perfil perfilGuardado(String email) {
        return perfilRepository.findByUsuarioId(usuarioGuardado(email).getId()).orElseThrow();
    }

    static Map<String, Object> datosValidos() {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("email", "ana@ejemplo.com");
        datos.put("contrasena", "secreta123");
        datos.put("nombre", "Ana");
        datos.put("apellido", "Rojas");
        datos.put("fechaNacimiento", "1990-05-20");
        return datos;
    }

    static Map<String, Object> datosValidosCon(String campo, Object valor) {
        Map<String, Object> datos = datosValidos();
        datos.put(campo, valor);
        return datos;
    }
}
