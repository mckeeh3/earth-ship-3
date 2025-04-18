package io.earthship3.application.stock;

import static akka.Done.done;
import static io.earthship3.ShortUUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.earthship3.domain.stock.StockItemsLeaf;
import io.earthship3.domain.stock.StockItemsLeaf.Quantity;

public class StockItemsLeafEntityTest {
  @Test
  void testCreateLeaf() {
    var testKit = EventSourcedTestKit.of(StockItemsLeafEntity::new);

    var leafId = randomUUID();
    var stockId = "stock-123";
    var quantityId = randomUUID();
    var quantity = Quantity.of(5);
    var parentBranchId = randomUUID();

    var command = new StockItemsLeaf.Command.CreateStockItems(leafId, parentBranchId, stockId, quantityId, quantity);
    var result = testKit.method(StockItemsLeafEntity::createLeaf).invoke(command);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertTrue(result.getAllEvents().size() > 0);

    {
      var event = result.getNextEventOfType(StockItemsLeaf.Event.StockItemsCreated.class);
      assertEquals(leafId, event.leafId());
      assertEquals(stockId, event.stockId());
      assertEquals(quantityId, event.quantityId());
      assertEquals(quantity, event.quantity());
      assertEquals(parentBranchId, event.parentBranchId());
      assertEquals(quantity.allocated(), event.stockOrderItems().size());
      assertTrue(event.stockOrderItems().stream().allMatch(item -> item.orderItemId().isEmpty() && item.orderItemsLeafId().isEmpty()));
    }

    {
      var event = result.getNextEventOfType(StockItemsLeaf.Event.LeafQuantityUpdated.class);
      assertEquals(leafId, event.leafId());
      assertEquals(stockId, event.stockId());
      assertEquals(quantityId, event.quantityId());
      assertEquals(quantity, event.quantity());
      assertEquals(quantity.allocated(), event.stockOrderItems().size());
      assertTrue(event.availableForOrders());
    }

    {
      var event = result.getNextEventOfType(StockItemsLeaf.Event.StockItemsNeedOrderItems.class);
      assertEquals(leafId, event.leafId());
      assertEquals(parentBranchId, event.parentBranchId());
      assertEquals(stockId, event.stockId());
      assertEquals(quantityId, event.quantityId());
      assertEquals(quantity, event.quantity());
      assertEquals(quantity.allocated(), event.stockOrderItems().size());
    }

    {
      var state = testKit.getState();
      assertEquals(leafId, state.leafId());
      assertEquals(stockId, state.stockId());
      assertEquals(quantityId, state.quantityId());
      assertEquals(quantity, state.quantity());
      assertEquals(parentBranchId, state.parentBranchId());
      assertEquals(quantity.allocated(), state.stockOrderItems().size());
      assertTrue(state.stockOrderItems().stream().allMatch(item -> item.orderItemId().isEmpty() && item.orderItemsLeafId().isEmpty()));
      assertTrue(state.availableForOrders());
    }
  }

