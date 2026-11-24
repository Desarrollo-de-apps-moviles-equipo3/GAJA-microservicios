package com.gaja.usuarios.model;

/** Ciclo de vida de un perfil: nace PENDIENTE y el administrador lo aprueba o rechaza; despues se puede desactivar. */
public enum EstadoUsuario {
    PENDIENTE, APROBADO, RECHAZADO, DESACTIVADO
}
