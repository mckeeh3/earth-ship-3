package io.earthship3.application.stock;

import static io.earthship3.ShortUUID.randomUUID;

import static akka.Done.done;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.earthship3.domain.stock.StockItems;

public class StockItemsEntityTest {
  @Test
  void testAddQuantityForSmallQuantity() {
    var testKit = EventSourcedTestKit.of(StockItemsEntity::new);

    var stockItemsId = randomUUID();
    var stockId = "stock-123";
    var quantityId = randomUUID();
    var quantity = 5;
    String parentStockItemsId = null;

    var command = new StockItems.Command.StockItemsAddQuantity(stockItemsId, stockId, quantityId, quantity, parentStockItemsId);
    var result = testKit.method(StockItemsEntity::addQuantity).invoke(command);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertTrue(result.getAllEvents().size() > 0);

    {
      var event = result.getNextEventOfType(StockItems.Event.StockItemsCreated.class);
      assertEquals(stockItemsId, event.stockItemsId());
      assertEquals(stockId, event.stockId());
      assertEquals(quantityId, event.quantityId());
      assertEquals(quantity, event.quantity());
      assertEquals(parentStockItemsId, event.parentStockItemsId());
      assertTrue(event.branchSubStockItems().size() > 0);
      assertTrue(event.leafSubStockItems().size() > 0);
    }

    {
      var state = testKit.getState();
      assertEquals(stockItemsId, state.stockItemsId());
      assertEquals(stockId, state.stockId());
      assertEquals(quantityId, state.quantityId());
      assertEquals(quantity, state.quantity());
      assertEquals(parentStockItemsId, state.parentStockItemsId());
      assertTrue(state.branchSubStockItems().size() > 0);
      assertTrue(state.leafSubStockItems().size() > 0);
    }
  }

  @Test
  void testAddQuantityForLargeQuantity() {
    var testKit = EventSourcedTestKit.of(StockItemsEntity::new);

    var stockItemsId = randomUUID();
    var stockId = "stock-123";
    var quantityId = randomUUID();
    var quantity = 1000;
    String parentStockItemsId = null;

    var command = new StockItems.Command.StockItemsAddQuantity(stockItemsId, stockId, quantityId, quantity, parentStockItemsId);
    var result = testKit.method(StockItemsEntity::addQuantity).invoke(command);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertTrue(result.getAllEvents().size() > 0);

    {
      var event = result.getNextEventOfType(StockItems.Event.StockItemsCreated.class);
      assertEquals(stockItemsId, event.stockItemsId());
      assertEquals(stockId, event.stockId());
      assertEquals(quantityId, event.quantityId());
      assertEquals(quantity, event.quantity());
    }

    {
      var state = testKit.getState();
      assertEquals(stockItemsId, state.stockItemsId());
      assertEquals(stockId, state.stockId());
      assertEquals(quantityId, state.quantityId());
      assertEquals(quantity, state.quantity());
    }

    {
      var branchQuantitySum = result.getAllEvents().stream()
          .filter(e -> e instanceof StockItems.Event.StockItemsBranchToBeAdded)
          .mapToInt(e -> ((StockItems.Event.StockItemsBranchToBeAdded) e).subStockItems().quantity())
          .sum();
      var leafQuantitySum = result.getAllEvents().stream()
          .filter(e -> e instanceof StockItems.Event.StockItemsLeafToBeAdded)
          .mapToInt(e -> ((StockItems.Event.StockItemsLeafToBeAdded) e).subStockItems().quantity())
          .sum();

      assertEquals(quantity, branchQuantitySum + leafQuantitySum);
    }
  }

  @Test
  void testAddQuantityForVeryLargeQuantity() {
    var testKit = EventSourcedTestKit.of(StockItemsEntity::new);

    var stockItemsId = randomUUID();
    var stockId = "stock-123";
    var quantityId = randomUUID();
    var quantity = 1_000_000; // Very large quantity
    String parentStockItemsId = null;

    var command = new StockItems.Command.StockItemsAddQuantity(stockItemsId, stockId, quantityId, quantity, parentStockItemsId);
    {
      var result = testKit.method(StockItemsEntity::addQuantity).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertTrue(result.getAllEvents().size() > 0);

      {
        var event = result.getNextEventOfType(StockItems.Event.StockItemsCreated.class);
        assertEquals(stockItemsId, event.stockItemsId());
        assertEquals(stockId, event.stockId());
        assertEquals(quantityId, event.quantityId());
        assertEquals(quantity, event.quantity());
      }

      {
        var branchQuantitySum = result.getAllEvents().stream()
            .filter(e -> e instanceof StockItems.Event.StockItemsBranchToBeAdded)
            .mapToInt(e -> ((StockItems.Event.StockItemsBranchToBeAdded) e).subStockItems().quantity())
            .sum();
        var leafQuantitySum = result.getAllEvents().stream()
            .filter(e -> e instanceof StockItems.Event.StockItemsLeafToBeAdded)
            .mapToInt(e -> ((StockItems.Event.StockItemsLeafToBeAdded) e).subStockItems().quantity())
            .sum();

        assertEquals(quantity, branchQuantitySum + leafQuantitySum);
      }
    }

    {
      var state = testKit.getState();
      assertEquals(stockItemsId, state.stockItemsId());
      assertEquals(stockId, state.stockId());
      assertEquals(quantityId, state.quantityId());
      assertEquals(quantity, state.quantity());
    }

    {
      // Test idempotency by submitting same command again
      var state = testKit.getState();
      var result = testKit.method(StockItemsEntity::addQuantity).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertTrue(result.getAllEvents().isEmpty());
      assertEquals(state, testKit.getState());
    }
  }

