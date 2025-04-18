package io.earthship3.application.order;

import static akka.Done.done;
import static io.earthship3.ShortUUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.earthship3.domain.order.OrderItemsLeaf;
import io.earthship3.domain.order.OrderItemsLeaf.Quantity;

public class OrderItemsLeafEntityTest {
  @Test
  public void testCreateOrderItemLeaf() {
    var testKit = EventSourcedTestKit.of(OrderItemsLeafEntity::new);

    var leafId = randomUUID();
    var parentBranchId = randomUUID();
    var quantityId = "123";
    var stockId = "234";
    var quantity = Quantity.of(5);

    var command = new OrderItemsLeaf.Command.CreateOrderItems(leafId, parentBranchId, stockId, quantityId, quantity);
    var result = testKit.method(OrderItemsLeafEntity::createLeaf).invoke(command);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertEquals(3, result.getAllEvents().size());

    {
      var event = result.getNextEventOfType(OrderItemsLeaf.Event.OrderItemsCreated.class);
      assertEquals(leafId, event.leafId());
      assertEquals(parentBranchId, event.parentBranchId());
      assertEquals(quantityId, event.quantityId());
      assertEquals(stockId, event.stockId());
      assertEquals(quantity, event.quantity());
      assertEquals(quantity.allocated(), event.orderStockItems().size());
    }

    {
      var event = result.getNextEventOfType(OrderItemsLeaf.Event.LeafQuantityUpdated.class);
      assertEquals(leafId, event.leafId());
      assertEquals(parentBranchId, event.parentBranchId());
      assertEquals(quantityId, event.quantityId());
      assertEquals(stockId, event.stockId());
      assertEquals(quantity, event.quantity());
      assertEquals(quantity.allocated(), event.orderStockItems().size());
    }

    {
      var event = result.getNextEventOfType(OrderItemsLeaf.Event.OrderItemsNeedStockItems.class);
      assertEquals(leafId, event.leafId());
      assertEquals(parentBranchId, event.parentBranchId());
      assertEquals(quantityId, event.quantityId());
      assertEquals(stockId, event.stockId());
      assertEquals(quantity, event.quantity());
      assertEquals(quantity.allocated(), event.orderStockItems().size());
    }

    {
      var state = testKit.getState();
      assertEquals(leafId, state.leafId());
      assertEquals(parentBranchId, state.parentBranchId());
      assertEquals(quantityId, state.quantityId());
      assertEquals(stockId, state.stockId());
      assertEquals(quantity, state.quantity());
      assertEquals(quantity.allocated(), state.orderStockItems().size());
    }
  }

