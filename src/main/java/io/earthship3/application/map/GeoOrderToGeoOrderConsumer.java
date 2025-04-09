package io.earthship3.application.map;

import static io.earthship3.ShortUUID.randomUUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import io.earthship3.domain.map.GeoOrder;

@ComponentId("geo-order-to-geo-order-consumer")
@Consume.FromEventSourcedEntity(GeoOrderEntity.class)
public class GeoOrderToGeoOrderConsumer extends Consumer {
  private final Logger log = LoggerFactory.getLogger(GeoOrderToGeoOrderConsumer.class);
  private final ComponentClient componentClient;

  public GeoOrderToGeoOrderConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onEvent(GeoOrder.Event event) {
    return switch (event) {
      case GeoOrder.Event.GeoOrdersToBeCreated e -> onEvent(e);
      default -> effects().ignore();
    };
  }

  private Effect onEvent(GeoOrder.Event.GeoOrdersToBeCreated event) {
    log.info("Event: {}", event);

    var orderId = randomUUID();
    var generatorPosition = event.generatorPosition();
    var generatorRadiusKm = event.generatorRadiusKm();
    var geoOrdersToBeCreated = event.geoOrdersToBeCreated();
    var command = new GeoOrder.Command.CreateGeoOrders(orderId, generatorPosition, generatorRadiusKm, geoOrdersToBeCreated);
    var done = componentClient.forEventSourcedEntity(orderId)
        .method(GeoOrderEntity::createGeoOrders)
        .invokeAsync(command);

    return effects().asyncDone(done);
  }
}
