package io.earthship3.application.order;

import static akka.Done.done;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.earthship3.domain.order.ShoppingCart;
import io.earthship3.domain.order.ShoppingCart.Command;
import io.earthship3.domain.order.ShoppingCart.Event;

public class ShoppingCartEntityTest {
  @Test
  void testAddLineItem() {
    var testKit = EventSourcedTestKit.of(ShoppingCartEntity::new);

    var customerId = "123";
    var skuId = "456";
    var skuName = "789";
    var price = BigDecimal.valueOf(100);
    var quantity = 1;

    var command = new Command.AddLineItem(customerId, skuId, skuName, price, quantity);
    var result = testKit.method(ShoppingCartEntity::addLineItem).invoke(command);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertEquals(1, result.getAllEvents().size());

    var event = result.getNextEventOfType(Event.LineItemAdded.class);
    assertEquals(customerId, event.customerId());
    assertEquals(skuId, event.lineItem().skuId());
    assertEquals(skuName, event.lineItem().skuName());
    assertEquals(price, event.lineItem().price());
    assertEquals(quantity, event.lineItem().quantity());

    var state = testKit.getState();
    assertEquals(customerId, state.customerId());
    assertEquals(List.of(new ShoppingCart.LineItem(skuId, skuName, price, quantity)), state.lineItems());
  }

  @Test
  void testUpdateLineItem() {
    var testKit = EventSourcedTestKit.of(ShoppingCartEntity::new);

    var customerId = "123";
    var lineItems = List.of(
        new ShoppingCart.LineItem("123", "item1", BigDecimal.valueOf(10), 1),
        new ShoppingCart.LineItem("456", "item2", BigDecimal.valueOf(20), 2),
        new ShoppingCart.LineItem("789", "item3", BigDecimal.valueOf(30), 3));
    var updatedLineItem = new ShoppingCart.LineItem("456", "updated item2", BigDecimal.valueOf(200), 22);

    {
      var lineItem = lineItems.get(0);
      var command = new Command.AddLineItem(customerId, lineItem.skuId(), lineItem.skuName(), lineItem.price(), lineItem.quantity());
      var result = testKit.method(ShoppingCartEntity::addLineItem).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(1, result.getAllEvents().size());
    }

    {
      var lineItem = lineItems.get(1);
      var command = new Command.AddLineItem(customerId, lineItem.skuId(), lineItem.skuName(), lineItem.price(), lineItem.quantity());
      var result = testKit.method(ShoppingCartEntity::addLineItem).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(1, result.getAllEvents().size());
    }

    {
      var lineItem = lineItems.get(2);
      var command = new Command.AddLineItem(customerId, lineItem.skuId(), lineItem.skuName(), lineItem.price(), lineItem.quantity());
      var result = testKit.method(ShoppingCartEntity::addLineItem).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(1, result.getAllEvents().size());
    }

    {
      var command = new Command.UpdateLineItem(customerId, updatedLineItem.skuId(), updatedLineItem.skuName(), updatedLineItem.price(), updatedLineItem.quantity());
      var result = testKit.method(ShoppingCartEntity::updateLineItem).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(1, result.getAllEvents().size());

      var event = result.getNextEventOfType(Event.LineItemUpdated.class);
      assertEquals(customerId, event.customerId());
      assertEquals(updatedLineItem.skuId(), event.lineItem().skuId());
      assertEquals(updatedLineItem.skuName(), event.lineItem().skuName());
      assertEquals(updatedLineItem.price(), event.lineItem().price());
      assertEquals(updatedLineItem.quantity(), event.lineItem().quantity());
    }

    {
      var state = testKit.getState();
      var lineItem = state.lineItems().stream()
          .filter(item -> item.skuId().equals(updatedLineItem.skuId()))
          .findFirst()
          .orElseThrow();
      assertEquals(updatedLineItem.skuName(), lineItem.skuName());
      assertEquals(updatedLineItem.price(), lineItem.price());
      assertEquals(updatedLineItem.quantity(), lineItem.quantity());
    }
  }

