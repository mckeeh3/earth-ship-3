package io.earthship3.application.order;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import io.earthship3.domain.order.OrderItemsBranch;
import io.earthship3.domain.order.OrderItemsLeaf;
import io.earthship3.domain.order.OrderItemsLeaf.Quantity;

@ComponentId("order-items-branch-consumer")
@Consume.FromEventSourcedEntity(OrderItemsBranchEntity.class)
public class OrderItemsBranchConsumer extends Consumer {
  private final Logger log = LoggerFactory.getLogger(OrderItemsBranchConsumer.class);
  private final ComponentClient componentClient;

  public OrderItemsBranchConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onEvent(OrderItemsBranch.Event event) {
    return switch (event) {
      case OrderItemsBranch.Event.BranchToBeAdded e -> onEvent(e);
      case OrderItemsBranch.Event.LeafToBeAdded e -> onEvent(e);
      case OrderItemsBranch.Event.DelegateToSubBranch e -> onEvent(e);
      default -> effects().ignore();
    };
  }

  Effect onEvent(OrderItemsBranch.Event.BranchToBeAdded event) {
    log.info("Event: {}", event);

    var command = new OrderItemsBranch.Command.AddQuantityToTree(
        event.branchId(),
        event.stockId(),
        event.quantityId(),
        event.quantity(),
        Optional.of(event.parentBranchId()));

    componentClient.forEventSourcedEntity(event.branchId())
        .method(OrderItemsBranchEntity::addQuantity)
        .invoke(command);

    return effects().done();
  }

  Effect onEvent(OrderItemsBranch.Event.LeafToBeAdded event) {
    log.info("Event: {}", event);

    var command = new OrderItemsLeaf.Command.CreateOrderItems(
        event.leafId(),
        event.parentBranchId(),
        event.stockId(),
        event.quantityId(),
        Quantity.of(event.quantity().ordered(), event.quantity().unallocated()));

    componentClient.forEventSourcedEntity(event.leafId())
        .method(OrderItemsLeafEntity::createLeaf)
        .invoke(command);

    return effects().done();
  }

  Effect onEvent(OrderItemsBranch.Event.DelegateToSubBranch event) {
    log.info("Event: {}", event);

    var command = new OrderItemsBranch.Command.AddQuantityToTree(
        event.subBranchId(),
        event.stockId(),
        event.quantityId(),
        event.quantity(),
        Optional.of(event.branchId()));

    componentClient.forEventSourcedEntity(event.subBranchId())
        .method(OrderItemsBranchEntity::addQuantity)
        .invoke(command);

    return effects().done();
  }
}
