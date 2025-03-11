package io.earthship3.application.map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import io.earthship3.domain.map.GeoOrder;
import io.earthship3.domain.map.GeoOrderGenerator;

@ComponentId("geo-order-generator-consumer")
@Consume.FromEventSourcedEntity(GeoOrderGeneratorEntity.class)
public class GeoOrderGeneratorConsumer extends Consumer {
  private final Logger log = LoggerFactory.getLogger(GeoOrderGeneratorConsumer.class);
  private final ComponentClient componentClient;

  public GeoOrderGeneratorConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onEvent(GeoOrderGenerator.Event event) {
    return switch (event) {
      case GeoOrderGenerator.Event.GeoOrdersToBeGenerated e -> onEvent(e);
      case GeoOrderGenerator.Event.GeneratorCycleCompleted e -> onEvent(e);
      default -> effects().ignore();
    };
  }

  private Effect onEvent(GeoOrderGenerator.Event.GeoOrdersToBeGenerated event) {
    log.info("Event: {}", event);

    var command = new GeoOrder.Command.CreateGeoOrders(event.geoOrderId(), event.position(), event.radiusKm(), event.geoOrdersToBeGenerated());
    var done = componentClient.forEventSourcedEntity(event.geoOrderId())
        .method(GeoOrderEntity::createGeoOrders)
        .invokeAsync(command);

    return effects().asyncDone(done);
  }

  private Effect onEvent(GeoOrderGenerator.Event.GeneratorCycleCompleted event) {
    log.info("Event: {}", event);

    var command = new GeoOrderGenerator.Command.GenerateGeoOrders(event.generatorId());
    var done = componentClient.forEventSourcedEntity(event.generatorId())
        .method(GeoOrderGeneratorEntity::generateGeoOrders)
        .invokeAsync(command);

    return effects().asyncDone(done);
  }
}
