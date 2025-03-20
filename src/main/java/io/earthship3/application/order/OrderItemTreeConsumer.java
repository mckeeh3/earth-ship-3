package io.earthship3.application.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import io.earthship3.domain.order.OrderItem;
import io.earthship3.domain.order.OrderStockItems;

@ComponentId("order-item-tree-consumer")
@Consume.FromEventSourcedEntity(OrderItemEntity.class)
public class OrderItemTreeConsumer extends Consumer {
  private final Logger log = LoggerFactory.getLogger(OrderItemTreeConsumer.class);
  private final ComponentClient componentClient;

  public OrderItemTreeConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onEvent(OrderItem.Event event) {
    return switch (event) {
      case OrderItem.Event.OrderItemBranchToBeCreated e -> onEvent(e);
      case OrderItem.Event.OrderStockItemsToBeCreated e -> onEvent(e);
      default -> effects().ignore();
    };
  }

  private Effect onEvent(OrderItem.Event.OrderItemBranchToBeCreated event) {
    log.info("Event: {}", event);

    var command = new OrderItem.Command.CreateOrderItem(
        event.orderItemId(),
        event.parentOrderItemId(),
        event.orderId(),
        event.stockId(),
        event.stockName(),
        event.price(),
        event.quantity());
    var done = componentClient.forEventSourcedEntity(event.orderItemId())
        .method(OrderItemEntity::createOrderItem)
        .invokeAsync(command);

    return effects().asyncDone(done);
  }

  private Effect onEvent(OrderItem.Event.OrderStockItemsToBeCreated event) {
    log.info("Event: {}", event);

    var command = new OrderStockItems.Command.CreateOrderStockItems(
        event.orderStockItemId(),
        event.parentOrderItemId(),
        event.orderId(),
        event.stockId(),
        event.quantity());
    var done = componentClient.forEventSourcedEntity(event.orderStockItemId())
        .method(OrderStockItemsEntity::createOrderStockItems)
        .invokeAsync(command);

    return effects().asyncDone(done);
  }
}
