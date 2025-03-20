package io.earthship3.application.order;

import static akka.Done.done;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.earthship3.domain.order.Order;

class OrderEntityTest {

  @Test
  void testCreateOrder() {
    var testKit = EventSourcedTestKit.of(OrderEntity::new);

    var orderId = "123";
    var customerId = "456";
    var lineItems = List.of(new Order.LineItem("789", "1000", BigDecimal.valueOf(100), 1, Optional.empty(), Optional.empty()));

    var orderedAt = Instant.now();
    var command = new Order.Command.CreateOrder(orderId, customerId, orderedAt, lineItems);
    var result = testKit.method(OrderEntity::createOrder).invoke(command);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertEquals(1 + lineItems.size(), result.getAllEvents().size());

    {
      var event = result.getNextEventOfType(Order.Event.OrderCreated.class);
      assertEquals(orderId, event.orderId());
      assertEquals(customerId, event.customerId());
      assertEquals(lineItems, event.lineItems());
      assertEquals(BigDecimal.valueOf(100), event.totalPrice());
      assertEquals(orderedAt, event.orderedAt());
    }

    {
      var event = result.getNextEventOfType(Order.Event.OrderItemCreated.class);
      assertEquals(orderId, event.orderId());
      assertEquals(lineItems.get(0).stockId(), event.stockId());
      assertEquals(lineItems.get(0), event.lineItem());
    }

    var state = testKit.getState();
    assertEquals(orderId, state.orderId());
    assertEquals(customerId, state.customerId());
    assertEquals(lineItems, state.lineItems());
    assertEquals(BigDecimal.valueOf(100), state.totalPrice());
    assertTrue(state.readyToShipAt().isEmpty());
    assertTrue(state.backOrderedAt().isEmpty());
    assertTrue(state.cancelledAt().isEmpty());
  }

  @Test
  void testOrderItemReadyToShip() {
    var testKit = EventSourcedTestKit.of(OrderEntity::new);

    var orderId = "123";
    var stockId1 = "456";
    var stockId2 = "789";
    var stockId3 = "012";
    var lineItems = List.of(
        new Order.LineItem(stockId1, "1000", BigDecimal.valueOf(100), 1, Optional.empty(), Optional.empty()),
        new Order.LineItem(stockId2, "1000", BigDecimal.valueOf(100), 1, Optional.empty(), Optional.empty()),
        new Order.LineItem(stockId3, "1000", BigDecimal.valueOf(100), 1, Optional.empty(), Optional.empty()));
    {
      var orderedAt = Instant.now();
      var command = new Order.Command.CreateOrder(orderId, "123", orderedAt, lineItems);
      var result = testKit.method(OrderEntity::createOrder).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(1 + lineItems.size(), result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(Order.Event.OrderCreated.class);
        assertEquals(orderId, event.orderId());
        assertEquals(lineItems, event.lineItems());
        assertEquals(orderedAt, event.orderedAt());
      }

      {
        var event = result.getNextEventOfType(Order.Event.OrderItemCreated.class);
        assertEquals(orderId, event.orderId());
        assertEquals(lineItems.get(0).stockId(), event.stockId());
      }

      {
        var event = result.getNextEventOfType(Order.Event.OrderItemCreated.class);
        assertEquals(orderId, event.orderId());
        assertEquals(lineItems.get(1).stockId(), event.stockId());
      }

      {
        var event = result.getNextEventOfType(Order.Event.OrderItemCreated.class);
        assertEquals(orderId, event.orderId());
        assertEquals(lineItems.get(2).stockId(), event.stockId());
      }
    }

    {
      var command = new Order.Command.OrderItemReadyToShip(orderId, stockId1);
      var result = testKit.method(OrderEntity::orderItemReadyToShip).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(1, result.getAllEvents().size());

      var event = result.getNextEventOfType(Order.Event.OrderItemReadyToShip.class);
      assertEquals(orderId, event.orderId());
      assertEquals(stockId1, event.stockId());

      var lineItem = event.lineItems().stream().filter(item -> item.stockId().equals(stockId1)).findFirst().orElseThrow();
      assertEquals(stockId1, lineItem.stockId());
      assertTrue(lineItem.readyToShipAt().isPresent());
    }

    {
      var command = new Order.Command.OrderItemReadyToShip(orderId, stockId2);
      var result = testKit.method(OrderEntity::orderItemReadyToShip).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(1, result.getAllEvents().size());

      var event = result.getNextEventOfType(Order.Event.OrderItemReadyToShip.class);
      assertEquals(orderId, event.orderId());
      assertEquals(stockId2, event.stockId());

      var lineItem = event.lineItems().stream().filter(item -> item.stockId().equals(stockId2)).findFirst().orElseThrow();
      assertEquals(stockId2, lineItem.stockId());
      assertTrue(lineItem.readyToShipAt().isPresent());
    }

    {
      var command = new Order.Command.OrderItemReadyToShip(orderId, stockId3);
      var result = testKit.method(OrderEntity::orderItemReadyToShip).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(2, result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(Order.Event.OrderItemReadyToShip.class);
        assertEquals(orderId, event.orderId());
        assertEquals(stockId3, event.stockId());
        assertEquals(lineItems.size(), event.lineItems().size());
        assertTrue(event.lineItems().stream().allMatch(item -> item.readyToShipAt().isPresent()));
      }

      {
        var event = result.getNextEventOfType(Order.Event.OrderReadyToShip.class);
        assertEquals(orderId, event.orderId());
        assertTrue(event.readyToShipAt().isPresent());
      }
    }

    {
      var state = testKit.getState();
      assertEquals(orderId, state.orderId());
      assertTrue(state.readyToShipAt().isPresent());
      assertTrue(state.backOrderedAt().isEmpty());
      assertTrue(state.cancelledAt().isEmpty());
    }
  }

