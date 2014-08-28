package com.mycompany.myproject.test.integration.java;

import io.vertx.rxcore.java.eventbus.RxEventBus;
import io.vertx.rxcore.java.eventbus.RxMessage;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.json.JsonObject;

import rx.functions.Action1;

public class RxVertxWorkerVerticle extends BusModBase {

    @Override
    public void start() {
        super.start();

        // Get config params
        String address = getOptionalStringConfig("address",
                "vertx.test.address");

        // Create RxEventBus
        final RxEventBus eventBus = new RxEventBus(vertx.eventBus());

        // Register app handler
        eventBus.<JsonObject> registerHandler(address + ".app").subscribe(
                new Action1<RxMessage<JsonObject>>() {

                    @Override
                    public void call(final RxMessage<JsonObject> event) {
                        container.logger().info(event.body());

                        Observables.clientObservable(eventBus).subscribe(
                                new Action1<RxMessage<JsonObject>>() {

                                    @Override
                                    public void call(RxMessage<JsonObject> t1) {
                                        event.reply(t1.body());

                                    }
                                });

                    }

                });

        // Register zip handler
        eventBus.<JsonObject> registerHandler(address + ".zip").subscribe(
                new Action1<RxMessage<JsonObject>>() {

                    @Override
                    public void call(final RxMessage<JsonObject> event) {
                        container.logger().info(event.body());

                        Observables.zipObservable(eventBus, container.logger())
                                .subscribe(new Action1<JsonObject>() {

                                    @Override
                                    public void call(JsonObject t1) {
                                        event.reply(t1);

                                    }
                                });

                    }

                });
    }
}
