package io.earthship3.domain.order;

import static io.earthship3.ShortUUID.randomUUID;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public interface OrderItemsLeaf {
  public record State(
      String leafId,
      String parentBranchId,
      String orderId,
      String stockId,
      int quantity,
      List<OrderStockItem> orderStockItems,
      Optional<Instant> readyToShipAt,
      Optional<Instant> backOrderedAt) {

    public static State empty() {
      return new State(null, null, null, null, 0, List.of(), Optional.empty(), Optional.empty());
    }

    public boolean isEmpty() {
      return leafId == null;
    }

    public List<Event> onCommand(Command.CreateLeaf command) {
      if (!isEmpty()) {
        return List.of();
      }

      var orderStockItems = Stream.generate(() -> new OrderStockItem(randomUUID(), Optional.empty(), Optional.empty()))
          .limit(command.quantity())
          .toList();

      return List.of(
          new Event.LeafCreated(
              command.leafId(),
              command.parentBranchId(),
              command.orderId(),
              command.stockId(),
              command.quantity(),
              orderStockItems),
          new Event.LeafQuantityUpdated(
              command.leafId(),
              command.parentBranchId(),
              command.orderId(),
              command.stockId(),
              command.quantity(),
              orderStockItems),
          new Event.LeafNeedsStockItems(
              command.leafId(),
              command.parentBranchId(),
              command.orderId(),
              command.stockId(),
              command.quantity(),
              orderStockItems));
    }

    public State onEvent(Event.LeafCreated event) {
      return new State(
          event.leafId(),
          event.parentBranchId(),
          event.orderId(),
          event.stockId(),
          event.quantity(),
          event.orderStockItems(),
          Optional.empty(),
          Optional.empty());
    }

    public State onEvent(Event.LeafQuantityUpdated event) {
      return new State(
          event.leafId(),
          event.parentBranchId(),
          event.orderId(),
          event.stockId(),
          event.quantity(),
          event.orderStockItems(),
          Optional.empty(),
          Optional.empty());
    }

    public State onEvent(Event.LeafNeedsStockItems event) {
      return this;
    }
  }

  record Allocation(
      String orderItemsLeafId,
      String orderItemId,
      String stockItemsLeafId,
      String stockItemId) {}

  public record OrderStockItem(
      String orderItemId,
      Optional<String> stockItemId,
      Optional<String> stockItemsLeafId) {}

  public sealed interface Command {
    record CreateLeaf(
        String leafId,
        String parentBranchId,
        String orderId,
        String stockId,
        int quantity) implements Command {}

    record RequestAllocation(
        String leafId,
        String stockItemsLeafId,
        List<String> stockItemsIds) implements Command {}
  }

  public sealed interface Event {
    record LeafCreated(
        String leafId,
        String parentBranchId,
        String orderId,
        String stockId,
        int quantity,
        List<OrderStockItem> orderStockItems) implements Event {}

    record LeafQuantityUpdated(
        String leafId,
        String parentBranchId,
        String orderId,
        String stockId,
        int quantity,
        List<OrderStockItem> orderStockItems) implements Event {}

    record LeafNeedsStockItems(
        String leafId,
        String parentBranchId,
        String orderId,
        String stockId,
        int quantity,
        List<OrderStockItem> orderStockItems) implements Event {}
  }
}
