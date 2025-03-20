package io.earthship3.domain.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShoppingCart {

  public record LineItem(String stockId, String stockName, BigDecimal price, int quantity) {}

  public record State(
      String customerId,
      List<LineItem> lineItems) {

    public static State empty() {
      return new State(null, List.of());
    }

    public boolean isEmpty() {
      return customerId == null;
    }

    public Optional<Event> onCommand(Command.AddLineItem command) {
      var exists = lineItems.stream()
          .anyMatch(item -> item.stockId().equals(command.stockId()));

      if (exists) {
        return Optional.empty();
      }

      var newItem = new LineItem(
          command.stockId(),
          command.stockName(),
          command.price(),
          command.quantity());

      var updatedItems = new java.util.ArrayList<>(lineItems);
      updatedItems.add(newItem);

      return Optional.of(new Event.LineItemAdded(command.customerId(), newItem, List.copyOf(updatedItems)));
    }

    public Optional<Event> onCommand(Command.UpdateLineItem command) {
      var exists = lineItems.stream()
          .anyMatch(item -> item.stockId().equals(command.stockId()));

      if (!exists) {
        return Optional.empty();
      }

      var updatedLineItem = new LineItem(
          command.stockId(),
          command.stockName(),
          command.price(),
          command.quantity());

      var updatedItems = lineItems.stream()
          .map(item -> item.stockId().equals(command.stockId()) ? updatedLineItem : item)
          .toList();

      return Optional.of(new Event.LineItemUpdated(command.customerId(), updatedLineItem, List.copyOf(updatedItems)));
    }

    public Optional<Event> onCommand(Command.RemoveLineItem command) {
      var exists = lineItems.stream()
          .anyMatch(item -> item.stockId().equals(command.stockId()));

      if (!exists) {
        return Optional.empty();
      }

      var updatedItems = lineItems.stream()
          .filter(item -> !item.stockId().equals(command.stockId()))
          .toList();

      return Optional.of(new Event.LineItemRemoved(command.customerId(), command.stockId(), List.copyOf(updatedItems)));
    }

    public Optional<Event> onCommand(Command.Checkout command) {
      if (lineItems.isEmpty()) {
        return Optional.empty();
      }

      var orderId = UUID.randomUUID().toString();
      return Optional.of(new Event.CheckedOut(command.customerId(), Instant.now(), orderId, List.copyOf(lineItems)));
    }

    public State onEvent(Event.LineItemAdded event) {
      return new State(event.customerId(), event.lineItems);
    }

    public State onEvent(Event.LineItemUpdated event) {
      return new State(event.customerId(), event.lineItems);
    }

    public State onEvent(Event.LineItemRemoved event) {
      return new State(event.customerId(), event.lineItems);
    }

    public State onEvent(Event.CheckedOut event) {
      return new State(event.customerId(), List.of());
    }
  }

  public sealed interface Command {
    record AddLineItem(String customerId, String stockId, String stockName, BigDecimal price, int quantity) implements Command {}

    record UpdateLineItem(String customerId, String stockId, String stockName, BigDecimal price, int quantity) implements Command {}

    record RemoveLineItem(String customerId, String stockId) implements Command {}

    record Checkout(String customerId) implements Command {}
  }

  public sealed interface Event {
    record LineItemAdded(String customerId, LineItem lineItem, List<LineItem> lineItems) implements Event {}

    record LineItemUpdated(String customerId, LineItem lineItem, List<LineItem> lineItems) implements Event {}

    record LineItemRemoved(String customerId, String stockId, List<LineItem> lineItems) implements Event {}

    record CheckedOut(String customerId, Instant checkedOutAt, String orderId, List<LineItem> lineItems) implements Event {}
  }
}
