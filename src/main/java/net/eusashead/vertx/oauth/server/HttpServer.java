package net.eusashead.vertx.oauth.server;

import io.netty.handler.codec.http.HttpHeaders;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import net.eusashead.vertx.oauth.authentication.LoginToken;
import net.eusashead.vertx.oauth.http.UrlEncoder;
import net.eusashead.vertx.oauth.worker.OAuthWorkerVerticle;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Future;
import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Container;
import org.vertx.java.platform.Verticle;
import org.vertx.mods.web.StaticFileHandler;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.MustacheException;
import com.samskivert.mustache.Template;

public class HttpServer extends Verticle {

    // If no next parameter specified
    private static final String DEFAULT_URI = "/default";

    // Worker Verticle address
    public static final String MONGO_WORKER_VERTICLE = "vertx.mongo";
    public static final String AUTH_WORKER_VERTICLE = "vertx.authmanager";
    public static final String OAUTH_WORKER_VERTICLE = "vertx.oauthmanager";

    // TODO configure SSL and check that secure cookie works
    // DONE use lambdas
    // DONE support partial templates
    // TODO csrf defense
    // DONE build util that binds request params to JsonObject
    // TODO build util that binds request params to JavaBean
    // TODO handle form element error messages
    // TODO switch to RxJava event bus
    // TODO convert OAuthVerticle into module

