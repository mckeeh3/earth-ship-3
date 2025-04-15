package io.earthship3.application.stock;

import static akka.Done.done;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.earthship3.domain.stock.StockItemsLeaf;

@ComponentId("stock-item-leaf-entity")
public class StockItemsLeafEntity extends EventSourcedEntity<StockItemsLeaf.State, StockItemsLeaf.Event> {
  private final Logger log = LoggerFactory.getLogger(StockItemsLeafEntity.class);
  private final String entityId;

  public StockItemsLeafEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public StockItemsLeaf.State emptyState() {
    return StockItemsLeaf.State.empty();
  }

  public Effect<Done> createLeaf(StockItemsLeaf.Command.CreateStockItems command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command))
        .thenReply(newState -> done());
  }

  public Effect<Done> requestAllocation(StockItemsLeaf.Command.AllocateStockItemsToOrderItems command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command))
        .thenReply(newState -> done());
  }

  public Effect<Done> setAvailableForOrders(StockItemsLeaf.Command.SetAvailableForOrders command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command))
        .thenReply(newState -> done());
  }

  public Effect<Done> releaseAllocation(StockItemsLeaf.Command.ReleaseOrderItemsAllocation command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command))
        .thenReply(newState -> done());
  }

  public ReadOnlyEffect<StockItemsLeaf.State> get() {
    return effects().reply(currentState());
  }

  @Override
  public StockItemsLeaf.State applyEvent(StockItemsLeaf.Event event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);

    return switch (event) {
      case StockItemsLeaf.Event.StockItemsCreated e -> currentState().onEvent(e);
      case StockItemsLeaf.Event.LeafQuantityUpdated e -> currentState().onEvent(e);
      case StockItemsLeaf.Event.StockItemsNeedOrderItems e -> currentState();
      case StockItemsLeaf.Event.StockItemsAllocatedToOrderItems e -> currentState();
      case StockItemsLeaf.Event.StockItemsAllocationConflictDetected e -> currentState();
      case StockItemsLeaf.Event.AvailableForOrdersSet e -> currentState().onEvent(e);
    };
  }
}
