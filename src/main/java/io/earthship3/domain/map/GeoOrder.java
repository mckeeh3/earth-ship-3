package io.earthship3.domain.map;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Stream;

import io.earthship3.domain.order.Order;

/**
 * Represents a geographical order entity that combines order information with geographic location. This interface
 * provides functionality to: - Create individual geo-located orders with specific positions - Generate multiple orders
 * within a specified radius around a central point - Handle the state management of geo-orders through command and
 * event patterns
 *
 * The implementation uses the Command-Event pattern where: - Commands (CreateGeoOrder, CreateGeoOrders) trigger state
 * changes - Events (GeoOrderCreated, GeoOrderToBeGenerated, GeoOrdersCreated) record what happened - State maintains
 * the current status of a geo-order including its position and order details
 *
 * For bulk order generation within a circular region: - Orders are randomly distributed using a uniform distribution
 * within the specified radius - The algorithm uses polar coordinates and the haversine formula to properly distribute
 * points on the Earth's surface - A maximum of maxOrdersPerCreateRequest orders can be created in a single request to
 * throttle system load - Each generated order includes random line items (1-5 items) with quantities between 1-5 -
 * Product IDs are generated in the format P0001 to P0030
 */
public interface GeoOrder {
  public record State(
      LatLng position,
      Order.State order) {

    static final Random random = new Random();

    public static State empty() {
      return new State(null, null);
    }

    public boolean isEmpty() {
      return order == null;
    }

    public Optional<Event> onCommand(Command.CreateGeoOrder command) {
      if (!isEmpty()) {
        return Optional.empty();
      }

      return Optional.of(new Event.GeoOrderCreated(command.order(), command.position()));
    }

    public List<Event> onCommand(Command.CreateGeoOrders command) {
      if (!isEmpty() || command.geoOrdersToBeCreated() <= 0) {
        return List.of();
      }

      var order = createOrder(command.orderId());
      var geoOrderPosition = geoOrderPosition(command.generatorPosition(), command.generatorRadiusKm());
      var event = new Event.GeoOrderCreated(order, geoOrderPosition);

      var count = command.geoOrdersToBeCreated() - 1;
      var events = count > 1
          ? List.<Event>of(
              event,
              new Event.GeoOrdersToBeCreated(command.orderId(), command.generatorPosition(), command.generatorRadiusKm(), count / 2),
              new Event.GeoOrdersToBeCreated(command.orderId(), command.generatorPosition(), command.generatorRadiusKm(), count - count / 2))
          : count > 0
              ? List.<Event>of(
                  event,
                  new Event.GeoOrdersToBeCreated(command.orderId(), command.generatorPosition(), command.generatorRadiusKm(), count))
              : List.<Event>of(event);

      return events;
    }

    public State onEvent(Event.GeoOrderCreated event) {
      return new State(
          event.position(),
          event.order());
    }

    public State onEvent(Event.GeoOrdersToBeCreated event) {
      return this;
    }

    static LatLng geoOrderPosition(LatLng position, double radiusKm) {
      final var angle = random.nextDouble() * 2 * Math.PI;
      final var distance = radiusKm * Math.sqrt(random.nextDouble());
      final var lat = Math.toRadians(position.lat());
      final var lng = Math.toRadians(position.lng());
      final var earthRadiusKm = 6371.0;
      final var distanceRatio = distance / earthRadiusKm;
      final var lat2 = Math.asin(Math.sin(lat) * Math.cos(distanceRatio) + Math.cos(lat) * Math.sin(distanceRatio) * Math.cos(angle));
      final var lng2 = lng + Math.atan2(Math.sin(angle) * Math.sin(distanceRatio) * Math.cos(lat), Math.cos(distanceRatio) - Math.sin(lat) * Math.sin(lat2));

      return new LatLng(Math.toDegrees(lat2), Math.toDegrees(lng2));
    }

    static Order.State createOrder(String orderId) {
      var customerId = java.util.UUID.randomUUID().toString();
      var lineItems = Stream.generate(() -> {
        var productId = String.format("P%04d", random.nextInt(1, 31));
        var quantity = random.nextInt(1, 6);
        return new Order.LineItem(productId, "TBD", BigDecimal.ONE, quantity, Optional.empty(), Optional.empty());
      })
          .limit(random.nextInt(1, 6))
          .toList();

      return new Order.State(orderId, customerId, lineItems, BigDecimal.ZERO, Optional.empty(), Optional.empty(), Optional.empty());
    }
  }

  public sealed interface Command {
    record CreateGeoOrder(Order.State order, LatLng position) implements Command {}

    record CreateGeoOrders(String orderId, LatLng generatorPosition, double generatorRadiusKm, int geoOrdersToBeCreated) implements Command {}
  }

  public sealed interface Event {
    record GeoOrderCreated(Order.State order, LatLng position) implements Event {}

    record GeoOrdersToBeCreated(String orderId, LatLng generatorPosition, double generatorRadiusKm, int geoOrdersToBeCreated) implements Event {}
  }
}