    @Override
    public void start(final Future<Void> startedResult) {

        // mod-mongo-persistor config
        final JsonObject modMongoConfig = new JsonObject()
                .putString("address", MONGO_WORKER_VERTICLE)
                .putString("host", "localhost").putNumber("port", 27017)
                .putString("db_name", "test_db").putNumber("pool_size", 20);

        // mod-auth-mgr config
        final JsonObject modAuthConfig = new JsonObject()
                .putString("address", AUTH_WORKER_VERTICLE)
                .putString("user_collection", "users")
                .putString("persistor_address", MONGO_WORKER_VERTICLE)
                .putNumber("session_timeout", 900000);

        // mod-oauth-provider config
        final JsonObject modOauthConfig = new JsonObject()
                .putString("address", OAUTH_WORKER_VERTICLE)
                .putString("app_collection", "applications")
                .putString("persistor_address", MONGO_WORKER_VERTICLE)
                .putNumber("token_timeout", 900000);

        // Get the event bus
        final EventBus eventBus = this.vertx.eventBus();

        // Deploy modules
        new ModuleDeployer(container)
                .withModule("io.vertx~mod-mongo-persistor~2.1.1",
                        modMongoConfig)
                .withModule("io.vertx~mod-auth-mgr~2.0.0-final", modAuthConfig)
                .withWorkerVerticle(OAuthWorkerVerticle.class.getName(),
                        modOauthConfig, 1, false)
                .deploy((
                        final AsyncResult<Map<String, AsyncResult<String>>> event) -> {
                    if (event.succeeded()) {
                        startedResult.setResult(null);
                    } else {
                        startedResult.setFailure(event.cause());
                    }

                });

        // Create a ViewFactory
        final ViewFactory viewFactory = new MustacheViewFactoryImpl(new File(
                "src/main/web/"), container.logger());

        // Create RouteMatcher
        final RouteMatcher routeMatcher = new RouteMatcher();

        // TODO extract login handler classes

        // Get the login form
        routeMatcher.get("/login", (final HttpServerRequest request) -> {

            // Get the next URI
                String next = getNextUri(request.params(), DEFAULT_URI);

                // Create the model
                Map<String, String> model = new ConcurrentHashMap<>();
                model.put("next", next);

                // Render the view
                View tmpl = viewFactory.getView("login.html");
                tmpl.render(request.response(), model);
            });

        // Handle login submission
        routeMatcher
                .post("/login",
                        (final HttpServerRequest request) -> {

                            // Get the form data
                            FormBinder
                                    .populate(
                                            request,
                                            (final JsonObject formData) -> {

                                                // Send login request
                                                eventBus.send(
                                                        AUTH_WORKER_VERTICLE
                                                                + ".login",
                                                        formData,
                                                        (final Message<JsonObject> message) -> {
                                                            if ("ok".equals(message
                                                                    .body()
                                                                    .getString(
                                                                            "status"))) {
                                                                LoginToken token = new LoginToken(
                                                                        message.body()
                                                                                .getString(
                                                                                        "sessionID"));
                                                                token.encode(request
                                                                        .response());
                                                                request.response()
                                                                        .headers()
                                                                        .add(HttpHeaders.Names.LOCATION,
                                                                                request.params()
                                                                                        .get("next"));
                                                                request.response()
                                                                        .setStatusCode(
                                                                                302)
                                                                        .end();
                                                            } else {

                                                                // Get the next
                                                                // URI
                                                                String next = getNextUri(
                                                                        request.params(),
                                                                        DEFAULT_URI);

                                                                // Create the
                                                                // model
                                                                Map<String, String> model = new ConcurrentHashMap<>();
                                                                model.put(
                                                                        "next",
                                                                        next);
                                                                model.put(
                                                                        "error",
                                                                        "Login failed. Please check username and password and try again.");

                                                                // Render the
                                                                // view
                                                                request.response()
                                                                        .setStatusCode(
                                                                                403);
                                                                viewFactory
                                                                        .getView(
                                                                                "login.html")
                                                                        .render(request
                                                                                .response(),
                                                                                model);
                                                            }
                                                        });
                                            });

                        });

        // TODO create logout handler class
        // Handle logout
        routeMatcher
                .get("/logout",
                        (final HttpServerRequest request) -> {
                            authenticate(
                                    request,
                                    (final AuthenticatedRequest event) -> {

                                        eventBus.send(
                                                AUTH_WORKER_VERTICLE
                                                        + ".logout",
                                                new JsonObject().putString(
                                                        "sessionID", event
                                                                .token()
                                                                .sessionId()),
                                                (final Message<JsonObject> message) -> {

                                                    container.logger().info(
                                                            message.body());

                                                    // Expire the token
                                                    event.token().expire(
                                                            request.response());

                                                    // Send logout UI
                                                    viewFactory
                                                            .getView(
                                                                    "logout.html")
                                                            .render(request
                                                                    .response(),
                                                                    new ConcurrentHashMap<String, String>());
                                                });

                                    });

                        });

        // Create authentication resource
        // TODO extract AuthHandler.java
        routeMatcher.get(
                "/auth",
                (final HttpServerRequest request) -> {

                    // Is the user logged in
                    authenticate(request,
                            (final AuthenticatedRequest event) -> {

                                // Get the form data
                            FormBinder.populate(request, (
                                    final JsonObject formData) -> {
                                eventBus.send(OAUTH_WORKER_VERTICLE
                                        + ".codeRequest",
                                        formData,
                                        (final Message<JsonObject> reply) -> {

                                            if ("ok".equals(reply.body()
                                                    .getField("status"))) {
                                                // Return authorization form
                                        View tmpl = viewFactory
                                                .getView("auth.html");
                                        tmpl.render(request.response(),
                                                reply.body());
                                    } else {
                                        request.response().setStatusCode(400);
                                        View tmpl = viewFactory
                                                .getView("error.html");
                                        tmpl.render(request.response(),
                                                reply.body());
                                    }

                                });
                            });
                        });
                });

        // TODO handle /auth post and create code
        routeMatcher
                .post("/auth",
                        (final HttpServerRequest request) -> {

                            // Get the form data
                            FormBinder
                                    .populate(
                                            request,
                                            (final JsonObject formData) -> {

                                                // Is the user logged in
                                                authenticate(
                                                        request,
                                                        (final AuthenticatedRequest event) -> {

                                                            // Add user to
                                                            // request
                                                            formData.putString(
                                                                    "username",
                                                                    event.principal());

                                                            // Send message
                                                            eventBus.send(
                                                                    OAUTH_WORKER_VERTICLE
                                                                            + ".codeResponse",
                                                                    formData,
                                                                    (final Message<JsonObject> reply) -> {
                                                                        request.response()
                                                                                .end(reply
                                                                                        .body()
                                                                                        .encode());
                                                                    });

                                                        });
                                            });
                        });

        // TODO handle POST /token to create a new auth token

        // Create token resource
        // TODO extract TokenHandler.java
        routeMatcher.get("/token/:tokenId",
                (final HttpServerRequest request) -> {

                    // Is the user logged in
                authenticate(request, new Handler<AuthenticatedRequest>() {

                    @Override
                    public void handle(final AuthenticatedRequest event) {
                        // TODO get token data from Verticle
                        request.response().end();
                    }

                });
            });

        // Add the static handler
        routeMatcher.noMatch(staticHandler());

        // Create the HttpServer
        this.vertx.createHttpServer().setCompressionSupported(true)
                .requestHandler(routeMatcher).listen(8080);

    }

