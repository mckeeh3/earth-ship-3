package io.earthship3.application.stock;

import static io.earthship3.ShortUUID.randomUUID;

import static akka.Done.done;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.earthship3.domain.stock.StockItemsBranch;

public class StockItemsBranchEntityTest {
  @Test
  void testAddQuantityForSmallQuantity() {
    var testKit = EventSourcedTestKit.of(StockItemsBranchEntity::new);

    var branchId = randomUUID();
    var stockId = "stock-123";
    var quantityId = randomUUID();
    var quantity = 5;
    var parentBranchId = randomUUID();

    var command = new StockItemsBranch.Command.AddQuantityToTree(branchId, stockId, quantityId, quantity, parentBranchId);
    var result = testKit.method(StockItemsBranchEntity::addQuantity).invoke(command);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertTrue(result.getAllEvents().size() > 0);

    {
      var event = result.getNextEventOfType(StockItemsBranch.Event.StockItemsCreated.class);
      assertEquals(branchId, event.branchId());
      assertEquals(stockId, event.stockId());
      assertEquals(quantityId, event.quantityId());
      assertEquals(quantity, event.quantity());
      assertEquals(parentBranchId, event.parentBranchId());
      assertTrue(event.subBranches().size() > 0);
      assertTrue(event.leaves().size() > 0);
    }

    {
      var state = testKit.getState();
      assertEquals(branchId, state.branchId());
      assertEquals(stockId, state.stockId());
      assertEquals(quantityId, state.quantityId());
      assertEquals(quantity, state.quantity());
      assertEquals(parentBranchId, state.parentBranchId());
      assertTrue(state.subBranches().size() > 0);
      assertTrue(state.leaves().size() > 0);
    }
  }

  @Test
  void testAddQuantityForLargeQuantity() {
    var testKit = EventSourcedTestKit.of(StockItemsBranchEntity::new);

    var branchId = randomUUID();
    var stockId = "stock-123";
    var quantityId = randomUUID();
    var quantity = 1000;
    var parentBranchId = randomUUID();

    var command = new StockItemsBranch.Command.AddQuantityToTree(branchId, stockId, quantityId, quantity, parentBranchId);
    var result = testKit.method(StockItemsBranchEntity::addQuantity).invoke(command);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertTrue(result.getAllEvents().size() > 0);

    {
      var event = result.getNextEventOfType(StockItemsBranch.Event.StockItemsCreated.class);
      assertEquals(branchId, event.branchId());
      assertEquals(stockId, event.stockId());
      assertEquals(quantityId, event.quantityId());
      assertEquals(quantity, event.quantity());
    }

    {
      var state = testKit.getState();
      assertEquals(branchId, state.branchId());
      assertEquals(stockId, state.stockId());
      assertEquals(quantityId, state.quantityId());
      assertEquals(quantity, state.quantity());
    }

    {
      var branchQuantitySum = result.getAllEvents().stream()
          .filter(e -> e instanceof StockItemsBranch.Event.BranchToBeAdded)
          .mapToInt(e -> ((StockItemsBranch.Event.BranchToBeAdded) e).subStockItems().quantity())
          .sum();
      var leafQuantitySum = result.getAllEvents().stream()
          .filter(e -> e instanceof StockItemsBranch.Event.LeafToBeAdded)
          .mapToInt(e -> ((StockItemsBranch.Event.LeafToBeAdded) e).subStockItems().quantity())
          .sum();

      assertEquals(quantity, branchQuantitySum + leafQuantitySum);
    }
  }

