package io.earthship3.application.order;

import static akka.Done.done;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.earthship3.domain.order.Order;

@ComponentId("order")
public class OrderEntity extends EventSourcedEntity<Order.State, Order.Event> {
  private final Logger log = LoggerFactory.getLogger(OrderEntity.class);
  private final String entityId;

  public OrderEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public Order.State emptyState() {
    return Order.State.empty();
  }

  public Effect<Done> createOrder(Order.Command.CreateOrder command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command))
        .thenReply(newState -> done());
  }

  public Effect<Done> orderItemReadyToShip(Order.Command.OrderItemReadyToShip command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command))
        .thenReply(newState -> done());
  }

  public Effect<Done> orderItemBackOrdered(Order.Command.OrderItemBackOrdered command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command))
        .thenReply(newState -> done());
  }

  public Effect<Done> cancelOrder(Order.Command.CancelOrder command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command))
        .thenReply(newState -> done());
  }

  public ReadOnlyEffect<Order.State> get() {
    return effects().reply(currentState());
  }

  @Override
  public Order.State applyEvent(Order.Event event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);

    return switch (event) {
      case Order.Event.OrderCreated e -> currentState().onEvent(e);
      case Order.Event.OrderItemCreated e -> currentState().onEvent(e);
      case Order.Event.OrderItemReadyToShip e -> currentState().onEvent(e);
      case Order.Event.OrderItemBackOrdered e -> currentState().onEvent(e);
      case Order.Event.OrderReadyToShip e -> currentState().onEvent(e);
      case Order.Event.OrderBackOrdered e -> currentState().onEvent(e);
      case Order.Event.OrderCancelled e -> currentState().onEvent(e);
      case Order.Event.OrderItemCancelled e -> currentState().onEvent(e);
    };
  }
}
