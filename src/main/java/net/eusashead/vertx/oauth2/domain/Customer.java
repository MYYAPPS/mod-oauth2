package net.eusashead.vertx.oauth2.domain;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Document
public class Customer {

    @Id
    private final ObjectId customerId;

    @Field
    private final String name;

    public Customer(ObjectId customerId, String name) {
        this.customerId = customerId;
        this.name = name;
    }

    public ObjectId getCustomerId() {
        return customerId;
    }

    public String getName() {
        return name;
    }
}