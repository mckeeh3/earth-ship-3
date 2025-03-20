package io.earthship3.application.order;

import static akka.Done.done;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.earthship3.domain.order.OrderItem;

public class OrderItemEntityTest {
  @Test
  public void testCreateOrderItemWithQuantity1() {
    var testKit = EventSourcedTestKit.of(OrderItemEntity::new);

    var orderItemId = UUID.randomUUID().toString();
    var parentOrderItemId = Optional.of(UUID.randomUUID().toString());
    var orderId = "123";
    var stockId = "234";
    var stockName = "567";
    var price = BigDecimal.valueOf(100);
    var quantity = 5;

    var command = new OrderItem.Command.CreateOrderItem(orderItemId, parentOrderItemId, orderId, stockId, stockName, price, quantity);
    var result = testKit.method(OrderItemEntity::createOrderItem).invoke(command);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertEquals(2, result.getAllEvents().size());

    {
      var event = result.getNextEventOfType(OrderItem.Event.OrderItemCreated.class);
      assertEquals(orderItemId, event.orderItemId());
      assertEquals(parentOrderItemId, event.parentOrderItemId());
      assertEquals(orderId, event.orderId());
      assertEquals(stockId, event.stockId());
      assertEquals(stockName, event.stockName());
      assertEquals(price, event.price());
      assertEquals(quantity, event.quantity());
    }

    {
      var state = testKit.getState();
      var event = result.getNextEventOfType(OrderItem.Event.OrderStockItemsToBeCreated.class);
      assertEquals(state.orderItemId(), event.parentOrderItemId().get());
      assertEquals(orderId, event.orderId());
      assertEquals(stockId, event.stockId());
      assertTrue(event.parentOrderItemId().isPresent());
      assertEquals(stockName, event.stockName());
      assertEquals(price, event.price());
      assertEquals(quantity, event.quantity());
    }

    {
      var state = testKit.getState();
      assertEquals(orderItemId, state.orderItemId());
      assertEquals(parentOrderItemId, state.parentOrderItemId());
      assertEquals(orderId, state.orderId());
      assertEquals(stockId, state.stockId());
      assertTrue(state.parentOrderItemId().isPresent());
      assertEquals(stockName, state.stockName());
      assertEquals(0, price.compareTo(state.price()));
      assertEquals(quantity, state.quantity());
      assertEquals(0, BigDecimal.valueOf(quantity * price.doubleValue()).compareTo(state.totalPrice()));
    }
  }

  @Test
  public void testCreateOrderItemWithQuantityMinUnitsPerBranchMinusOne() {
    var testKit = EventSourcedTestKit.of(OrderItemEntity::new);

    var orderItemId = UUID.randomUUID().toString();
    var parentOrderItemId = Optional.of(UUID.randomUUID().toString());
    var orderId = "123";
    var stockId = "234";
    var stockName = "567";
    var price = BigDecimal.valueOf(100);
    var quantity = OrderItem.minUnitsPerBranch - 1;

    var command = new OrderItem.Command.CreateOrderItem(orderItemId, parentOrderItemId, orderId, stockId, stockName, price, quantity);
    var result = testKit.method(OrderItemEntity::createOrderItem).invoke(command);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertEquals(2, result.getAllEvents().size());

    {
      var event = result.getNextEventOfType(OrderItem.Event.OrderItemCreated.class);
      assertEquals(quantity, event.quantity());
    }

    {
      var event = result.getNextEventOfType(OrderItem.Event.OrderStockItemsToBeCreated.class);
      assertEquals(quantity, event.quantity());
      assertTrue(event.parentOrderItemId().isPresent());
      assertEquals(orderItemId, event.parentOrderItemId().get());
    }
  }

