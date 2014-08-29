package net.eusashead.vertx.oauth.worker;

import java.util.Optional;

import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

public class FindResponse extends AbstractMongoResponse implements
        MongoResponse {

    private final JsonArray result;

    public FindResponse(JsonObject response) {
        super(response);
        this.result = response.getArray("results");
    }

    public Optional<JsonArray> results() {
        return Optional.ofNullable(this.result);
    }

}