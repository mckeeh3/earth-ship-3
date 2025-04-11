package io.earthship3.application.stock;

import static akka.Done.done;
import static io.earthship3.ShortUUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.earthship3.domain.stock.StockItemsLeaf;

public class StockItemsLeafEntityTest {
  @Test
  void testCreateLeaf() {
    var testKit = EventSourcedTestKit.of(StockItemsLeafEntity::new);

    var leafId = randomUUID();
    var stockId = "stock-123";
    var quantityId = randomUUID();
    var quantity = 5;
    var parentBranchId = randomUUID();

    var command = new StockItemsLeaf.Command.CreateLeaf(leafId, parentBranchId, stockId, quantityId, quantity);
    var result = testKit.method(StockItemsLeafEntity::createLeaf).invoke(command);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertTrue(result.getAllEvents().size() > 0);

    {
      var event = result.getNextEventOfType(StockItemsLeaf.Event.LeafCreated.class);
      assertEquals(leafId, event.leafId());
      assertEquals(stockId, event.stockId());
      assertEquals(quantityId, event.quantityId());
      assertEquals(quantity, event.quantity());
      assertEquals(parentBranchId, event.parentBranchId());
      assertEquals(quantity, event.stockOrderItems().size());
      assertTrue(event.stockOrderItems().stream().allMatch(item -> item.orderItemId().isEmpty() && item.orderItemsLeafId().isEmpty()));
    }

    {
      var state = testKit.getState();
      assertEquals(leafId, state.leafId());
      assertEquals(stockId, state.stockId());
      assertEquals(quantityId, state.quantityId());
      assertEquals(quantity, state.quantity());
      assertEquals(parentBranchId, state.parentBranchId());
      assertEquals(quantity, state.stockOrderItems().size());
      assertTrue(state.stockOrderItems().stream().allMatch(item -> item.orderItemId().isEmpty() && item.orderItemsLeafId().isEmpty()));
    }
  }

  @Test
  void testRequestAllocation() {
    var testKit = EventSourcedTestKit.of(StockItemsLeafEntity::new);

    // First create the leaf
    var leafId = randomUUID();
    var stockId = "stock-123";
    var quantityId = randomUUID();
    var quantity = 5;
    var parentBranchId = randomUUID();

    var createCommand = new StockItemsLeaf.Command.CreateLeaf(leafId, parentBranchId, stockId, quantityId, quantity);
    testKit.method(StockItemsLeafEntity::createLeaf).invoke(createCommand);

    // Then request allocation
    var orderItemsLeafId = randomUUID();
    var orderItemIds = List.of(randomUUID(), randomUUID());
    var requestCommand = new StockItemsLeaf.Command.RequestAllocation(leafId, orderItemsLeafId, orderItemIds);
    var result = testKit.method(StockItemsLeafEntity::requestAllocation).invoke(requestCommand);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertTrue(result.getAllEvents().size() > 0);

    {
      var event = result.getNextEventOfType(StockItemsLeaf.Event.LeafQuantityUpdated.class);
      assertEquals(leafId, event.leafId());
      assertEquals(stockId, event.stockId());
      assertEquals(quantityId, event.quantityId());
      assertEquals(quantity - orderItemIds.size(), event.quantity());
      assertEquals(parentBranchId, event.parentBranchId());

      // Verify that the requested items are allocated
      var allocatedItems = event.stockOrderItems().stream()
          .filter(item -> item.orderItemId().isPresent())
          .toList();
      assertEquals(orderItemIds.size(), allocatedItems.size());
      assertTrue(allocatedItems.stream()
          .allMatch(item -> item.orderItemsLeafId().isPresent() &&
              item.orderItemsLeafId().get().equals(orderItemsLeafId) &&
              orderItemIds.contains(item.orderItemId().get())));
    }

    {
      var event = result.getNextEventOfType(StockItemsLeaf.Event.AllocationResponse.class);
      assertEquals(leafId, event.leafId());
      assertEquals(orderItemsLeafId, event.orderItemsLeafId());
      assertEquals(orderItemIds.size(), event.allocations().size());
      assertTrue(event.allocations().stream()
          .allMatch(allocation -> allocation.stockItemLeafId().equals(leafId) &&
              orderItemIds.contains(allocation.orderItemId())));
    }

    {
      var state = testKit.getState();
      assertEquals(leafId, state.leafId());
      assertEquals(stockId, state.stockId());
      assertEquals(quantityId, state.quantityId());
      assertEquals(quantity - orderItemIds.size(), state.quantity());
      assertEquals(parentBranchId, state.parentBranchId());

      // Verify that the state reflects the allocation
      var allocatedItems = state.stockOrderItems().stream()
          .filter(item -> item.orderItemId().isPresent())
          .toList();
      assertEquals(orderItemIds.size(), allocatedItems.size());
      assertTrue(allocatedItems.stream()
          .allMatch(item -> item.orderItemsLeafId().isPresent() &&
              item.orderItemsLeafId().get().equals(orderItemsLeafId) &&
              orderItemIds.contains(item.orderItemId().get())));
    }
  }

