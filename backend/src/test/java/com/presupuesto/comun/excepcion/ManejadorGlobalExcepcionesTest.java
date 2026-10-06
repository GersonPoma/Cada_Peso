package com.presupuesto.comun.excepcion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ControladorDePrueba.class)
@Import(ManejadorGlobalExcepciones.class)
class ManejadorGlobalExcepcionesTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    void recursoNoEncontradoDevuelve404ConProblemDetail() throws Exception {
        mockMvc.perform(get("/prueba/recurso-no-encontrado"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.detail").value("Recurso de prueba no encontrado"));
    }

    @Test
    @WithMockUser
    void conflictoDevuelve409ConProblemDetail() throws Exception {
        mockMvc.perform(get("/prueba/conflicto"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    @WithMockUser
    void conflictoConCodigoPropioDevuelve409ConEseCodigo() throws Exception {
        mockMvc.perform(get("/prueba/conflicto-con-codigo"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("EMAIL_YA_REGISTRADO"))
                .andExpect(jsonPath("$.detail").value("Email de prueba repetido"));
    }

    @Test
    @WithMockUser
    void reglaNegocioDevuelve422ConProblemDetail() throws Exception {
        mockMvc.perform(get("/prueba/regla-negocio"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO_VIOLADA"))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    @WithMockUser
    void datosInvalidosDevuelve400ConCodigoDatosInvalidosYSinErrores() throws Exception {
        mockMvc.perform(get("/prueba/datos-invalidos"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.detail").value("Datos de prueba invalidos"))
                .andExpect(jsonPath("$.errores").doesNotExist());
    }

    @Test
    @WithMockUser
    void noAutenticadoDevuelve401ConElCodigoYElMensajeRecibidos() throws Exception {
        mockMvc.perform(get("/prueba/no-autenticado"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.detail").value("Credenciales de prueba incorrectas"));
    }

    @Test
    @WithMockUser
    void campoInvalidoDevuelve400DatosInvalidosConElCampoEnErrores() throws Exception {
        mockMvc.perform(post("/prueba/validacion")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"\", \"fecha\": \"2000-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.detail").value("Uno o más campos no son válidos"))
                .andExpect(jsonPath("$.errores.nombre", notNullValue()))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"nombre\": \"Ana\", \"fecha\": \"2000-01-01\"",
        "",
        "{\"nombre\": \"Ana\", \"fecha\": \"2000-13-01\"}"
    })
    @WithMockUser
    void cuerpoIlegibleDevuelve400DatosInvalidosSinDetallesDelParser(String cuerpo)
            throws Exception {
        String respuesta = mockMvc.perform(post("/prueba/validacion")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.detail").value("El cuerpo de la petición no es válido"))
                .andExpect(jsonPath("$.errores").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(respuesta)
                .doesNotContain("com.")
                .doesNotContain("tools.jackson")
                .doesNotContainIgnoringCase("line")
                .doesNotContainIgnoringCase("column");
    }
}
