package io.earthship3.application.stock;

import static akka.Done.done;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.earthship3.domain.stock.StockItems;

@ComponentId("stock-items-entity")
public class StockItemsEntity extends EventSourcedEntity<StockItems.State, StockItems.Event> {
  private final Logger log = LoggerFactory.getLogger(StockItemsEntity.class);
  private final String entityId;

  public StockItemsEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public StockItems.State emptyState() {
    return StockItems.State.empty();
  }

  public Effect<Done> addQuantity(StockItems.Command.StockItemsAddQuantity command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command))
        .thenReply(newState -> done());
  }

  public Effect<Done> updateBranchQuantity(StockItems.Command.UpdateBranchQuantity command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persist(currentState().onCommand(command))
        .thenReply(newState -> done());
  }

  public Effect<Done> updateLeafQuantity(StockItems.Command.UpdateLeafQuantity command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persist(currentState().onCommand(command))
        .thenReply(newState -> done());
  }

  public ReadOnlyEffect<StockItems.State> get() {
    return effects().reply(currentState());
  }

  @Override
  public StockItems.State applyEvent(StockItems.Event event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);

    return switch (event) {
      case StockItems.Event.StockItemsCreated e -> currentState().onEvent(e);
      case StockItems.Event.StockItemsBranchToBeAdded e -> currentState().onEvent(e);
      case StockItems.Event.StockItemsLeafToBeAdded e -> currentState().onEvent(e);
      case StockItems.Event.DelegateToSubStockItems e -> currentState().onEvent(e);
      case StockItems.Event.BranchQuantityUpdated e -> currentState().onEvent(e);
      case StockItems.Event.LeafQuantityUpdated e -> currentState().onEvent(e);
    };
  }
}