  @Test
  void testAddQuantityForVeryLargeQuantity() {
    var testKit = EventSourcedTestKit.of(StockItemsBranchEntity::new);

    var branchId = randomUUID();
    var stockId = "stock-123";
    var quantityId = randomUUID();
    var quantity = 1_000_000; // Very large quantity
    var parentBranchId = randomUUID();

    var command = new StockItemsBranch.Command.AddQuantityToTree(branchId, stockId, quantityId, quantity, parentBranchId);
    {
      var result = testKit.method(StockItemsBranchEntity::addQuantity).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertTrue(result.getAllEvents().size() > 0);

      {
        var event = result.getNextEventOfType(StockItemsBranch.Event.StockItemsCreated.class);
        assertEquals(branchId, event.branchId());
        assertEquals(stockId, event.stockId());
        assertEquals(quantityId, event.quantityId());
        assertEquals(quantity, event.quantity());
      }

      {
        var branchQuantitySum = result.getAllEvents().stream()
            .filter(e -> e instanceof StockItemsBranch.Event.BranchToBeAdded)
            .mapToInt(e -> ((StockItemsBranch.Event.BranchToBeAdded) e).subStockItems().quantity())
            .sum();
        var leafQuantitySum = result.getAllEvents().stream()
            .filter(e -> e instanceof StockItemsBranch.Event.LeafToBeAdded)
            .mapToInt(e -> ((StockItemsBranch.Event.LeafToBeAdded) e).subStockItems().quantity())
            .sum();

        assertEquals(quantity, branchQuantitySum + leafQuantitySum);
      }
    }

    {
      var state = testKit.getState();
      assertEquals(branchId, state.branchId());
      assertEquals(stockId, state.stockId());
      assertEquals(quantityId, state.quantityId());
      assertEquals(quantity, state.quantity());
    }

    {
      // Test idempotency by submitting same command again
      var state = testKit.getState();
      var result = testKit.method(StockItemsBranchEntity::addQuantity).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertTrue(result.getAllEvents().isEmpty());
      assertEquals(state, testKit.getState());
    }
  }

  @Test
  void testAddQuantityForLargeQuantityWithParent() {
    var testKit = EventSourcedTestKit.of(StockItemsBranchEntity::new);

    var branchId = randomUUID();
    var stockId = "stock-123";
    var quantityId = randomUUID();
    var quantity = 1000;
    var parentBranchId = randomUUID();

    var command = new StockItemsBranch.Command.AddQuantityToTree(branchId, stockId, quantityId, quantity, parentBranchId);
    var result = testKit.method(StockItemsBranchEntity::addQuantity).invoke(command);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertTrue(result.getAllEvents().size() > 0);

    {
      var event = result.getNextEventOfType(StockItemsBranch.Event.StockItemsCreated.class);
      assertEquals(branchId, event.branchId());
      assertEquals(stockId, event.stockId());
      assertEquals(quantityId, event.quantityId());
      assertEquals(quantity, event.quantity());
      assertEquals(parentBranchId, event.parentBranchId());
    }

    {
      var state = testKit.getState();
      assertEquals(branchId, state.branchId());
      assertEquals(stockId, state.stockId());
      assertEquals(quantityId, state.quantityId());
      assertEquals(quantity, state.quantity());
      assertEquals(parentBranchId, state.parentBranchId());
    }

    {
      for (var event : result.getAllEvents()) {
        if (event instanceof StockItemsBranch.Event.BranchToBeAdded branchEvent) {
          assertEquals(branchId, branchEvent.parentBranchId());
        } else if (event instanceof StockItemsBranch.Event.LeafToBeAdded leafEvent) {
          assertEquals(branchId, leafEvent.parentBranchId());
        }
      }
    }

    {
      var state = testKit.getState();
      assertEquals(branchId, state.branchId());
      assertEquals(stockId, state.stockId());
      assertEquals(quantityId, state.quantityId());
      assertEquals(quantity, state.quantity());
      assertEquals(parentBranchId, state.parentBranchId());
    }
  }

