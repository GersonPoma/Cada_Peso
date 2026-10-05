package com.presupuesto.usuario;

import com.presupuesto.comun.excepcion.CodigoError;
import com.presupuesto.comun.excepcion.NoAutenticadoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final PerfilRepository perfilRepository;
    private final UsuarioMapper usuarioMapper;

    /**
     * Datos del usuario dueño del token. Si ya no existe, el token no representa a nadie: responde
     * 401 {@code NO_AUTENTICADO}, igual que un token inválido, para que el cliente cierre sesión.
     */
    @Transactional(readOnly = true)
    public UsuarioActualResponse obtenerActual(Long usuarioId) {
        return perfilRepository.findByUsuarioId(usuarioId)
                .map(usuarioMapper::aUsuarioActual)
                .orElseThrow(() -> new NoAutenticadoException(
                        CodigoError.NO_AUTENTICADO, NoAutenticadoException.MENSAJE_NO_AUTENTICADO));
    }
}
