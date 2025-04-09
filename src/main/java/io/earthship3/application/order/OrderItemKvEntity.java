package io.earthship3.application.order;

import static akka.Done.done;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import akka.javasdk.keyvalueentity.KeyValueEntityContext;
import io.earthship3.domain.order.OrderItemKv;

@ComponentId("order-item-kv-entity")
public class OrderItemKvEntity extends KeyValueEntity<OrderItemKv.State> {
  private final Logger log = LoggerFactory.getLogger(OrderItemKvEntity.class);
  private final String entityId;

  public OrderItemKvEntity(KeyValueEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public OrderItemKv.State emptyState() {
    return OrderItemKv.State.empty();
  }

  public Effect<Done> onCommand(OrderItemKv.Command.CreateOrderItemKv command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .updateState(currentState().onCommand(command))
        .thenReply(done());
  }

  public Effect<Done> onCommand(OrderItemKv.Command.ChangeQuantity command) {
    log.info("EntityId: {} _State: {} _Command: {}", entityId, currentState(), command);

    return effects()
        .updateState(currentState().onCommand(command))
        .thenReply(done());
  }

  public Effect<Done> onCommand(OrderItemKv.Command.ChangePrice command) {
    log.info("EntityId: {} _State: {} _Command: {}", entityId, currentState(), command);

    return effects()
        .updateState(currentState().onCommand(command))
        .thenReply(done());
  }

  public Effect<Done> onCommand(OrderItemKv.Command.CancelOrderItemKv command) {
    log.info("EntityId: {} _State: {} _Command: {}", entityId, currentState(), command);

    return effects()
        // .deleteEntity() // it will still exist with an empty state for default one week
        .updateState(currentState().onCommand(command))
        .thenReply(done());
  }

  public ReadOnlyEffect<OrderItemKv.State> get() {
    if (isDeleted() || currentState().isEmpty()) {
      return effects().error("Order item not found, orderItem: %s".formatted(entityId));
    }
    return effects().reply(currentState());
  }
}
