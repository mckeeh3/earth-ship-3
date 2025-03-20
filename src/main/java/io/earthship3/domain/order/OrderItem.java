package io.earthship3.domain.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public interface OrderItem {
  static final int maxBranches = 10;
  static final int minUnitsPerBranch = 10;

  public record State(
      String orderItemId,
      Optional<String> parentOrderItemId,
      String orderId,
      String stockId,
      String stockName,
      BigDecimal price,
      int quantity,
      BigDecimal totalPrice,
      Optional<Instant> readyToShipAt,
      Optional<Instant> backOrderedAt) {

    public static State empty() {
      return new State(null, Optional.empty(), null, null, null, BigDecimal.ZERO, 0, BigDecimal.ZERO, Optional.empty(), Optional.empty());
    }

    public boolean isEmpty() {
      return orderId == null;
    }

    public List<Event> onCommand(Command.CreateOrderItem command) {
      if (!isEmpty()) {
        return List.of();
      }

      var totalPrice = command.price().multiply(BigDecimal.valueOf(command.quantity()));
      var event = new Event.OrderItemCreated(
          command.orderItemId(),
          command.parentOrderItemId(),
          command.orderId(),
          command.stockId(),
          command.stockName(),
          command.price(),
          command.quantity(),
          totalPrice);

      var branches = distributeQuantity(command.quantity(), maxBranches, minUnitsPerBranch);
      var events = branches.stream()
          .map(branchQuantity -> {
            var subOrderItemId = UUID.randomUUID().toString();
            var subOrderItemParentOrderItemId = Optional.of(command.orderItemId());
            return branchQuantity <= minUnitsPerBranch * 2
                ? new Event.OrderStockItemsToBeCreated(
                    subOrderItemId,
                    subOrderItemParentOrderItemId,
                    command.orderId(),
                    command.stockId(),
                    command.stockName(),
                    command.price(),
                    branchQuantity)
                : new Event.OrderItemBranchToBeCreated(
                    subOrderItemId,
                    subOrderItemParentOrderItemId,
                    command.orderId(),
                    command.stockId(),
                    command.stockName(),
                    command.price(),
                    branchQuantity);
          })
          .toList();

      return Stream.concat(Stream.of((Event) event), events.stream()).toList();
    }

    public State onEvent(Event.OrderItemCreated event) {
      return new State(
          event.orderItemId(),
          event.parentOrderItemId(),
          event.orderId(),
          event.stockId(),
          event.stockName(),
          event.price(),
          event.quantity(),
          event.totalPrice(),
          Optional.empty(),
          Optional.empty());
    }

    public State onEvent(Event.OrderItemBranchToBeCreated event) {
      return this;
    }

    public State onEvent(Event.OrderStockItemsToBeCreated event) {
      return this;
    }

    static List<Integer> distributeQuantity(int quantity, int maxBranches, int minUnitsPerBranch) {
      if (quantity <= 0 || maxBranches <= 0 || minUnitsPerBranch <= 0) {
        return List.of();
      }

      // For small quantity, use a single branch
      if (quantity <= minUnitsPerBranch) {
        return List.of(quantity);
      }

      // check if quantity is less than maxBranches * minUnitsPerBranch
      if (quantity <= maxBranches * minUnitsPerBranch) {
        var remainder = quantity % minUnitsPerBranch;
        return Stream.concat(
            Stream.generate(() -> minUnitsPerBranch)
                .limit((quantity - remainder) / minUnitsPerBranch),
            Stream.of(remainder))
            .toList();
      }

      // Calculate initial number of branches needed
      var numBranches = Math.min(maxBranches, (quantity + minUnitsPerBranch - 1) / minUnitsPerBranch);

      // For larger counts that still fit in one branch
      if (numBranches == 1) {
        return List.of(quantity);
      }

      // Calculate base units and remainder
      var baseUnits = quantity / numBranches;

      // If base units would be less than minimum, recalculate with fewer branches
      if (baseUnits < minUnitsPerBranch) {
        numBranches = quantity / minUnitsPerBranch;
        if (quantity % minUnitsPerBranch > 0) {
          numBranches++;
        }
        baseUnits = quantity / numBranches;
      }

      var remainder = quantity % numBranches;
      var unitsPerBranch = baseUnits;
      var lastBranchUnits = unitsPerBranch + remainder;

      // Generate the distribution
      return Stream.concat(
          Stream.generate(() -> unitsPerBranch)
              .limit(numBranches - 1),
          Stream.of(lastBranchUnits)).toList();
    }
  }

  public sealed interface Command {
    record CreateOrderItem(
        String orderItemId,
        Optional<String> parentOrderItemId,
        String orderId,
        String stockId,
        String stockName,
        BigDecimal price,
        int quantity) implements Command {}
  }

  public sealed interface Event {
    record OrderItemCreated(
        String orderItemId,
        Optional<String> parentOrderItemId,
        String orderId,
        String stockId,
        String stockName,
        BigDecimal price,
        int quantity,
        BigDecimal totalPrice) implements Event {}

    record OrderItemBranchToBeCreated(
        String orderItemId,
        Optional<String> parentOrderItemId,
        String orderId,
        String stockId,
        String stockName,
        BigDecimal price,
        int quantity) implements Event {}

    record OrderStockItemsToBeCreated(
        String orderStockItemId,
        Optional<String> parentOrderItemId,
        String orderId,
        String stockId,
        String stockName,
        BigDecimal price,
        int quantity) implements Event {}
  }
}
