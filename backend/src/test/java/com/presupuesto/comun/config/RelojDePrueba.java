package com.presupuesto.comun.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Reloj de pruebas con un instante modificable, siempre en UTC. */
public class RelojDePrueba extends Clock {

    public static final Instant INSTANTE_INICIAL = Instant.parse("2026-10-02T12:00:00Z");

    private Instant instante = INSTANTE_INICIAL;

    public void fijar(Instant nuevoInstante) {
        this.instante = nuevoInstante;
    }

    public void avanzar(Duration duracion) {
        this.instante = instante.plus(duracion);
    }

    public void reiniciar() {
        this.instante = INSTANTE_INICIAL;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        throw new UnsupportedOperationException("RelojDePrueba solo trabaja en UTC");
    }

    @Override
    public Instant instant() {
        return instante;
    }
}
