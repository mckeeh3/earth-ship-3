package io.earthship3.application.stock;

import static akka.Done.done;
import static io.earthship3.ShortUUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.earthship3.domain.stock.StockItemsBranch;
import io.earthship3.domain.stock.StockItemsBranch.Quantity;

public class StockItemsBranchEntityTest {
  @Test
  void testAddQuantityForSmallQuantity() {
    var testKit = EventSourcedTestKit.of(StockItemsBranchEntity::new);

    var branchId = randomUUID();
    var stockId = "stock-123";
    var quantityId = randomUUID();
    var quantity = Quantity.of(StockItemsBranch.State.maxStockItemsPerLeaf); // No branches should be created
    var parentBranchId = Optional.of(randomUUID());

    var command = new StockItemsBranch.Command.AddQuantityToTree(branchId, stockId, quantityId, quantity, parentBranchId);
    var result = testKit.method(StockItemsBranchEntity::addQuantity).invoke(command);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertEquals(2, result.getAllEvents().size());

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
      var event = result.getNextEventOfType(StockItemsBranch.Event.LeafToBeAdded.class);
      assertTrue(testKit.getState().leaves().stream().map(l -> l.leafId()).toList().contains(event.leafId()));
      assertEquals(stockId, event.stockId());
      assertEquals(quantityId, event.quantityId());
      assertEquals(quantity, event.quantity());
      assertEquals(branchId, event.parentBranchId());
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
  void testAddQuantityForLargeQuantityThatShouldCreateTwoSubBranches() {
    var testKit = EventSourcedTestKit.of(StockItemsBranchEntity::new);

    var branchId = randomUUID();
    var stockId = "stock-123";
    var quantityId = randomUUID();
    var quantity = Quantity.of(StockItemsBranch.State.maxStockItemsPerBranch * 3); // Should create trunk and two sub branches
    var parentBranchId = Optional.of(randomUUID());

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
      var event = result.getNextEventOfType(StockItemsBranch.Event.BranchToBeAdded.class);
      assertTrue(testKit.getState().subBranches().stream().map(b -> b.branchId()).toList().contains(event.branchId()));
      assertEquals(branchId, event.parentBranchId());
    }

    {
      var branchQuantitySum = result.getAllEvents().stream()
          .filter(e -> e instanceof StockItemsBranch.Event.BranchToBeAdded)
          .map(e -> ((StockItemsBranch.Event.BranchToBeAdded) e).quantity())
          .reduce(Quantity.zero(), Quantity::add);
      var leafQuantitySum = result.getAllEvents().stream()
          .filter(e -> e instanceof StockItemsBranch.Event.LeafToBeAdded)
          .map(e -> ((StockItemsBranch.Event.LeafToBeAdded) e).quantity())
          .reduce(Quantity.zero(), Quantity::add);

      assertEquals(quantity, branchQuantitySum.add(leafQuantitySum));
    }

    {
      var state = testKit.getState();
      assertEquals(branchId, state.branchId());
      assertEquals(stockId, state.stockId());
      assertEquals(quantityId, state.quantityId());
      assertEquals(quantity, state.quantity());
    }
  }

