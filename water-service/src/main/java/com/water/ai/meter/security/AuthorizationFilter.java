package com.water.ai.meter.security;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Map;

@Component
@Order(1)
@RequiredArgsConstructor
public class AuthorizationFilter extends OncePerRequestFilter {
    private final SessionService sessions;
    private final ObjectMapper json;
    public static String bearer(HttpServletRequest request) {
        String value=request.getHeader("Authorization");
        return value!=null && value.startsWith("Bearer ")?value.substring(7):null;
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws IOException,ServletException {
        String path=request.getServletPath();
        if(path.isEmpty()) path=request.getRequestURI().substring(request.getContextPath().length());
        if(path.contains("..") || path.contains(";") || path.contains("%")) { reject(response,400,"接口路径无效"); return; }
        response.setHeader("Cache-Control","no-store");
        response.setHeader("X-Content-Type-Options","nosniff");
        if((path.equals("/api/v1/auth/login") && request.getMethod().equals("POST")) || (path.equals("/actuator/health") && request.getMethod().equals("GET"))) {
            chain.doFilter(request,response); return;
        }
        SessionUser user=sessions.authenticate(bearer(request));
        if(user==null) { reject(response,401,"登录已失效，请重新登录"); return; }
        boolean self=path.startsWith("/api/v1/me/") || path.equals("/api/v1/auth/me") || path.equals("/api/v1/auth/logout");
        if(!user.isAdmin() && !self) { reject(response,403,"无权访问管理员功能"); return; }
        request.setAttribute(SessionUser.ATTRIBUTE,user);
        chain.doFilter(request,response);
    }
    private void reject(HttpServletResponse response,int status,String message) throws IOException {
        response.setStatus(status);response.setContentType("application/json;charset=UTF-8");
        json.writeValue(response.getWriter(),Map.of("success",false,"code",status,"message",message));
    }
}
