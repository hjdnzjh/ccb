package com.water.ai.meter.security;
import jakarta.servlet.http.HttpServletRequest;
public record SessionUser(long id,String username,String realName,String role) {
    public static final String ATTRIBUTE=SessionUser.class.getName();
    public boolean isAdmin() { return "admin".equals(role); }
    public static SessionUser from(HttpServletRequest request) {
        Object value=request.getAttribute(ATTRIBUTE);
        if(!(value instanceof SessionUser user)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED,"请先登录");
        return user;
    }
}
