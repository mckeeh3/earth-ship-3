package io.earthship3.application.map;

import static akka.Done.done;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import akka.javasdk.eventsourcedentity.EventSourcedEntityContext;
import io.earthship3.domain.map.GeoOrderGenerator;
import io.earthship3.domain.map.GeoOrderGenerator.Event;
import io.earthship3.domain.map.GeoOrderGenerator.State;

@ComponentId("geo-order-generator")
public class GeoOrderGeneratorEntity extends EventSourcedEntity<GeoOrderGenerator.State, GeoOrderGenerator.Event> {
  private final Logger log = LoggerFactory.getLogger(GeoOrderGeneratorEntity.class);
  private final String entityId;

  public GeoOrderGeneratorEntity(EventSourcedEntityContext context) {
    this.entityId = context.entityId();
  }

  @Override
  public State emptyState() {
    return GeoOrderGenerator.State.empty();
  }

  public Effect<Done> createGenerator(GeoOrderGenerator.Command.CreateGeoOrderGenerator command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command))
        .thenReply(newState -> done());
  }

  public Effect<Done> generateGeoOrders(GeoOrderGenerator.Command.GenerateGeoOrders command) {
    log.info("EntityId: {}\n_State: {}\n_Command: {}", entityId, currentState(), command);

    return effects()
        .persistAll(currentState().onCommand(command))
        .thenReply(newState -> done());
  }

  public Effect<State> get() {
    return effects().reply(currentState());
  }

  @Override
  public State applyEvent(Event event) {
    log.info("EntityId: {}\n_State: {}\n_Event: {}", entityId, currentState(), event);

    return switch (event) {
      case GeoOrderGenerator.Event.GeoOrderGeneratorCreated e -> currentState().onEvent(e);
      case GeoOrderGenerator.Event.GeoOrdersToBeGenerated e -> currentState().onEvent(e);
      default -> currentState();
    };
  }
}