  @Test
  void testRequestAllocation() {
    var testKit = EventSourcedTestKit.of(OrderItemsLeafEntity::new);

    // First create the leaf
    var leafId = randomUUID();
    var quantityId = randomUUID();
    var quantity = Quantity.of(5);
    var parentBranchId = randomUUID();
    var stockId = "stock-123";

    var createCommand = new OrderItemsLeaf.Command.CreateOrderItems(leafId, parentBranchId, stockId, quantityId, quantity);
    testKit.method(OrderItemsLeafEntity::createLeaf).invoke(createCommand);

    // Then set to back ordered on because allocation is only allowed when the leaf is back ordered
    var setBackOrderedCommand = new OrderItemsLeaf.Command.SetBackOrdered(leafId, Optional.of(Instant.now()));
    testKit.method(OrderItemsLeafEntity::setToBackOrdered).invoke(setBackOrderedCommand);

    // Then request allocation
    var stockItemsLeafId = randomUUID();
    var stockItemIds = List.of(randomUUID(), randomUUID(), randomUUID());
    var requestCommand = new OrderItemsLeaf.Command.AllocateOrderItemsToStockItems(leafId, stockItemsLeafId, stockItemIds);
    var result = testKit.method(OrderItemsLeafEntity::requestAllocation).invoke(requestCommand);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertTrue(result.getAllEvents().size() > 0);

    {
      var event = result.getNextEventOfType(OrderItemsLeaf.Event.LeafQuantityUpdated.class);
      assertEquals(leafId, event.leafId());
      assertEquals(parentBranchId, event.parentBranchId());
      assertEquals(quantityId, event.quantityId());
      assertEquals(stockId, event.stockId());
      assertEquals(Quantity.of(quantity.allocated(), quantity.available() - stockItemIds.size()), event.quantity());

      // Verify that the requested items are allocated
      var allocatedItems = event.orderStockItems().stream()
          .filter(item -> item.stockItemId().isPresent())
          .toList();
      assertEquals(stockItemIds.size(), allocatedItems.size());
      assertTrue(allocatedItems.stream()
          .allMatch(item -> item.stockItemsLeafId().isPresent() &&
              item.stockItemsLeafId().get().equals(stockItemsLeafId) &&
              stockItemIds.contains(item.stockItemId().get())));
    }

    {
      var event = result.getNextEventOfType(OrderItemsLeaf.Event.OrderItemsAllocatedToStockItems.class);
      assertEquals(leafId, event.leafId());
      assertEquals(stockItemsLeafId, event.stockItemsLeafId());
      assertEquals(stockItemIds.size(), event.allocations().size());
      assertTrue(event.allocations().stream()
          .allMatch(allocation -> allocation.stockItemsLeafId().equals(stockItemsLeafId) &&
              stockItemIds.contains(allocation.stockItemId())));
    }

    {
      var state = testKit.getState();
      assertEquals(leafId, state.leafId());
      assertEquals(parentBranchId, state.parentBranchId());
      assertEquals(quantityId, state.quantityId());
      assertEquals(stockId, state.stockId());
      assertEquals(Quantity.of(quantity.allocated(), quantity.available() - stockItemIds.size()), state.quantity());
      assertTrue(state.readyToShipAt().isEmpty());
      assertTrue(state.backOrderedAt().isEmpty());

      // Verify that the state reflects the allocation
      var allocatedItems = state.orderStockItems().stream()
          .filter(item -> item.stockItemId().isPresent())
          .toList();
      assertEquals(stockItemIds.size(), allocatedItems.size());
      assertTrue(allocatedItems.stream()
          .allMatch(item -> item.stockItemsLeafId().isPresent() &&
              item.stockItemsLeafId().get().equals(stockItemsLeafId) &&
              stockItemIds.contains(item.stockItemId().get())));
    }
  }