    private void authenticate(final HttpServerRequest request,
            final Handler<AuthenticatedRequest> handler) {

        // Is the user logged in
        final Optional<LoginToken> token = LoginToken.decode(request);
        if (token.isPresent()) {

            container.logger().info(
                    "Authorising token: " + token.get().sessionId());

            vertx.eventBus().send(
                    AUTH_WORKER_VERTICLE + ".authorise",
                    new JsonObject().putString("sessionID", token.get()
                            .sessionId()), new Handler<Message<JsonObject>>() {

                        @Override
                        public void handle(Message<JsonObject> event) {

                            container.logger().info(
                                    "Authorise response: " + event.body());
                            if ("ok".equals(event.body().getString("status"))) {
                                String principal = event.body().getString(
                                        "username");
                                handler.handle(new AuthenticatedRequest(
                                        principal, token.get(), request));
                            } else {
                                sendLogin(request);
                            }

                        }

                    });
        } else {
            sendLogin(request);
        }

    }

    private void sendLogin(final HttpServerRequest request) {
        String url = "/login?next="
                + UrlEncoder.encode(request.absoluteURI().toString()).get();
        request.response().headers().set(HttpHeaders.Names.LOCATION, url);
        request.response().setStatusCode(302).end();
    }

    private String getNextUri(MultiMap params, String defaultUri) {
        Optional<String> nextOption = UrlEncoder.encode(params.get("next"));
        String next = nextOption.isPresent() ? nextOption.get() : defaultUri;
        return next;
    }

    static class FormBinder {
        public static void populate(final HttpServerRequest request,
                final Handler<JsonObject> handler) {
            final JsonObject requestData = new JsonObject();

            // Get query string and path params
            addParams(requestData, request.params());

            // Parse body
            if (request.headers().contains(HttpHeaders.Names.CONTENT_TYPE)) {

                // Get the content type
                String contentType = request.headers().get(
                        HttpHeaders.Names.CONTENT_TYPE);

                // Handle POST values
                if ("application/x-www-form-urlencoded".equals(contentType)) {
                    request.expectMultiPart(true);
                    request.endHandler(new VoidHandler() {
                        public void handle() {
                            MultiMap attrs = request.formAttributes();
                            addParams(requestData, attrs);
                            handler.handle(requestData);
                        }
                    });
                }
                // Handle JSON body
                else if ("application/json".equals(contentType)) {
                    request.bodyHandler((final Buffer event) -> {
                        JsonObject body = new JsonObject(event.toString());
                        requestData.putObject("body", body);
                        handler.handle(requestData);
                    });
                } else {
                    handler.handle(requestData);
                }
            } else {
                handler.handle(requestData);
            }
        }

        public static void addParams(final JsonObject requestData,
                MultiMap attrs) {
            for (String name : attrs.names()) {
                List<String> vals = attrs.getAll(name);
                if (1 == vals.size()) {
                    requestData.putString(name, vals.get(0));
                } else {
                    JsonArray arr = new JsonArray();
                    for (String val : vals) {
                        arr.addString(val);
                    }
                    requestData.putArray(name, arr);
                }

            }
        }
    }

    private Handler<HttpServerRequest> staticHandler() {

        // TODO why is gzip failing?
        return new StaticFileHandler(vertx, "src/main/web/", "index.html",
                false, true);
    }
}

class AuthenticatedRequest {

    private final String principal;
    private final LoginToken token;
    private final HttpServerRequest request;

    public AuthenticatedRequest(String principal, LoginToken token,
            HttpServerRequest request) {
        this.principal = Objects.requireNonNull(principal);
        this.token = Objects.requireNonNull(token);
        this.request = Objects.requireNonNull(request);
    }

    public String principal() {
        return this.principal;
    }

    public LoginToken token() {
        return this.token;
    }

    public HttpServerRequest request() {
        return this.request;
    }
}

interface View {

    void render(HttpServerResponse response, Map<String, ?> model);

    void render(HttpServerResponse response, JsonObject model);
}

interface ViewFactory {

    View getView(String name);
}

class MustacheViewImpl implements View {

    private final File templateDir;
    private final String name;
    private final Logger logger;

    public MustacheViewImpl(File templateDir, String name, Logger logger) {
        this.templateDir = Objects.requireNonNull(templateDir);
        this.name = Objects.requireNonNull(name);
        this.logger = Objects.requireNonNull(logger);
    }

    @Override
    public void render(HttpServerResponse response, Map<String, ?> model) {
        try {
            Objects.requireNonNull(response);
            Objects.requireNonNull(model);
            Reader reader = new FileReader(new File(templateDir, name));
            Template tmpl = Mustache.compiler()
                    .withLoader(new Mustache.TemplateLoader() {
                        public Reader getTemplate(String name)
                                throws FileNotFoundException {
                            return new FileReader(new File(templateDir, name));
                        }
                    }).compile(reader);
            response.end(tmpl.execute(model));
        } catch (MustacheException | FileNotFoundException e) {
            logger.error("Template rendering failed", e);
            response.setStatusCode(500).end();
        }

    }

