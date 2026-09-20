package com.water.ai.meter.security;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SessionService {
    private final JdbcTemplate jdbc;
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final String USER_COLUMNS="u.id,u.username,COALESCE(u.real_name,u.username) real_name,COALESCE(r.role,'user') role";
    @Transactional
    public Map<String,Object> login(String username,String password) {
        if(username==null || username.isBlank() || username.length()>50 || password==null || password.length()>72) throw invalidLogin();
        var rows=jdbc.queryForList("SELECT u.* FROM sys_user u WHERE username=? AND deleted=0 FOR UPDATE",username.trim());
        if(rows.isEmpty()) throw invalidLogin();
        var row=rows.get(0);
        if(row.get("status")==null || ((Number)row.get("status")).intValue()!=0 || !Passwords.matches(password,(String)row.get("password"))) throw invalidLogin();
        long id=((Number)row.get("id")).longValue();
        String stored=(String)row.get("password");
        if(!stored.startsWith("$2")) jdbc.update("UPDATE sys_user SET password=? WHERE id=?",Passwords.upgrade(password),id);
        jdbc.update("UPDATE sys_user SET last_login_time=NOW() WHERE id=?",id);
        byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("DELETE FROM auth_session WHERE expires_at<=NOW()");
        jdbc.update("INSERT INTO auth_session(token_hash,user_id,expires_at) VALUES (?,?,DATE_ADD(NOW(),INTERVAL 8 HOUR))",hash(token),id);
        return Map.of("token",token,"userInfo",findUser(id));
    }
    private ResponseStatusException invalidLogin() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED,"用户名或密码错误，或账号已停用"); }
    public SessionUser findUser(long id) {
        return jdbc.queryForObject("SELECT "+USER_COLUMNS+" FROM sys_user u LEFT JOIN auth_role r ON r.user_id=u.id WHERE u.id=? AND u.deleted=0 AND u.status=0",
                (rs,n)->new SessionUser(rs.getLong("id"),rs.getString("username"),rs.getString("real_name"),rs.getString("role")),id);
    }
    public SessionUser authenticate(String token) {
        if(token==null || !token.matches("[A-Za-z0-9_-]{43}")) return null;
        var users=jdbc.query("SELECT "+USER_COLUMNS+" FROM auth_session s JOIN sys_user u ON u.id=s.user_id LEFT JOIN auth_role r ON r.user_id=u.id " +
                        "WHERE s.token_hash=? AND s.expires_at>NOW() AND u.deleted=0 AND u.status=0",
                (rs,n)->new SessionUser(rs.getLong("id"),rs.getString("username"),rs.getString("real_name"),rs.getString("role")),hash(token));
        return users.isEmpty()?null:users.get(0);
    }
    public void logout(String token) { if(token!=null)jdbc.update("DELETE FROM auth_session WHERE token_hash=?",hash(token)); }
    static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e) {throw new IllegalStateException(e);}
    }
}
