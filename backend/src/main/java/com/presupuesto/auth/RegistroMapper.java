package com.presupuesto.auth;

import com.presupuesto.usuario.Perfil;
import com.presupuesto.usuario.Usuario;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
interface RegistroMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "fechaCreacion", ignore = true)
    @Mapping(target = "fechaActualizacion", ignore = true)
    @Mapping(target = "usuario", source = "usuario")
    Perfil aPerfil(RegistroRequest request, Usuario usuario);
}
