package net.eusashead.vertx.oauth.worker;

import io.vertx.rxcore.java.eventbus.RxEventBus;
import io.vertx.rxcore.java.eventbus.RxMessage;

import java.util.Objects;
import java.util.Optional;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import rx.Observable;

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
    private String userCollection;

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
        this.userCollection = getOptionalStringConfig("user_collection",
                "users");
        String address = getOptionalStringConfig("address", DEFAULT_ADDR);

        // Register codeRequest handler
        rxEventBus.<JsonObject> registerHandler(address + ".codeRequest")
                .subscribe((RxMessage<JsonObject> event) -> {
                    doCodeRequest(event, rxEventBus);
                });

        // Register codeResponse handler
        rxEventBus.<JsonObject> registerHandler(address + ".codeResponse")
                .subscribe(
                        (RxMessage<JsonObject> event) -> {
                            container.logger().info(
                                    "Code response message: " + event.body());

                            // validate input

                            doCodeResponse(event, rxEventBus);
                        });

    }

    private void doCodeResponse(final RxMessage<JsonObject> event,
            final RxEventBus eventBus) {

        // Validate input
        final Validator validator = new Validator(event.body());
        final String clientId = validator.requireString(CLIENT_ID_PARAM,
                "Client identifier missing");
        final String username = validator.requireString("username",
                "User identifier missing");
        final String redirectUri = validator.requireString(REDIRECT_URI_PARAM,
                "Redirect URI missing");
        final JsonArray scope = validator.requireArray(SCOPE_PARAM,
                "Scope missing");
        // If invalid, send error response
        if (validator.hasErrors()) {
            replyError(event, validator.errors());
        } else {
            // load user and application
            Observable.zip(applicationObservable(clientId, eventBus),
                    userObservable(username, eventBus),
                    (RxMessage<JsonObject> t1, RxMessage<JsonObject> t2) -> {
                        container.logger().info("application: " + t1.body());
                        container.logger().info("users: " + t2.body());
                        return Optional.<JsonObject> empty();
                    }).subscribe((Optional<JsonObject> t1) -> {

                // Create authorization code with 10 minute expiration
                    event.reply(event.body());
                });
        }

    }

    // TODO refactor to return JsonObject
    // TODO validation errors are thrown as exceptions
    private void doCodeRequest(final RxMessage<JsonObject> event,
            final RxEventBus eventBus) {

        // Validate input
        final Validator validator = new Validator(event.body());
        final String clientId = validator.requireString(CLIENT_ID_PARAM,
                "Client identifier missing");
        final String responseType = validator.requireString(
                RESPONSE_TYPE_PARAM, "Response type missing");
        final String redirectUri = validator.requireString(REDIRECT_URI_PARAM,
                "Redirect URI missing");
        final String scope = validator.requireString(SCOPE_PARAM,
                "Scope missing");

        // If invalid, send error response
        if (validator.hasErrors()) {
            replyError(event, validator.errors());
        } else {

            // Load application and permissions
            Observable
                    .zip(applicationObservable(clientId, eventBus),
                            permissionsObservable(scope, eventBus),
                            (RxMessage<JsonObject> t1, RxMessage<JsonObject> t2) -> {

                                final FindOneResponse appResponse = new FindOneResponse(
                                        t1.body());
                                final FindResponse permResponse = new FindResponse(
                                        t2.body());
                                if (Status.ok.equals(appResponse.status())
                                        && appResponse.result().isPresent()) {
                                    final JsonObject application = appResponse
                                            .result().get();
                                    if (permResponse.results().isPresent()) {
                                        application.putArray("permissions",
                                                permResponse.results().get());
                                    }
                                    return Optional
                                            .<JsonObject> of(application);
                                } else {
                                    return Optional.<JsonObject> empty();
                                }
                            }).subscribe((Optional<JsonObject> t1) -> {

                        // Did application load?
                            if (t1.isPresent()) {

                                // Application was loaded
                                JsonObject result = t1.get();

                                // Check redirect URI matches
                                if (redirectUri.equals(result
                                        .getString("redirectUri"))) {

                                    // Add responseType field
                                    result.putString("responseType",
                                            responseType);

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
                        });
        }
    }

    private void replyError(final RxMessage<JsonObject> event,
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

    private Observable<RxMessage<JsonObject>> userObservable(
            final String username, final RxEventBus eventBus) {
        return eventBus.send(
                this.mongoAddr,
                new JsonObject()
                        .putString("action", "findone")
                        .putString("collection", this.userCollection)
                        .putObject(
                                "matcher",
                                new JsonObject()
                                        .putString("username", username)));
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

    public String requireString(String fieldName, String message) {
        if (!target.containsField(fieldName)) {
            errors.add(errorMessage(fieldName, message));
            return null;
        } else {
            return target.getString(fieldName);
        }
    }

    public JsonArray requireArray(String fieldName, String message) {
        if (!target.containsField(fieldName)) {
            errors.add(errorMessage(fieldName, message));
            return null;
        } else {
            return target.getArray(fieldName);
        }

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