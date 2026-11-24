package com.gaja.usuarios.dto;

public record LoginResponse(Long id, String email, String nombre, String rol, String estado, String token) {
}
