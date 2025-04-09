package io.earthship3.domain.stock;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class StockItemsTest {

  @Test
  void testStockItemsAddQuantity() {
    var stockItemsId = "stock-items-1";
    var stockId = "stock-1";
    var quantityId = "quantity-1";
    var parentStockItemsId = "parent-1";
    var quantity = 64;
    var leafEventCount = DistributeQuantity.distributeQuantity(
        quantity,
        StockItemsBranch.State.maxStockItemsPerLeaf,
        StockItemsBranch.State.maxSubBranches - 1);
    var branchEventCount = DistributeQuantity.distributeQuantity(
        leafEventCount.leftoverQuantity(),
        StockItemsBranch.State.maxLeafStockItemsPerBranch);

    var command = new StockItemsBranch.Command.AddQuantityToTree(
        stockItemsId,
        stockId,
        quantityId,
        quantity,
        parentStockItemsId);

    var state = StockItemsBranch.State.empty();
    var events = state.onCommand(command);

    // First event should be StockItemsCreated
    assertTrue(events.get(0) instanceof StockItemsBranch.Event.StockItemsCreated);
    var createdEvent = (StockItemsBranch.Event.StockItemsCreated) events.get(0);
    assertEquals(stockItemsId, createdEvent.branchId());
    assertEquals(stockId, createdEvent.stockId());
    assertEquals(quantity, createdEvent.quantity());
    assertEquals(parentStockItemsId, createdEvent.parentBranchId());

    // Should have branch and leaf events
    assertTrue(events.stream().anyMatch(e -> e instanceof StockItemsBranch.Event.LeafToBeAdded));
    if (branchEventCount.bucketLevels().size() > 0) {
      assertTrue(events.stream().anyMatch(e -> e instanceof StockItemsBranch.Event.BranchToBeAdded));
    }
  }

  @Test
  void testStockItemsAddQuantityMedium() {
    var stockItemsId = "stock-items-2";
    var stockId = "stock-2";
    var quantityId = "quantity-2";
    var parentStockItemsId = "parent-2";
    var quantity = 450;

    var command = new StockItemsBranch.Command.AddQuantityToTree(
        stockItemsId,
        stockId,
        quantityId,
        quantity,
        parentStockItemsId);

    var state = StockItemsBranch.State.empty();
    var events = state.onCommand(command);

    // First event should be StockItemsCreated
    assertTrue(events.get(0) instanceof StockItemsBranch.Event.StockItemsCreated);
    var createdEvent = (StockItemsBranch.Event.StockItemsCreated) events.get(0);
    assertEquals(stockItemsId, createdEvent.branchId());
    assertEquals(stockId, createdEvent.stockId());
    assertEquals(quantity, createdEvent.quantity());
    assertEquals(parentStockItemsId, createdEvent.parentBranchId());

    // Should have branch and leaf events
    assertTrue(events.stream().anyMatch(e -> e instanceof StockItemsBranch.Event.LeafToBeAdded));
    assertTrue(events.stream().anyMatch(e -> e instanceof StockItemsBranch.Event.BranchToBeAdded));
  }

  @Test
  void testSubStockItemsLists() {
    var stockId = "stock-1";
    var subStockItems = List.of(
        new StockItemsBranch.SubStockItems("sub-1", stockId, 10),
        new StockItemsBranch.SubStockItems("sub-2", stockId, 20),
        new StockItemsBranch.SubStockItems("sub-3", stockId, 30));

    assertEquals(3, subStockItems.size());
    assertEquals(10, subStockItems.get(0).quantity());
    assertEquals(20, subStockItems.get(1).quantity());
    assertEquals(30, subStockItems.get(2).quantity());
    assertEquals(stockId, subStockItems.get(0).stockId());
  }
}
