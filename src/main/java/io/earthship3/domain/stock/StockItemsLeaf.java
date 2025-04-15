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

    // Create a new stock items leaf
    public List<Event> onCommand(Command.CreateStockItems command) {
      if (!isEmpty()) {
        return List.of();
      }

      var stockOrderItems = Stream.generate(() -> new StockOrderItem(randomUUID().toString(), Optional.empty(), Optional.empty()))
          .limit(command.quantity)
          .toList();

      return List.of(
          new Event.StockItemsCreated(
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
              stockOrderItems,
              true),
          new Event.StockItemsNeedOrderItems(
              command.leafId,
              command.parentBranchId,
              command.stockId,
              command.quantityId,
              command.quantity,
              stockOrderItems));
    }

    // Allocate stock items to order items
    public List<Event> onCommand(Command.AllocateStockItemsToOrderItems command) {
      if (isEmpty() || !availableForOrders) {
        return List.of(
            new Event.StockItemsAllocatedToOrderItems(
                leafId,
                command.orderItemsLeafId,
                List.of()));
      }

      var newStockOrderItems = stockOrderItems;
      for (var orderItemId : command.orderItemsIds) {
        newStockOrderItems = updateStockOrderItems(command.orderItemsLeafId, orderItemId, newStockOrderItems);
      }

      var newQuantity = (int) newStockOrderItems.stream().filter(item -> item.orderItemId().isEmpty()).count();

      var leafQuantityUpdated = new Event.LeafQuantityUpdated(
          leafId,
          parentBranchId,
          stockId,
          quantityId,
          newQuantity,
          newStockOrderItems,
          newQuantity > 0 ? availableForOrders : false);

      var stockItemsAllocatedToOrderItems = new Event.StockItemsAllocatedToOrderItems(
          leafId,
          command.orderItemsLeafId,
          newStockOrderItems.stream()
              .filter(item -> item.orderItemsLeafId().isPresent() && item.orderItemsLeafId().get().equals(command.orderItemsLeafId))
              .map(item -> new Allocation(leafId, item.orderItemId().get(), command.orderItemsLeafId, item.orderItemId().get()))
              .toList());

      return newQuantity == 0
          ? List.of(leafQuantityUpdated, stockItemsAllocatedToOrderItems)
          : List.of(
              leafQuantityUpdated,
              stockItemsAllocatedToOrderItems,
              new Event.StockItemsNeedOrderItems(
                  leafId,
                  parentBranchId,
                  stockId,
                  quantityId,
                  newQuantity,
                  newStockOrderItems));
    }

    // Apply order items allocation
    public List<Event> onCommand(Command.ApplyOrderItemsAllocation command) {
      if (isEmpty() || !availableForOrders) {
        return List.of(
            new Event.StockItemsAllocationConflictDetected(
                leafId,
                command.orderItemsLeafId,
                command.allocations));
      }

      // First, verify that all of the allocations are still available
      var availableAllocations = command.allocations.stream()
          .filter(allocation -> stockOrderItems.stream()
              .anyMatch(item -> item.stockItemId().equals(allocation.stockItemId())
                  && item.orderItemsLeafId().isEmpty()))
          .toList();

      if (availableAllocations.size() != command.allocations.size()) {
        return List.of(
            new Event.StockItemsAllocationConflictDetected(
                leafId,
                command.orderItemsLeafId,
                command.allocations));
      }

      var newStockOrderItems = stockOrderItems;
      for (var allocation : availableAllocations) {
        newStockOrderItems = updateStockOrderItems(allocation.orderItemsLeafId(), allocation.orderItemId(), newStockOrderItems);
      }

      var newQuantity = (int) newStockOrderItems.stream().filter(item -> item.orderItemId().isEmpty()).count();

      return List.of(
          new Event.LeafQuantityUpdated(
              leafId,
              parentBranchId,
              stockId,
              quantityId,
              newQuantity,
              newStockOrderItems,
              newQuantity > 0 ? availableForOrders : false));
    }

    // Release order items allocation
    public List<Event> onCommand(Command.ReleaseOrderItemsAllocation command) {
      if (isEmpty()) {
        return List.of();
      }

      var newStockOrderItems = stockOrderItems;
      for (var allocation : command.allocations) {
        newStockOrderItems = releaseOrderItemFromStockItem(allocation.orderItemsLeafId(), allocation.orderItemId(), newStockOrderItems);
      }

      var newQuantity = (int) newStockOrderItems.stream().filter(item -> item.orderItemId().isEmpty()).count();

      return List.of(
          new Event.LeafQuantityUpdated(
              leafId,
              parentBranchId,
              stockId,
              quantityId,
              newQuantity,
              newStockOrderItems,
              newQuantity > 0 ? availableForOrders : false));
    }

    // Set available for orders on/off
    public List<Event> onCommand(Command.SetAvailableForOrders command) {
      if (isEmpty()) {
        return List.of();
      }

      return List.of(
          new Event.AvailableForOrdersSet(
              leafId,
              command.availableForOrders()));
    }

    public State onEvent(Event.StockItemsCreated event) {
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
          event.quantity(),
          event.stockOrderItems(),
          event.availableForOrders());
    }

    public State onEvent(Event.StockItemsNeedOrderItems event) {
      return this;
    }

    public State onEvent(Event.StockItemsAllocatedToOrderItems event) {
      return this;
    }

    public State onEvent(Event.StockItemsAllocationConflictDetected event) {
      return this;
    }

    public State onEvent(Event.AvailableForOrdersSet event) {
      return new State(
          leafId,
          parentBranchId,
          stockId,
          quantityId,
          quantity,
          stockOrderItems,
          event.availableForOrders());
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

    private List<StockOrderItem> releaseOrderItemFromStockItem(String orderItemsLeafId, String orderItemId, List<StockOrderItem> stockOrderItems) {
      return stockOrderItems.stream()
          .map(item -> item.orderItemId().isPresent() && item.orderItemId().get().equals(orderItemId)
              && item.orderItemsLeafId().isPresent() && item.orderItemsLeafId().get().equals(orderItemsLeafId)
                  ? new StockOrderItem(item.stockItemId(), Optional.empty(), Optional.empty())
                  : item)
          .toList();
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
    record CreateStockItems(
        String leafId,
        String parentBranchId,
        String stockId,
        String quantityId,
        int quantity) implements Command {}

    record AllocateStockItemsToOrderItems(
        String leafId,
        String orderItemsLeafId,
        List<String> orderItemsIds) implements Command {}

    record ApplyOrderItemsAllocation(
        String leafId,
        String orderItemsLeafId,
        List<Allocation> allocations) implements Command {}

    record ReleaseOrderItemsAllocation(
        String leafId,
        String orderItemsLeafId,
        List<Allocation> allocations) implements Command {}

    record SetAvailableForOrders(
        String leafId,
        boolean availableForOrders) implements Command {}
  }

  public sealed interface Event {
    record StockItemsCreated(
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
        List<StockOrderItem> stockOrderItems,
        boolean availableForOrders) implements Event {}

    record StockItemsNeedOrderItems(
        String leafId,
        String parentBranchId,
        String stockId,
        String quantityId,
        int quantity,
        List<StockOrderItem> stockOrderItems) implements Event {}

    record StockItemsAllocatedToOrderItems(
        String leafId,
        String orderItemsLeafId,
        List<Allocation> allocations) implements Event {}

    record StockItemsAllocationConflictDetected(
        String leafId,
        String orderItemsLeafId,
        List<Allocation> allocations) implements Event {}

    record AvailableForOrdersSet(
        String leafId,
        boolean availableForOrders) implements Event {}
  }
}
