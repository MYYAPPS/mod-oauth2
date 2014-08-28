package net.eusashead.vertx.oauth2.domain;

import java.math.BigDecimal;
import java.util.Date;

public class Order {

    private final Identity orderId;
    private final Identity customerId;
    private final Date date;
    private final BigDecimal total;

    public Order(Identity orderId, Identity customerId, Date date,
            BigDecimal total) {
        super();
        this.orderId = orderId;
        this.customerId = customerId;
        this.date = date;
        this.total = total;
    }

    public Identity getOrderId() {
        return orderId;
    }

    public Identity getCustomerId() {
        return customerId;
    }

    public Date getDate() {
        return date;
    }

    public BigDecimal getTotal() {
        return total;
    }

}