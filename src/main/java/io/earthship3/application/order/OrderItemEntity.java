package io.earthship3.application.order;

import static akka.Done.done;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.earthship3.domain.order.OrderItem;

@ComponentId("order-item-entity")
public class OrderItemEntity extends EventSourcedEntity<OrderItem.State, OrderItem.Event> {
  private final Logger log = LoggerFactory.getLogger(OrderItemEntity.class);
  private final String entityId;

  public OrderItemEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public OrderItem.State emptyState() {
    return OrderItem.State.empty();
  }

  public Effect<Done> createOrderItem(OrderItem.Command.CreateOrderItem command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command))
        .thenReply(newState -> done());
  }

  public ReadOnlyEffect<OrderItem.State> get() {
    return effects().reply(currentState());
  }

  @Override
  public OrderItem.State applyEvent(OrderItem.Event event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);

    return switch (event) {
      case OrderItem.Event.OrderItemCreated e -> currentState().onEvent(e);
      case OrderItem.Event.OrderItemBranchToBeCreated e -> currentState().onEvent(e);
      case OrderItem.Event.OrderStockItemsToBeCreated e -> currentState().onEvent(e);
    };
  }
}
