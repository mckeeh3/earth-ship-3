package io.earthship3.domain.map;

import static io.earthship3.ShortUUID.randomUUID;

import java.math.BigDecimal;
import java.time.Instant;
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
 * The implementation uses the Command-Event pattern where:
 * <ul>
 * <li>Commands (CreateGeoOrder, CreateGeoOrders) trigger state changes</li>
 * <li>Events (GeoOrderCreated, GeoOrderToBeGenerated, GeoOrdersCreated) record what happened</li>
 * <li>State maintains the current status of a geo-order including its position and order details</li>
 * </ul>
 *
 * For bulk order generation within a circular region:
 * <ul>
 * <li>Orders are randomly distributed using a uniform distribution within the specified radius</li>
 * <li>The algorithm uses polar coordinates and the haversine formula to properly distribute points on the Earth's
 * surface</li>
 * <li>A maximum of maxOrdersPerCreateRequest orders can be created in a single request to throttle system load</li>
 * <li>Each generated order includes random line items (1-5 items) with quantities between 1-5</li>
 * <li>Product IDs are generated in the format P0001 to P0030</li>
 * </ul>
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

      return count > 1
          ? List.<Event>of(
              event,
              new Event.GeoOrdersToBeCreated(command.orderId(), command.generatorPosition(), command.generatorRadiusKm(), count / 2),
              new Event.GeoOrdersToBeCreated(command.orderId(), command.generatorPosition(), command.generatorRadiusKm(), count - count / 2))
          : count > 0
              ? List.<Event>of(
                  event,
                  new Event.GeoOrdersToBeCreated(command.orderId(), command.generatorPosition(), command.generatorRadiusKm(), count))
              : List.<Event>of(event);
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
      var customerId = randomUUID();
      var lineItems = Stream.generate(() -> {
        var productId = String.format("P%04d", random.nextInt(1, 31));
        var quantity = random.nextInt(1, 6);
        return new Order.LineItem(productId, "TBD", BigDecimal.ONE, quantity, Optional.empty(), Optional.empty());
      })
          .limit(random.nextInt(1, 6))
          .toList();

      return new Order.State(orderId, customerId, lineItems, BigDecimal.ZERO, Instant.EPOCH, Optional.empty(), Optional.empty(), Optional.empty());
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
