package com.mycompany.myproject;

import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Query;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Verticle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fakemongo.Fongo;
import com.mongodb.Mongo;

public class HelloServer extends Verticle {

    @Override
    public void start() {

        // Get the event bus
        final EventBus eventBus = this.vertx.eventBus();

        // Create Fongo
        final Mongo mongo = new Fongo("test-db").getMongo();
        final MongoTemplate mongoTemplate = new MongoTemplate(mongo, "test-db");

        // Create test data
        final Customer p1 = new Customer(new ObjectId(), "Patrick");
        mongoTemplate.insert(p1);
        final Customer p2 = new Customer(new ObjectId(), "Fred");
        mongoTemplate.insert(p2);

        // Create a Mongo handler for querying
        eventBus.registerHandler("customer",
                new Handler<Message<JsonObject>>() {

                    private final ObjectMapper objectMapper = new ObjectMapper();

                    @Override
                    public void handle(final Message<JsonObject> event) {
                        final Query query = new BasicQuery(event.body()
                                .encode()).with(new PageRequest(0, 10));
                        final List<Customer> ps = mongoTemplate.find(query,
                                Customer.class);

                        try {
                            final byte[] bytes = objectMapper
                                    .writeValueAsBytes(ps);
                            final Buffer buffer = new Buffer(bytes);
                            event.reply(buffer);
                        } catch (final JsonProcessingException e) {
                            container.logger().error(e.getMessage(), e);
                            event.fail(123, e.getMessage());
                        }

                    }

                });

        // Create RouteMatcher and resource
        final RouteMatcher routeMatcher = new RouteMatcher();
        routeMatcher.get("/resource", new Handler<HttpServerRequest>() {

            @Override
            public void handle(final HttpServerRequest event) {

                // Use chunked transfer
                event.response().setChunked(true);

                eventBus.send("customer",
                        new JsonObject().putString("name", "Patrick"),
                        new Handler<Message<Buffer>>() {

                            @Override
                            public void handle(final Message<Buffer> message) {
                                event.response().end(message.body());

                            }

                        });

            }
        });

        // Create the HttpServer
        this.vertx.createHttpServer().setCompressionSupported(true)
                .requestHandler(routeMatcher).listen(8080);

    }
}