package net.eusashead.vertx.oauth.http;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Optional;

public class UrlEncoder {
    public static Optional<String> encode(String target) {
        if (target == null) {
            return Optional.empty();
        }

        try {
            String encoded = URLEncoder.encode(target, "UTF-8");
            return Optional.of(encoded);
        } catch (UnsupportedEncodingException e) {
            // This should never happen
            return Optional.empty();
        }

    }
}