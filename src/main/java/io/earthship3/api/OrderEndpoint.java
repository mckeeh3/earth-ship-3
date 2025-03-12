package io.earthship3.api;

import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.client.ComponentClient;
import io.earthship3.application.order.OrderEntity;
import io.earthship3.application.order.OrderView;
import io.earthship3.application.order.OrderView.Orders;
import io.earthship3.domain.order.Order;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/order")
public class OrderEndpoint {
  private final Logger log = LoggerFactory.getLogger(OrderEndpoint.class);
  private final ComponentClient componentClient;

  public OrderEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Get("/{orderId}")
  public CompletionStage<Order.State> order(String orderId) {
    log.info("GET {}", orderId);

    return componentClient.forEventSourcedEntity(orderId)
        .method(OrderEntity::get)
        .invokeAsync();
  }

  @Get("/find-by-customer-id/{customerId}")
  public CompletionStage<Orders> findByCustomerId(String customerId) {
    log.info("GET {}", customerId);

    return componentClient.forView()
        .method(OrderView::findByCustomerId)
        .invokeAsync(customerId);
  }
}