  @Test
  void testTwoAllocationFromLeafWithFiveItemsAllocationOneRequestsThreeAllocationTwoRequestsFive() {
    var testKit = EventSourcedTestKit.of(OrderItemsLeafEntity::new);

    // First create the leaf
    var leafId = randomUUID();
    var quantityId = randomUUID();
    var quantity = Quantity.of(5);
    var parentBranchId = randomUUID();
    var stockId = "stock-123";

    var createCommand = new OrderItemsLeaf.Command.CreateOrderItems(leafId, parentBranchId, stockId, quantityId, quantity);
    testKit.method(OrderItemsLeafEntity::createLeaf).invoke(createCommand);

    {
      // Then set to back ordered on because allocation is only allowed when the leaf is back ordered
      var setBackOrderedCommand = new OrderItemsLeaf.Command.SetBackOrdered(leafId, Optional.of(Instant.now()));
      testKit.method(OrderItemsLeafEntity::setToBackOrdered).invoke(setBackOrderedCommand);

      // Then request the first allocation
      var stockItemsLeafId = randomUUID();
      var stockItemIds = List.of(randomUUID(), randomUUID(), randomUUID());
      var requestCommand = new OrderItemsLeaf.Command.AllocateOrderItemsToStockItems(leafId, stockItemsLeafId, stockItemIds);
      var result = testKit.method(OrderItemsLeafEntity::requestAllocation).invoke(requestCommand);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertTrue(result.getAllEvents().size() > 0);

      {
        var event = result.getNextEventOfType(OrderItemsLeaf.Event.LeafQuantityUpdated.class);
        assertEquals(leafId, event.leafId());
        assertEquals(parentBranchId, event.parentBranchId());
        assertEquals(quantityId, event.quantityId());
        assertEquals(stockId, event.stockId());
        assertEquals(Quantity.of(quantity.allocated(), quantity.available() - stockItemIds.size()), event.quantity());

        // Verify that the requested items are allocated
        var allocatedItems = event.orderStockItems().stream()
            .filter(item -> item.stockItemId().isPresent())
            .toList();
        assertEquals(stockItemIds.size(), allocatedItems.size());
        assertTrue(allocatedItems.stream()
            .allMatch(item -> item.stockItemsLeafId().isPresent() &&
                item.stockItemsLeafId().get().equals(stockItemsLeafId) &&
                stockItemIds.contains(item.stockItemId().get())));
      }

      {
        var event = result.getNextEventOfType(OrderItemsLeaf.Event.OrderItemsAllocatedToStockItems.class);
        assertEquals(leafId, event.leafId());
        assertEquals(stockItemsLeafId, event.stockItemsLeafId());
        assertEquals(stockItemIds.size(), event.allocations().size());
        assertTrue(event.allocations().stream()
            .allMatch(allocation -> allocation.stockItemsLeafId().equals(stockItemsLeafId) &&
                stockItemIds.contains(allocation.stockItemId())));
      }

      {
        var state = testKit.getState();
        assertEquals(leafId, state.leafId());
        assertEquals(parentBranchId, state.parentBranchId());
        assertEquals(quantityId, state.quantityId());
        assertEquals(stockId, state.stockId());
        assertEquals(Quantity.of(quantity.allocated(), quantity.available() - stockItemIds.size()), state.quantity());
        assertTrue(state.readyToShipAt().isEmpty());
        assertTrue(state.backOrderedAt().isEmpty());

        // Verify that the state reflects the allocation
        var allocatedItems = state.orderStockItems().stream()
            .filter(item -> item.stockItemId().isPresent())
            .toList();
        assertEquals(stockItemIds.size(), allocatedItems.size());
        assertTrue(allocatedItems.stream()
            .allMatch(item -> item.stockItemsLeafId().isPresent() &&
                item.stockItemsLeafId().get().equals(stockItemsLeafId) &&
                stockItemIds.contains(item.stockItemId().get())));
      }
    }

    {
      // Then set to back ordered on because allocation is only allowed when the leaf is back ordered
      var setBackOrderedCommand = new OrderItemsLeaf.Command.SetBackOrdered(leafId, Optional.of(Instant.now()));
      testKit.method(OrderItemsLeafEntity::setToBackOrdered).invoke(setBackOrderedCommand);

      // Then request the second allocation
      var stockItemsLeafId = randomUUID();
      var stockItemIds = List.of(randomUUID(), randomUUID(), randomUUID());
      var requestCommand = new OrderItemsLeaf.Command.AllocateOrderItemsToStockItems(leafId, stockItemsLeafId, stockItemIds);
      var result = testKit.method(OrderItemsLeafEntity::requestAllocation).invoke(requestCommand);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertTrue(result.getAllEvents().size() > 0);

      {
        var event = result.getNextEventOfType(OrderItemsLeaf.Event.LeafQuantityUpdated.class);
        assertEquals(leafId, event.leafId());
        assertEquals(parentBranchId, event.parentBranchId());
        assertEquals(quantityId, event.quantityId());
        assertEquals(stockId, event.stockId());
        assertEquals(0, event.quantity().available());

        // Verify that the requested items are allocated
        var allocatedItems = event.orderStockItems().stream()
            .filter(item -> item.stockItemId().isPresent() &&
                item.stockItemsLeafId().get().equals(stockItemsLeafId))
            .toList();
        assertEquals(stockItemIds.size() - 1, allocatedItems.size());
        assertTrue(allocatedItems.stream()
            .allMatch(item -> item.stockItemsLeafId().isPresent() &&
                item.stockItemsLeafId().get().equals(stockItemsLeafId) &&
                stockItemIds.contains(item.stockItemId().get())));
      }

      {
        var event = result.getNextEventOfType(OrderItemsLeaf.Event.OrderItemsAllocatedToStockItems.class);
        assertEquals(leafId, event.leafId());
        assertEquals(stockItemsLeafId, event.stockItemsLeafId());
        assertEquals(stockItemIds.size() - 1, event.allocations().size());
        assertTrue(event.allocations().stream()
            .allMatch(allocation -> allocation.stockItemsLeafId().equals(stockItemsLeafId) &&
                stockItemIds.contains(allocation.stockItemId())));
      }
    }

    {
      // Then get the state and verify that all items have been allocated
      var state = testKit.getState();
      assertEquals(0, state.quantity().available());
      assertTrue(state.readyToShipAt().isPresent());
      assertTrue(state.backOrderedAt().isEmpty());
    }
  }