  @Test
  void testRemoveLineItem() {
    var testKit = EventSourcedTestKit.of(ShoppingCartEntity::new);

    var customerId = "123";
    var lineItems = List.of(
        new ShoppingCart.LineItem("123", "item1", BigDecimal.valueOf(10), 1),
        new ShoppingCart.LineItem("456", "item2", BigDecimal.valueOf(20), 2),
        new ShoppingCart.LineItem("789", "item3", BigDecimal.valueOf(30), 3));

    {
      var lineItem = lineItems.get(0);
      var command = new Command.AddLineItem(customerId, lineItem.skuId(), lineItem.skuName(), lineItem.price(), lineItem.quantity());
      var result = testKit.method(ShoppingCartEntity::addLineItem).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(1, result.getAllEvents().size());
    }

    {
      var lineItem = lineItems.get(1);
      var command = new Command.AddLineItem(customerId, lineItem.skuId(), lineItem.skuName(), lineItem.price(), lineItem.quantity());
      var result = testKit.method(ShoppingCartEntity::addLineItem).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(1, result.getAllEvents().size());
    }

    {
      var lineItem = lineItems.get(2);
      var command = new Command.AddLineItem(customerId, lineItem.skuId(), lineItem.skuName(), lineItem.price(), lineItem.quantity());
      var result = testKit.method(ShoppingCartEntity::addLineItem).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(1, result.getAllEvents().size());
    }

    {
      var lineItem = lineItems.get(1);
      var command = new Command.RemoveLineItem(customerId, lineItem.skuId());
      var result = testKit.method(ShoppingCartEntity::removeLineItem).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(1, result.getAllEvents().size());

      var event = result.getNextEventOfType(Event.LineItemRemoved.class);
      assertEquals(customerId, event.customerId());
      assertEquals(lineItem.skuId(), event.skuId());
    }

    {
      var state = testKit.getState();
      assertEquals(customerId, state.customerId());
      assertEquals(List.of(lineItems.get(0), lineItems.get(2)), state.lineItems());
    }
  }

  @Test
  void testCheckout() {
    var testKit = EventSourcedTestKit.of(ShoppingCartEntity::new);

    var customerId = "123";
    var lineItems = List.of(
        new ShoppingCart.LineItem("123", "item1", BigDecimal.valueOf(10), 1),
        new ShoppingCart.LineItem("456", "item2", BigDecimal.valueOf(20), 2),
        new ShoppingCart.LineItem("789", "item3", BigDecimal.valueOf(30), 3));

    {
      var lineItem = lineItems.get(0);
      var command = new Command.AddLineItem(customerId, lineItem.skuId(), lineItem.skuName(), lineItem.price(), lineItem.quantity());
      var result = testKit.method(ShoppingCartEntity::addLineItem).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(1, result.getAllEvents().size());
    }

    {
      var lineItem = lineItems.get(1);
      var command = new Command.AddLineItem(customerId, lineItem.skuId(), lineItem.skuName(), lineItem.price(), lineItem.quantity());
      var result = testKit.method(ShoppingCartEntity::addLineItem).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(1, result.getAllEvents().size());
    }

    {
      var lineItem = lineItems.get(2);
      var command = new Command.AddLineItem(customerId, lineItem.skuId(), lineItem.skuName(), lineItem.price(), lineItem.quantity());
      var result = testKit.method(ShoppingCartEntity::addLineItem).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(1, result.getAllEvents().size());
    }

    {
      var command = new Command.Checkout(customerId);
      var result = testKit.method(ShoppingCartEntity::checkout).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(1, result.getAllEvents().size());

      var event = result.getNextEventOfType(Event.CheckedOut.class);
      assertEquals(customerId, event.customerId());
      assertTrue(event.checkedOutAt().isAfter(Instant.EPOCH));
      assertEquals(lineItems, event.lineItems());
      assertNotNull(event.orderId());
    }

    {
      var state = testKit.getState();
      assertEquals(customerId, state.customerId());
      assertEquals(List.of(), state.lineItems());
    }
  }
}
