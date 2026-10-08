package com.presupuesto.usuario.service;

import com.presupuesto.comun.excepcion.CodigoError;
import com.presupuesto.comun.excepcion.NoAutenticadoException;
import com.presupuesto.comun.excepcion.ReglaNegocioException;
import com.presupuesto.usuario.dto.request.ActualizarNombreRequest;
import com.presupuesto.usuario.dto.request.CambiarContrasenaRequest;
import com.presupuesto.usuario.dto.response.UsuarioActualResponse;
import com.presupuesto.usuario.entity.Perfil;
import com.presupuesto.usuario.entity.Usuario;
import com.presupuesto.usuario.repository.PerfilRepository;
import com.presupuesto.usuario.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    static final String MENSAJE_CONTRASENA_ACTUAL_INCORRECTA = "La contraseña actual es incorrecta";
    static final String MENSAJE_CONTRASENA_NUEVA_IGUAL =
            "La contraseña nueva debe ser distinta de la actual";

    /** Límite real de BCrypt: ninguna contraseña guardada puede ocupar más. */
    private static final int MAXIMO_BYTES_CONTRASENA = 72;

    private final PerfilRepository perfilRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Datos del usuario dueño del token. Si ya no existe, el token no representa a nadie: responde
     * 401 {@code NO_AUTENTICADO}, igual que un token inválido, para que el cliente cierre sesión.
     */
    @Transactional(readOnly = true)
    public UsuarioActualResponse obtenerActual(Long usuarioId) {
        return UsuarioActualResponse.desde(perfilDe(usuarioId));
    }

    /** Cambia solo el nombre del perfil del usuario dueño del token. */
    @Transactional
    public UsuarioActualResponse cambiarNombre(Long usuarioId, ActualizarNombreRequest request) {
        Perfil perfil = perfilDe(usuarioId);
        perfil.setNombre(request.nombre());
        return UsuarioActualResponse.desde(perfilRepository.save(perfil));
    }

    /**
     * Cambia la contraseña del usuario dueño del token. La actual incorrecta (o de más de 72
     * bytes, que BCrypt no puede haber guardado) es 422 con un mensaje fijo, nunca 401: el cliente
     * cierra la sesión ante cualquier 401. La nueva igual a la actual se comprueba solo después de
     * verificar la actual. No emite ni invalida tokens.
     */
    @Transactional
    public void cambiarContrasena(Long usuarioId, CambiarContrasenaRequest request) {
        Usuario usuario = usuarioRepository.findById(usuarioId).orElseThrow(
                UsuarioService::noAutenticado);
        if (!coincideConLaGuardada(request.contrasenaActual(), usuario)) {
            throw new ReglaNegocioException(MENSAJE_CONTRASENA_ACTUAL_INCORRECTA);
        }
        if (request.contrasenaNueva().equals(request.contrasenaActual())) {
            throw new ReglaNegocioException(MENSAJE_CONTRASENA_NUEVA_IGUAL);
        }
        usuario.setContrasena(passwordEncoder.encode(request.contrasenaNueva()));
        usuarioRepository.save(usuario);
    }

    private boolean coincideConLaGuardada(String contrasenaActual, Usuario usuario) {
        return contrasenaActual.getBytes(StandardCharsets.UTF_8).length <= MAXIMO_BYTES_CONTRASENA
                && passwordEncoder.matches(contrasenaActual, usuario.getContrasena());
    }

    private Perfil perfilDe(Long usuarioId) {
        return perfilRepository.findByUsuarioId(usuarioId).orElseThrow(
                UsuarioService::noAutenticado);
    }

    private static NoAutenticadoException noAutenticado() {
        return new NoAutenticadoException(
                CodigoError.NO_AUTENTICADO, NoAutenticadoException.MENSAJE_NO_AUTENTICADO);
    }
}
