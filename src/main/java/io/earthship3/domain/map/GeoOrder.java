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
      String orderId,
      LatLng position,
      Order.State order) {

    public static final int maxOrdersPerCreateRequest = 10;
    static final Random random = new Random();

    public static State empty() {
      return new State(null, null, null);
    }

    public boolean isEmpty() {
      return orderId == null;
    }

    public Optional<Event> onCommand(Command.CreateGeoOrder command) {
      if (!isEmpty()) {
        return Optional.empty();
      }

      return Optional.of(new Event.GeoOrderCreated(command.orderId(), command.orderPosition(), command.order()));
    }

    public List<Event> onCommand(Command.CreateGeoOrders command) {
      if (!isEmpty()) {
        return List.of();
      }

      var geoOrders = createGeoOrders(command.geoOrdersToBeCreated());
      var count = command.geoOrdersToBeCreated() - geoOrders.size();
      var event = count > 0
          ? Optional.of(new Event.GeoOrdersCreated(command.orderId(), command.generatorPosition(), command.radiusKm(), count))
          : Optional.<Event>empty();

      var events = geoOrders.stream()
          .map(order -> order == geoOrders.get(0)
              ? new Order.State(command.orderId(), order.customerId(), order.lineItems(), order.totalPrice(), order.readyToShipAt(), order.backOrderedAt(), order.cancelledAt())
              : order)
          .map(order -> new Event.GeoOrderToBeGenerated(order.orderId(), geoOrderPosition(command.generatorPosition(), command.radiusKm()), order))
          .toList();

      return Stream.concat(event.stream(), events.stream()).toList();
    }

    public State onEvent(Event.GeoOrderCreated event) {
      return new State(
          event.orderId(),
          event.orderPosition(),
          event.order());
    }

    public State onEvent(Event.GeoOrderToBeGenerated event) {
      return this;
    }

    public State onEvent(Event.GeoOrdersCreated event) {
      return this;
    }

    private LatLng geoOrderPosition(LatLng position, double radiusKm) {
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

    private List<Order.State> createGeoOrders(int count) {
      return Stream.generate(this::createOrder)
          .limit(Math.min(count, maxOrdersPerCreateRequest))
          .toList();
    }

    private Order.State createOrder() {
      var orderId = java.util.UUID.randomUUID().toString();
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
    record CreateGeoOrder(String orderId, LatLng orderPosition, Order.State order) implements Command {}

    record CreateGeoOrders(String orderId, LatLng generatorPosition, double radiusKm, int geoOrdersToBeCreated) implements Command {}
  }

  public sealed interface Event {
    record GeoOrderCreated(String orderId, LatLng orderPosition, Order.State order) implements Event {}

    record GeoOrderToBeGenerated(String orderId, LatLng orderPosition, Order.State order) implements Event {}

    record GeoOrdersCreated(String orderId, LatLng generatorPosition, double radiusKm, int geoOrdersToBeCreated) implements Event {}
  }
}