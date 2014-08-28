package net.eusashead.vertx.oauth2.server;/*
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
import net.eusashead.vertx.oauth.authentication.LoginToken;
import net.eusashead.vertx.oauth.http.UrlEncoder;
import net.eusashead.vertx.oauth.server.HttpServer;

import org.junit.Test;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.VoidHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpHeaders;
import org.vertx.java.core.json.JsonObject;
import org.vertx.testtools.TestVerticle;
import org.vertx.testtools.VertxAssert;

import rx.Observable;
import rx.functions.Action0;
import rx.functions.Action1;

/**
 * Example Java integration test that deploys the module that this project
 * builds.
 * 
 * Quite often in integration tests you want to deploy the same module for all
 * tests and you don't want tests to start before the module has been deployed.
 * 
 * This test demonstrates how to do that.
 */
public class HttpServerIntegrationTest extends TestVerticle {

    private static final String PASSWORD = "secret";
    private static final String USERNAME = "patrick";
    private static final int HOST_PORT = 8080;
    private static final String HOST_NAME = "localhost";
    private static final String RESOURCE_URI = "/auth";
    private static final String LOGIN_URI = "/login";
    private static final String CLIENT_ID = "faa97e70-f79f-4329-81e3-ac879882cc34";
    // private static final String REDIRECT_URI =
    // "http%3A%2F%2Flocalhost%3A8081%2Foauthcallback";
    private static final String REDIRECT_URI = "http://localhost:8081/oauthcallback";
    private static final String CANCEL_URI = "http://localhost:8081/oauthcancel";

    // TODO verify headers in tests
    // TODO verify response body in tests

    @Test
    public void testGetCss() {
        final HttpClient client = httpClient();
        final String uri = "/css/styles.css";
        final HttpClientRequest request = client.get(uri,
                new Handler<HttpClientResponse>() {

                    @Override
                    public void handle(final HttpClientResponse response) {
                        VertxAssert.assertEquals(200, response.statusCode());
                        response.bodyHandler(new Handler<Buffer>() {

                            @Override
                            public void handle(Buffer event) {
                                container.logger().info(event);
                                VertxAssert.testComplete();
                            }
                        });

                    }
                });
        request.end();
    }

    @Test
    public void testGetLoginUI() {
        final HttpClient client = httpClient();
        final String uri = loginUri();
        final HttpClientRequest request = client.get(uri,
                new Handler<HttpClientResponse>() {

                    @Override
                    public void handle(final HttpClientResponse response) {
                        VertxAssert.assertEquals(200, response.statusCode());
                        response.bodyHandler(new Handler<Buffer>() {

                            @Override
                            public void handle(Buffer event) {
                                container.logger().info(event);
                                VertxAssert.testComplete();
                            }
                        });

                    }
                });
        request.end();
    }

    @Test
    public void testGetLoginUIWithoutNext() {
        final HttpClient client = httpClient();
        final String uri = "/login";
        final HttpClientRequest request = client.get(uri,
                new Handler<HttpClientResponse>() {

                    @Override
                    public void handle(final HttpClientResponse response) {
                        VertxAssert.assertEquals(200, response.statusCode());
                        response.bodyHandler(new Handler<Buffer>() {

                            @Override
                            public void handle(Buffer event) {
                                container.logger().info(event);
                                VertxAssert.testComplete();
                            }
                        });

                    }
                });
        request.end();
    }

    @Test
    public void testPostLoginValid() {
        final HttpClient client = httpClient();
        final String uri = loginUri();
        final HttpClientRequest request = client.post(uri,
                new Handler<HttpClientResponse>() {

                    @Override
                    public void handle(final HttpClientResponse response) {

                        // Check 302 redirect sent
                        VertxAssert.assertEquals(302, response.statusCode());

                        // Cookie should be sent
                        VertxAssert.assertNotNull(response.headers().get(
                                HttpHeaders.SET_COOKIE));

                        // Check location header
                        VertxAssert.assertEquals(authUri(), response.headers()
                                .get(HttpHeaders.LOCATION));

                        // Check the body is absent
                        response.bodyHandler(new Handler<Buffer>() {

                            @Override
                            public void handle(Buffer event) {
                                VertxAssert.assertEquals(0, event.length());
                                VertxAssert.testComplete();
                            }
                        });

                    }
                });
        String data = new StringBuilder("username=").append(USERNAME)
                .append("&password=").append(PASSWORD).toString();
        request.putHeader(HttpHeaders.CONTENT_TYPE,
                "application/x-www-form-urlencoded");
        request.putHeader(HttpHeaders.CONTENT_LENGTH,
                Integer.toString(data.length()));
        request.end(data);
    }

