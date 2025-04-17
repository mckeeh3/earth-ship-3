package io.earthship3.domain.order;

import static io.earthship3.ShortUUID.randomUUID;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.earthship3.DistributeQuantity;

public interface OrderItemsBranch {

  public record State(
      String branchId,
      Optional<String> parentBranchId,
      String stockId,
      String quantityId,
      Quantity quantity,
      Optional<Instant> readyToShipAt,
      Optional<Instant> backOrderedAt,
      List<SubOrderItems> subBranches,
      List<LeafOrderItems> leaves) {

    public static final int maxSubBranches = 10;
    public static final int maxOrderItemsPerLeaf = 20;
    public static final int maxOrderItemsPerBranch = maxOrderItemsPerLeaf * maxSubBranches;

    public static State empty() {
      return new State(null, Optional.empty(), null, null, Quantity.zero(), Optional.empty(), Optional.empty(), List.of(), List.of());
    }

    public boolean isEmpty() {
      return stockId == null;
    }

    public boolean isTreeTrunk() {
      return parentBranchId.isEmpty();
    }

    // Handle command to add quantity to tree
    public List<Event> onCommand(Command.AddQuantityToTree command) {
      if (!isEmpty() && quantityId.equals(command.quantityId)) {
        return List.of();
      }

      return isEmpty()
          ? create(command)
          : delegate(command);
    }

    private List<Event> create(Command.AddQuantityToTree command) {
      var leafOrderItems = DistributeQuantity.distributeAllowLeftover(command.quantity().allocated(), maxOrderItemsPerLeaf, maxSubBranches);
      var leftoverOrderItems = leafOrderItems.leftoverQuantity();
      var branchOrderItems = DistributeQuantity.distributeWithoutLeftover(leftoverOrderItems, maxOrderItemsPerBranch, maxSubBranches);

      var newBranchSubOrderItems = Stream.generate(() -> new SubOrderItems(randomUUID(), command.stockId, command.quantityId, Quantity.zero()))
          .limit(maxSubBranches)
          .toList();

      var newLeafSubOrderItems = Stream.generate(() -> new LeafOrderItems(randomUUID(), command.stockId, command.quantityId, Quantity.zero()))
          .limit(maxSubBranches)
          .toList();

      var orderItemCreated = new Event.OrderItemsCreated(
          command.branchId(),
          command.stockId(),
          command.quantityId(),
          command.quantity(),
          command.parentBranchId(),
          newBranchSubOrderItems,
          newLeafSubOrderItems);

      var branchEvents = IntStream.range(0, branchOrderItems.bucketLevels().size())
          .mapToObj(i -> new SubOrderItems(
              newBranchSubOrderItems.get(i).branchId,
              command.quantityId,
              command.stockId,
              Quantity.of(branchOrderItems.bucketLevels().get(i))))
          .map(s -> new Event.BranchToBeAdded(
              s.branchId,
              command.stockId,
              command.quantityId,
              s.quantity(),
              command.branchId()))
          .toList();

      var leafEvents = IntStream.range(0, leafOrderItems.bucketLevels().size())
          .mapToObj(i -> new SubOrderItems(
              newLeafSubOrderItems.get(i).leafId,
              command.stockId,
              command.quantityId,
              Quantity.of(leafOrderItems.bucketLevels().get(i))))
          .map(s -> new Event.LeafToBeAdded(
              s.branchId,
              command.stockId(),
              command.quantityId,
              s.quantity(),
              command.branchId()))
          .toList();

      return Stream.of(List.of(orderItemCreated), branchEvents, leafEvents)
          .flatMap(Collection::stream)
          .map(e -> (Event) e)
          .toList();
    }

    private List<Event> delegate(Command.AddQuantityToTree command) {
      var subBranchId = subBranches.get(Math.abs(command.quantityId().hashCode()) % subBranches.size()).branchId;
      return List.of(new Event.DelegateToSubBranch(
          branchId,
          subBranchId,
          command.stockId(),
          command.quantityId(),
          command.quantity()));
    }

    public List<Event> onCommand(Command.UpdateBranchQuantity command) {
      var newSubBranches = subBranches.stream()
          .map(s -> s.branchId.equals(command.subBranchId)
              ? new SubOrderItems(s.branchId, s.orderId, s.stockId, Quantity.of(command.branchQuantity().allocated(), command.branchQuantity().available()))
              : s)
          .toList();
      var newBranchesQuantity = newSubBranches.stream()
          .map(s -> s.quantity())
          .reduce(Quantity.zero(), (a, c) -> a.add(c));
      var newLeavesQuantity = leaves.stream()
          .map(s -> s.quantity())
          .reduce(Quantity.zero(), (a, c) -> a.add(c));

      return List.of(new Event.BranchQuantityUpdated(
          branchId,
          parentBranchId,
          newBranchesQuantity.add(newLeavesQuantity),
          command.subBranchId(),
          newSubBranches));
    }

    public List<Event> onCommand(Command.UpdateLeafQuantity command) {
      var newLeaves = leaves.stream()
          .map(s -> s.leafId.equals(command.leafId())
              ? new LeafOrderItems(s.leafId, s.orderId, s.stockId, Quantity.of(command.leafQuantity().allocated(), command.leafQuantity().available()))
              : s)
          .toList();
      var newLeavesQuantity = newLeaves.stream()
          .map(s -> s.quantity())
          .reduce(Quantity.zero(), (a, c) -> a.add(c));
      var newBranchesQuantity = subBranches.stream()
          .map(s -> s.quantity())
          .reduce(Quantity.zero(), (a, c) -> a.add(c));

      return List.of(new Event.LeafQuantityUpdated(
          branchId,
          parentBranchId,
          newBranchesQuantity.add(newLeavesQuantity),
          command.leafId(),
          newLeaves));
    }

    public State onEvent(Event.OrderItemsCreated event) {
      return new State(
          event.branchId(),
          event.parentBranchId(),
          event.stockId(),
          event.quantityId(),
          event.quantity(),
          Optional.empty(),
          Optional.empty(),
          event.subBranches(),
          event.leaves());
    }

    public State onEvent(Event.BranchQuantityUpdated event) {
      return new State(
          event.branchId(),
          event.parentBranchId(),
          stockId,
          quantityId,
          event.quantity(),
          Optional.empty(),
          Optional.empty(),
          event.subBranches(),
          leaves);
    }

    public State onEvent(Event.LeafQuantityUpdated event) {
      return new State(
          event.branchId(),
          event.parentBranchId(),
          stockId,
          quantityId,
          event.quantity(),
          Optional.empty(),
          Optional.empty(),
          subBranches,
          event.leaves());
    }

    public State onEvent(Event.BranchToBeAdded event) {
      return this;
    }

    public State onEvent(Event.LeafToBeAdded event) {
      return this;
    }

    public State onEvent(Event.DelegateToSubBranch event) {
      return this;
    }
  }

