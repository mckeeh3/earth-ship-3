package io.earthship3.application.order;

import static akka.Done.done;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.earthship3.domain.order.OrderItemsBranch;

@ComponentId("order-items-branch-entity")
public class OrderItemsBranchEntity extends EventSourcedEntity<OrderItemsBranch.State, OrderItemsBranch.Event> {
  private final Logger log = LoggerFactory.getLogger(OrderItemsBranchEntity.class);
  private final String entityId;

  public OrderItemsBranchEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public OrderItemsBranch.State emptyState() {
    return OrderItemsBranch.State.empty();
  }

  public Effect<Done> addQuantity(OrderItemsBranch.Command.AddQuantityToTree command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command))
        .thenReply(newState -> done());
  }

  public Effect<Done> updateBranchQuantity(OrderItemsBranch.Command.UpdateBranchQuantity command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command))
        .thenReply(newState -> done());
  }

  public Effect<Done> updateLeafQuantity(OrderItemsBranch.Command.UpdateLeafQuantity command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command))
        .thenReply(newState -> done());
  }

  public ReadOnlyEffect<OrderItemsBranch.State> get() {
    return effects().reply(currentState());
  }

  @Override
  public OrderItemsBranch.State applyEvent(OrderItemsBranch.Event event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);

    return switch (event) {
      case OrderItemsBranch.Event.OrderItemsCreated e -> currentState().onEvent(e);
      case OrderItemsBranch.Event.DelegateToSubBranch e -> currentState().onEvent(e);
      case OrderItemsBranch.Event.BranchToBeAdded e -> currentState().onEvent(e);
      case OrderItemsBranch.Event.LeafToBeAdded e -> currentState().onEvent(e);
      case OrderItemsBranch.Event.BranchQuantityUpdated e -> currentState().onEvent(e);
      case OrderItemsBranch.Event.LeafQuantityUpdated e -> currentState().onEvent(e);
    };
  }
}
