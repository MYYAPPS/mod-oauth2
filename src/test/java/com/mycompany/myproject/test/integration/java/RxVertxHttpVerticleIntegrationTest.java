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

import static org.vertx.testtools.VertxAssert.assertNotNull;
import static org.vertx.testtools.VertxAssert.assertTrue;
import io.vertx.rxcore.java.eventbus.RxEventBus;
import io.vertx.rxcore.java.eventbus.RxMessage;

import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.json.JsonObject;
import org.vertx.testtools.TestVerticle;
import org.vertx.testtools.VertxAssert;

import rx.functions.Action1;

/**
 * Integration tests verifying RxVertx and mod-mongo-persistor
 * 
 */
public class RxVertxHttpVerticleIntegrationTest extends TestVerticle {

    private static final String RESOURCE_URI = "/resource";

    private Action1<RxMessage<JsonObject>> rxMessageAction() {
        return new Action1<RxMessage<JsonObject>>() {

            @Override
            public void call(RxMessage<JsonObject> t1) {
                container.logger().info(
                        "Observable returned with: " + t1.body());
                VertxAssert.testComplete();

            }
        };
    }

    @Test
    public void testClientObservable() {
        Observables.clientObservable(new RxEventBus(vertx.eventBus()))
                .subscribe(rxMessageAction());
    }

    @Test
    public void testPermissionsObservable() {
        Observables.permissionsObservable(new RxEventBus(vertx.eventBus()))
                .subscribe(rxMessageAction());
    }

    @Test
    public void testZipObservable() {
        RxEventBus eventBus = new RxEventBus(vertx.eventBus());
        Observables.zipObservable(eventBus, container.logger()).subscribe(
                new Action1<JsonObject>() {

                    @Override
                    public void call(JsonObject t1) {
                        container.logger().info(
                                "Zip Observable returned with: " + t1);
                        VertxAssert.testComplete();

                    }

                });
    }

    @Test
    public void testGetResource() {

        HttpClient client = vertx.createHttpClient();
        client.setPort(8080);
        client.setHost("localhost");
        client.get(RESOURCE_URI, new Handler<HttpClientResponse>() {

            @Override
            public void handle(HttpClientResponse event) {
                assertTrue(200 == event.statusCode());
                event.bodyHandler(new Handler<Buffer>() {

                    @Override
                    public void handle(Buffer event) {
                        System.out.println(event);
                        VertxAssert.testComplete();
                    }
                });

            }
        }).end();

    }

    @Override
    public void start() {
        // Set up plumbing
        initialize();

        // Deploy the module
        container.deployVerticle(RxVertxHttpVerticle.class.getName(),
                new AsyncResultHandler<String>() {

                    @Override
                    public void handle(AsyncResult<String> asyncResult) {
                        // Deployment is asynchronous and this this handler will
                        // be called when it's complete (or failed)
                        if (asyncResult.failed()) {
                            container.logger().error(asyncResult.cause());
                        }
                        assertTrue(asyncResult.succeeded());
                        assertNotNull("deploymentID should not be null",
                                asyncResult.result());

                        // If deployed correctly then start the tests!
                        startTests();
                    }
                });
    }

}
