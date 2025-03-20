package io.earthship3.application.order;

import static akka.Done.done;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.earthship3.domain.order.OrderStockItems;

@ComponentId("order-stock-items-entity")
public class OrderStockItemsEntity extends EventSourcedEntity<OrderStockItems.State, OrderStockItems.Event> {
  private final Logger log = LoggerFactory.getLogger(OrderStockItemsEntity.class);
  private final String entityId;

  public OrderStockItemsEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public OrderStockItems.State emptyState() {
    return OrderStockItems.State.empty();
  }

  public Effect<Done> createOrderStockItems(OrderStockItems.Command.CreateOrderStockItems command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command))
        .thenReply(newState -> done());
  }

  public ReadOnlyEffect<OrderStockItems.State> get() {
    return effects().reply(currentState());
  }

  @Override
  public OrderStockItems.State applyEvent(OrderStockItems.Event event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);

    return switch (event) {
      case OrderStockItems.Event.OrderStockItemsCreated e -> currentState().onEvent(e);
      case OrderStockItems.Event.OrderStockItemsNeedingStock e -> currentState().onEvent(e);
    };
  }
}
