package com.assistantrh.assistant_rh_api.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an API handler as restricted to an active SPERS_CHEF account.
 * The authorization decision is enforced centrally by RoleAuthorizationInterceptor.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresSpersChef {
}