  @Test
  void testAddQuantityForVeryLargeQuantity() {
    var testKit = EventSourcedTestKit.of(StockItemsBranchEntity::new);

    var branchId = randomUUID();
    var stockId = "stock-123";
    var quantityId = randomUUID();
    var quantity = Quantity.of(StockItemsBranch.State.maxStockItemsPerBranch * 1_000); // Very large quantity
    var parentBranchId = Optional.of(randomUUID());

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
        var branchQuantity = result.getAllEvents().stream()
            .filter(e -> e instanceof StockItemsBranch.Event.BranchToBeAdded)
            .mapToInt(e -> ((StockItemsBranch.Event.BranchToBeAdded) e).quantity().available())
            .sum();
        var leafQuantity = result.getAllEvents().stream()
            .filter(e -> e instanceof StockItemsBranch.Event.LeafToBeAdded)
            .mapToInt(e -> ((StockItemsBranch.Event.LeafToBeAdded) e).quantity().available())
            .sum();

        assertEquals(quantity.available(), branchQuantity + leafQuantity);
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
    var quantity = Quantity.of(StockItemsBranch.State.maxStockItemsPerBranch * 100);
    var parentBranchId = Optional.of(randomUUID());

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
      result.getAllEvents().stream()
          .forEach(event -> {
            if (event instanceof StockItemsBranch.Event.BranchToBeAdded branchEvent) {
              assertEquals(branchId, branchEvent.parentBranchId());
            } else if (event instanceof StockItemsBranch.Event.LeafToBeAdded leafEvent) {
              assertEquals(branchId, leafEvent.parentBranchId());
            }
          });
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
    var quantity = Quantity.of(StockItemsBranch.State.maxStockItemsPerBranch * 50);
    var parentBranchId = Optional.of(randomUUID());

    {
      var command = new StockItemsBranch.Command.AddQuantityToTree(branchId, stockId, quantityId, quantity, parentBranchId);
      var result = testKit.method(StockItemsBranchEntity::addQuantity).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertTrue(result.getAllEvents().size() > 0);

      result.getAllEvents().stream()
          .forEach(event -> {
            if (event instanceof StockItemsBranch.Event.BranchToBeAdded branchEvent) {
              var updateBranchCommand = new StockItemsBranch.Command.UpdateBranchQuantity(
                  branchId,
                  branchEvent.branchId(),
                  branchEvent.quantity());
              var updateResult = testKit.method(StockItemsBranchEntity::updateBranchQuantity).invoke(updateBranchCommand);
              assertTrue(updateResult.isReply());
              assertEquals(done(), updateResult.getReply());
            } else if (event instanceof StockItemsBranch.Event.LeafToBeAdded leafEvent) {
              var updateLeafCommand = new StockItemsBranch.Command.UpdateLeafQuantity(
                  branchId,
                  leafEvent.leafId(),
                  leafEvent.quantity());
              var updateResult = testKit.method(StockItemsBranchEntity::updateLeafQuantity).invoke(updateLeafCommand);
              assertTrue(updateResult.isReply());
              assertEquals(done(), updateResult.getReply());
            }
          });
    }

    var state = testKit.getState();
    assertEquals(quantity, state.quantity());

    var currentState = testKit.getState();
    var initialQuantity = currentState.quantity();
    var initialBranchQuantity = currentState.subBranches().get(0).quantity();
    var initialLeafQuantity = currentState.leaves().get(0).quantity();

    // Then update branch quantity
    var subBranchQuantity = currentState.subBranches().get(0).quantity();
    var updateBranchCommand = new StockItemsBranch.Command.UpdateBranchQuantity(
        branchId,
        currentState.subBranches().get(0).branchId(),
        Quantity.of(subBranchQuantity.acquired(), 0));
    var updateBranchResult = testKit.method(StockItemsBranchEntity::updateBranchQuantity).invoke(updateBranchCommand);
    assertTrue(updateBranchResult.isReply());
    assertEquals(done(), updateBranchResult.getReply());

    {
      var event = updateBranchResult.getNextEventOfType(StockItemsBranch.Event.BranchQuantityUpdated.class);
      assertNotNull(event);
      assertEquals(branchId, event.branchId());
      assertEquals(parentBranchId, event.parentBranchId());
      assertEquals(initialQuantity.sub(initialBranchQuantity.available()), event.quantity());
      assertEquals(currentState.subBranches().get(0).branchId(), event.subBranchId());
    }

    // Then update leaf quantity
    var leafQuantity = currentState.leaves().get(0).quantity();
    var updateLeafCommand = new StockItemsBranch.Command.UpdateLeafQuantity(
        branchId,
        currentState.leaves().get(0).leafId(),
        leafQuantity.sub(leafQuantity.available()));
    var updateLeafResult = testKit.method(StockItemsBranchEntity::updateLeafQuantity).invoke(updateLeafCommand);
    assertTrue(updateLeafResult.isReply());
    assertEquals(done(), updateLeafResult.getReply());

    {
      var event = updateLeafResult.getNextEventOfType(StockItemsBranch.Event.LeafQuantityUpdated.class);
      assertNotNull(event);
      assertEquals(branchId, event.branchId());
      assertEquals(parentBranchId, event.parentBranchId());
      assertEquals(initialQuantity.sub(subBranchQuantity.available()).sub(leafQuantity.available()), event.quantity());
      assertEquals(currentState.leaves().get(0).leafId(), event.leafId());
    }

    var newState = testKit.getState();
    var newQuantity = newState.quantity();
    assertEquals(
        initialQuantity.sub(initialBranchQuantity.available()).sub(initialLeafQuantity.available()),
        newQuantity);
  }

  @Test
  void testAddQuantityWithDelegation() {
    var testKit = EventSourcedTestKit.of(StockItemsBranchEntity::new);

    var branchId = randomUUID();
    var stockId = "stock-123";
    var parentBranchId = Optional.of(randomUUID());

    // First add quantity with quantityId1
    {
      var quantityId = randomUUID();
      var quantity = Quantity.of(100);
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
      var quantityAvailable = Quantity.of(50);
      var command = new StockItemsBranch.Command.AddQuantityToTree(branchId, stockId, quantityId, quantityAvailable, parentBranchId);
      var result = testKit.method(StockItemsBranchEntity::addQuantity).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());

      // Verify that a delegation event was emitted
      var delegateEvent = result.getNextEventOfType(StockItemsBranch.Event.DelegateToSubBranch.class);
      var subBranch = state.subBranches().stream()
          .filter(b -> b.branchId().equals(delegateEvent.subBranchId()))
          .findFirst();
      assertTrue(subBranch.isPresent());
      assertEquals(stockId, delegateEvent.stockId());
      assertEquals(quantityId, delegateEvent.quantityId());
      assertEquals(quantityAvailable, delegateEvent.quantity());
      assertEquals(state.branchId(), delegateEvent.branchId());
    }

    // Verify final state
    var finalState = testKit.getState();
    assertEquals(state, finalState); // State should remain unchanged after delegation
  }
}
