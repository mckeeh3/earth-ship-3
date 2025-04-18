package io.earthship3.domain.stock;

import static io.earthship3.ShortUUID.randomUUID;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import io.earthship3.DistributeQuantity;

public interface StockItemsBranch {

  public record State(
      String branchId,
      Optional<String> parentBranchId,
      String stockId,
      String quantityId,
      Quantity quantity,
      List<SubStockItems> subBranches,
      List<LeafStockItems> leaves) {

    public static final int maxSubBranches = 10;
    public static final int maxStockItemsPerLeaf = 20;
    public static final int maxStockItemsPerBranch = maxStockItemsPerLeaf * maxSubBranches;

    public static State empty() {
      return new State(null, Optional.empty(), null, null, Quantity.zero(), List.of(), List.of());
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
      var leafQuantities = DistributeQuantity.distributeAllowLeftover(command.quantity().acquired(), maxStockItemsPerLeaf, maxSubBranches);
      var leftoverQuantity = leafQuantities.leftoverQuantity();
      var branchQuantities = DistributeQuantity.distributeWithoutLeftover(leftoverQuantity, maxStockItemsPerBranch, maxSubBranches);

      var newBranchSubStockItems = Stream.generate(() -> new SubStockItems(randomUUID(), command.stockId, Quantity.zero()))
          .limit(maxSubBranches)
          .toList();

      var newLeafSubStockItems = Stream.generate(() -> new LeafStockItems(randomUUID(), command.stockId, Quantity.zero()))
          .limit(maxSubBranches)
          .toList();

      var stockItemsCreated = new Event.StockItemsCreated(
          command.branchId(),
          command.stockId(),
          command.quantityId(),
          command.quantity(),
          command.parentBranchId(),
          newBranchSubStockItems,
          newLeafSubStockItems);

      var branchEvents = IntStream.range(0, branchQuantities.bucketLevels().size())
          .mapToObj(i -> new SubStockItems(
              newBranchSubStockItems.get(i).branchId,
              command.stockId,
              Quantity.of(branchQuantities.bucketLevels().get(i).intValue())))
          .map(s -> new Event.BranchToBeAdded(
              s.branchId,
              command.stockId,
              command.quantityId(),
              s.quantity(),
              command.branchId()))
          .toList();

      var leafEvents = IntStream.range(0, leafQuantities.bucketLevels().size())
          .mapToObj(i -> new SubStockItems(
              newLeafSubStockItems.get(i).leafId,
              command.stockId,
              Quantity.of(leafQuantities.bucketLevels().get(i))))
          .map(s -> new Event.LeafToBeAdded(
              s.branchId,
              command.stockId,
              command.quantityId(),
              s.quantity(),
              command.branchId()))
          .toList();

      return Stream.of(List.of(stockItemsCreated), branchEvents, leafEvents)
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

    // Handle command to update branch quantity
    public Event onCommand(Command.UpdateBranchQuantity command) {
      var newSubBranches = subBranches.stream()
          .map(s -> s.branchId.equals(command.subBranchId)
              ? new SubStockItems(s.branchId, s.stockId, Quantity.of(command.branchQuantity().acquired(), command.branchQuantity().available()))
              : s)
          .toList();
      var newBranchesQuantity = newSubBranches.stream()
          .map(s -> s.quantity())
          .reduce(Quantity.zero(), (a, c) -> a.add(c));
      var newLeavesQuantity = leaves.stream()
          .map(s -> s.quantity())
          .reduce(Quantity.zero(), (a, c) -> a.add(c));

      return new Event.BranchQuantityUpdated(
          command.branchId,
          parentBranchId,
          newBranchesQuantity.add(newLeavesQuantity),
          command.subBranchId,
          newSubBranches);
    }

    public Event onCommand(Command.UpdateLeafQuantity command) {
      var newLeaves = leaves.stream()
          .map(s -> s.leafId.equals(command.leafId)
              ? new LeafStockItems(s.leafId, s.stockId, Quantity.of(command.leafQuantity().acquired(), command.leafQuantity().available()))
              : s)
          .toList();
      var newLeavesQuantity = newLeaves.stream()
          .map(s -> s.quantity())
          .reduce(Quantity.zero(), (a, c) -> a.add(c));
      var newBranchesQuantity = subBranches.stream()
          .map(s -> s.quantity())
          .reduce(Quantity.zero(), (a, c) -> a.add(c));

      return new Event.LeafQuantityUpdated(
          branchId,
          parentBranchId,
          newBranchesQuantity.add(newLeavesQuantity),
          command.leafId,
          newLeaves);
    }

    public State onEvent(Event.StockItemsCreated event) {
      return new State(
          event.branchId(),
          event.parentBranchId(),
          event.stockId(),
          event.quantityId(),
          event.quantity(),
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
          subBranches,
          event.leaves);
    }

    public State onEvent(Event.DelegateToSubBranch event) {
      return this;
    }

    public State onEvent(Event.BranchToBeAdded event) {
      return this;
    }

    public State onEvent(Event.LeafToBeAdded event) {
      return this;
    }
  }

  record Quantity(int acquired, int available) {
    public static Quantity of(int quantity) {
      return new Quantity(quantity, quantity);
    }

    public static Quantity of(int acquired, int available) {
      return new Quantity(acquired, available);
    }

    public static Quantity zero() {
      return new Quantity(0, 0);
    }

    public Quantity add(Quantity other) {
      return new Quantity(acquired + other.acquired, available + other.available);
    }

    public Quantity sub(int stocked) {
      return new Quantity(acquired, this.available - stocked);
    }
  }

  record SubStockItems(
      String branchId,
      String stockId,
      Quantity quantity) {}

  record LeafStockItems(
      String leafId,
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
    record StockItemsCreated(
        String branchId,
        String stockId,
        String quantityId,
        Quantity quantity,
        Optional<String> parentBranchId,
        List<SubStockItems> subBranches,
        List<LeafStockItems> leaves) implements Event {}

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
        List<SubStockItems> subBranches) implements Event {}

    record LeafQuantityUpdated(
        String branchId,
        Optional<String> parentBranchId,
        Quantity quantity,
        String leafId,
        List<LeafStockItems> leaves) implements Event {}
  }
}
