package com.presupuesto.beneficiario.dto.response;

import com.presupuesto.beneficiario.entity.Beneficiario;

public record BeneficiarioResponse(Long id, String nombre, Long categoriaPredeterminadaId) {

    public static BeneficiarioResponse desde(Beneficiario beneficiario) {
        return new BeneficiarioResponse(
                beneficiario.getId(),
                beneficiario.getNombre(),
                beneficiario.getCategoriaPredeterminada() == null
                        ? null
                        : beneficiario.getCategoriaPredeterminada().getId());
    }
}