  @Test
  void testSetBackOrdered() {
    var testKit = EventSourcedTestKit.of(OrderItemsLeafEntity::new);

    // First create the leaf
    var leafId = randomUUID();
    var quantityId = randomUUID();
    var quantity = Quantity.of(5);
    var parentBranchId = randomUUID();
    var stockId = "stock-123";

    var createCommand = new OrderItemsLeaf.Command.CreateOrderItems(leafId, parentBranchId, stockId, quantityId, quantity);
    testKit.method(OrderItemsLeafEntity::createLeaf).invoke(createCommand);

    {
      // Then send a back order command with a back ordered at
      var backOrderedAt = Instant.now();
      var backOrderCommand = new OrderItemsLeaf.Command.SetBackOrdered(leafId, Optional.of(backOrderedAt));
      var backOrderResult = testKit.method(OrderItemsLeafEntity::setToBackOrdered).invoke(backOrderCommand);

      assertTrue(backOrderResult.isReply());
      assertEquals(done(), backOrderResult.getReply());
      assertEquals(1, backOrderResult.getAllEvents().size());

      var event = backOrderResult.getNextEventOfType(OrderItemsLeaf.Event.BackOrderedSet.class);
      assertEquals(leafId, event.leafId());
      assertTrue(event.backOrderedAt().isPresent());
      assertEquals(backOrderedAt, event.backOrderedAt().get());

      var state = testKit.getState();
      assertTrue(state.readyToShipAt().isEmpty());
      assertTrue(state.backOrderedAt().isPresent());
      assertEquals(backOrderedAt, state.backOrderedAt().get());
    }

    {
      // Then send a back order command without a back ordered at
      var backOrderCommand = new OrderItemsLeaf.Command.SetBackOrdered(leafId, Optional.empty());
      var backOrderResult = testKit.method(OrderItemsLeafEntity::setToBackOrdered).invoke(backOrderCommand);

      assertTrue(backOrderResult.isReply());
      assertEquals(done(), backOrderResult.getReply());
      assertEquals(1, backOrderResult.getAllEvents().size());

      var event = backOrderResult.getNextEventOfType(OrderItemsLeaf.Event.BackOrderedSet.class);
      assertEquals(leafId, event.leafId());
      assertTrue(event.backOrderedAt().isEmpty());

      var state = testKit.getState();
      assertTrue(state.readyToShipAt().isEmpty());
      assertTrue(state.backOrderedAt().isEmpty());
    }
  }

