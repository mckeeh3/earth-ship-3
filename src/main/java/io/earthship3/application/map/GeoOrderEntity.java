package io.earthship3.application.map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static akka.Done.done;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.earthship3.domain.map.GeoOrder;
import io.earthship3.domain.map.GeoOrder.State;

@ComponentId("geo-order-entity")
public class GeoOrderEntity extends EventSourcedEntity<GeoOrder.State, GeoOrder.Event> {
  private final Logger log = LoggerFactory.getLogger(GeoOrderEntity.class);
  private final String entityId;

  public GeoOrderEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return GeoOrder.State.empty();
  }

  public Effect<Done> createGeoOrder(GeoOrder.Command.CreateGeoOrder command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command).stream().toList())
        .thenReply(newState -> done());
  }

  public Effect<Done> createGeoOrders(GeoOrder.Command.CreateGeoOrders command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command))
        .thenReply(newState -> done());
  }

  public Effect<State> get() {
    return effects().reply(currentState());
  }

  @Override
  public State applyEvent(GeoOrder.Event event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);

    return switch (event) {
      case GeoOrder.Event.GeoOrderCreated e -> currentState().onEvent(e);
      case GeoOrder.Event.GeoOrdersToBeCreated e -> currentState().onEvent(e);
    };
  }
}