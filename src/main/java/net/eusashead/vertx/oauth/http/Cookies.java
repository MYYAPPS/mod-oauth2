package net.eusashead.vertx.oauth.http;

import io.netty.handler.codec.http.Cookie;
import io.netty.handler.codec.http.CookieDecoder;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.vertx.java.core.http.HttpServerRequest;

public class Cookies {

    public static Set<Cookie> getCookies(HttpServerRequest request) {
        String value = request.headers().get("Cookie");
        if (value != null) {
            return CookieDecoder.decode(value);
        } else {
            return new HashSet<>();
        }
    }

    public static Optional<Cookie> getCookie(HttpServerRequest request,
            String name) {
        for (Cookie cookie : getCookies(request)) {
            if (cookie.getName().equals(name)) {
                return Optional.of(cookie);
            }
        }
        return Optional.empty();
    }
}