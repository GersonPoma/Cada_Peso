package com.presupuesto.comun.seguridad;

import com.presupuesto.usuario.Rol;
import com.presupuesto.usuario.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Emite y valida los JWT de acceso (HS256, sub = id del usuario, claim {@code rol}).
 *
 * <p>La clave se construye una sola vez: un {@code JWT_SECRET} de menos de 32 bytes lanza
 * {@code WeakKeyException} y la aplicación no arranca.
 */
@Service
public class JwtService {

    private static final String CLAIM_ROL = "rol";

    private final SecretKey clave;
    private final Duration expiracion;
    private final Clock clock;
    private final JwtParser parser;

    public JwtService(JwtProperties propiedades, Clock clock) {
        this.clave = Keys.hmacShaKeyFor(propiedades.secreto().getBytes(StandardCharsets.UTF_8));
        this.expiracion = propiedades.expiracion();
        this.clock = clock;
        this.parser = Jwts.parser()
                .verifyWith(clave)
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    public TokenEmitido emitir(Usuario usuario) {
        // Los JWT guardan los instantes en segundos: se trunca para que expiraEn coincida con exp.
        Instant emision = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiraEn = emision.plus(expiracion);
        String token = Jwts.builder()
                .subject(String.valueOf(usuario.getId()))
                .claim(CLAIM_ROL, usuario.getRol().name())
                .issuedAt(Date.from(emision))
                .expiration(Date.from(expiraEn))
                .signWith(clave, Jwts.SIG.HS256)
                .compact();
        return new TokenEmitido(token, expiraEn);
    }

    /**
     * Valida firma y expiración y devuelve el usuario del token.
     *
     * @throws JwtException si el token está mal formado, alterado o expirado
     * @throws IllegalArgumentException si el token está vacío o sus claims no son válidos
     */
    public UsuarioAutenticado validar(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();
        Long id = Long.valueOf(claims.getSubject());
        Rol rol = Rol.valueOf(claims.get(CLAIM_ROL, String.class));
        return new UsuarioAutenticado(id, rol);
    }
}
