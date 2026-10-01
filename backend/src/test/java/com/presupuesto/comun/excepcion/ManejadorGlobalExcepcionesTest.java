package com.presupuesto.comun.excepcion;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
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
    void reglaNegocioDevuelve422ConProblemDetail() throws Exception {
        mockMvc.perform(get("/prueba/regla-negocio"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("REGLA_NEGOCIO_VIOLADA"))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }
}
