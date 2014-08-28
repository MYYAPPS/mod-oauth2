package com.mycompany.myproject.test.integration.java;

import io.vertx.rxcore.java.eventbus.RxEventBus;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Future;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Verticle;

import rx.functions.Action1;

public class RxVertxHttpVerticle extends Verticle {

    private static final String MONGO_WORKER_VERTICLE = "vertx.mongo";

    @Override
    public void start(final Future<Void> startedResult) {

        // mod-mongo-persistor config
        final JsonObject modMongoConfig = new JsonObject()
                .putString("address", MONGO_WORKER_VERTICLE)
                .putString("host", "localhost").putNumber("port", 27017)
                .putString("db_name", "test_db").putNumber("pool_size", 20);

        // Get the event bus
        final RxEventBus eventBus = new RxEventBus(vertx.eventBus());

        // Deploy modules
        container.deployModule("io.vertx~mod-mongo-persistor~2.1.1",
                modMongoConfig, new Handler<AsyncResult<String>>() {

                    @Override
                    public void handle(AsyncResult<String> event) {
                        if (event.succeeded()) {
                            startedResult.setResult(null);
                        } else {
                            startedResult.setFailure(event.cause());
                        }

                    }

                });

        // Create RouteMatcher
        final RouteMatcher routeMatcher = new RouteMatcher();

        // Get the test resource
        routeMatcher.get("/resource", new Handler<HttpServerRequest>() {

            @Override
            public void handle(final HttpServerRequest request) {

                Observables.zipObservable(eventBus, container.logger())
                        .subscribe(new Action1<JsonObject>() {

                            @Override
                            public void call(JsonObject t1) {
                                container.logger().info(
                                        "Zip Observable returned with: " + t1);
                                request.response().end(t1.encode());

                            }

                        });

            }

        });

        // Create the HttpServer
        vertx.createHttpServer().setCompressionSupported(true)
                .requestHandler(routeMatcher).listen(8080);
    }

}
