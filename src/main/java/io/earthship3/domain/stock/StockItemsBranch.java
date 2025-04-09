package io.earthship3.domain.stock;

import static io.earthship3.ShortUUID.randomUUID;

import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public interface StockItemsBranch {

  public record State(
      String branchId,
      String stockId,
      String quantityId,
      int quantity,
      String parentBranchId,
      List<SubStockItems> subBranches,
      List<SubStockItems> leaves) {

    static final int maxSubBranches = 10;
    static final int maxStockItemsPerLeaf = 20;
    static final int maxLeafStockItemsPerBranch = maxStockItemsPerLeaf * maxSubBranches;

    public static State empty() {
      return new State(null, null, null, 0, null, List.of(), List.of());
    }

    public boolean isEmpty() {
      return stockId == null;
    }

    public boolean isTreeTrunk() {
      return stockId.equals(branchId);
    }

    public List<Event> onCommand(Command.AddQuantityToTree command) {
      if (!isEmpty() && quantityId.equals(command.quantityId)) {
        return List.of();
      }

      return isEmpty()
          ? create(command)
          : delegate(command);
    }

    private List<Event> create(Command.AddQuantityToTree command) {
      var leafQuantities = DistributeQuantity.distributeQuantity(command.quantity, maxStockItemsPerLeaf, maxSubBranches - leaves.size());
      var leftoverQuantity = leafQuantities.leftoverQuantity();
      var branchQuantities = leftoverQuantity > maxLeafStockItemsPerBranch * maxSubBranches
          ? DistributeQuantity.distributeQuantity(leftoverQuantity, maxSubBranches)
          : DistributeQuantity.distributeQuantity(leftoverQuantity, maxLeafStockItemsPerBranch, maxSubBranches);

      var newBranchSubStockItems = Stream.generate(() -> new SubStockItems(randomUUID(), command.stockId, 0))
          .limit(maxSubBranches)
          .toList();

      var newLeafSubStockItems = Stream.generate(() -> new SubStockItems(randomUUID(), command.stockId, 0))
          .limit(maxSubBranches)
          .toList();

      var stockItemsCreated = new Event.StockItemsCreated(
          command.branchId,
          command.stockId,
          command.quantityId,
          command.quantity,
          command.parentBranchId,
          newBranchSubStockItems,
          newLeafSubStockItems);

      var branchEvents = IntStream.range(0, branchQuantities.bucketLevels().size())
          .mapToObj(i -> new SubStockItems(newBranchSubStockItems.get(i).stockItemsId, command.stockId, branchQuantities.bucketLevels().get(i)))
          .map(s -> new Event.BranchToBeAdded(command.branchId, s))
          .toList();

      var leafEvents = IntStream.range(0, leafQuantities.bucketLevels().size())
          .mapToObj(i -> new SubStockItems(newLeafSubStockItems.get(i).stockItemsId, command.stockId, leafQuantities.bucketLevels().get(i)))
          .map(s -> new Event.LeafToBeAdded(command.branchId, s))
          .toList();

      return Stream.of(List.of(stockItemsCreated), branchEvents, leafEvents)
          .flatMap(Collection::stream)
          .map(e -> (Event) e)
          .toList();
    }

    private List<Event> delegate(Command.AddQuantityToTree command) {
      var subBranchId = subBranches.get(Math.abs(command.quantityId().hashCode()) % subBranches.size()).stockItemsId;
      return List.of(new Event.DelegateToSubBranch(
          subBranchId,
          command.stockId,
          command.quantityId,
          command.quantity,
          branchId));
    }

    public State onEvent(Event.StockItemsCreated event) {
      return new State(
          event.branchId,
          event.stockId,
          event.quantityId,
          event.quantity,
          event.parentBranchId,
          event.subBranches,
          event.leaves);
    }

    public Event onCommand(Command.UpdateBranchQuantity command) {
      var newSubBranches = subBranches.stream()
          .map(s -> s.stockItemsId.equals(command.subBranchId)
              ? new SubStockItems(s.stockItemsId, s.stockId, command.branchQuantity)
              : s)
          .toList();
      var totalQuantity = newSubBranches.stream().mapToInt(SubStockItems::quantity).sum()
          + leaves.stream().mapToInt(SubStockItems::quantity).sum();

      return new Event.BranchQuantityUpdated(
          command.branchId,
          totalQuantity,
          command.subBranchId,
          command.branchQuantity,
          newSubBranches);
    }

    public Event onCommand(Command.UpdateLeafQuantity command) {
      var newLeaves = leaves.stream()
          .map(s -> s.stockItemsId.equals(command.leafId)
              ? new SubStockItems(s.stockItemsId, s.stockId, command.leafQuantity)
              : s)
          .toList();
      var totalQuantity = newLeaves.stream().mapToInt(SubStockItems::quantity).sum()
          + subBranches.stream().mapToInt(SubStockItems::quantity).sum();

      return new Event.LeafQuantityUpdated(
          command.branchId,
          totalQuantity,
          command.leafId,
          command.leafQuantity,
          newLeaves);
    }

    public State onEvent(Event.BranchQuantityUpdated event) {
      return new State(
          event.branchId,
          stockId,
          quantityId,
          event.totalQuantity,
          parentBranchId,
          event.subBranches,
          leaves);
    }

    public State onEvent(Event.LeafQuantityUpdated event) {
      return new State(
          event.branchId,
          stockId,
          quantityId,
          event.totalQuantity,
          parentBranchId,
          subBranches,
          event.leaves);
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

  record SubStockItems(
      String stockItemsId,
      String stockId,
      int quantity) {}

  public sealed interface Command {
    record AddQuantityToTree(String branchId, String stockId, String quantityId, int quantity, String parentBranchId) implements Command {}

    record UpdateBranchQuantity(String branchId, String subBranchId, int branchQuantity) implements Command {}

    record UpdateLeafQuantity(String branchId, String leafId, int leafQuantity) implements Command {}
  }

  public sealed interface Event {
    record StockItemsCreated(
        String branchId,
        String stockId,
        String quantityId,
        int quantity,
        String parentBranchId,
        List<SubStockItems> subBranches,
        List<SubStockItems> leaves) implements Event {}

    record BranchToBeAdded(String parentBranchId, SubStockItems subStockItems) implements Event {}

    record LeafToBeAdded(String parentBranchId, SubStockItems subStockItems) implements Event {}

    record DelegateToSubBranch(String branchId, String stockId, String quantityId, int quantity, String parentBranchId) implements Event {}

    record BranchQuantityUpdated(String branchId, int totalQuantity, String subBranchId, int branchQuantity, List<SubStockItems> subBranches) implements Event {}

    record LeafQuantityUpdated(String branchId, int totalQuantity, String leafId, int leafQuantity, List<SubStockItems> leaves) implements Event {}
  }
}