  @Test
  void testAddQuantityForLargeQuantityWithParent() {
    var testKit = EventSourcedTestKit.of(StockItemsEntity::new);

    var stockItemsId = randomUUID();
    var stockId = "stock-123";
    var quantityId = randomUUID();
    var quantity = 1000;
    String parentStockItemsId = randomUUID();

    var command = new StockItems.Command.StockItemsAddQuantity(stockItemsId, stockId, quantityId, quantity, parentStockItemsId);
    var result = testKit.method(StockItemsEntity::addQuantity).invoke(command);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertTrue(result.getAllEvents().size() > 0);

    {
      var event = result.getNextEventOfType(StockItems.Event.StockItemsCreated.class);
      assertEquals(stockItemsId, event.stockItemsId());
      assertEquals(stockId, event.stockId());
      assertEquals(quantityId, event.quantityId());
      assertEquals(quantity, event.quantity());
      assertEquals(parentStockItemsId, event.parentStockItemsId());
    }

    {
      var state = testKit.getState();
      assertEquals(stockItemsId, state.stockItemsId());
      assertEquals(stockId, state.stockId());
      assertEquals(quantityId, state.quantityId());
      assertEquals(quantity, state.quantity());
      assertEquals(parentStockItemsId, state.parentStockItemsId());
    }

    {
      for (var event : result.getAllEvents()) {
        if (event instanceof StockItems.Event.StockItemsBranchToBeAdded branchEvent) {
          assertEquals(stockItemsId, branchEvent.parentStockItemsId());
        } else if (event instanceof StockItems.Event.StockItemsLeafToBeAdded leafEvent) {
          assertEquals(stockItemsId, leafEvent.parentStockItemsId());
        }
      }
    }

    {
      var state = testKit.getState();
      assertEquals(stockItemsId, state.stockItemsId());
      assertEquals(stockId, state.stockId());
      assertEquals(quantityId, state.quantityId());
      assertEquals(quantity, state.quantity());
      assertEquals(parentStockItemsId, state.parentStockItemsId());
    }
  }

  @Test
  void testUpdateBranchAndLeafQuantity() {
    var testKit = EventSourcedTestKit.of(StockItemsEntity::new);

    var stockItemsId = randomUUID();
    var stockId = "stock-123";
    var quantityId = randomUUID();
    var quantity = 5_678;
    String parentStockItemsId = null;

    {
      var command = new StockItems.Command.StockItemsAddQuantity(stockItemsId, stockId, quantityId, quantity, parentStockItemsId);
      var result = testKit.method(StockItemsEntity::addQuantity).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertTrue(result.getAllEvents().size() > 0);

      for (var event : result.getAllEvents()) {
        if (event instanceof StockItems.Event.StockItemsBranchToBeAdded branchEvent) {
          var updateBranchCommand = new StockItems.Command.UpdateBranchQuantity(
              stockItemsId,
              branchEvent.subStockItems().stockItemsId(),
              branchEvent.subStockItems().quantity());
          var updateResult = testKit.method(StockItemsEntity::updateBranchQuantity).invoke(updateBranchCommand);
          assertTrue(updateResult.isReply());
          assertEquals(done(), updateResult.getReply());
        } else if (event instanceof StockItems.Event.StockItemsLeafToBeAdded leafEvent) {
          var updateLeafCommand = new StockItems.Command.UpdateLeafQuantity(
              stockItemsId,
              leafEvent.subStockItems().stockItemsId(),
              leafEvent.subStockItems().quantity());
          var updateResult = testKit.method(StockItemsEntity::updateLeafQuantity).invoke(updateLeafCommand);
          assertTrue(updateResult.isReply());
          assertEquals(done(), updateResult.getReply());
        }
      }
    }

    var state = testKit.getState();
    assertEquals(quantity, state.quantity());
  }
}
