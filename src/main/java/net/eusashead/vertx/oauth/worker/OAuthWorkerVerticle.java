package net.eusashead.vertx.oauth.worker;

import io.vertx.rxcore.java.eventbus.RxEventBus;
import io.vertx.rxcore.java.eventbus.RxMessage;

import java.util.Objects;
import java.util.Optional;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func2;

public class OAuthWorkerVerticle extends BusModBase {

    // Default address
    private static final String DEFAULT_ADDR = "vertx.oauthmanager";

    // OAuth request parameter names
    private static final String RESPONSE_TYPE_PARAM = "response_type";
    private static final String CLIENT_ID_PARAM = "client_id";
    private static final String REDIRECT_URI_PARAM = "redirect_uri";
    private static final String SCOPE_PARAM = "scope";

    // Mongo persistor address
    private String mongoAddr;
    private String appCollection;
    private String permCollection;

    @Override
    public void start() {
        super.start();
        // Trace
        container.logger().trace("Started OAuthWorkerVerticle");

        // Create RxEventBus
        final RxEventBus rxEventBus = new RxEventBus(vertx.eventBus());

        // Get configuration
        this.mongoAddr = getOptionalStringConfig("persistor_address",
                "vertx.mongo");
        this.appCollection = getOptionalStringConfig("app_collection",
                "applications");
        this.permCollection = getOptionalStringConfig("perm_collection",
                "permissions");
        String address = getOptionalStringConfig("address", DEFAULT_ADDR);

        // Register auth handler
        eb.registerHandler(address + ".auth",
                new Handler<Message<JsonObject>>() {

                    @Override
                    public void handle(Message<JsonObject> event) {
                        doAuth(event, rxEventBus);
                    }

                });

        // Register test handler with lambda expression
        eb.registerHandler(address + ".test", (event) -> {
            container.logger().info("test");
            event.reply();
        });

    }

    private void doAuth(final Message<JsonObject> event,
            final RxEventBus eventBus) {

        // Validate input
        final Validator validator = new Validator(event.body())
                .requireField(CLIENT_ID_PARAM, "Client identifier missing")
                .requireField(RESPONSE_TYPE_PARAM, "Response type missing")
                .requireField(REDIRECT_URI_PARAM, "Redirect URI missing")
                .requireField(SCOPE_PARAM, "Scope missing");

        if (validator.hasErrors()) {
            replyError(event, validator.errors());
        } else {

            // Load application and permissions
            Observable
                    .zip(applicationObservable(
                            event.body().getString("client_id"), eventBus),
                            permissionsObservable(
                                    event.body().getString("scope"), eventBus),
                            new Func2<RxMessage<JsonObject>, RxMessage<JsonObject>, Optional<JsonObject>>() {

                                @Override
                                public Optional<JsonObject> call(
                                        RxMessage<JsonObject> t1,
                                        RxMessage<JsonObject> t2) {

                                    final String status = t1.body().getString(
                                            "status");
                                    final JsonObject application = t1.body()
                                            .getObject("result");
                                    final JsonArray permissions = t2.body()
                                            .getArray("results");

                                    if ("ok".equals(status)
                                            && application != null) {
                                        application.putArray("permissions",
                                                permissions);
                                        return Optional.of(application);
                                    } else {
                                        return Optional.empty();
                                    }
                                }
                            }).subscribe(new Action1<Optional<JsonObject>>() {

                        @Override
                        public void call(Optional<JsonObject> t1) {

                            // Did application load?
                            if (t1.isPresent()) {
                                JsonObject result = t1.get();
                                // Check redirect URI matches
                                if (event
                                        .body()
                                        .getString(REDIRECT_URI_PARAM)
                                        .equals(result.getString("redirectUri"))) {

                                    // Set status OK
                                    result.putString("status", "ok");

                                    // Send the reply
                                    event.reply(result);

                                } else {
                                    // Redirect URI invalid
                                    validator.addError(REDIRECT_URI_PARAM,
                                            "Invalid redirect URI.");
                                    replyError(event, validator.errors());
                                }
                            } else {
                                // No application loaded
                                validator.addError(CLIENT_ID_PARAM,
                                        "Failed to load application.");
                                replyError(event, validator.errors());
                            }
                        }
                    });
        }
    }

    private void replyError(final Message<JsonObject> event,
            final JsonArray messages) {
        event.reply(new JsonObject().putString("status", "error").putArray(
                "messages", messages));
    }

    /**
     * Create {@link Observable} for loading the application from Mongodb
     * 
     * @param clientId
     * @param eventBus
     * @return
     */
    private Observable<RxMessage<JsonObject>> applicationObservable(
            final String clientId, final RxEventBus eventBus) {
        return eventBus.send(
                this.mongoAddr,
                new JsonObject()
                        .putString("action", "findone")
                        .putString("collection", this.appCollection)
                        .putObject("matcher",
                                new JsonObject().putString("_id", clientId)));
    }

    /**
     * Create {@link Observable} for loading the permissions from Mongodb
     * 
     * @param scope
     * @param eventBus
     * @return
     */
    private Observable<RxMessage<JsonObject>> permissionsObservable(
            final String scope, final RxEventBus eventBus) {

        // Create permissions array
        final JsonArray permissions = new JsonArray();
        final String[] scopeArr = scope.split(" ");
        for (String name : scopeArr) {
            permissions.addString(name);
        }

        return eventBus.send(
                mongoAddr,
                new JsonObject()
                        .putString("action", "find")
                        .putString("collection", permCollection)
                        .putObject(
                                "matcher",
                                new JsonObject().putObject("name",
                                        new JsonObject().putArray("$in",
                                                permissions))));
    }

}

class Validator {

    private final JsonObject target;
    private final JsonArray errors;

    public Validator(JsonObject target) {
        this.target = Objects.requireNonNull(target);
        this.errors = new JsonArray();
    }

    public boolean hasErrors() {
        return this.errors.size() > 0;
    }

    public JsonArray errors() {
        return this.errors;
    }

    public Validator requireField(String fieldName, String message) {
        if (!target.containsField(fieldName)) {
            errors.add(errorMessage(fieldName, message));
        }
        return this;

    }

    public Validator addError(String fieldName, String message) {
        errors.add(errorMessage(fieldName, message));
        return this;
    }

    private JsonObject errorMessage(String field, String message) {
        return new JsonObject().putString("field", field).putString("message",
                message);
    }

}
