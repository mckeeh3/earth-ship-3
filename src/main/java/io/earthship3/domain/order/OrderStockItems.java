package io.earthship3.domain.order;

import static io.earthship3.ShortUUID.randomUUID;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public interface OrderStockItems {
  public record State(
      String orderStockItemId,
      Optional<String> parentOrderItemId,
      String orderId,
      String stockId,
      int quantity,
      List<OrderStockItem> orderStockItems,
      Optional<Instant> readyToShipAt,
      Optional<Instant> backOrderedAt) {

    public static State empty() {
      return new State(null, Optional.empty(), null, null, 0, List.of(), Optional.empty(), Optional.empty());
    }

    public boolean isEmpty() {
      return orderStockItemId == null;
    }

    public List<Event> onCommand(Command.CreateOrderStockItems command) {
      if (!isEmpty()) {
        return List.of();
      }

      var orderStockItems = Stream.generate(() -> randomUUID())
          .limit(command.quantity())
          .map(id -> new OrderStockItem(
              id,
              command.orderStockItemId(),
              command.orderId(),
              command.stockId(),
              Optional.empty(),
              Optional.empty(),
              Optional.empty()))
          .toList();

      return List.of(
          new Event.OrderStockItemsCreated(
              command.orderStockItemId(),
              command.parentOrderItemId(),
              command.orderId(),
              command.stockId(),
              command.quantity(),
              orderStockItems),
          new Event.OrderStockItemsNeedingStock(
              command.orderStockItemId(),
              command.parentOrderItemId(),
              command.orderId(),
              command.stockId(),
              command.quantity(),
              orderStockItems));
    }

    public State onEvent(Event.OrderStockItemsCreated event) {
      return new State(
          event.orderStockItemId(),
          event.parentOrderItemId(),
          event.orderId(),
          event.stockId(),
          event.quantity(),
          event.orderStockItems(),
          Optional.empty(),
          Optional.empty());
    }

    public State onEvent(Event.OrderStockItemsNeedingStock event) {
      return this;
    }
  }

  public record OrderStockItem(
      String orderStockItemId,
      String parentOrderStockItemId,
      String orderId,
      String stockId,
      Optional<String> stockItemId,
      Optional<Instant> readyToShipAt,
      Optional<Instant> backOrderedAt) {}

  public sealed interface Command {
    record CreateOrderStockItems(
        String orderStockItemId,
        Optional<String> parentOrderItemId,
        String orderId,
        String stockId,
        int quantity) implements Command {}
  }

  public sealed interface Event {
    record OrderStockItemsCreated(
        String orderStockItemId,
        Optional<String> parentOrderItemId,
        String orderId,
        String stockId,
        int quantity,
        List<OrderStockItem> orderStockItems) implements Event {}

    record OrderStockItemsNeedingStock(
        String orderStockItemId,
        Optional<String> parentOrderItemId,
        String orderId,
        String stockId,
        int quantity,
        List<OrderStockItem> orderStockItems) implements Event {}
  }
}
