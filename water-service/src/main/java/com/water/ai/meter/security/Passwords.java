package com.water.ai.meter.security;
import cn.hutool.crypto.digest.BCrypt;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class Passwords {
    private Passwords() {}
    public static String hash(String password) {
        if (password == null || password.length()<8 || password.getBytes(StandardCharsets.UTF_8).length>72)
            throw new IllegalArgumentException("密码至少 8 个字符，UTF-8 编码不超过 72 字节");
        return BCrypt.hashpw(password,BCrypt.gensalt(12));
    }
    public static boolean matches(String password,String stored) {
        if(password==null || stored==null || password.getBytes(StandardCharsets.UTF_8).length>72) return false;
        if(stored.startsWith("$2")) {
            try { return BCrypt.checkpw(password,stored); } catch (IllegalArgumentException e) { return false; }
        }
        return MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8),stored.getBytes(StandardCharsets.UTF_8));
    }
    public static String upgrade(String password) { return BCrypt.hashpw(password,BCrypt.gensalt(12)); }
}
