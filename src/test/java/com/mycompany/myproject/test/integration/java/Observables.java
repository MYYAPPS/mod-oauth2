package com.mycompany.myproject.test.integration.java;

import io.vertx.rxcore.java.eventbus.RxEventBus;
import io.vertx.rxcore.java.eventbus.RxMessage;
import net.eusashead.vertx.oauth.server.HttpServer;

import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;

import rx.Observable;
import rx.functions.Func2;

public class Observables {
    public static Observable<RxMessage<JsonObject>> clientObservable(
            RxEventBus eventBus) {
        return eventBus
                .send(HttpServer.MONGO_WORKER_VERTICLE,
                        new JsonObject()
                                .putString("action", "findone")
                                .putString("collection", "applications")
                                .putObject(
                                        "matcher",
                                        new JsonObject()
                                                .putString("_id",
                                                        "faa97e70-f79f-4329-81e3-ac879882cc34")));
    }

    public static Observable<RxMessage<JsonObject>> permissionsObservable(
            RxEventBus eventBus) {
        return eventBus.send(
                HttpServer.MONGO_WORKER_VERTICLE,
                new JsonObject()
                        .putString("action", "find")
                        .putString("collection", "permissions")
                        .putObject(
                                "matcher",
                                new JsonObject().putObject("name",
                                        new JsonObject().putArray(
                                                "$in",
                                                new JsonArray().addString(
                                                        "friends").addString(
                                                        "photos")))));
    }

    public static Observable<JsonObject> zipObservable(
            final RxEventBus eventBus, final Logger logger) {
        return Observable
                .zip(Observables.clientObservable(eventBus),
                        Observables.permissionsObservable(eventBus),
                        new Func2<RxMessage<JsonObject>, RxMessage<JsonObject>, JsonObject>() {

                            @Override
                            public JsonObject call(RxMessage<JsonObject> t1,
                                    RxMessage<JsonObject> t2) {
                                logger.info("Zip function t1: " + t1.body());
                                logger.info("Zip function t2: " + t2.body());
                                return t1
                                        .body()
                                        .getObject("result")
                                        .putArray("perms",
                                                t2.body().getArray("results"));
                            }
                        });
    }
}