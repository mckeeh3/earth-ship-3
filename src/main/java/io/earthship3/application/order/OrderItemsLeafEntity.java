package io.earthship3.application.order;

import static akka.Done.done;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.earthship3.domain.order.OrderItemsLeaf;

@ComponentId("order-items-leaf-entity")
public class OrderItemsLeafEntity extends EventSourcedEntity<OrderItemsLeaf.State, OrderItemsLeaf.Event> {
  private final Logger log = LoggerFactory.getLogger(OrderItemsLeafEntity.class);
  private final String entityId;

  public OrderItemsLeafEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public OrderItemsLeaf.State emptyState() {
    return OrderItemsLeaf.State.empty();
  }

  public Effect<Done> createLeaf(OrderItemsLeaf.Command.CreateOrderItems command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command))
        .thenReply(newState -> done());
  }

  public Effect<Done> requestAllocation(OrderItemsLeaf.Command.AllocateOrderItemsToStockItems command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command))
        .thenReply(newState -> done());
  }

  public Effect<Done> releaseAllocation(OrderItemsLeaf.Command.ReleaseStockItemsAllocation command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command))
        .thenReply(newState -> done());
  }

  public Effect<Done> setToBackOrdered(OrderItemsLeaf.Command.SetBackOrdered command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command))
        .thenReply(newState -> done());
  }

  public ReadOnlyEffect<OrderItemsLeaf.State> get() {
    return effects().reply(currentState());
  }

  @Override
  public OrderItemsLeaf.State applyEvent(OrderItemsLeaf.Event event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);

    return switch (event) {
      case OrderItemsLeaf.Event.OrderItemsCreated e -> currentState().onEvent(e);
      case OrderItemsLeaf.Event.LeafQuantityUpdated e -> currentState().onEvent(e);
      case OrderItemsLeaf.Event.OrderItemsNeedStockItems e -> currentState().onEvent(e);
      case OrderItemsLeaf.Event.OrderItemsAllocatedToStockItems e -> currentState().onEvent(e);
      case OrderItemsLeaf.Event.OrderItemsAllocationConflictDetected e -> currentState().onEvent(e);
      case OrderItemsLeaf.Event.BackOrderedSet e -> currentState().onEvent(e);
    };
  }
}
