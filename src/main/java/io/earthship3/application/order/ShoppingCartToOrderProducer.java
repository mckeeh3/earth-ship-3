package io.earthship3.application.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Produce;
import akka.javasdk.consumer.Consumer;
import io.earthship3.domain.order.ShoppingCart;
import io.earthship3.domain.order.ShoppingCartToOrderStream.Event;
import io.earthship3.domain.order.ShoppingCartToOrderStream.LineItem;

@ComponentId("shopping-cart-to-order-producer")
@Consume.FromEventSourcedEntity(ShoppingCartEntity.class)
@Produce.ServiceStream(id = "shopping-cart-to-order-events")
@Acl(allow = @Acl.Matcher(service = "*"))
public class ShoppingCartToOrderProducer extends Consumer {
  private final Logger log = LoggerFactory.getLogger(ShoppingCartToOrderProducer.class);

  public Effect onEvent(ShoppingCart.Event event) {
    return switch (event) {
      case ShoppingCart.Event.CheckedOut e -> onEvent(e);
      default -> effects().ignore();
    };
  }

  private Effect onEvent(ShoppingCart.Event.CheckedOut event) {
    log.info("Event: {}", event);

    var lineItems = event.lineItems().stream()
        .map(item -> new LineItem(item.skuId(), item.skuName(), item.price(), item.quantity()))
        .toList();
    var checkedOut = new Event.CheckedOut(event.orderId(), event.customerId(), lineItems);

    return effects().produce(checkedOut);
  }
}