  @Test
  void testTwoAllocationFromLeafWithFiveItemsAllocationOneRequestsThreeAllocationTwoRequestsFive() {
    var testKit = EventSourcedTestKit.of(StockItemsLeafEntity::new);

    // First create the leaf
    var leafId = randomUUID();
    var stockId = "stock-123";
    var quantityId = randomUUID();
    var quantity = 5;
    var parentBranchId = randomUUID();

    var createCommand = new StockItemsLeaf.Command.CreateLeaf(leafId, parentBranchId, stockId, quantityId, quantity);
    testKit.method(StockItemsLeafEntity::createLeaf).invoke(createCommand);

    // Then request the first allocation
    {
      var orderItemsLeafId = randomUUID();
      var orderItemIds = List.of(randomUUID(), randomUUID(), randomUUID());
      var requestCommand = new StockItemsLeaf.Command.RequestAllocation(leafId, orderItemsLeafId, orderItemIds);
      var result = testKit.method(StockItemsLeafEntity::requestAllocation).invoke(requestCommand);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertTrue(result.getAllEvents().size() > 0);

      {
        var event = result.getNextEventOfType(StockItemsLeaf.Event.LeafQuantityUpdated.class);
        assertEquals(leafId, event.leafId());
        assertEquals(stockId, event.stockId());
        assertEquals(quantityId, event.quantityId());
        assertEquals(quantity - orderItemIds.size(), event.quantity());
        assertEquals(parentBranchId, event.parentBranchId());

        // Verify that the requested items are allocated
        var allocatedItems = event.stockOrderItems().stream()
            .filter(item -> item.orderItemId().isPresent())
            .toList();
        assertEquals(orderItemIds.size(), allocatedItems.size());
        assertTrue(allocatedItems.stream()
            .allMatch(item -> item.orderItemsLeafId().isPresent() &&
                item.orderItemsLeafId().get().equals(orderItemsLeafId) &&
                orderItemIds.contains(item.orderItemId().get())));
      }

      {
        var event = result.getNextEventOfType(StockItemsLeaf.Event.AllocationResponse.class);
        assertEquals(leafId, event.leafId());
        assertEquals(orderItemsLeafId, event.orderItemsLeafId());
        assertEquals(orderItemIds.size(), event.allocations().size());
        assertTrue(event.allocations().stream()
            .allMatch(allocation -> allocation.stockItemLeafId().equals(leafId) &&
                orderItemIds.contains(allocation.orderItemId())));
      }
    }

    // Then request the second allocation
    {
      var orderItemsLeafId = randomUUID();
      var orderItemIds = List.of(randomUUID(), randomUUID(), randomUUID());
      var requestCommand = new StockItemsLeaf.Command.RequestAllocation(leafId, orderItemsLeafId, orderItemIds);
      var result = testKit.method(StockItemsLeafEntity::requestAllocation).invoke(requestCommand);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertTrue(result.getAllEvents().size() > 0);

      {
        var event = result.getNextEventOfType(StockItemsLeaf.Event.LeafQuantityUpdated.class);
        assertEquals(leafId, event.leafId());
        assertEquals(stockId, event.stockId());
        assertEquals(quantityId, event.quantityId());
        assertEquals(0, event.quantity());
        assertEquals(parentBranchId, event.parentBranchId());

        // Verify that the requested items are allocated
        var allocatedItems = event.stockOrderItems().stream()
            .filter(item -> item.orderItemsLeafId().isPresent() &&
                item.orderItemsLeafId().get().equals(orderItemsLeafId))
            .toList();
        assertEquals(orderItemIds.size() - 1, allocatedItems.size());
        assertTrue(allocatedItems.stream()
            .allMatch(item -> orderItemIds.contains(item.orderItemId().get())));
      }

      {
        var event = result.getNextEventOfType(StockItemsLeaf.Event.AllocationResponse.class);
        assertEquals(leafId, event.leafId());
        assertEquals(orderItemsLeafId, event.orderItemsLeafId());
        assertEquals(orderItemIds.size() - 1, event.allocations().size());
        assertTrue(event.allocations().stream()
            .allMatch(allocation -> allocation.stockItemLeafId().equals(leafId) &&
                orderItemIds.contains(allocation.orderItemId())));
      }
    }
  }

  @Test
  void testGetState() {
    var testKit = EventSourcedTestKit.of(StockItemsLeafEntity::new);

    // First create the leaf
    var leafId = randomUUID();
    var stockId = "stock-123";
    var quantityId = randomUUID();
    var quantity = 5;
    var parentBranchId = randomUUID();

    var createCommand = new StockItemsLeaf.Command.CreateLeaf(leafId, parentBranchId, stockId, quantityId, quantity);
    testKit.method(StockItemsLeafEntity::createLeaf).invoke(createCommand);

    // Then get the state
    var result = testKit.method(StockItemsLeafEntity::get).invoke();

    assertTrue(result.isReply());
    var state = result.getReply();
    assertNotNull(state);
    assertEquals(leafId, state.leafId());
    assertEquals(stockId, state.stockId());
    assertEquals(quantityId, state.quantityId());
    assertEquals(quantity, state.quantity());
    assertEquals(parentBranchId, state.parentBranchId());
    assertEquals(quantity, state.stockOrderItems().size());
  }
}