  @Test
  void testUpdateBranchAndLeafQuantity() {
    var testKit = EventSourcedTestKit.of(StockItemsBranchEntity::new);

    var branchId = randomUUID();
    var stockId = "stock-123";
    var quantityId = randomUUID();
    var quantity = 5_678;
    var parentBranchId = randomUUID();

    {
      var command = new StockItemsBranch.Command.AddQuantityToTree(branchId, stockId, quantityId, quantity, parentBranchId);
      var result = testKit.method(StockItemsBranchEntity::addQuantity).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertTrue(result.getAllEvents().size() > 0);

      for (var event : result.getAllEvents()) {
        if (event instanceof StockItemsBranch.Event.BranchToBeAdded branchEvent) {
          var updateBranchCommand = new StockItemsBranch.Command.UpdateBranchQuantity(
              branchId,
              branchEvent.subStockItems().stockItemsId(),
              branchEvent.subStockItems().quantity());
          var updateResult = testKit.method(StockItemsBranchEntity::updateBranchQuantity).invoke(updateBranchCommand);
          assertTrue(updateResult.isReply());
          assertEquals(done(), updateResult.getReply());
        } else if (event instanceof StockItemsBranch.Event.LeafToBeAdded leafEvent) {
          var updateLeafCommand = new StockItemsBranch.Command.UpdateLeafQuantity(
              branchId,
              leafEvent.subStockItems().stockItemsId(),
              leafEvent.subStockItems().quantity());
          var updateResult = testKit.method(StockItemsBranchEntity::updateLeafQuantity).invoke(updateLeafCommand);
          assertTrue(updateResult.isReply());
          assertEquals(done(), updateResult.getReply());
        }
      }
    }

    var state = testKit.getState();
    assertEquals(quantity, state.quantity());

    var currentState = testKit.getState();
    var initialQuantity = currentState.quantity();
    var initialBranchQuantity = currentState.subBranches().get(0).quantity();
    var initialLeafQuantity = currentState.leaves().get(0).quantity();

    var updateBranchCommand = new StockItemsBranch.Command.UpdateBranchQuantity(
        branchId,
        currentState.subBranches().get(0).stockItemsId(),
        0);
    var updateBranchResult = testKit.method(StockItemsBranchEntity::updateBranchQuantity).invoke(updateBranchCommand);
    assertTrue(updateBranchResult.isReply());
    assertEquals(done(), updateBranchResult.getReply());

    var updateLeafCommand = new StockItemsBranch.Command.UpdateLeafQuantity(
        branchId,
        currentState.leaves().get(0).stockItemsId(),
        0);
    var updateLeafResult = testKit.method(StockItemsBranchEntity::updateLeafQuantity).invoke(updateLeafCommand);
    assertTrue(updateLeafResult.isReply());
    assertEquals(done(), updateLeafResult.getReply());

    var newState = testKit.getState();
    var newQuantity = newState.quantity();
    assertEquals(
        initialQuantity - initialBranchQuantity - initialLeafQuantity,
        newQuantity);
  }

  @Test
  void testAddQuantityWithDelegation() {
    var testKit = EventSourcedTestKit.of(StockItemsBranchEntity::new);

    var branchId = randomUUID();
    var stockId = "stock-123";
    var parentBranchId = randomUUID();

    // First add quantity with quantityId1
    {
      var quantityId = randomUUID();
      var quantity = 100;
      var command = new StockItemsBranch.Command.AddQuantityToTree(branchId, stockId, quantityId, quantity, parentBranchId);
      var result = testKit.method(StockItemsBranchEntity::addQuantity).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertTrue(result.getAllEvents().size() > 0);
    }

    var state = testKit.getState();

    // Now add quantity with a different quantityId
    {
      var quantityId = randomUUID();
      var quantity = 50;
      var command = new StockItemsBranch.Command.AddQuantityToTree(branchId, stockId, quantityId, quantity, parentBranchId);
      var result = testKit.method(StockItemsBranchEntity::addQuantity).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());

      // Verify that a delegation event was emitted
      var delegateEvent = result.getNextEventOfType(StockItemsBranch.Event.DelegateToSubBranch.class);
      assertNotNull(delegateEvent);
      var matchingBranch = state.subBranches().stream()
          .filter(b -> b.stockItemsId().equals(delegateEvent.branchId()))
          .findFirst();
      assertTrue(matchingBranch.isPresent());
      assertEquals(stockId, delegateEvent.stockId());
      assertEquals(quantityId, delegateEvent.quantityId());
      assertEquals(quantity, delegateEvent.quantity());
      assertEquals(state.branchId(), delegateEvent.parentBranchId());
    }

    // Verify final state
    var finalState = testKit.getState();
    assertEquals(state, finalState); // State should remain unchanged after delegation
  }
}
