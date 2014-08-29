package net.eusashead.vertx.oauth.worker;

import java.util.Objects;

import org.vertx.java.core.json.JsonObject;

public class AbstractMongoResponse implements MongoResponse {

    protected final Status status;

    public AbstractMongoResponse(final JsonObject response) {
        super();
        Objects.requireNonNull(response);
        this.status = Status.valueOf(response.getString("status"));
    }

    @Override
    public Status status() {
        return this.status;
    }

}