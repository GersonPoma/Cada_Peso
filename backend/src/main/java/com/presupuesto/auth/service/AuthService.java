package com.presupuesto.auth.service;

import com.presupuesto.auth.dto.LoginRequest;
import com.presupuesto.auth.dto.RegistroRequest;
import com.presupuesto.auth.dto.TokenResponse;
import com.presupuesto.auth.mapper.RegistroMapper;
import com.presupuesto.comun.excepcion.CodigoError;
import com.presupuesto.comun.excepcion.ConflictoException;
import com.presupuesto.comun.excepcion.NoAutenticadoException;
import com.presupuesto.comun.seguridad.JwtService;
import com.presupuesto.usuario.entity.Usuario;
import com.presupuesto.usuario.repository.PerfilRepository;
import com.presupuesto.usuario.repository.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    static final String MENSAJE_EMAIL_YA_REGISTRADO = "Ya existe una cuenta con ese email";
    static final String MENSAJE_CREDENCIALES_INVALIDAS = "Email o contraseña incorrectos";

    /** Límite real de BCrypt: ninguna contraseña registrada puede ocupar más. */
    private static final int MAXIMO_BYTES_CONTRASENA = 72;

    private final UsuarioRepository usuarioRepository;
    private final PerfilRepository perfilRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RegistroMapper registroMapper;

    /**
     * Hash contra el que se compara cuando el email no existe, para que el tiempo de respuesta no
     * revele si un email tiene cuenta.
     */
    private final String hashFicticio;

    public AuthService(
            UsuarioRepository usuarioRepository,
            PerfilRepository perfilRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RegistroMapper registroMapper) {
        this.usuarioRepository = usuarioRepository;
        this.perfilRepository = perfilRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.registroMapper = registroMapper;
        this.hashFicticio = passwordEncoder.encode("contrasena-ficticia-para-igualar-tiempos");
    }

    /** Crea el usuario y su perfil en una sola transacción y devuelve su token de acceso. */
    @Transactional
    public TokenResponse registrar(RegistroRequest request) {
        if (usuarioRepository.existsByEmail(request.email())) {
            throw emailYaRegistrado();
        }
        Usuario usuario = Usuario.builder()
                .email(request.email())
                .contrasena(passwordEncoder.encode(request.contrasena()))
                .build();
        try {
            // Red de seguridad ante dos registros simultáneos con el mismo email.
            usuario = usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException emailDuplicado) {
            throw emailYaRegistrado();
        }
        perfilRepository.save(registroMapper.aPerfil(request, usuario));
        return TokenResponse.desde(jwtService.emitir(usuario.getId(), usuario.getRol()));
    }

    /**
     * Devuelve un token si las credenciales son correctas. Email inexistente, contraseña
     * incorrecta o contraseña de más de 72 bytes responden igual, sin revelar qué falló.
     */
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        if (request.contrasena().getBytes(StandardCharsets.UTF_8).length
                > MAXIMO_BYTES_CONTRASENA) {
            throw credencialesInvalidas();
        }
        Optional<Usuario> usuario = usuarioRepository.findByEmail(request.email());
        if (usuario.isEmpty()) {
            passwordEncoder.matches(request.contrasena(), hashFicticio);
            throw credencialesInvalidas();
        }
        if (!passwordEncoder.matches(request.contrasena(), usuario.get().getContrasena())) {
            throw credencialesInvalidas();
        }
        return TokenResponse.desde(
                jwtService.emitir(usuario.get().getId(), usuario.get().getRol()));
    }

    private static ConflictoException emailYaRegistrado() {
        return new ConflictoException(CodigoError.EMAIL_YA_REGISTRADO, MENSAJE_EMAIL_YA_REGISTRADO);
    }

    private static NoAutenticadoException credencialesInvalidas() {
        return new NoAutenticadoException(
                CodigoError.CREDENCIALES_INVALIDAS, MENSAJE_CREDENCIALES_INVALIDAS);
    }
}
