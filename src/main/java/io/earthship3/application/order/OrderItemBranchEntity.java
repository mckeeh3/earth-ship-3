package io.earthship3.application.order;

import static akka.Done.done;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.earthship3.domain.order.OrderItemBranch;

@ComponentId("order-item-branch-entity")
public class OrderItemBranchEntity extends EventSourcedEntity<OrderItemBranch.State, OrderItemBranch.Event> {
  private final Logger log = LoggerFactory.getLogger(OrderItemBranchEntity.class);
  private final String entityId;

  public OrderItemBranchEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public OrderItemBranch.State emptyState() {
    return OrderItemBranch.State.empty();
  }

  public Effect<Done> createBranch(OrderItemBranch.Command.CreateBranch command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command))
        .thenReply(newState -> done());
  }

  public ReadOnlyEffect<OrderItemBranch.State> get() {
    return effects().reply(currentState());
  }

  @Override
  public OrderItemBranch.State applyEvent(OrderItemBranch.Event event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);

    return switch (event) {
      case OrderItemBranch.Event.BranchCreated e -> currentState().onEvent(e);
      case OrderItemBranch.Event.BranchToBeCreated e -> currentState().onEvent(e);
      case OrderItemBranch.Event.LeafToBeCreated e -> currentState().onEvent(e);
    };
  }
}
