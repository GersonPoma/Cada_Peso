package com.presupuesto.comun.seguridad;

import java.time.Instant;

public record TokenEmitido(String token, Instant expiraEn) {}
