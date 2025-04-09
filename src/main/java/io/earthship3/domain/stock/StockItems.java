package io.earthship3.domain.stock;

import static io.earthship3.ShortUUID.randomUUID;

import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public interface StockItems {

  public record State(
      String stockItemsId,
      String stockId,
      String quantityId,
      int quantity,
      String parentStockItemsId,
      List<SubStockItems> branchSubStockItems,
      List<SubStockItems> leafSubStockItems) {

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
      return stockId.equals(stockItemsId);
    }

    public List<Event> onCommand(Command.StockItemsAddQuantity command) {
      if (!isEmpty() && quantityId.equals(command.quantityId)) {
        return List.of();
      }

      return isEmpty()
          ? create(command)
          : delegate(command);
    }

    private List<Event> create(Command.StockItemsAddQuantity command) {
      var leafQuantities = DistributeQuantity.distributeQuantity(command.quantity, maxStockItemsPerLeaf, maxSubBranches - leafSubStockItems.size());
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
          command.stockItemsId,
          command.stockId,
          command.quantityId,
          command.quantity,
          command.parentStockItemsId,
          newBranchSubStockItems,
          newLeafSubStockItems);

      var branchEvents = IntStream.range(0, branchQuantities.bucketLevels().size())
          .mapToObj(i -> new SubStockItems(newBranchSubStockItems.get(i).stockItemsId, command.stockId, branchQuantities.bucketLevels().get(i)))
          .map(s -> new Event.StockItemsBranchToBeAdded(command.stockItemsId, s))
          .toList();

      var leafEvents = IntStream.range(0, leafQuantities.bucketLevels().size())
          .mapToObj(i -> new SubStockItems(newLeafSubStockItems.get(i).stockItemsId, command.stockId, leafQuantities.bucketLevels().get(i)))
          .map(s -> new Event.StockItemsLeafToBeAdded(command.stockItemsId, s))
          .toList();

      return Stream.of(List.of(stockItemsCreated), branchEvents, leafEvents)
          .flatMap(Collection::stream)
          .map(e -> (Event) e)
          .toList();
    }

    private List<Event> delegate(Command.StockItemsAddQuantity command) {
      var branchSubStockId = branchSubStockItems.get(Math.abs(command.quantityId().hashCode()) % branchSubStockItems.size()).stockItemsId;
      return List.of(new Event.DelegateToSubStockItems(
          branchSubStockId,
          command.stockId,
          command.quantityId,
          command.quantity,
          command.stockItemsId));
    }

    public State onEvent(Event.StockItemsCreated event) {
      return new State(
          event.stockItemsId,
          event.stockId,
          event.quantityId,
          event.quantity,
          event.parentStockItemsId,
          event.branchSubStockItems,
          event.leafSubStockItems);
    }

    public Event onCommand(Command.UpdateBranchQuantity command) {
      var newBranchSubStockItems = branchSubStockItems.stream()
          .map(s -> s.stockItemsId.equals(command.branchStockItemsId)
              ? new SubStockItems(s.stockItemsId, s.stockId, command.branchQuantity)
              : s)
          .toList();
      var totalQuantity = newBranchSubStockItems.stream().mapToInt(SubStockItems::quantity).sum()
          + leafSubStockItems.stream().mapToInt(SubStockItems::quantity).sum();

      return new Event.BranchQuantityUpdated(
          command.stockItemsId,
          totalQuantity,
          command.branchStockItemsId,
          command.branchQuantity,
          newBranchSubStockItems);
    }

    public Event onCommand(Command.UpdateLeafQuantity command) {
      var newLeafSubStockItems = leafSubStockItems.stream()
          .map(s -> s.stockItemsId.equals(command.leafStockItemsId)
              ? new SubStockItems(s.stockItemsId, s.stockId, command.leafQuantity)
              : s)
          .toList();
      var totalQuantity = newLeafSubStockItems.stream().mapToInt(SubStockItems::quantity).sum()
          + branchSubStockItems.stream().mapToInt(SubStockItems::quantity).sum();

      return new Event.LeafQuantityUpdated(
          command.stockItemsId,
          totalQuantity,
          command.leafStockItemsId,
          command.leafQuantity,
          newLeafSubStockItems);
    }

    public State onEvent(Event.BranchQuantityUpdated event) {
      return new State(
          event.stockItemsId,
          stockId,
          quantityId,
          event.totalQuantity,
          parentStockItemsId,
          event.branchSubStockItems,
          leafSubStockItems);
    }

    public State onEvent(Event.LeafQuantityUpdated event) {
      return new State(
          event.stockItemsId,
          stockId,
          quantityId,
          event.totalQuantity,
          parentStockItemsId,
          branchSubStockItems,
          event.leafSubStockItems);
    }

    public State onEvent(Event.StockItemsBranchToBeAdded event) {
      return this;
    }

    public State onEvent(Event.StockItemsLeafToBeAdded event) {
      return this;
    }

    public State onEvent(Event.DelegateToSubStockItems event) {
      return this;
    }
  }

  record SubStockItems(
      String stockItemsId,
      String stockId,
      int quantity) {}

  public sealed interface Command {
    record StockItemsAddQuantity(String stockItemsId, String stockId, String quantityId, int quantity, String parentStockItemsId) implements Command {}

    record AddStockItems(String stockItemsId, SubStockItems subStockItems) implements Command {}

    record UpdateBranchQuantity(String stockItemsId, String branchStockItemsId, int branchQuantity) implements Command {}

    record UpdateLeafQuantity(String stockItemsId, String leafStockItemsId, int leafQuantity) implements Command {}
  }

  public sealed interface Event {
    record StockItemsCreated(
        String stockItemsId,
        String stockId,
        String quantityId,
        int quantity,
        String parentStockItemsId,
        List<SubStockItems> branchSubStockItems,
        List<SubStockItems> leafSubStockItems) implements Event {}

    record StockItemsBranchToBeAdded(String parentStockItemsId, SubStockItems subStockItems) implements Event {}

    record StockItemsLeafToBeAdded(String parentStockItemsId, SubStockItems subStockItems) implements Event {}

    record DelegateToSubStockItems(String stockItemsId, String stockId, String quantityId, int quantity, String parentStockItemsId) implements Event {}

    record BranchQuantityUpdated(String stockItemsId, int totalQuantity, String branchStockItemsId, int branchQuantity, List<SubStockItems> branchSubStockItems) implements Event {}

    record LeafQuantityUpdated(String stockItemsId, int totalQuantity, String leafStockItemsId, int leafQuantity, List<SubStockItems> leafSubStockItems) implements Event {}
  }
}
