package com.lrj.risk.admin.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

final class SecurityErrorWriter {
    private SecurityErrorWriter() { }

    static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().printf("{\"code\":\"%s\",\"message\":\"%s\"}", code, message);
    }
}