  @Test
  void testCreateOrderItemsLeafWith15ItemsAllocate5ItemsTo3StockItemsLeavesAndRelease5Items() {
    var testKit = EventSourcedTestKit.of(OrderItemsLeafEntity::new);

    // First create the leaf
    var leafId = randomUUID();
    var quantityId = randomUUID();
    var quantity = Quantity.of(15);
    var parentBranchId = randomUUID();
    var stockId = "stock-123";

    var createCommand = new OrderItemsLeaf.Command.CreateOrderItems(leafId, parentBranchId, stockId, quantityId, quantity);
    testKit.method(OrderItemsLeafEntity::createLeaf).invoke(createCommand);

    {
      // Then send a back order command because allocation is only allowed when in a back ordered state
      var backOrderCommand = new OrderItemsLeaf.Command.SetBackOrdered(leafId, Optional.of(Instant.now()));
      testKit.method(OrderItemsLeafEntity::setToBackOrdered).invoke(backOrderCommand);

      // Then request allocation of 5 items to stock items leaf 1
      var stockItemsLeafId = randomUUID();
      var stockItemIds = List.of(randomUUID(), randomUUID(), randomUUID(), randomUUID(), randomUUID());
      var command = new OrderItemsLeaf.Command.AllocateOrderItemsToStockItems(leafId, stockItemsLeafId, stockItemIds);
      var result = testKit.method(OrderItemsLeafEntity::requestAllocation).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertTrue(result.getAllEvents().size() > 0);
    }

    var stockItemsLeafIdToBeReleased = randomUUID();
    var stockItemIdsToBeReleased = List.of(randomUUID(), randomUUID(), randomUUID(), randomUUID(), randomUUID());
    {
      // Then send a back order command because allocation is only allowed when in a back ordered state
      var backOrderCommand = new OrderItemsLeaf.Command.SetBackOrdered(leafId, Optional.of(Instant.now()));
      testKit.method(OrderItemsLeafEntity::setToBackOrdered).invoke(backOrderCommand);

      // Then request allocation of 5 items to stock items leaf 2
      var command = new OrderItemsLeaf.Command.AllocateOrderItemsToStockItems(leafId, stockItemsLeafIdToBeReleased, stockItemIdsToBeReleased);
      var result = testKit.method(OrderItemsLeafEntity::requestAllocation).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertTrue(result.getAllEvents().size() > 0);
    }

    {
      // Then send a back order command because allocation is only allowed when in a back ordered state
      var backOrderCommand = new OrderItemsLeaf.Command.SetBackOrdered(leafId, Optional.of(Instant.now()));
      testKit.method(OrderItemsLeafEntity::setToBackOrdered).invoke(backOrderCommand);

      // Then request allocation of 5 items to stock items leaf 3
      var stockItemsLeafId = randomUUID();
      var stockItemIds = List.of(randomUUID(), randomUUID(), randomUUID(), randomUUID(), randomUUID());
      var command = new OrderItemsLeaf.Command.AllocateOrderItemsToStockItems(leafId, stockItemsLeafId, stockItemIds);
      var result = testKit.method(OrderItemsLeafEntity::requestAllocation).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertTrue(result.getAllEvents().size() > 0);
    }

    var allocationsToBeReleased = List.<OrderItemsLeaf.Allocation>of();
    {
      // Then get the state and verify that all items have been allocated
      var state = testKit.getState();
      assertEquals(0, state.quantity().available());
      allocationsToBeReleased = state.orderStockItems().stream()
          .filter(item -> item.stockItemsLeafId().isPresent() &&
              item.stockItemsLeafId().get().equals(stockItemsLeafIdToBeReleased))
          .map(item -> new OrderItemsLeaf.Allocation(leafId, item.orderItemId(), stockItemsLeafIdToBeReleased, item.stockItemId().get()))
          .toList();
    }

    {
      // Then release the allocation
      var releaseCommand = new OrderItemsLeaf.Command.ReleaseStockItemsAllocation(leafId, stockItemsLeafIdToBeReleased, allocationsToBeReleased);
      var result = testKit.method(OrderItemsLeafEntity::releaseAllocation).invoke(releaseCommand);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertTrue(result.getAllEvents().size() > 0);
    }

    {
      // Verify that the state quantity is 5
      var state = testKit.getState();
      assertEquals(5, state.quantity().available());
    }
  }

  @Test
  void testGetState() {
    var testKit = EventSourcedTestKit.of(OrderItemsLeafEntity::new);

    // First create the leaf
    var leafId = randomUUID();
    var quantityId = randomUUID();
    var quantity = Quantity.of(5);
    var parentBranchId = randomUUID();
    var stockId = "stock-123";

    var createCommand = new OrderItemsLeaf.Command.CreateOrderItems(leafId, parentBranchId, stockId, quantityId, quantity);
    testKit.method(OrderItemsLeafEntity::createLeaf).invoke(createCommand);

    // Then get the state
    var result = testKit.method(OrderItemsLeafEntity::get).invoke();

    assertTrue(result.isReply());
    var state = result.getReply();
    assertNotNull(state);
    assertEquals(leafId, state.leafId());
    assertEquals(parentBranchId, state.parentBranchId());
    assertEquals(quantityId, state.quantityId());
    assertEquals(stockId, state.stockId());
    assertEquals(quantity, state.quantity());
    assertEquals(quantity.allocated(), state.orderStockItems().size());
  }
}
