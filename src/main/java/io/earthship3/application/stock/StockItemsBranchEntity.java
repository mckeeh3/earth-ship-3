package io.earthship3.application.stock;

import static akka.Done.done;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.earthship3.domain.stock.StockItemsBranch;

@ComponentId("stock-items-branch-entity")
public class StockItemsBranchEntity extends EventSourcedEntity<StockItemsBranch.State, StockItemsBranch.Event> {
  private final Logger log = LoggerFactory.getLogger(StockItemsBranchEntity.class);
  private final String entityId;

  public StockItemsBranchEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public StockItemsBranch.State emptyState() {
    return StockItemsBranch.State.empty();
  }

  public Effect<Done> addQuantity(StockItemsBranch.Command.AddQuantityToTree command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command))
        .thenReply(newState -> done());
  }

  public Effect<Done> updateBranchQuantity(StockItemsBranch.Command.UpdateBranchQuantity command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persist(currentState().onCommand(command))
        .thenReply(newState -> done());
  }

  public Effect<Done> updateLeafQuantity(StockItemsBranch.Command.UpdateLeafQuantity command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persist(currentState().onCommand(command))
        .thenReply(newState -> done());
  }

  public ReadOnlyEffect<StockItemsBranch.State> get() {
    return effects().reply(currentState());
  }

  @Override
  public StockItemsBranch.State applyEvent(StockItemsBranch.Event event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);

    return switch (event) {
      case StockItemsBranch.Event.StockItemsCreated e -> currentState().onEvent(e);
      case StockItemsBranch.Event.DelegateToSubBranch e -> currentState().onEvent(e);
      case StockItemsBranch.Event.BranchToBeAdded e -> currentState().onEvent(e);
      case StockItemsBranch.Event.LeafToBeAdded e -> currentState().onEvent(e);
      case StockItemsBranch.Event.BranchQuantityUpdated e -> currentState().onEvent(e);
      case StockItemsBranch.Event.LeafQuantityUpdated e -> currentState().onEvent(e);
    };
  }
}
