package com.presupuesto.auth;

import com.presupuesto.comun.seguridad.TokenEmitido;
import java.time.Instant;

public record TokenResponse(String token, String tipo, Instant expiraEn) {

    private static final String TIPO_BEARER = "Bearer";

    static TokenResponse desde(TokenEmitido emitido) {
        return new TokenResponse(emitido.token(), TIPO_BEARER, emitido.expiraEn());
    }
}
