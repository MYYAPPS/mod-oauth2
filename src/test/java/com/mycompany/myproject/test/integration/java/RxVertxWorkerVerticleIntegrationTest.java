package com.mycompany.myproject.test.integration.java;/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */

import io.vertx.rxcore.java.eventbus.RxEventBus;
import io.vertx.rxcore.java.eventbus.RxMessage;

import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonObject;
import org.vertx.testtools.TestVerticle;
import org.vertx.testtools.VertxAssert;

import rx.functions.Action1;

/**
 * Integration tests verifying RxVertx and mod-mongo-persistor
 * 
 */
public class RxVertxWorkerVerticleIntegrationTest extends TestVerticle {

    private static final String WORKER_VERTICLE_ADDR = "vertx.test.verticle";
    private static final String MONGO_WORKER_VERTICLE = "vertx.mongo";

    // mod-mongo-persistor config
    private static final JsonObject modMongoConfig = new JsonObject()
            .putString("address", MONGO_WORKER_VERTICLE)
            .putString("host", "localhost").putNumber("port", 27017)
            .putString("db_name", "test_db").putNumber("pool_size", 20);

    @Test
    public void testClient() {

        RxEventBus rxeb = new RxEventBus(vertx.eventBus());

        rxeb.<JsonObject, JsonObject> send(WORKER_VERTICLE_ADDR + ".app",
                new JsonObject().putString("hello", "ping")).subscribe(
                new Action1<RxMessage<JsonObject>>() {

                    @Override
                    public void call(RxMessage<JsonObject> event) {
                        container.logger().info(event.body());
                        VertxAssert.testComplete();

                    }
                });

    }

    @Test
    public void testZip() {

        RxEventBus rxeb = new RxEventBus(vertx.eventBus());

        rxeb.<JsonObject, JsonObject> send(WORKER_VERTICLE_ADDR + ".zip",
                new JsonObject().putString("hello", "ping")).subscribe(
                new Action1<RxMessage<JsonObject>>() {

                    @Override
                    public void call(RxMessage<JsonObject> event) {
                        container.logger().info(event.body());
                        VertxAssert.testComplete();

                    }
                });

    }

    @Override
    public void start() {
        // Set up plumbing
        initialize();

        // Deploy mod-mongo-persistor
        container.deployModule("io.vertx~mod-mongo-persistor~2.1.1",
                modMongoConfig, new Handler<AsyncResult<String>>() {

                    @Override
                    public void handle(AsyncResult<String> event) {
                        if (event.succeeded()) {

                            // Deploy the verticle
                            container.deployWorkerVerticle(
                                    RxVertxWorkerVerticle.class.getName(),
                                    new JsonObject().putString("address",
                                            WORKER_VERTICLE_ADDR), 1, false,
                                    new AsyncResultHandler<String>() {

                                        @Override
                                        public void handle(
                                                AsyncResult<String> asyncResult) {

                                            if (asyncResult.succeeded()) {
                                                startTests();
                                            } else {
                                                VertxAssert.fail(asyncResult
                                                        .cause()
                                                        .getLocalizedMessage());
                                            }
                                        }
                                    });

                        } else {
                            VertxAssert.fail(event.cause()
                                    .getLocalizedMessage());
                        }

                    }

                });

    }

}