  @Test
  public void testCreateOrderItemWithQuantityMinUnitsPerBranchPlusOne() {
    var testKit = EventSourcedTestKit.of(OrderItemEntity::new);

    var orderItemId = UUID.randomUUID().toString();
    var parentOrderItemId = Optional.of(UUID.randomUUID().toString());
    var orderId = "123";
    var stockId = "234";
    var stockName = "567";
    var price = BigDecimal.valueOf(100);
    var quantity = OrderItem.minUnitsPerBranch + 1;

    var command = new OrderItem.Command.CreateOrderItem(orderItemId, parentOrderItemId, orderId, stockId, stockName, price, quantity);
    var result = testKit.method(OrderItemEntity::createOrderItem).invoke(command);
    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertEquals(3, result.getAllEvents().size());

    {
      var event = result.getNextEventOfType(OrderItem.Event.OrderItemCreated.class);
      assertEquals(quantity, event.quantity());
    }

    {
      var event = result.getNextEventOfType(OrderItem.Event.OrderStockItemsToBeCreated.class);
      assertEquals(OrderItem.minUnitsPerBranch, event.quantity());
      assertTrue(event.parentOrderItemId().isPresent());
      assertEquals(orderItemId, event.parentOrderItemId().get());
    }

    {
      var event = result.getNextEventOfType(OrderItem.Event.OrderStockItemsToBeCreated.class);
      assertEquals(quantity - OrderItem.minUnitsPerBranch, event.quantity());
      assertTrue(event.parentOrderItemId().isPresent());
      assertEquals(orderItemId, event.parentOrderItemId().get());
    }
  }

  @Test
  public void testCreateOrderItemWithQuantity2BranchesPlusOne() {
    var testKit = EventSourcedTestKit.of(OrderItemEntity::new);

    var orderItemId = UUID.randomUUID().toString();
    var parentOrderItemId = Optional.of(UUID.randomUUID().toString());
    var orderId = "123";
    var stockId = "234";
    var stockName = "567";
    var price = BigDecimal.valueOf(100);
    var quantity = 2 * OrderItem.minUnitsPerBranch + 1;

    var command = new OrderItem.Command.CreateOrderItem(orderItemId, parentOrderItemId, orderId, stockId, stockName, price, quantity);
    var result = testKit.method(OrderItemEntity::createOrderItem).invoke(command);
    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertEquals(4, result.getAllEvents().size());

    {
      var event = result.getNextEventOfType(OrderItem.Event.OrderItemCreated.class);
      assertEquals(quantity, event.quantity());
    }

    {
      var event = result.getNextEventOfType(OrderItem.Event.OrderStockItemsToBeCreated.class);
      assertEquals(OrderItem.minUnitsPerBranch, event.quantity());
      assertTrue(event.parentOrderItemId().isPresent());
      assertEquals(orderItemId, event.parentOrderItemId().get());
    }

    {
      var event = result.getNextEventOfType(OrderItem.Event.OrderStockItemsToBeCreated.class);
      assertEquals(OrderItem.minUnitsPerBranch, event.quantity());
      assertTrue(event.parentOrderItemId().isPresent());
      assertEquals(orderItemId, event.parentOrderItemId().get());
    }

    {
      var event = result.getNextEventOfType(OrderItem.Event.OrderStockItemsToBeCreated.class);
      assertEquals(quantity % OrderItem.minUnitsPerBranch, event.quantity());
      assertTrue(event.parentOrderItemId().isPresent());
      assertEquals(orderItemId, event.parentOrderItemId().get());
    }

    {
      var state = testKit.getState();
      assertEquals(quantity, state.quantity());
    }
  }

