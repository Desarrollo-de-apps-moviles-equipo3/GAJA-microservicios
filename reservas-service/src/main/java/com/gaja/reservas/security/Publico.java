package com.gaja.reservas.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca un endpoint que se puede usar SIN iniciar sesión (por ejemplo, el login).
 * Todo endpoint que no tenga esta marca exige un token válido.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Publico {
}
