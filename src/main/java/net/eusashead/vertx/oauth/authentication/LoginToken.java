package net.eusashead.vertx.oauth.authentication;

import io.netty.handler.codec.http.ClientCookieEncoder;
import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.DefaultCookie;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.ServerCookieEncoder;

import java.util.Objects;
import java.util.Optional;

import net.eusashead.vertx.oauth.http.Cookies;

import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;

public class LoginToken {

    public static final String COOKIE_NAME = "sessionId";

    private final String sessionId;

    public LoginToken(String sessionId) {
        Objects.requireNonNull(sessionId);
        this.sessionId = sessionId;
    }

    public String sessionId() {
        return this.sessionId;
    }

    public static Optional<LoginToken> decode(HttpServerRequest request) {
        Optional<Cookie> login = Cookies.getCookie(request, COOKIE_NAME);
        return login.isPresent() ? Optional.<LoginToken> of(new LoginToken(
                login.get().getValue())) : Optional.<LoginToken> empty();
    }

    public void encode(HttpClientRequest request) {
        request.headers().add(HttpHeaders.Names.COOKIE,
                ClientCookieEncoder.encode(createCookie()));
    }

    public void encode(HttpServerResponse response) {
        response.headers().add(HttpHeaders.Names.SET_COOKIE,
                ServerCookieEncoder.encode(createCookie()));
    }

    public void expire(HttpServerResponse response) {
        response.headers().add(HttpHeaders.Names.SET_COOKIE,
                ServerCookieEncoder.encode(expireCookie()));
    }

    private Cookie createCookie() {
        Cookie cookie = new DefaultCookie(COOKIE_NAME, sessionId);
        // cookie.setSecure(true); // TODO set this when HTTPS is enabled (only
        // works over HTTPS)
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(Long.MIN_VALUE);
        return cookie;
    }

    private Cookie expireCookie() {
        Cookie cookie = new DefaultCookie(COOKIE_NAME, sessionId);
        // cookie.setSecure(true); // TODO set this when HTTPS is enabled (only
        // works over HTTPS)
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        cookie.setDiscard(true);
        return cookie;
    }
}