package com.presupuesto.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.presupuesto.categoria.entity.GrupoCategoria;
import com.presupuesto.categoria.repository.CategoriaRepository;
import com.presupuesto.categoria.repository.GrupoCategoriaRepository;
import com.presupuesto.presupuesto.entity.Presupuesto;
import com.presupuesto.presupuesto.repository.PresupuestoRepository;
import com.presupuesto.usuario.entity.Perfil;
import com.presupuesto.usuario.entity.Usuario;
import com.presupuesto.usuario.repository.PerfilRepository;
import com.presupuesto.usuario.repository.UsuarioRepository;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * No es {@code @Transactional}: necesita que la transacción del registro se revierta por sí sola,
 * así que borra al terminar cualquier dato que haya quedado. Los repositorios son espías: hacen
 * lo real salvo donde el test les hace fallar.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegistroAtomicidadTest {

    private static final String EMAIL = "atomicidad@ejemplo.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private CategoriaRepository categoriaRepository;

    @MockitoSpyBean
    private PerfilRepository perfilRepository;

    @MockitoSpyBean
    private PresupuestoRepository presupuestoRepository;

    @MockitoSpyBean
    private GrupoCategoriaRepository grupoRepository;

    @AfterEach
    void borrarDatosDePrueba() {
        Mockito.reset(perfilRepository, presupuestoRepository, grupoRepository);
        usuarioRepository.findByEmail(EMAIL).ifPresent(this::borrar);
    }

    @Test
    void siFallaGuardarElPerfilNoQuedaNingunUsuarioConEseEmail() {
        doThrow(new IllegalStateException("Fallo simulado al guardar el perfil"))
                .when(perfilRepository).save(any(Perfil.class));

        assertThatThrownBy(this::registrar).hasRootCauseInstanceOf(IllegalStateException.class);

        assertThat(usuarioRepository.findByEmail(EMAIL)).isEmpty();
    }

    @Test
    void siFallaCrearElPresupuestoInicialNoQuedaNingunUsuarioConEseEmail() {
        doThrow(new IllegalStateException("Fallo simulado al guardar el presupuesto"))
                .when(presupuestoRepository).saveAndFlush(any(Presupuesto.class));

        assertThatThrownBy(this::registrar).hasRootCauseInstanceOf(IllegalStateException.class);

        assertThat(usuarioRepository.findByEmail(EMAIL)).isEmpty();
    }

    @Test
    void siFallaCrearLasCategoriasInicialesNoQuedaElUsuarioNiElPresupuestoNiElPerfil() {
        long presupuestosAntes = presupuestoRepository.count();
        long perfilesAntes = perfilRepository.count();
        long gruposAntes = grupoRepository.count();
        doThrow(new IllegalStateException("Fallo simulado al guardar los grupos"))
                .when(grupoRepository).save(any(GrupoCategoria.class));

        assertThatThrownBy(this::registrar).hasRootCauseInstanceOf(IllegalStateException.class);

        assertThat(usuarioRepository.findByEmail(EMAIL)).isEmpty();
        assertThat(presupuestoRepository.count()).isEqualTo(presupuestosAntes);
        assertThat(perfilRepository.count()).isEqualTo(perfilesAntes);
        assertThat(grupoRepository.count()).isEqualTo(gruposAntes);
    }

    @Test
    void siFallaCrearLasCategoriasInicialesDeUnPostNoQuedaElPresupuestoNuevo() throws Exception {
        String token = registrarConExito();
        Usuario usuario = usuarioRepository.findByEmail(EMAIL).orElseThrow();
        long presupuestosAntes = presupuestoRepository.count();
        long gruposAntes = grupoRepository.count();
        doThrow(new IllegalStateException("Fallo simulado al guardar los grupos"))
                .when(grupoRepository).save(any(GrupoCategoria.class));

        assertThatThrownBy(() -> mockMvc.perform(post("/api/v1/presupuestos")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\": \"Viajes\"}")))
                .hasRootCauseInstanceOf(IllegalStateException.class);

        assertThat(presupuestoRepository.count()).isEqualTo(presupuestosAntes);
        assertThat(presupuestoRepository.findByUsuarioIdOrderByNombreNormalizado(usuario.getId()))
                .extracting(Presupuesto::getNombre).containsExactly("Mi presupuesto");
        assertThat(grupoRepository.count()).isEqualTo(gruposAntes);
    }

    private void registrar() throws Exception {
        mockMvc.perform(post("/api/v1/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(datos())));
    }

    private String registrarConExito() throws Exception {
        String cuerpo = mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(datos())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo).get("token").asString();
    }

    private static Map<String, Object> datos() {
        return RegistroIntegracionTest.datosValidosCon("email", EMAIL);
    }

    /** Borra del hijo al padre: categorías, grupos, presupuestos, perfil y usuario. */
    private void borrar(Usuario usuario) {
        for (Presupuesto presupuesto :
                presupuestoRepository.findByUsuarioIdOrderByNombreNormalizado(usuario.getId())) {
            categoriaRepository.deleteAll(
                    categoriaRepository.findByGrupoPresupuestoIdOrderByOrden(presupuesto.getId()));
            grupoRepository.deleteAll(
                    grupoRepository.findByPresupuestoIdOrderByOrden(presupuesto.getId()));
            presupuestoRepository.delete(presupuesto);
        }
        perfilRepository.findByUsuarioId(usuario.getId()).ifPresent(perfilRepository::delete);
        usuarioRepository.delete(usuario);
    }
}
