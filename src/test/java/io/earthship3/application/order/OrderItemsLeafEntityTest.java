package io.earthship3.application.order;

import static akka.Done.done;
import static io.earthship3.ShortUUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.earthship3.domain.order.OrderItemsLeaf;

public class OrderItemsLeafEntityTest {
  @Test
  public void testCreateOrderItemLeaf() {
    var testKit = EventSourcedTestKit.of(OrderItemsLeafEntity::new);

    var leafId = randomUUID();
    var parentBranchId = randomUUID();
    var orderId = "123";
    var stockId = "234";
    var quantity = 5;

    var command = new OrderItemsLeaf.Command.CreateLeaf(leafId, parentBranchId, orderId, stockId, quantity);
    var result = testKit.method(OrderItemsLeafEntity::createLeaf).invoke(command);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertEquals(3, result.getAllEvents().size());

    {
      var event = result.getNextEventOfType(OrderItemsLeaf.Event.LeafCreated.class);
      assertEquals(leafId, event.leafId());
      assertEquals(parentBranchId, event.parentBranchId());
      assertEquals(orderId, event.orderId());
      assertEquals(stockId, event.stockId());
      assertEquals(quantity, event.quantity());
      assertEquals(quantity, event.orderStockItems().size());
    }

    {
      var event = result.getNextEventOfType(OrderItemsLeaf.Event.LeafQuantityUpdated.class);
      assertEquals(leafId, event.leafId());
      assertEquals(parentBranchId, event.parentBranchId());
      assertEquals(orderId, event.orderId());
      assertEquals(stockId, event.stockId());
      assertEquals(quantity, event.quantity());
      assertEquals(quantity, event.orderStockItems().size());
    }

    {
      var event = result.getNextEventOfType(OrderItemsLeaf.Event.LeafNeedsStockItems.class);
      assertEquals(leafId, event.leafId());
      assertEquals(parentBranchId, event.parentBranchId());
      assertEquals(orderId, event.orderId());
      assertEquals(stockId, event.stockId());
      assertEquals(quantity, event.quantity());
      assertEquals(quantity, event.orderStockItems().size());
    }

    {
      var state = testKit.getState();
      assertEquals(leafId, state.leafId());
      assertEquals(parentBranchId, state.parentBranchId());
      assertEquals(orderId, state.orderId());
      assertEquals(stockId, state.stockId());
      assertEquals(quantity, state.quantity());
      assertEquals(quantity, state.orderStockItems().size());
    }
  }
}