  @Test
  public void testCreateOrderItemWithQuantity3BranchesMinusOne() {
    var testKit = EventSourcedTestKit.of(OrderItemEntity::new);

    var orderItemId = UUID.randomUUID().toString();
    var parentOrderItemId = Optional.of(UUID.randomUUID().toString());
    var orderId = "123";
    var stockId = "234";
    var stockName = "567";
    var price = BigDecimal.valueOf(100);
    var quantity = 3 * OrderItem.minUnitsPerBranch - 1;

    var command = new OrderItem.Command.CreateOrderItem(orderItemId, parentOrderItemId, orderId, stockId, stockName, price, quantity);
    var result = testKit.method(OrderItemEntity::createOrderItem).invoke(command);
    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertEquals(4, result.getAllEvents().size());

    {
      var event = result.getNextEventOfType(OrderItem.Event.OrderItemCreated.class);
      assertEquals(quantity, event.quantity());
    }

    {
      var event = result.getNextEventOfType(OrderItem.Event.OrderStockItemsToBeCreated.class);
      assertEquals(OrderItem.minUnitsPerBranch, event.quantity());
      assertTrue(event.parentOrderItemId().isPresent());
      assertEquals(orderItemId, event.parentOrderItemId().get());
    }

    {
      var event = result.getNextEventOfType(OrderItem.Event.OrderStockItemsToBeCreated.class);
      assertEquals(OrderItem.minUnitsPerBranch, event.quantity());
      assertTrue(event.parentOrderItemId().isPresent());
      assertEquals(orderItemId, event.parentOrderItemId().get());
    }

    {
      var event = result.getNextEventOfType(OrderItem.Event.OrderStockItemsToBeCreated.class);
      assertEquals(quantity % OrderItem.minUnitsPerBranch, event.quantity());
      assertTrue(event.parentOrderItemId().isPresent());
      assertEquals(orderItemId, event.parentOrderItemId().get());
    }

    {
      var events = result.getAllEvents().stream()
          .filter(event -> event instanceof OrderItem.Event.OrderStockItemsToBeCreated)
          .map(event -> (OrderItem.Event.OrderStockItemsToBeCreated) event)
          .toList();
      assertEquals(quantity / OrderItem.minUnitsPerBranch + (quantity % OrderItem.minUnitsPerBranch > 0 ? 1 : 0), events.size());

      var eventsQuantity = events.stream()
          .map(event -> event.quantity())
          .reduce(0, (a, b) -> a + b);
      assertEquals(quantity, eventsQuantity);
    }
  }

  @Test
  public void testCreateOrderItemWithQuantityOnePointFiveMaxBranches() {
    var testKit = EventSourcedTestKit.of(OrderItemEntity::new);

    var orderItemId = UUID.randomUUID().toString();
    var parentOrderItemId = Optional.of(UUID.randomUUID().toString());
    var orderId = "123";
    var stockId = "234";
    var stockName = "567";
    var price = BigDecimal.valueOf(100);
    var quantity = (int) (1.5 * OrderItem.maxBranches * OrderItem.minUnitsPerBranch) + OrderItem.minUnitsPerBranch - 1;

    var command = new OrderItem.Command.CreateOrderItem(orderItemId, parentOrderItemId, orderId, stockId, stockName, price, quantity);
    var result = testKit.method(OrderItemEntity::createOrderItem).invoke(command);
    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertEquals(OrderItem.maxBranches + 1, result.getAllEvents().size());

    {
      var event = result.getNextEventOfType(OrderItem.Event.OrderItemCreated.class);
      assertEquals(quantity, event.quantity());
    }

    {
      var events = result.getAllEvents().stream()
          .filter(event -> event instanceof OrderItem.Event.OrderItemBranchToBeCreated)
          .map(event -> (OrderItem.Event.OrderItemBranchToBeCreated) event)
          .toList();
      assertEquals(OrderItem.maxBranches, events.size());

      var eventsQuantity = events.stream()
          .map(event -> event.quantity())
          .reduce(0, (a, b) -> a + b);
      assertEquals(quantity, eventsQuantity);

      var allEventsMatchParentOrderItemId = events.stream()
          .filter(event -> event instanceof OrderItem.Event.OrderItemBranchToBeCreated)
          .map(event -> (OrderItem.Event.OrderItemBranchToBeCreated) event)
          .allMatch(event -> event.parentOrderItemId().isPresent() && event.parentOrderItemId().get().equals(orderItemId));
      assertTrue(allEventsMatchParentOrderItemId);
    }
  }
}