    @Override
    public void render(HttpServerResponse response, JsonObject model) {
        Objects.requireNonNull(response);
        Objects.requireNonNull(model);
        this.render(response, model.toMap());
    }

}

class MustacheViewFactoryImpl implements ViewFactory {

    private final File templateDir;
    private final Logger logger;

    public MustacheViewFactoryImpl(File templateDir, Logger logger) {
        this.templateDir = Objects.requireNonNull(templateDir);
        this.logger = Objects.requireNonNull(logger);
    }

    @Override
    public View getView(String name) {
        return new MustacheViewImpl(templateDir, name, logger);
    }

}

// TODO extract class and refactor
class ModuleDeployer {

    private final Container container;

    private final Map<String, DeployAction> deployActions = new ConcurrentHashMap<>();

    private final Map<String, AsyncResult<String>> deployResults = new ConcurrentHashMap<>();

    public ModuleDeployer(final Container container) {
        this.container = Objects.requireNonNull(container);
    }

    public ModuleDeployer withModule(final String name, final JsonObject config) {
        this.deployActions.put(name, new ModuleDeployAction(name, config));
        return this;
    }

    public ModuleDeployer withWorkerVerticle(final String name,
            final JsonObject config, final int instances,
            final boolean multiThreaded) {
        this.deployActions.put(name, new WorkerVerticleDeployAction(name,
                config, instances, multiThreaded));
        return this;
    }

    public void deploy(
            final Handler<AsyncResult<Map<String, AsyncResult<String>>>> handler) {
        Iterator<Entry<String, DeployAction>> iterator = deployActions
                .entrySet().iterator();
        if (iterator.hasNext()) {
            Entry<String, DeployAction> first = iterator.next();
            deploy(handler, first, iterator, true);
        }
    }

    private void deploy(
            final Handler<AsyncResult<Map<String, AsyncResult<String>>>> handler,
            final Entry<String, DeployAction> first,
            final Iterator<Entry<String, DeployAction>> iterator,
            final boolean succeeded) {
        final String modName = first.getKey();
        first.getValue().deploy(container, new Handler<AsyncResult<String>>() {

            private boolean success = succeeded;

            @Override
            public void handle(AsyncResult<String> event) {
                deployResults.put(modName, event);
                if (event.succeeded()) {
                    container.logger().info(
                            String.format(
                                    "Module %s deployed with deployment id %s",
                                    modName, event.result()));
                } else {
                    container.logger().error(
                            String.format("Module %s failed", modName),
                            event.cause());
                    success = false;
                }
                if (iterator.hasNext()) {
                    Entry<String, DeployAction> first = iterator.next();
                    deploy(handler, first, iterator, success);
                } else {
                    handler.handle(new AsyncResult<Map<String, AsyncResult<String>>>() {

                        @Override
                        public Map<String, AsyncResult<String>> result() {
                            return deployResults;
                        }

                        @Override
                        public Throwable cause() {
                            // TODO Auto-generated method stub
                            return null;
                        }

                        @Override
                        public boolean succeeded() {
                            return succeeded;
                        }

                        @Override
                        public boolean failed() {
                            return succeeded;
                        }
                    });
                }

            }

        });

    }

}

interface DeployAction {

    String name();

    JsonObject config();

    void deploy(Container container, Handler<AsyncResult<String>> handler);

}

class ModuleDeployAction implements DeployAction {

    private final String name;
    private final JsonObject config;

    public ModuleDeployAction(String name, JsonObject config) {
        this.name = Objects.requireNonNull(name);
        this.config = Objects.requireNonNull(config);
    }

    @Override
    public void deploy(final Container container,
            final Handler<AsyncResult<String>> handler) {
        container.deployModule(name, config, handler);
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public JsonObject config() {
        return this.config;
    }

}

class WorkerVerticleDeployAction implements DeployAction {

    private final String name;
    private final JsonObject config;
    private final int instances;
    private final boolean multiThreaded;

    public WorkerVerticleDeployAction(String name, JsonObject config,
            int instances, boolean multiThreaded) {
        this.name = Objects.requireNonNull(name);
        this.config = Objects.requireNonNull(config);
        this.instances = instances;
        this.multiThreaded = multiThreaded;
    }

    @Override
    public void deploy(final Container container,
            final Handler<AsyncResult<String>> handler) {
        container.deployWorkerVerticle(name, config, instances, multiThreaded,
                handler);
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public JsonObject config() {
        return this.config;
    }

}