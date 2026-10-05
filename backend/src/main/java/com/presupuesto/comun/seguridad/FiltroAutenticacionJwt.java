package com.presupuesto.comun.seguridad;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentica la petición si trae {@code Authorization: Bearer <token>} con un token válido. Si no
 * hay token, o es inválido o expiró, no autentica y deja seguir la cadena: en una ruta protegida
 * responde el {@code AuthenticationEntryPoint} (401) y en una pública la petición sigue normal.
 *
 * <p>No es un bean a propósito: si lo fuera, Spring Boot también lo registraría como filtro de
 * servlet y se ejecutaría fuera de la cadena de seguridad. Se crea en {@link SecurityConfig}.
 */
public class FiltroAutenticacionJwt extends OncePerRequestFilter {

    private static final String PREFIJO_BEARER = "Bearer ";

    private final JwtService jwtService;

    public FiltroAutenticacionJwt(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String cabecera = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecera != null && cabecera.startsWith(PREFIJO_BEARER)) {
            autenticar(cabecera.substring(PREFIJO_BEARER.length()));
        }
        filterChain.doFilter(request, response);
    }

    private void autenticar(String token) {
        try {
            UsuarioAutenticado usuario = jwtService.validar(token);
            var autenticacion = UsernamePasswordAuthenticationToken.authenticated(
                    usuario, null, List.of(new SimpleGrantedAuthority("ROLE_" + usuario.rol())));
            SecurityContext contexto = SecurityContextHolder.createEmptyContext();
            contexto.setAuthentication(autenticacion);
            SecurityContextHolder.setContext(contexto);
        } catch (JwtException | IllegalArgumentException tokenInvalido) {
            SecurityContextHolder.clearContext();
        }
    }
}