    @Test
    public void testPostLoginInvalid() {
        final HttpClient client = httpClient();
        final String uri = loginUri();
        final HttpClientRequest request = client.post(uri,
                new Handler<HttpClientResponse>() {

                    @Override
                    public void handle(final HttpClientResponse response) {

                        // Check 403 error sent
                        VertxAssert.assertEquals(403, response.statusCode());

                        // Cookie should not be sent
                        VertxAssert.assertNull(response.headers().get(
                                HttpHeaders.SET_COOKIE));

                        // Check location header null
                        VertxAssert.assertNull(response.headers().get(
                                HttpHeaders.LOCATION));

                        // Check the body is present
                        response.bodyHandler(new Handler<Buffer>() {

                            @Override
                            public void handle(Buffer event) {
                                container.logger().info(event);
                                VertxAssert.assertTrue(0 < event.length());
                                VertxAssert.testComplete();
                            }
                        });

                    }
                });
        String data = new StringBuilder("username=").append(USERNAME)
                .append("&password=").append("invalid").toString();
        request.putHeader(HttpHeaders.CONTENT_TYPE,
                "application/x-www-form-urlencoded");
        request.putHeader(HttpHeaders.CONTENT_LENGTH,
                Integer.toString(data.length()));
        request.end(data);
    }

    @Test
    public void testLogoutAuthenticated() {

        login(USERNAME, PASSWORD, new Handler<LoginToken>() {

            @Override
            public void handle(final LoginToken sessionId) {

                final HttpClient client = httpClient();
                final String uri = "/logout";
                final HttpClientRequest request = client.get(uri,
                        new Handler<HttpClientResponse>() {

                            @Override
                            public void handle(final HttpClientResponse response) {

                                // Should be 200
                                VertxAssert.assertEquals(200,
                                        response.statusCode());

                                // Cookie should be resent
                                VertxAssert.assertNotNull(response.headers()
                                        .get(HttpHeaders.SET_COOKIE));

                                // Verify body
                                response.bodyHandler(new Handler<Buffer>() {

                                    @Override
                                    public void handle(Buffer event) {
                                        container.logger().info(event);
                                        VertxAssert.testComplete();
                                    }
                                });

                            }
                        });
                sessionId.encode(request);
                request.end();

            }

        });

    }

    @Test
    public void testGetAuthValid() {

        login(USERNAME, PASSWORD, new Handler<LoginToken>() {

            @Override
            public void handle(final LoginToken sessionId) {

                final HttpClient client = httpClient();
                final String uri = authUri();
                final HttpClientRequest request = client.get(uri,
                        new Handler<HttpClientResponse>() {

                            @Override
                            public void handle(final HttpClientResponse response) {
                                VertxAssert.assertEquals(200,
                                        response.statusCode());
                                response.bodyHandler(new Handler<Buffer>() {

                                    @Override
                                    public void handle(Buffer event) {
                                        container.logger().info(event);
                                        VertxAssert.testComplete();
                                    }
                                });

                            }
                        });
                sessionId.encode(request);
                request.end();

            }

        });

    }

    @Test
    public void testGetAuthInvalid() {

        login(USERNAME, PASSWORD, new Handler<LoginToken>() {

            @Override
            public void handle(final LoginToken sessionId) {

                final HttpClient client = httpClient();
                final String uri = "/auth";
                final HttpClientRequest request = client.get(uri,
                        new Handler<HttpClientResponse>() {

                            @Override
                            public void handle(final HttpClientResponse response) {
                                VertxAssert.assertEquals(400,
                                        response.statusCode());
                                response.bodyHandler(new Handler<Buffer>() {

                                    @Override
                                    public void handle(Buffer event) {
                                        container.logger().info(event);
                                        VertxAssert.testComplete();
                                    }
                                });

                            }
                        });
                sessionId.encode(request);
                request.end();

            }

        });

    }

    @Test
    public void testPostAuthValid() {

        login(USERNAME, PASSWORD, new Handler<LoginToken>() {

            @Override
            public void handle(final LoginToken sessionId) {

                final HttpClient client = httpClient();
                final String uri = "/auth";
                final HttpClientRequest request = client.post(uri,
                        new Handler<HttpClientResponse>() {

                            @Override
                            public void handle(final HttpClientResponse response) {
                                VertxAssert.assertEquals(200,
                                        response.statusCode());
                                response.bodyHandler(new Handler<Buffer>() {

                                    @Override
                                    public void handle(Buffer event) {
                                        container.logger().info(event);
                                        VertxAssert.testComplete();
                                    }
                                });

                            }
                        });

                // Form data
                String data = new StringBuilder("clientId=").append("1234")
                        .append("&redirectUri=")
                        .append(UrlEncoder.encode(REDIRECT_URI).get())
                        .append("&scope=").append("friends").append("&scope=")
                        .append("photos").toString();
                request.putHeader(HttpHeaders.CONTENT_TYPE,
                        "application/x-www-form-urlencoded");
                request.putHeader(HttpHeaders.CONTENT_LENGTH,
                        Integer.toString(data.length()));

                // Send session id
                sessionId.encode(request);

                // End request
                request.end(data);

            }

        });

    }

    // TODO test postAuthInvalid()

