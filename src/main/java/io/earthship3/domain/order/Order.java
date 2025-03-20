package io.earthship3.domain.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public interface Order {

  public record LineItem(
      String stockId,
      String stockName,
      BigDecimal price,
      int quantity,
      Optional<Instant> readyToShipAt,
      Optional<Instant> backOrderedAt) {

    public LineItem withReadyToShipAt() {
      return new LineItem(stockId, stockName, price, quantity, Optional.of(Instant.now()), Optional.empty());
    }

    public LineItem withBackOrderedAt() {
      return new LineItem(stockId, stockName, price, quantity, Optional.empty(), Optional.of(Instant.now()));
    }
  }

  public record State(
      String orderId,
      String customerId,
      List<LineItem> lineItems,
      BigDecimal totalPrice,
      Instant orderedAt,
      Optional<Instant> readyToShipAt,
      Optional<Instant> backOrderedAt,
      Optional<Instant> cancelledAt) {

    public static State empty() {
      return new State(null, null, List.of(), BigDecimal.ZERO, Instant.EPOCH, Optional.empty(), Optional.empty(), Optional.empty());
    }

    public boolean isEmpty() {
      return orderId == null;
    }

    public List<Event> onCommand(Command.CreateOrder command) {
      if (!lineItems.isEmpty()) {
        return List.of();
      }

      var totalPrice = command.lineItems().stream()
          .map(item -> item.price().multiply(BigDecimal.valueOf(item.quantity())))
          .reduce(BigDecimal.ZERO, BigDecimal::add);

      var event = new Event.OrderCreated(command.orderId(), command.customerId(), command.orderedAt(), List.copyOf(command.lineItems()), totalPrice);
      var events = command.lineItems().stream()
          .map(item -> new Event.OrderItemCreated(command.orderId(), item.stockId(), item))
          .toList();

      return Stream.concat(Stream.of((Event) event), events.stream()).toList();
    }

    public List<Event> onCommand(Command.OrderItemReadyToShip command) {
      if (lineItems.stream().noneMatch(item -> item.stockId().equals(command.stockId()))) {
        return List.of();
      }

      var newLineItems = lineItems.stream()
          .map(item -> item.stockId().equals(command.stockId()) ? item.withReadyToShipAt() : item)
          .toList();
      var orderItemReadyToShip = new Event.OrderItemReadyToShip(command.orderId(), command.stockId(), newLineItems);

      return isOrderReadyToShip(newLineItems)
          ? List.of(orderItemReadyToShip, new Event.OrderReadyToShip(orderId, Optional.of(Instant.now())))
          : List.of(orderItemReadyToShip);
    }

    public List<Event> onCommand(Command.OrderItemBackOrdered command) {
      if (lineItems.stream().noneMatch(item -> item.stockId().equals(command.stockId()))) {
        return List.of();
      }

      var newLineItems = lineItems.stream()
          .map(item -> item.stockId().equals(command.stockId()) ? item.withBackOrderedAt() : item)
          .toList();
      var orderItemBackOrdered = new Event.OrderItemBackOrdered(command.orderId(), command.stockId(), newLineItems);

      return isOrderBackOrdered(newLineItems)
          ? List.of(orderItemBackOrdered, new Event.OrderBackOrdered(orderId, Optional.of(Instant.now())))
          : List.of(orderItemBackOrdered);
    }

    public List<Event> onCommand(Command.CancelOrder command) {
      if (cancelledAt.isPresent()) {
        return List.of();
      }

      var event = new Event.OrderCancelled(orderId, Optional.of(Instant.now()));
      var events = lineItems.stream()
          .map(item -> new Event.OrderItemCancelled(orderId, item.stockId(), Optional.of(Instant.now())))
          .toList();

      return Stream.concat(Stream.of((Event) event), events.stream()).toList();
    }

    private boolean isOrderReadyToShip(List<LineItem> lineItems) {
      return lineItems.stream().allMatch(item -> item.readyToShipAt().isPresent());
    }

    private boolean isOrderBackOrdered(List<LineItem> lineItems) {
      return lineItems.stream().anyMatch(item -> item.backOrderedAt().isPresent());
    }

    public State onEvent(Event.OrderCreated event) {
      return new State(event.orderId(),
          event.customerId(),
          List.copyOf(event.lineItems()),
          event.totalPrice(),
          event.orderedAt(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty());
    }

    public State onEvent(Event.OrderItemCreated event) {
      return this;
    }

    public State onEvent(Event.OrderReadyToShip event) {
      return new State(
          orderId,
          customerId,
          lineItems,
          totalPrice,
          orderedAt,
          event.readyToShipAt(),
          Optional.empty(),
          Optional.empty());
    }

    public State onEvent(Event.OrderBackOrdered event) {
      return new State(
          orderId,
          customerId,
          lineItems,
          totalPrice,
          orderedAt,
          Optional.empty(),
          event.backOrderedAt(),
          Optional.empty());
    }

    public State onEvent(Event.OrderItemReadyToShip event) {
      return new State(
          orderId,
          customerId,
          event.lineItems(),
          totalPrice,
          orderedAt,
          readyToShipAt,
          backOrderedAt,
          cancelledAt);
    }

    public State onEvent(Event.OrderItemBackOrdered event) {
      return new State(
          orderId,
          customerId,
          event.lineItems(),
          totalPrice,
          orderedAt,
          readyToShipAt,
          backOrderedAt,
          cancelledAt);
    }

    public State onEvent(Event.OrderCancelled event) {
      return new State(
          orderId,
          customerId,
          lineItems,
          totalPrice,
          orderedAt,
          readyToShipAt,
          backOrderedAt,
          event.cancelledAt());
    }

    public State onEvent(Event.OrderItemCancelled event) {
      return this;
    }
  }

  public sealed interface Command {
    record CreateOrder(String orderId, String customerId, Instant orderedAt, List<LineItem> lineItems) implements Command {}

    record OrderItemReadyToShip(String orderId, String stockId) implements Command {}

    record OrderItemBackOrdered(String orderId, String stockId) implements Command {}

    record CancelOrder(String orderId) implements Command {}
  }

  public sealed interface Event {
    record OrderCreated(String orderId, String customerId, Instant orderedAt, List<LineItem> lineItems, BigDecimal totalPrice) implements Event {}

    record OrderItemCreated(String orderId, String stockId, LineItem lineItem) implements Event {}

    record OrderReadyToShip(String orderId, Optional<Instant> readyToShipAt) implements Event {}

    record OrderBackOrdered(String orderId, Optional<Instant> backOrderedAt) implements Event {}

    record OrderItemReadyToShip(String orderId, String stockId, List<LineItem> lineItems) implements Event {}

    record OrderItemBackOrdered(String orderId, String stockId, List<LineItem> lineItems) implements Event {}

    record OrderCancelled(String orderId, Optional<Instant> cancelledAt) implements Event {}

    record OrderItemCancelled(String orderId, String stockId, Optional<Instant> cancelledAt) implements Event {}
  }
}
