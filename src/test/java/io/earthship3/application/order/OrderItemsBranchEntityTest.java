package io.earthship3.application.order;

import static akka.Done.done;
import static io.earthship3.ShortUUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.earthship3.domain.order.OrderItemsBranch;
import io.earthship3.domain.order.OrderItemsBranch.Quantity;

public class OrderItemsBranchEntityTest {
  @Test
  public void testAddQuantityForSmallQuantity() {
    var testKit = EventSourcedTestKit.of(OrderItemsBranchEntity::new);

    var branchId = randomUUID();
    var stockId = "stock-123";
    var quantityId = randomUUID();
    var quantity = Quantity.of(OrderItemsBranch.State.maxOrderItemsPerLeaf); // No branches should be created
    var parentBranchId = Optional.of(randomUUID());

    var command = new OrderItemsBranch.Command.AddQuantityToTree(branchId, stockId, quantityId, quantity, parentBranchId);
    var result = testKit.method(OrderItemsBranchEntity::addQuantity).invoke(command);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertEquals(2, result.getAllEvents().size());

    {
      var event = result.getNextEventOfType(OrderItemsBranch.Event.OrderItemsCreated.class);
      assertEquals(branchId, event.branchId());
      assertEquals(parentBranchId, event.parentBranchId());
      assertEquals(quantityId, event.quantityId());
      assertEquals(stockId, event.stockId());
      assertEquals(quantity, event.quantity());
      assertTrue(event.subBranches().size() > 0);
      assertTrue(event.leaves().size() > 0);
    }

    {
      var event = result.getNextEventOfType(OrderItemsBranch.Event.LeafToBeAdded.class);
      assertTrue(testKit.getState().leaves().stream().map(l -> l.leafId()).toList().contains(event.leafId()));
      assertEquals(stockId, event.stockId());
      assertEquals(quantityId, event.quantityId());
      assertEquals(quantity, event.quantity());
      assertEquals(branchId, event.parentBranchId());
    }

    {
      var state = testKit.getState();
      assertEquals(branchId, state.branchId());
      assertEquals(parentBranchId, state.parentBranchId());
      assertEquals(quantityId, state.quantityId());
      assertEquals(stockId, state.stockId());
      assertEquals(quantity, state.quantity());
      assertTrue(state.subBranches().size() > 0);
      assertTrue(state.leaves().size() > 0);
    }
  }

  @Test
  void testAddQuantityForLargeQuantityThatShouldCreateTwoSubBranches() {
    var testKit = EventSourcedTestKit.of(OrderItemsBranchEntity::new);

    var branchId = randomUUID();
    var stockId = "stock-123";
    var quantityId = randomUUID();
    var quantity = Quantity.of(OrderItemsBranch.State.maxOrderItemsPerBranch * 3); // Should create trunk and two sub branches
    var parentBranchId = Optional.of(randomUUID());

    var command = new OrderItemsBranch.Command.AddQuantityToTree(branchId, stockId, quantityId, quantity, parentBranchId);
    var result = testKit.method(OrderItemsBranchEntity::addQuantity).invoke(command);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertTrue(result.getAllEvents().size() > 0);

    {
      var event = result.getNextEventOfType(OrderItemsBranch.Event.OrderItemsCreated.class);
      assertEquals(branchId, event.branchId());
      assertEquals(parentBranchId, event.parentBranchId());
      assertEquals(quantityId, event.quantityId());
      assertEquals(stockId, event.stockId());
      assertEquals(quantity, event.quantity());
    }

    {
      var event = result.getNextEventOfType(OrderItemsBranch.Event.BranchToBeAdded.class);
      assertTrue(testKit.getState().subBranches().stream().map(b -> b.branchId()).toList().contains(event.branchId()));
      assertEquals(branchId, event.parentBranchId());
    }

    {
      var branchQuantity = result.getAllEvents().stream()
          .filter(e -> e instanceof OrderItemsBranch.Event.BranchToBeAdded)
          .map(e -> ((OrderItemsBranch.Event.BranchToBeAdded) e).quantity())
          .reduce(Quantity.zero(), Quantity::add);
      var leafQuantity = result.getAllEvents().stream()
          .filter(e -> e instanceof OrderItemsBranch.Event.LeafToBeAdded)
          .map(e -> ((OrderItemsBranch.Event.LeafToBeAdded) e).quantity())
          .reduce(Quantity.zero(), Quantity::add);

      assertEquals(quantity, branchQuantity.add(leafQuantity));
    }

    {
      var state = testKit.getState();
      assertEquals(branchId, state.branchId());
      assertEquals(parentBranchId, state.parentBranchId());
      assertEquals(quantityId, state.quantityId());
      assertEquals(stockId, state.stockId());
      assertEquals(quantity, state.quantity());
    }
  }

