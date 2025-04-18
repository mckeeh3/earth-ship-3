package io.earthship3.application.order;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import io.earthship3.domain.order.Order;
import io.earthship3.domain.order.OrderItemsBranch;
import io.earthship3.domain.order.OrderItemsBranch.Quantity;

@ComponentId("order-to-order-items-branch-consumer")
@Consume.FromEventSourcedEntity(OrderEntity.class)
public class OrderToOrderItemsBranchConsumer extends Consumer {
  private final Logger log = LoggerFactory.getLogger(OrderToOrderItemsBranchConsumer.class);
  private final ComponentClient componentClient;

  public OrderToOrderItemsBranchConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onEvent(Order.Event event) {
    return switch (event) {
      case Order.Event.OrderItemCreated e -> onEvent(e);
      default -> effects().ignore();
    };
  }

  private Effect onEvent(Order.Event.OrderItemCreated event) {
    log.info("Event: {}", event);

    var parentOrderItemId = Optional.<String>empty();
    var command = new OrderItemsBranch.Command.AddQuantityToTree(
        event.stockId(), // this is the tree trunk branch ID
        event.stockId(),
        event.orderId(), // this is the branch quantity ID
        Quantity.of(event.lineItem().quantity()),
        parentOrderItemId);

    componentClient.forEventSourcedEntity(event.stockId())
        .method(OrderItemsBranchEntity::addQuantity)
        .invoke(command);

    return effects().done();
  }
}