  @Test
  void testRequestAllocation() {
    var testKit = EventSourcedTestKit.of(StockItemsLeafEntity::new);

    // First create the leaf
    var leafId = randomUUID();
    var stockId = "stock-123";
    var quantityId = randomUUID();
    var quantity = Quantity.of(5);
    var parentBranchId = randomUUID();

    var createCommand = new StockItemsLeaf.Command.CreateStockItems(leafId, parentBranchId, stockId, quantityId, quantity);
    testKit.method(StockItemsLeafEntity::createLeaf).invoke(createCommand);

    // Then request allocation
    var orderItemsLeafId = randomUUID();
    var orderItemIds = List.of(randomUUID(), randomUUID());
    var requestCommand = new StockItemsLeaf.Command.AllocateStockItemsToOrderItems(leafId, orderItemsLeafId, orderItemIds);
    var result = testKit.method(StockItemsLeafEntity::requestAllocation).invoke(requestCommand);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertTrue(result.getAllEvents().size() > 0);

    {
      var event = result.getNextEventOfType(StockItemsLeaf.Event.LeafQuantityUpdated.class);
      assertEquals(leafId, event.leafId());
      assertEquals(parentBranchId, event.parentBranchId());
      assertEquals(stockId, event.stockId());
      assertEquals(quantityId, event.quantityId());
      assertEquals(Quantity.of(quantity.allocated(), quantity.available() - orderItemIds.size()), event.quantity());

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
      var event = result.getNextEventOfType(StockItemsLeaf.Event.StockItemsAllocatedToOrderItems.class);
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
      assertEquals(parentBranchId, state.parentBranchId());
      assertEquals(stockId, state.stockId());
      assertEquals(quantityId, state.quantityId());
      assertEquals(Quantity.of(quantity.allocated(), quantity.available() - orderItemIds.size()), state.quantity());
      assertTrue(state.availableForOrders());

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
    var quantity = Quantity.of(5);
    var parentBranchId = randomUUID();

    var createCommand = new StockItemsLeaf.Command.CreateStockItems(leafId, parentBranchId, stockId, quantityId, quantity);
    testKit.method(StockItemsLeafEntity::createLeaf).invoke(createCommand);

    // Then request the first allocation
    {
      var orderItemsLeafId = randomUUID();
      var orderItemIds = List.of(randomUUID(), randomUUID(), randomUUID());
      var requestCommand = new StockItemsLeaf.Command.AllocateStockItemsToOrderItems(leafId, orderItemsLeafId, orderItemIds);
      var result = testKit.method(StockItemsLeafEntity::requestAllocation).invoke(requestCommand);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertTrue(result.getAllEvents().size() > 0);

      {
        var event = result.getNextEventOfType(StockItemsLeaf.Event.LeafQuantityUpdated.class);
        assertEquals(leafId, event.leafId());
        assertEquals(stockId, event.stockId());
        assertEquals(quantityId, event.quantityId());
        assertEquals(Quantity.of(quantity.allocated(), quantity.available() - orderItemIds.size()), event.quantity());
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
        var event = result.getNextEventOfType(StockItemsLeaf.Event.StockItemsAllocatedToOrderItems.class);
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
        assertEquals(parentBranchId, state.parentBranchId());
        assertEquals(stockId, state.stockId());
        assertEquals(Quantity.of(quantity.allocated(), quantity.available() - orderItemIds.size()), state.quantity());
        assertTrue(state.availableForOrders());

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

    {
      // Then request the second allocation
      var orderItemsLeafId = randomUUID();
      var orderItemIds = List.of(randomUUID(), randomUUID(), randomUUID());
      var requestCommand = new StockItemsLeaf.Command.AllocateStockItemsToOrderItems(leafId, orderItemsLeafId, orderItemIds);
      var result = testKit.method(StockItemsLeafEntity::requestAllocation).invoke(requestCommand);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertTrue(result.getAllEvents().size() > 0);

      {
        var event = result.getNextEventOfType(StockItemsLeaf.Event.LeafQuantityUpdated.class);
        assertEquals(leafId, event.leafId());
        assertEquals(stockId, event.stockId());
        assertEquals(quantityId, event.quantityId());
        assertEquals(Quantity.of(quantity.allocated(), 0), event.quantity());
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
        var event = result.getNextEventOfType(StockItemsLeaf.Event.StockItemsAllocatedToOrderItems.class);
        assertEquals(leafId, event.leafId());
        assertEquals(orderItemsLeafId, event.orderItemsLeafId());
        assertEquals(orderItemIds.size() - 1, event.allocations().size());
        assertTrue(event.allocations().stream()
            .allMatch(allocation -> allocation.stockItemLeafId().equals(leafId) &&
                orderItemIds.contains(allocation.orderItemId())));
      }
    }

    {
      // Then get the state and verify that all items have been allocated
      var state = testKit.getState();
      assertEquals(0, state.quantity().available());
      assertFalse(state.availableForOrders());
    }
  }

  @Test
  void testSetAvailableForOrders() {
    var testKit = EventSourcedTestKit.of(StockItemsLeafEntity::new);

    // First create the leaf
    var leafId = randomUUID();
    var stockId = "stock-123";
    var quantityId = randomUUID();
    var quantity = Quantity.of(5);
    var parentBranchId = randomUUID();

    var createCommand = new StockItemsLeaf.Command.CreateStockItems(leafId, parentBranchId, stockId, quantityId, quantity);
    testKit.method(StockItemsLeafEntity::createLeaf).invoke(createCommand);

    {
      // Then send a set available for orders command
      var setAvailableForOrdersCommand = new StockItemsLeaf.Command.SetAvailableForOrders(leafId, true);
      var setAvailableForOrdersResult = testKit.method(StockItemsLeafEntity::setAvailableForOrders).invoke(setAvailableForOrdersCommand);

      assertTrue(setAvailableForOrdersResult.isReply());
      assertEquals(done(), setAvailableForOrdersResult.getReply());
      assertEquals(1, setAvailableForOrdersResult.getAllEvents().size());

      var event = setAvailableForOrdersResult.getNextEventOfType(StockItemsLeaf.Event.AvailableForOrdersSet.class);
      assertEquals(leafId, event.leafId());
      assertTrue(event.availableForOrders());

      var state = testKit.getState();
      assertTrue(state.availableForOrders());
    }

    {
      // Then send a set available for orders command
      var setAvailableForOrdersCommand = new StockItemsLeaf.Command.SetAvailableForOrders(leafId, false);
      var setAvailableForOrdersResult = testKit.method(StockItemsLeafEntity::setAvailableForOrders).invoke(setAvailableForOrdersCommand);

      assertTrue(setAvailableForOrdersResult.isReply());
      assertEquals(done(), setAvailableForOrdersResult.getReply());
      assertEquals(1, setAvailableForOrdersResult.getAllEvents().size());

      var event = setAvailableForOrdersResult.getNextEventOfType(StockItemsLeaf.Event.AvailableForOrdersSet.class);
      assertEquals(leafId, event.leafId());
      assertFalse(event.availableForOrders());

      var state = testKit.getState();
      assertFalse(state.availableForOrders());
    }
  }

  @Test
  void testCreateOneStockItemsLeafWith15ItemsAllocate5ItemsTo3OrderItemsLeavesAndRelease5Items() {
    var testKit = EventSourcedTestKit.of(StockItemsLeafEntity::new);

    // First create the leaf
    var leafId = randomUUID();
    var stockId = "stock-123";
    var quantityId = randomUUID();
    var quantity = Quantity.of(15);
    var parentBranchId = randomUUID();

    var createCommand = new StockItemsLeaf.Command.CreateStockItems(leafId, parentBranchId, stockId, quantityId, quantity);
    testKit.method(StockItemsLeafEntity::createLeaf).invoke(createCommand);

    {
      // Then request allocation of 5 items to order items leaf 1
      var orderItemsLeafId = randomUUID();
      var orderItemIds = List.of(randomUUID(), randomUUID(), randomUUID(), randomUUID(), randomUUID());
      var command = new StockItemsLeaf.Command.AllocateStockItemsToOrderItems(leafId, orderItemsLeafId, orderItemIds);
      var result = testKit.method(StockItemsLeafEntity::requestAllocation).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertTrue(result.getAllEvents().size() > 0);
    }

    var orderItemsLeafIdToBeReleased = randomUUID();
    var orderItemIdsToBeReleased = List.of(randomUUID(), randomUUID(), randomUUID(), randomUUID(), randomUUID());
    {
      // Then request allocation of 5 items to order items leaf 2
      var command = new StockItemsLeaf.Command.AllocateStockItemsToOrderItems(leafId, orderItemsLeafIdToBeReleased, orderItemIdsToBeReleased);
      var result = testKit.method(StockItemsLeafEntity::requestAllocation).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertTrue(result.getAllEvents().size() > 0);
    }

    {
      // Then request allocation of 5 items to order items leaf 3
      var orderItemsLeafId = randomUUID();
      var orderItemIds = List.of(randomUUID(), randomUUID(), randomUUID(), randomUUID(), randomUUID());
      var command = new StockItemsLeaf.Command.AllocateStockItemsToOrderItems(leafId, orderItemsLeafId, orderItemIds);
      var result = testKit.method(StockItemsLeafEntity::requestAllocation).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertTrue(result.getAllEvents().size() > 0);
    }

    var allocationsToBeReleased = List.<StockItemsLeaf.Allocation>of();
    {
      // Then get the state and verify that all items have been allocated
      var state = testKit.getState();
      assertEquals(Quantity.of(quantity.allocated(), 0), state.quantity());
      allocationsToBeReleased = state.stockOrderItems().stream()
          .filter(item -> item.orderItemsLeafId().isPresent() &&
              item.orderItemsLeafId().get().equals(orderItemsLeafIdToBeReleased))
          .map(item -> new StockItemsLeaf.Allocation(leafId, item.stockItemId(), orderItemsLeafIdToBeReleased, item.orderItemId().get()))
          .toList();
    }

    {
      // Then release the allocation
      var releaseCommand = new StockItemsLeaf.Command.ReleaseOrderItemsAllocation(leafId, orderItemsLeafIdToBeReleased, allocationsToBeReleased);
      var result = testKit.method(StockItemsLeafEntity::releaseAllocation).invoke(releaseCommand);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertTrue(result.getAllEvents().size() > 0);
    }

    {
      // Then get the state quantity is 5
      var state = testKit.getState();
      assertEquals(Quantity.of(quantity.allocated(), quantity.available() - allocationsToBeReleased.size()), state.quantity());
    }
  }

  @Test
  void testGetState() {
    var testKit = EventSourcedTestKit.of(StockItemsLeafEntity::new);

    // First create the leaf
    var leafId = randomUUID();
    var stockId = "stock-123";
    var quantityId = randomUUID();
    var quantity = Quantity.of(5);
    var parentBranchId = randomUUID();

    var createCommand = new StockItemsLeaf.Command.CreateStockItems(leafId, parentBranchId, stockId, quantityId, quantity);
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
    assertEquals(quantity.available(), state.stockOrderItems().size());
  }
}