  @Test
  void testAddQuantityForVeryLargeQuantity() {
    var testKit = EventSourcedTestKit.of(OrderItemsBranchEntity::new);

    var branchId = randomUUID();
    var stockId = "stock-123";
    var quantityId = randomUUID();
    var quantity = Quantity.of(1_000_000); // Very large quantity
    var parentBranchId = Optional.of(randomUUID());

    var command = new OrderItemsBranch.Command.AddQuantityToTree(branchId, stockId, quantityId, quantity, parentBranchId);
    {
      var result = testKit.method(OrderItemsBranchEntity::addQuantity).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertTrue(result.getAllEvents().size() > 0);

      {
        var event = result.getNextEventOfType(OrderItemsBranch.Event.OrderItemsCreated.class);
        assertEquals(branchId, event.branchId());
        assertEquals(parentBranchId, event.parentBranchId());
        assertEquals(quantityId, event.quantityId());
        assertEquals(stockId, event.stockId());
        assertEquals(quantity, event.quantity());
      }

      {
        var branchQuantity = result.getAllEvents().stream()
            .filter(e -> e instanceof OrderItemsBranch.Event.BranchToBeAdded)
            .map(e -> ((OrderItemsBranch.Event.BranchToBeAdded) e).quantity())
            .reduce(Quantity.zero(), Quantity::add);
        var leafQuantity = result.getAllEvents().stream()
            .filter(e -> e instanceof OrderItemsBranch.Event.LeafToBeAdded)
            .map(e -> ((OrderItemsBranch.Event.LeafToBeAdded) e).quantity())
            .reduce(Quantity.zero(), Quantity::add);

        assertEquals(quantity, branchQuantity.add(leafQuantity));
      }
    }

    {
      var state = testKit.getState();
      assertEquals(branchId, state.branchId());
      assertEquals(parentBranchId, state.parentBranchId());
      assertEquals(quantityId, state.quantityId());
      assertEquals(stockId, state.stockId());
      assertEquals(quantity, state.quantity());
    }

    {
      // Test idempotency by submitting same command again
      var state = testKit.getState();
      var result = testKit.method(OrderItemsBranchEntity::addQuantity).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertTrue(result.getAllEvents().isEmpty());
      assertEquals(state, testKit.getState());
    }
  }

  @Test
  void testAddQuantityForLargeQuantityWithParent() {
    var testKit = EventSourcedTestKit.of(OrderItemsBranchEntity::new);

    var branchId = randomUUID();
    var stockId = "stock-123";
    var quantityId = randomUUID();
    var quantity = Quantity.of(1_000);
    var parentBranchId = Optional.of(randomUUID());

    var command = new OrderItemsBranch.Command.AddQuantityToTree(branchId, stockId, quantityId, quantity, parentBranchId);
    var result = testKit.method(OrderItemsBranchEntity::addQuantity).invoke(command);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertTrue(result.getAllEvents().size() > 0);

    {
      var event = result.getNextEventOfType(OrderItemsBranch.Event.OrderItemsCreated.class);
      assertEquals(branchId, event.branchId());
      assertEquals(parentBranchId, event.parentBranchId());
      assertEquals(quantityId, event.quantityId());
      assertEquals(stockId, event.stockId());
      assertEquals(quantity, event.quantity());
    }

    {
      var state = testKit.getState();
      assertEquals(branchId, state.branchId());
      assertEquals(parentBranchId, state.parentBranchId());
      assertEquals(quantityId, state.quantityId());
      assertEquals(stockId, state.stockId());
      assertEquals(quantity, state.quantity());
    }

    {
      result.getAllEvents().stream()
          .forEach(event -> {
            if (event instanceof OrderItemsBranch.Event.BranchToBeAdded branchEvent) {
              assertEquals(branchId, branchEvent.parentBranchId());
            } else if (event instanceof OrderItemsBranch.Event.LeafToBeAdded leafEvent) {
              assertEquals(branchId, leafEvent.parentBranchId());
            }
          });
    }

    {
      var state = testKit.getState();
      assertEquals(branchId, state.branchId());
      assertEquals(parentBranchId, state.parentBranchId());
      assertEquals(quantityId, state.quantityId());
      assertEquals(stockId, state.stockId());
      assertEquals(quantity, state.quantity());
    }
  }

