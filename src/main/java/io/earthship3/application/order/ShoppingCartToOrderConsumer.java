package io.earthship3.application.order;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import io.earthship3.domain.order.Order;
import io.earthship3.domain.order.ShoppingCart;

@ComponentId("shopping-cart-to-order-consumer")
@Consume.FromEventSourcedEntity(ShoppingCartEntity.class)
public class ShoppingCartToOrderConsumer extends Consumer {
  private final Logger log = LoggerFactory.getLogger(ShoppingCartToOrderConsumer.class);
  private final ComponentClient componentClient;

  public ShoppingCartToOrderConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onEvent(ShoppingCart.Event event) {
    return switch (event) {
      case ShoppingCart.Event.CheckedOut e -> onEvent(e);
      default -> effects().ignore();
    };
  }

  private Effect onEvent(ShoppingCart.Event.CheckedOut event) {
    log.info("Event: {}", event);

    var lineItems = event.lineItems().stream()
        .map(item -> new Order.LineItem(
            item.stockId(),
            item.stockName(),
            item.price(),
            item.quantity(),
            Optional.empty(),
            Optional.empty()))
        .toList();
    var command = new Order.Command.CreateOrder(event.orderId(), event.customerId(), event.checkedOutAt(), lineItems);
    componentClient.forEventSourcedEntity(event.orderId())
        .method(OrderEntity::createOrder)
        .invoke(command);

    return effects().done();
  }
}
