package io.earthship3.application.order;

import java.util.Optional;
import static io.earthship3.ShortUUID.randomUUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import io.earthship3.domain.order.Order;
import io.earthship3.domain.order.OrderItemBranch;

@ComponentId("order-to-order-item-consumer")
@Consume.FromEventSourcedEntity(OrderEntity.class)
public class OrderToOrderItemConsumer extends Consumer {
  private final Logger log = LoggerFactory.getLogger(OrderToOrderItemConsumer.class);
  private final ComponentClient componentClient;

  public OrderToOrderItemConsumer(ComponentClient componentClient) {
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

    var orderItemId = randomUUID();
    var parentOrderItemId = Optional.<String>empty();
    var command = new OrderItemBranch.Command.CreateBranch(
        orderItemId,
        parentOrderItemId,
        event.orderId(),
        event.stockId(),
        event.lineItem().stockName(),
        event.lineItem().price(),
        event.lineItem().quantity());
    var done = componentClient.forEventSourcedEntity(event.orderId())
        .method(OrderItemBranchEntity::createBranch)
        .invokeAsync(command);

    return effects().asyncDone(done);
  }
}