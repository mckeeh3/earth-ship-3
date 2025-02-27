package io.earthship3.application.order;

import static akka.Done.done;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.earthship3.Validator;
import io.earthship3.domain.order.ShoppingCart;
import io.earthship3.domain.order.ShoppingCart.Event;
import io.earthship3.domain.order.ShoppingCart.State;

@ComponentId("shopping-cart")
public class ShoppingCartEntity extends EventSourcedEntity<ShoppingCart.State, ShoppingCart.Event> {
  private final Logger log = LoggerFactory.getLogger(ShoppingCartEntity.class);
  private final String entityId;

  public ShoppingCartEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public ShoppingCart.State emptyState() {
    return ShoppingCart.State.empty();
  }

  public Effect<Done> addLineItem(ShoppingCart.Command.AddLineItem command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return Validator
        .isEmpty(command.customerId(), "customerId is required")
        .isEmpty(command.skuId(), "skuId is required")
        .isEmpty(command.skuName(), "skuName is required")
        .isLtEqZero(command.quantity(), "quantity must be greater than 0")
        .onSuccess(() -> effects()
            .persistAll(currentState().onCommand(command).stream().toList())
            .thenReply(newState -> done()))
        .onError(error -> effects().error(error));
  }

  public Effect<Done> updateLineItem(ShoppingCart.Command.UpdateLineItem command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return Validator
        .isEmpty(command.customerId(), "customerId is required")
        .isEmpty(command.skuId(), "skuId is required")
        .isEmpty(command.skuName(), "skuName is required")
        .isLtEqZero(command.price(), "price must be greater than 0.0")
        .isLtEqZero(command.quantity(), "quantity must be greater than 0")
        .onSuccess(() -> effects()
            .persistAll(currentState().onCommand(command).stream().toList())
            .thenReply(newState -> done()))
        .onError(error -> effects().error(error));
  }

  public Effect<Done> removeLineItem(ShoppingCart.Command.RemoveLineItem command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return Validator
        .isEmpty(command.customerId(), "customerId is required")
        .isEmpty(command.skuId(), "skuId is required")
        .onSuccess(() -> effects()
            .persistAll(currentState().onCommand(command).stream().toList())
            .thenReply(newState -> done()))
        .onError(error -> effects().error(error));
  }

  public Effect<Done> checkout(ShoppingCart.Command.Checkout command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return Validator
        .isEmpty(command.customerId(), "customerId is required")
        .onSuccess(() -> effects()
            .persistAll(currentState().onCommand(command).stream().toList())
            .thenReply(newState -> done()))
        .onError(error -> effects().error(error));
  }

  public ReadOnlyEffect<ShoppingCart.State> get() {
    if (currentState().isEmpty()) {
      return effects().error("Shopping cart is empty, customerId: %s".formatted(entityId));
    }

    return effects().reply(currentState());
  }

  @Override
  public State applyEvent(Event event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);

    return switch (event) {
      case ShoppingCart.Event.LineItemAdded e -> currentState().onEvent(e);
      case ShoppingCart.Event.LineItemUpdated e -> currentState().onEvent(e);
      case ShoppingCart.Event.LineItemRemoved e -> currentState().onEvent(e);
      case ShoppingCart.Event.CheckedOut e -> currentState().onEvent(e);
    };
  }
}
