package io.earthship3.domain.order;

import static io.earthship3.ShortUUID.randomUUID;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import io.earthship3.DistributeQuantity;

public interface OrderItemBranch {
  static final int maxBranches = 10;
  static final int minUnitsPerBranch = 10;

  public record State(
      String branchId,
      Optional<String> parentBranchId,
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

    public boolean isTreeTrunk() {
      return parentBranchId.isEmpty();
    }

    public List<Event> onCommand(Command.CreateBranch command) {
      if (!isEmpty()) {
        return List.of();
      }

      var totalPrice = command.price().multiply(BigDecimal.valueOf(command.quantity()));
      var event = new Event.BranchCreated(
          command.branchId(),
          command.parentBranchId(),
          command.orderId(),
          command.stockId(),
          command.stockName(),
          command.price(),
          command.quantity(),
          totalPrice);

      var branches = DistributeQuantity.distributeWithoutLeftover(command.quantity(), maxBranches, minUnitsPerBranch);
      var events = branches.bucketLevels().stream()
          .map(branchQuantity -> {
            var subBranchId = randomUUID();
            var subBranchParentBranchId = command.branchId();
            return branchQuantity <= minUnitsPerBranch * 2
                ? new Event.LeafToBeCreated(
                    subBranchId,
                    subBranchParentBranchId,
                    command.orderId(),
                    command.stockId(),
                    command.stockName(),
                    command.price(),
                    branchQuantity)
                : new Event.BranchToBeCreated(
                    subBranchId,
                    Optional.of(subBranchParentBranchId),
                    command.orderId(),
                    command.stockId(),
                    command.stockName(),
                    command.price(),
                    branchQuantity);
          })
          .toList();

      return Stream.concat(Stream.of((Event) event), events.stream()).toList();
    }

    public State onEvent(Event.BranchCreated event) {
      return new State(
          event.branchId(),
          event.parentBranchIs(),
          event.orderId(),
          event.stockId(),
          event.stockName(),
          event.price(),
          event.quantity(),
          event.totalPrice(),
          Optional.empty(),
          Optional.empty());
    }

    public State onEvent(Event.BranchToBeCreated event) {
      return this;
    }

    public State onEvent(Event.LeafToBeCreated event) {
      return this;
    }
  }

  public sealed interface Command {
    record CreateBranch(
        String branchId,
        Optional<String> parentBranchId,
        String orderId,
        String stockId,
        String stockName,
        BigDecimal price,
        int quantity) implements Command {}
  }

  public sealed interface Event {
    record BranchCreated(
        String branchId,
        Optional<String> parentBranchIs,
        String orderId,
        String stockId,
        String stockName,
        BigDecimal price,
        int quantity,
        BigDecimal totalPrice) implements Event {}

    record BranchToBeCreated(
        String branchId,
        Optional<String> parentBranchId,
        String orderId,
        String stockId,
        String stockName,
        BigDecimal price,
        int quantity) implements Event {}

    record LeafToBeCreated(
        String branchId,
        String parentBranchId,
        String orderId,
        String stockId,
        String stockName,
        BigDecimal price,
        int quantity) implements Event {}
  }
}
