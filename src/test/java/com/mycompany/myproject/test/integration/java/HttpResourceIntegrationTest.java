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

import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.testtools.TestVerticle;
import org.vertx.testtools.VertxAssert;

import com.mycompany.myproject.HelloServer;

/**
 * Example Java integration test that deploys the module that this project
 * builds.
 * 
 * Quite often in integration tests you want to deploy the same module for all
 * tests and you don't want tests to start before the module has been deployed.
 * 
 * This test demonstrates how to do that.
 */
public class HttpResourceIntegrationTest extends TestVerticle {

    private static final String RESOURCE_URI = "/resource";

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
        container.deployVerticle(HelloServer.class.getName(),
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

                        // Sleep for a bit to allow nested modules to deploy
                        try {
                            Thread.sleep(200); // This is a dirty hack - need to
                                               // ask what to do about this on
                                               // the newsgroup at some point
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        // If deployed correctly then start the tests!
                        startTests();
                    }
                });
    }

}