  @Test
  void testUpdateBranchAndLeafQuantity() {
    var testKit = EventSourcedTestKit.of(OrderItemsBranchEntity::new);

    var branchId = randomUUID();
    var stockId = "stock-123";
    var quantityId = randomUUID();
    var quantity = Quantity.of(OrderItemsBranch.State.maxOrderItemsPerBranch * 50);
    var parentBranchId = Optional.of(randomUUID());

    {
      var command = new OrderItemsBranch.Command.AddQuantityToTree(branchId, stockId, quantityId, quantity, parentBranchId);
      var result = testKit.method(OrderItemsBranchEntity::addQuantity).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertTrue(result.getAllEvents().size() > 0);

      result.getAllEvents().stream()
          .forEach(event -> {
            if (event instanceof OrderItemsBranch.Event.BranchToBeAdded branchEvent) {
              var updateBranchCommand = new OrderItemsBranch.Command.UpdateBranchQuantity(
                  branchId,
                  branchEvent.branchId(),
                  branchEvent.quantity());
              var updateResult = testKit.method(OrderItemsBranchEntity::updateBranchQuantity).invoke(updateBranchCommand);
              assertTrue(updateResult.isReply());
              assertEquals(done(), updateResult.getReply());
            } else if (event instanceof OrderItemsBranch.Event.LeafToBeAdded leafEvent) {
              var updateLeafCommand = new OrderItemsBranch.Command.UpdateLeafQuantity(
                  branchId,
                  leafEvent.leafId(),
                  leafEvent.quantity());
              var updateResult = testKit.method(OrderItemsBranchEntity::updateLeafQuantity).invoke(updateLeafCommand);
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
    var updateBranchCommand = new OrderItemsBranch.Command.UpdateBranchQuantity(
        branchId,
        currentState.subBranches().get(0).branchId(),
        Quantity.of(subBranchQuantity.ordered(), 0));
    var updateBranchResult = testKit.method(OrderItemsBranchEntity::updateBranchQuantity).invoke(updateBranchCommand);
    assertTrue(updateBranchResult.isReply());
    assertEquals(done(), updateBranchResult.getReply());

    {
      var event = updateBranchResult.getNextEventOfType(OrderItemsBranch.Event.BranchQuantityUpdated.class);
      assertNotNull(event);
      assertEquals(branchId, event.branchId());
      assertEquals(parentBranchId, event.parentBranchId());
      assertEquals(initialQuantity.sub(initialBranchQuantity.unallocated()), event.quantity());
      assertEquals(currentState.subBranches().get(0).branchId(), event.subBranchId());
    }

    // Then update leaf quantity
    var leafQuantity = currentState.leaves().get(0).quantity();
    var updateLeafCommand = new OrderItemsBranch.Command.UpdateLeafQuantity(
        branchId,
        currentState.leaves().get(0).leafId(),
        leafQuantity.sub(leafQuantity.unallocated()));
    var updateLeafResult = testKit.method(OrderItemsBranchEntity::updateLeafQuantity).invoke(updateLeafCommand);
    assertTrue(updateLeafResult.isReply());
    assertEquals(done(), updateLeafResult.getReply());

    {
      var event = updateLeafResult.getNextEventOfType(OrderItemsBranch.Event.LeafQuantityUpdated.class);
      assertNotNull(event);
      assertEquals(branchId, event.branchId());
      assertEquals(parentBranchId, event.parentBranchId());
      assertEquals(initialQuantity.sub(subBranchQuantity.unallocated()).sub(leafQuantity.unallocated()), event.quantity());
      assertEquals(currentState.leaves().get(0).leafId(), event.leafId());
    }

    var newState = testKit.getState();
    var newQuantity = newState.quantity();
    assertEquals(
        initialQuantity.sub(initialBranchQuantity.unallocated()).sub(initialLeafQuantity.unallocated()),
        newQuantity);
  }

  @Test
  void testAddQuantityWithDelegation() {
    var testKit = EventSourcedTestKit.of(OrderItemsBranchEntity::new);

    var branchId = randomUUID();
    var stockId = "stock-123";
    var parentBranchId = Optional.of(randomUUID());

    // First add quantity with quantityId1
    {
      var quantityId = randomUUID();
      var quantity = Quantity.of(100);
      var command = new OrderItemsBranch.Command.AddQuantityToTree(branchId, stockId, quantityId, quantity, parentBranchId);
      var result = testKit.method(OrderItemsBranchEntity::addQuantity).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertTrue(result.getAllEvents().size() > 0);
    }

    var state = testKit.getState();

    // Now add quantity with a different quantityId
    {
      var quantityId = randomUUID();
      var quantity = Quantity.of(50);
      var command = new OrderItemsBranch.Command.AddQuantityToTree(branchId, stockId, quantityId, quantity, parentBranchId);
      var result = testKit.method(OrderItemsBranchEntity::addQuantity).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());

      // Verify that a delegation event was emitted
      var delegateEvent = result.getNextEventOfType(OrderItemsBranch.Event.DelegateToSubBranch.class);
      var subBranch = state.subBranches().stream()
          .filter(b -> b.branchId().equals(delegateEvent.subBranchId()))
          .findFirst();
      assertTrue(subBranch.isPresent());
      assertEquals(stockId, delegateEvent.stockId());
      assertEquals(quantityId, delegateEvent.quantityId());
      assertEquals(quantity, delegateEvent.quantity());
      assertEquals(state.branchId(), delegateEvent.branchId());
    }

    // Verify final state
    var finalState = testKit.getState();
    assertEquals(state, finalState); // State should remain unchanged after delegation
  }
}