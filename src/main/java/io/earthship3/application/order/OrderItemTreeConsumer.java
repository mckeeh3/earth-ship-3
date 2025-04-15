package io.earthship3.application.order;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import io.earthship3.domain.order.OrderItemBranch;
import io.earthship3.domain.order.OrderItemsLeaf;

@ComponentId("order-item-tree-consumer")
@Consume.FromEventSourcedEntity(OrderItemBranchEntity.class)
public class OrderItemTreeConsumer extends Consumer {
  private final Logger log = LoggerFactory.getLogger(OrderItemTreeConsumer.class);
  private final ComponentClient componentClient;

  public OrderItemTreeConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onEvent(OrderItemBranch.Event event) {
    return switch (event) {
      case OrderItemBranch.Event.BranchToBeCreated e -> onEvent(e);
      case OrderItemBranch.Event.LeafToBeCreated e -> onEvent(e);
      default -> effects().ignore();
    };
  }

  private Effect onEvent(OrderItemBranch.Event.BranchToBeCreated event) {
    log.info("Event: {}", event);

    var command = new OrderItemBranch.Command.CreateBranch(
        event.branchId(),
        event.parentBranchId(),
        event.orderId(),
        event.stockId(),
        event.stockName(),
        event.price(),
        event.quantity());
    var done = componentClient.forEventSourcedEntity(event.branchId())
        .method(OrderItemBranchEntity::createBranch)
        .invokeAsync(command);

    return effects().asyncDone(done);
  }

  private Effect onEvent(OrderItemBranch.Event.LeafToBeCreated event) {
    log.info("Event: {}", event);

    var command = new OrderItemsLeaf.Command.CreateOrderItems(
        event.branchId(),
        event.parentBranchId(),
        event.orderId(),
        event.stockId(),
        event.quantity());
    var done = componentClient.forEventSourcedEntity(event.branchId())
        .method(OrderItemsLeafEntity::createLeaf)
        .invokeAsync(command);

    return effects().asyncDone(done);
  }
}
