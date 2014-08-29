package net.eusashead.vertx.oauth.worker;

import java.util.Optional;

import org.vertx.java.core.json.JsonObject;

public class FindOneResponse extends AbstractMongoResponse implements
        MongoResponse {

    private final JsonObject result;

    public FindOneResponse(JsonObject response) {
        super(response);
        this.result = response.getObject("result");
    }

    public Optional<JsonObject> result() {
        return Optional.ofNullable(this.result);
    }

}