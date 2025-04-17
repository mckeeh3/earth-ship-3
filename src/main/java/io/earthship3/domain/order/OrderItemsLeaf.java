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
      String stockId,
      String quantityId,
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

    // Create a new order items leaf
    public List<Event> onCommand(Command.CreateOrderItems command) {
      if (!isEmpty()) {
        return List.of();
      }

      var orderStockItems = Stream.generate(() -> new OrderStockItem(randomUUID(), Optional.empty(), Optional.empty()))
          .limit(command.quantity())
          .toList();

      return List.of(
          new Event.OrderItemsCreated(
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
              orderStockItems,
              Optional.empty(),
              Optional.empty()),
          new Event.OrderItemsNeedStockItems(
              command.leafId(),
              command.parentBranchId(),
              command.orderId(),
              command.stockId(),
              command.quantity(),
              orderStockItems));
    }

    // Allocate order items to stock items
    public List<Event> onCommand(Command.AllocateOrderItemsToStockItems command) {
      if (isEmpty() || readyToShipAt.isPresent() || backOrderedAt.isEmpty()) {
        return List.of(
            new Event.OrderItemsAllocatedToStockItems(
                leafId,
                command.stockItemsLeafId,
                List.of()));
      }

      var newOrderStockItems = orderStockItems;
      for (var stockItemId : command.stockItemsIds) {
        newOrderStockItems = allocateStockItemToOrderItem(command.stockItemsLeafId, stockItemId, newOrderStockItems);
      }

      var newQuantity = (int) newOrderStockItems.stream().filter(item -> item.stockItemId().isEmpty()).count();

      var leafQuantityUpdated = new Event.LeafQuantityUpdated(
          leafId,
          parentBranchId,
          stockId,
          quantityId,
          newQuantity,
          newOrderStockItems,
          newQuantity > 0 ? Optional.empty() : Optional.of(Instant.now()),
          Optional.empty());

      var orderItemsAllocatedToStockItems = new Event.OrderItemsAllocatedToStockItems(
          leafId,
          command.stockItemsLeafId,
          newOrderStockItems.stream()
              .filter(item -> item.stockItemsLeafId().isPresent() && item.stockItemsLeafId().get().equals(command.stockItemsLeafId))
              .map(item -> new Allocation(leafId, item.orderItemId(), command.stockItemsLeafId, item.stockItemId().get()))
              .toList());

      return newQuantity == 0
          ? List.of(leafQuantityUpdated, orderItemsAllocatedToStockItems)
          : List.of(
              leafQuantityUpdated,
              orderItemsAllocatedToStockItems,
              new Event.OrderItemsNeedStockItems(
                  leafId,
                  parentBranchId,
                  stockId,
                  quantityId,
                  newQuantity,
                  newOrderStockItems));
    }

    // Apply stock items allocation
    public List<Event> onCommand(Command.ApplyStockItemsAllocation command) {
      if (isEmpty() || readyToShipAt.isPresent() || backOrderedAt.isEmpty()) {
        return List.of(
            new Event.OrderItemsAllocationConflictDetected(
                leafId,
                command.stockItemsLeafId,
                command.allocations));
      }

      // First, verify that all of the allocations are still available
      var availableAllocations = command.allocations.stream()
          .filter(allocation -> orderStockItems.stream()
              .anyMatch(item -> item.orderItemId().equals(allocation.orderItemId())
                  && item.stockItemsLeafId().isEmpty()))
          .toList();

      if (availableAllocations.size() != command.allocations.size()) {
        return List.of(
            new Event.OrderItemsAllocationConflictDetected(
                leafId,
                command.stockItemsLeafId,
                command.allocations));
      }

      var newOrderStockItems = orderStockItems;
      for (var allocation : availableAllocations) {
        newOrderStockItems = allocateStockItemToOrderItem(allocation.stockItemsLeafId(), allocation.stockItemId(), newOrderStockItems);
      }

      var newQuantity = (int) newOrderStockItems.stream().filter(item -> item.stockItemId().isEmpty()).count();

      return List.of(
          new Event.LeafQuantityUpdated(
              leafId,
              parentBranchId,
              stockId,
              quantityId,
              newQuantity,
              newOrderStockItems,
              newQuantity > 0 ? Optional.empty() : Optional.of(Instant.now()),
              newQuantity > 0 ? Optional.empty() : backOrderedAt));
    }

    // Release stock items allocation
    public List<Event> onCommand(Command.ReleaseStockItemsAllocation command) {
      if (isEmpty()) {
        return List.of();
      }

      var newOrderStockItems = orderStockItems;
      for (var allocation : command.allocations) {
        newOrderStockItems = releaseStockItemFromOrderItem(allocation.stockItemsLeafId(), allocation.stockItemId(), newOrderStockItems);
      }

      var newQuantity = (int) newOrderStockItems.stream().filter(item -> item.stockItemId().isEmpty()).count();

      return List.of(
          new Event.LeafQuantityUpdated(
              leafId,
              parentBranchId,
              stockId,
              quantityId,
              newQuantity,
              newOrderStockItems,
              newQuantity > 0 ? Optional.empty() : Optional.of(Instant.now()),
              newQuantity > 0 ? Optional.empty() : backOrderedAt));
    }

    // Set back ordered on/off
    public List<Event> onCommand(Command.SetBackOrdered command) {
      if (isEmpty()) {
        return List.of();
      }

      return List.of(
          new Event.BackOrderedSet(
              leafId(),
              command.backOrderedAt().isEmpty() ? readyToShipAt : Optional.empty(),
              command.backOrderedAt()));
    }

    public State onEvent(Event.OrderItemsCreated event) {
      return new State(
          event.leafId(),
          event.parentBranchId(),
          event.stockId(),
          event.quantityId(),
          event.quantity(),
          event.orderStockItems(),
          Optional.empty(),
          Optional.empty());
    }

    public State onEvent(Event.LeafQuantityUpdated event) {
      return new State(
          leafId,
          parentBranchId,
          stockId,
          quantityId,
          event.quantity(),
          event.orderStockItems(),
          event.readyToShipAt(),
          event.backOrderedAt());
    }

    public State onEvent(Event.OrderItemsNeedStockItems event) {
      return this;
    }

    public State onEvent(Event.OrderItemsAllocatedToStockItems event) {
      return this;
    }

    public State onEvent(Event.OrderItemsAllocationConflictDetected event) {
      return this;
    }

    public State onEvent(Event.BackOrderedSet event) {
      return new State(
          leafId,
          parentBranchId,
          stockId,
          quantityId,
          quantity,
          orderStockItems,
          event.readyToShipAt(),
          event.backOrderedAt());
    }

    private List<OrderStockItem> allocateStockItemToOrderItem(String stockItemsLeafId, String stockItemId, List<OrderStockItem> orderStockItems) {
      if (orderStockItems.stream().anyMatch(item -> item.stockItemId().isPresent() && item.stockItemId().get().equals(stockItemId))) {
        return orderStockItems;
      }
      var availableItem = orderStockItems.stream()
          .filter(item -> item.stockItemId().isEmpty())
          .findFirst();
      return availableItem.isPresent()
          ? orderStockItems.stream()
              .map(item -> item.equals(availableItem.get())
                  ? new OrderStockItem(item.orderItemId(), Optional.of(stockItemId), Optional.of(stockItemsLeafId))
                  : item)
              .toList()
          : orderStockItems;
    }

    private List<OrderStockItem> releaseStockItemFromOrderItem(String stockItemsLeafId, String stockItemId, List<OrderStockItem> orderStockItems) {
      return orderStockItems.stream()
          .map(item -> item.stockItemsLeafId().isPresent() && item.stockItemsLeafId().get().equals(stockItemsLeafId)
              && item.stockItemId().isPresent() && item.stockItemId().get().equals(stockItemId)
                  ? new OrderStockItem(item.orderItemId(), Optional.empty(), Optional.empty())
                  : item)
          .toList();
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
    record CreateOrderItems(
        String leafId,
        String parentBranchId,
        String orderId,
        String stockId,
        int quantity) implements Command {}

    record AllocateOrderItemsToStockItems(
        String leafId,
        String stockItemsLeafId,
        List<String> stockItemsIds) implements Command {}

    record ApplyStockItemsAllocation(
        String leafId,
        String stockItemsLeafId,
        List<Allocation> allocations) implements Command {}

    record ReleaseStockItemsAllocation(
        String leafId,
        String stockItemsLeafId,
        List<Allocation> allocations) implements Command {}

    record SetBackOrdered(
        String leafId,
        Optional<Instant> backOrderedAt) implements Command {}
  }

  public sealed interface Event {
    record OrderItemsCreated(
        String leafId,
        String parentBranchId,
        String stockId,
        String quantityId,
        int quantity,
        List<OrderStockItem> orderStockItems) implements Event {}

    record LeafQuantityUpdated(
        String leafId,
        String parentBranchId,
        String stockId,
        String quantityId,
        int quantity,
        List<OrderStockItem> orderStockItems,
        Optional<Instant> readyToShipAt,
        Optional<Instant> backOrderedAt) implements Event {}

    record OrderItemsNeedStockItems(
        String leafId,
        String parentBranchId,
        String stockId,
        String quantityId,
        int quantity,
        List<OrderStockItem> orderStockItems) implements Event {}

    record OrderItemsAllocatedToStockItems(
        String leafId,
        String stockItemsLeafId,
        List<Allocation> allocations) implements Event {}

    record OrderItemsAllocationConflictDetected(
        String leafId,
        String stockItemsLeafId,
        List<Allocation> allocations) implements Event {}

    record BackOrderedSet(
        String leafId,
        Optional<Instant> readyToShipAt,
        Optional<Instant> backOrderedAt) implements Event {}
  }
}
