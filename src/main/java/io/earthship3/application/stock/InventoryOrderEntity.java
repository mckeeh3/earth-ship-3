package io.earthship3.application.stock;

import static akka.Done.done;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.earthship3.domain.stock.InventoryOrder;
import io.earthship3.domain.stock.InventoryOrder.State;

@ComponentId("inventory-order-entity")
public class InventoryOrderEntity extends EventSourcedEntity<InventoryOrder.State, InventoryOrder.Event> {
  private final Logger log = LoggerFactory.getLogger(InventoryOrderEntity.class);
  private final String entityId;

  public InventoryOrderEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return State.empty();
  }

  public Effect<Done> create(InventoryOrder.Command.CreateInventoryOrder command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command).stream().toList())
        .thenReply(newState -> done());
  }

  public ReadOnlyEffect<State> get() {
    if (currentState().isEmpty()) {
      return effects().error("Inventory order is empty, inventoryOrderId: %s".formatted(entityId));
    }

    return effects().reply(currentState());
  }

  @Override
  public State applyEvent(InventoryOrder.Event event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);

    return switch (event) {
      case InventoryOrder.Event.InventoryOrderCreated e -> currentState().onEvent(e);
    };
  }
}