  @Test
  void testOrderItemBackOrdered() {
    var testKit = EventSourcedTestKit.of(OrderEntity::new);

    var orderId = "123";
    var stockId1 = "456";
    var stockId2 = "789";
    var stockId3 = "012";
    var lineItems = List.of(
        new Order.LineItem(stockId1, "1000", BigDecimal.valueOf(101), 2, Optional.empty(), Optional.empty()),
        new Order.LineItem(stockId2, "1000", BigDecimal.valueOf(102), 3, Optional.empty(), Optional.empty()),
        new Order.LineItem(stockId3, "1000", BigDecimal.valueOf(103), 4, Optional.empty(), Optional.empty()));
    var totalPrice = lineItems.stream()
        .map(item -> item.price().multiply(BigDecimal.valueOf(item.quantity())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    {
      var orderedAt = Instant.now();
      var command = new Order.Command.CreateOrder(orderId, "123", orderedAt, lineItems);
      var result = testKit.method(OrderEntity::createOrder).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(1 + lineItems.size(), result.getAllEvents().size());

      var event = result.getNextEventOfType(Order.Event.OrderCreated.class);
      assertEquals(orderId, event.orderId());
      assertEquals(lineItems, event.lineItems());
      assertEquals(orderedAt, event.orderedAt());
    }

    {
      var command = new Order.Command.OrderItemBackOrdered(orderId, stockId1);
      var result = testKit.method(OrderEntity::orderItemBackOrdered).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(2, result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(Order.Event.OrderItemBackOrdered.class);
        assertEquals(orderId, event.orderId());
        assertEquals(stockId1, event.stockId());

        var lineItem = event.lineItems().stream().filter(item -> item.stockId().equals(stockId1)).findFirst().orElseThrow();
        assertEquals(stockId1, lineItem.stockId());
        assertTrue(lineItem.backOrderedAt().isPresent());
      }

      {
        var event = result.getNextEventOfType(Order.Event.OrderBackOrdered.class);
        assertEquals(orderId, event.orderId());
        assertTrue(event.backOrderedAt().isPresent());
      }
    }

    {
      var state = testKit.getState();
      assertEquals(orderId, state.orderId());
      assertEquals(totalPrice, state.totalPrice());
      assertTrue(state.readyToShipAt().isEmpty());
      assertTrue(state.backOrderedAt().isPresent());
      assertTrue(state.cancelledAt().isEmpty());
    }
  }

  @Test
  void testCancelOrder() {
    var testKit = EventSourcedTestKit.of(OrderEntity::new);

    var orderId = "123";
    var stockId1 = "456";
    var stockId2 = "789";
    var stockId3 = "012";
    var lineItems = List.of(
        new Order.LineItem(stockId1, "1000", BigDecimal.valueOf(100), 1, Optional.empty(), Optional.empty()),
        new Order.LineItem(stockId2, "1000", BigDecimal.valueOf(100), 1, Optional.empty(), Optional.empty()),
        new Order.LineItem(stockId3, "1000", BigDecimal.valueOf(100), 1, Optional.empty(), Optional.empty()));
    {
      var orderedAt = Instant.now();
      var command = new Order.Command.CreateOrder(orderId, "123", orderedAt, lineItems);
      var result = testKit.method(OrderEntity::createOrder).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(1 + lineItems.size(), result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(Order.Event.OrderCreated.class);
        assertEquals(orderId, event.orderId());
        assertEquals(lineItems, event.lineItems());
      }

      {
        var event = result.getNextEventOfType(Order.Event.OrderItemCreated.class);
        assertEquals(orderId, event.orderId());
        assertEquals(lineItems.get(0).stockId(), event.stockId());
      }

      {
        var event = result.getNextEventOfType(Order.Event.OrderItemCreated.class);
        assertEquals(orderId, event.orderId());
        assertEquals(lineItems.get(1).stockId(), event.stockId());
      }

      {
        var event = result.getNextEventOfType(Order.Event.OrderItemCreated.class);
        assertEquals(orderId, event.orderId());
        assertEquals(lineItems.get(2).stockId(), event.stockId());
      }
    }

    {
      var command = new Order.Command.CancelOrder(orderId);
      var result = testKit.method(OrderEntity::cancelOrder).invoke(command);

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(1 + lineItems.size(), result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(Order.Event.OrderCancelled.class);
        assertEquals(orderId, event.orderId());
        assertTrue(event.cancelledAt().isPresent());
      }

      {
        var event = result.getNextEventOfType(Order.Event.OrderItemCancelled.class);
        assertEquals(orderId, event.orderId());
        assertEquals(lineItems.get(0).stockId(), event.stockId());
        assertTrue(event.cancelledAt().isPresent());
      }

      {
        var event = result.getNextEventOfType(Order.Event.OrderItemCancelled.class);
        assertEquals(orderId, event.orderId());
        assertEquals(lineItems.get(1).stockId(), event.stockId());
        assertTrue(event.cancelledAt().isPresent());
      }

      {
        var event = result.getNextEventOfType(Order.Event.OrderItemCancelled.class);
        assertEquals(orderId, event.orderId());
        assertEquals(lineItems.get(2).stockId(), event.stockId());
        assertTrue(event.cancelledAt().isPresent());
      }
    }

    {
      var state = testKit.getState();
      assertEquals(orderId, state.orderId());
      assertTrue(state.cancelledAt().isPresent());
      assertTrue(state.readyToShipAt().isEmpty());
      assertTrue(state.backOrderedAt().isEmpty());
    }
  }
}