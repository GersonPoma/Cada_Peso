package com.presupuesto.usuario.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.presupuesto.comun.seguridad.Rol;
import com.presupuesto.usuario.entity.Perfil;
import com.presupuesto.usuario.entity.Usuario;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class UsuarioPerfilRepositoryTest {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PerfilRepository perfilRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void unUsuarioNuevoTieneRolUsuario() {
        Usuario usuario = usuarioRepository.saveAndFlush(nuevoUsuario("rol@ejemplo.com"));

        assertThat(usuario.getRol()).isEqualTo(Rol.USUARIO);
    }

    @Test
    void guardarDosUsuariosConElMismoEmailFalla() {
        usuarioRepository.saveAndFlush(nuevoUsuario("repetido@ejemplo.com"));

        assertThatThrownBy(() -> usuarioRepository.saveAndFlush(
                        nuevoUsuario("repetido@ejemplo.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByUsuarioIdDevuelveElPerfilConSuUsuarioYaCargado() {
        Usuario usuario = usuarioRepository.saveAndFlush(nuevoUsuario("perfil@ejemplo.com"));
        perfilRepository.saveAndFlush(Perfil.builder()
                .usuario(usuario)
                .nombre("Ana")
                .apellido("Rojas")
                .fechaNacimiento(LocalDate.parse("1990-05-20"))
                .monedaPredeterminada("BOB")
                .build());
        entityManager.clear();

        Perfil perfil = perfilRepository.findByUsuarioId(usuario.getId()).orElseThrow();

        assertThat(Hibernate.isInitialized(perfil.getUsuario())).isTrue();
        assertThat(perfil.getUsuario().getEmail()).isEqualTo("perfil@ejemplo.com");
        assertThat(perfil.getNombre()).isEqualTo("Ana");
    }

    private static Usuario nuevoUsuario(String email) {
        return Usuario.builder().email(email).contrasena("hash-de-prueba").build();
    }
}