  record Quantity(int allocated, int available) {
    public static Quantity of(int quantity) {
      return new Quantity(quantity, quantity);
    }

    public static Quantity of(int allocated, int available) {
      return new Quantity(allocated, available);
    }

    public static Quantity zero() {
      return new Quantity(0, 0);
    }

    public Quantity add(Quantity other) {
      return new Quantity(allocated + other.allocated, available + other.available);
    }

    public Quantity sub(int available) {
      return new Quantity(allocated, this.available - available);
    }
  }

  record SubOrderItems(
      String branchId,
      String orderId,
      String stockId,
      Quantity quantity) {}

  record LeafOrderItems(
      String leafId,
      String orderId,
      String stockId,
      Quantity quantity) {}

  public sealed interface Command {
    record AddQuantityToTree(
        String branchId,
        String stockId,
        String quantityId,
        Quantity quantity,
        Optional<String> parentBranchId) implements Command {}

    record UpdateBranchQuantity(
        String branchId,
        String subBranchId,
        Quantity branchQuantity) implements Command {}

    record UpdateLeafQuantity(
        String branchId,
        String leafId,
        Quantity leafQuantity) implements Command {}
  }

  public sealed interface Event {
    record OrderItemsCreated(
        String branchId,
        String stockId,
        String quantityId,
        Quantity quantity,
        Optional<String> parentBranchId,
        List<SubOrderItems> subBranches,
        List<LeafOrderItems> leaves) implements Event {}

    record BranchToBeAdded(
        String branchId,
        String stockId,
        String quantityId,
        Quantity quantity,
        String parentBranchId) implements Event {}

    record LeafToBeAdded(
        String leafId,
        String stockId,
        String quantityId,
        Quantity quantity,
        String parentBranchId) implements Event {}

    record DelegateToSubBranch(
        String branchId,
        String subBranchId,
        String stockId,
        String quantityId,
        Quantity quantity) implements Event {}

    record BranchQuantityUpdated(
        String branchId,
        Optional<String> parentBranchId,
        Quantity quantity,
        String subBranchId,
        List<SubOrderItems> subBranches) implements Event {}

    record LeafQuantityUpdated(
        String branchId,
        Optional<String> parentBranchId,
        Quantity quantity,
        String leafId,
        List<LeafOrderItems> leaves) implements Event {}
  }
}