    private void login(final String username, final String password,
            final Handler<LoginToken> handler) {
        vertx.eventBus().send(
                HttpServer.AUTH_WORKER_VERTICLE + ".login",
                new JsonObject().putString("username", username).putString(
                        "password", password),
                new Handler<Message<JsonObject>>() {

                    @Override
                    public void handle(Message<JsonObject> event) {
                        container.logger().info(
                                "Login response: " + event.body());
                        if ("ok".equals(event.body().getString("status"))) {
                            final String sessionId = event.body().getString(
                                    "sessionID");

                            handler.handle(new LoginToken(sessionId));
                        } else {
                            VertxAssert.fail("Login failed: " + event.body());
                        }
                    }
                });
    }

    private String authUri() {
        return new StringBuilder(RESOURCE_URI).append('?')
                .append("response_type=code&").append("client_id=")
                .append(CLIENT_ID).append('&').append("redirect_uri=")
                .append(UrlEncoder.encode(REDIRECT_URI).get()).append('&')
                .append("scope=")
                .append(UrlEncoder.encode("photos friends").get()).toString()
                .toString();
    }

    private String loginUri() {
        return new StringBuilder(LOGIN_URI).append('?').append("next=")
                .append(UrlEncoder.encode(authUri()).get()).toString();
    }

    private HttpClient httpClient() {
        HttpClient client = vertx.createHttpClient();
        client.setPort(HOST_PORT);
        client.setHost(HOST_NAME);
        return client;
    }

    @Override
    public void start() {
        // Set up plumbing
        initialize();

        // TODO deploy as verticle from mod.json
        // TODO get config params from mod.json

        // Deploy the module
        container.deployVerticle(HttpServer.class.getName(),
                new AsyncResultHandler<String>() {

                    @Override
                    public void handle(AsyncResult<String> asyncResult) {
                        // Deployment is asynchronous and this this handler will
                        // be called when it's complete (or failed)
                        if (asyncResult.failed()) {
                            container.logger().error(asyncResult.cause());
                        }
                        VertxAssert.assertTrue(asyncResult.succeeded());
                        VertxAssert.assertNotNull(
                                "deploymentID should not be null",
                                asyncResult.result());

                        // Create test data
                        createData(new VoidHandler() {

                            @Override
                            public void handle() {
                                // Start tests
                                startTests();

                            }
                        });

                    }

                    private void createData(final VoidHandler postCreate) {

                        RxEventBus rxEventBus = new RxEventBus(vertx.eventBus());

                        Observable<RxMessage<JsonObject>> perm1 = saveObservable(
                                rxEventBus,
                                "permissions",
                                new JsonObject()
                                        .putString("_id",
                                                "0db2f1e0-b34c-47e6-a43e-4a3f02c9d69c")
                                        .putString("name", "friends")
                                        .putString("description",
                                                "Your contact list"));

                        Observable<RxMessage<JsonObject>> perm2 = saveObservable(
                                rxEventBus,
                                "permissions",
                                new JsonObject()
                                        .putString("_id",
                                                "0db2f1e0-b34c-47e6-a43e-4a3f02c9d69d")
                                        .putString("name", "photos")
                                        .putString("description",
                                                "Your photo stream"));

                        Observable<RxMessage<JsonObject>> user1 = saveObservable(
                                rxEventBus,
                                "users",
                                new JsonObject()
                                        .putString("_id",
                                                "0db2f1e0-b34c-47e6-a43e-4a3f02c9d69c")
                                        .putString("username", USERNAME)
                                        .putString("password", PASSWORD));

                        Observable<RxMessage<JsonObject>> app1 = saveObservable(
                                rxEventBus,
                                "applications",
                                new JsonObject()
                                        .putString("_id", CLIENT_ID)
                                        .putString("name", "Walrus Corporation")
                                        .putString("redirectUri", REDIRECT_URI)
                                        .putString("cancelUri", CANCEL_URI));

                        Observable.merge(perm1, perm2, user1, app1).subscribe(
                                new Action1<RxMessage<JsonObject>>() {

                                    @Override
                                    public void call(RxMessage<JsonObject> t1) {
                                        container.logger().info(
                                                "On next :"
                                                        + t1.body().encode());
                                    }
                                }, new Action1<Throwable>() {

                                    @Override
                                    public void call(Throwable t) {
                                        container
                                                .logger()
                                                .error("Error during data creation",
                                                        t);
                                        VertxAssert
                                                .fail("Error during data creation");
                                    }
                                }, new Action0() {

                                    @Override
                                    public void call() {
                                        container.logger().info("Done");
                                        postCreate.handle(null);
                                    }
                                });

                    }

                    private Observable<RxMessage<JsonObject>> saveObservable(
                            RxEventBus rxEventBus, String collection,
                            JsonObject document) {
                        return rxEventBus.send(
                                HttpServer.MONGO_WORKER_VERTICLE,
                                new JsonObject().putString("action", "save")
                                        .putString("collection", collection)
                                        .putObject("document", document));
                    }
                });
    }
}
