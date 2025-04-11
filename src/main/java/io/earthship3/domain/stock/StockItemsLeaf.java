package io.earthship3.domain.stock;

import static io.earthship3.ShortUUID.randomUUID;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public interface StockItemsLeaf {

  public record State(
      String leafId,
      String parentBranchId,
      String stockId,
      String quantityId,
      int quantity,
      List<StockOrderItem> stockOrderItems,
      boolean availableForOrders) {

    public static State empty() {
      return new State(null, null, null, null, 0, List.of(), false);
    }

    public boolean isEmpty() {
      return leafId == null;
    }

    public List<Event> onCommand(Command.CreateLeaf command) {
      if (!isEmpty()) {
        return List.of();
      }

      var stockOrderItems = Stream.generate(() -> new StockOrderItem(randomUUID().toString(), Optional.empty(), Optional.empty()))
          .limit(command.quantity)
          .toList();

      return List.of(
          new Event.LeafCreated(
              command.leafId,
              command.parentBranchId,
              command.stockId,
              command.quantityId,
              command.quantity,
              stockOrderItems),
          new Event.LeafQuantityUpdated(
              command.leafId,
              command.parentBranchId,
              command.stockId,
              command.quantityId,
              command.quantity,
              stockOrderItems),
          new Event.LeafQuantityUpdated(
              command.leafId,
              command.parentBranchId,
              command.stockId,
              command.quantityId,
              command.quantity,
              stockOrderItems));
    }

    public List<Event> onCommand(Command.RequestAllocation command) {
      if (isEmpty()) {
        return List.of();
      }

      var newStockOrderItems = stockOrderItems;
      for (var orderItemId : command.orderItemsIds) {
        newStockOrderItems = updateStockOrderItems(command.orderItemsLeafId, orderItemId, newStockOrderItems);
      }

      var newQuantity = (int) newStockOrderItems.stream().filter(item -> item.orderItemId().isEmpty()).count();

      return List.of(
          new Event.LeafQuantityUpdated(
              leafId,
              parentBranchId,
              stockId,
              quantityId,
              newQuantity,
              newStockOrderItems),
          new Event.AllocationResponse(
              leafId,
              command.orderItemsLeafId,
              newStockOrderItems.stream()
                  .filter(item -> item.orderItemsLeafId().isPresent() && item.orderItemsLeafId().get().equals(command.orderItemsLeafId))
                  .map(item -> new Allocation(leafId, item.orderItemId().get(), command.orderItemsLeafId, item.orderItemId().get()))
                  .toList()));
    }

    public State onEvent(Event.LeafCreated event) {
      return new State(
          event.leafId,
          event.parentBranchId,
          event.stockId,
          event.quantityId,
          event.quantity,
          event.stockOrderItems,
          false);
    }

    public State onEvent(Event.LeafQuantityUpdated event) {
      return new State(
          leafId,
          parentBranchId,
          stockId,
          quantityId,
          event.quantity,
          event.stockOrderItems,
          availableForOrders);
    }

    private List<StockOrderItem> updateStockOrderItems(String orderItemsLeafId, String orderItemId, List<StockOrderItem> stockOrderItems) {
      if (stockOrderItems.stream().anyMatch(item -> item.orderItemId().isPresent() && item.orderItemId().get().equals(orderItemId))) {
        return stockOrderItems;
      }
      var availableItem = stockOrderItems.stream()
          .filter(item -> item.orderItemId().isEmpty())
          .findFirst();
      return availableItem.isPresent()
          ? stockOrderItems.stream()
              .map(item -> item.equals(availableItem.get())
                  ? new StockOrderItem(item.stockItemId(), Optional.of(orderItemId), Optional.of(orderItemsLeafId))
                  : item)
              .toList()
          : stockOrderItems;
    }
  }

  record Allocation(
      String stockItemLeafId,
      String stockItemId,
      String orderItemsLeafId,
      String orderItemId) {}

  record StockOrderItem(
      String stockItemId,
      Optional<String> orderItemId,
      Optional<String> orderItemsLeafId) {}

  public sealed interface Command {
    record CreateLeaf(
        String leafId,
        String parentBranchId,
        String stockId,
        String quantityId,
        int quantity) implements Command {}

    record RequestAllocation(
        String leafId,
        String orderItemsLeafId,
        List<String> orderItemsIds) implements Command {}
  }

  public sealed interface Event {
    record LeafCreated(
        String leafId,
        String parentBranchId,
        String stockId,
        String quantityId,
        int quantity,
        List<StockOrderItem> stockOrderItems) implements Event {}

    record LeafQuantityUpdated(
        String leafId,
        String parentBranchId,
        String stockId,
        String quantityId,
        int quantity,
        List<StockOrderItem> stockOrderItems) implements Event {}

    record LeafNeedsOrderItems(
        String leafId,
        String parentBranchId,
        String stockId,
        String quantityId,
        int quantity,
        List<StockOrderItem> stockOrderItems) implements Event {}

    record AllocationResponse(
        String leafId,
        String orderItemsLeafId,
        List<Allocation> allocations) implements Event {}
  }
}
