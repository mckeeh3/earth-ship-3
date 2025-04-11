package io.earthship3.application.order;

import static akka.Done.done;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static io.earthship3.ShortUUID.randomUUID;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.earthship3.domain.order.OrderItemBranch;

public class OrderItemBranchEntityTest {
  @Test
  public void testCreateOrderItemWithQuantity1() {
    var testKit = EventSourcedTestKit.of(OrderItemBranchEntity::new);

    var branchId = randomUUID();
    var parentBranchId = Optional.of(randomUUID());
    var orderId = "123";
    var stockId = "234";
    var stockName = "567";
    var price = BigDecimal.valueOf(100);
    var quantity = 5;

    var command = new OrderItemBranch.Command.CreateBranch(branchId, parentBranchId, orderId, stockId, stockName, price, quantity);
    var result = testKit.method(OrderItemBranchEntity::createBranch).invoke(command);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertEquals(2, result.getAllEvents().size());

    {
      var event = result.getNextEventOfType(OrderItemBranch.Event.BranchCreated.class);
      assertEquals(branchId, event.branchId());
      assertEquals(parentBranchId, event.parentBranchIs());
      assertEquals(orderId, event.orderId());
      assertEquals(stockId, event.stockId());
      assertEquals(stockName, event.stockName());
      assertEquals(price, event.price());
      assertEquals(quantity, event.quantity());
    }

    {
      var state = testKit.getState();
      var event = result.getNextEventOfType(OrderItemBranch.Event.LeafToBeCreated.class);
      assertEquals(state.branchId(), event.parentBranchId());
      assertEquals(orderId, event.orderId());
      assertEquals(stockId, event.stockId());
      assertEquals(stockName, event.stockName());
      assertEquals(price, event.price());
      assertEquals(quantity, event.quantity());
    }

    {
      var state = testKit.getState();
      assertEquals(branchId, state.branchId());
      assertEquals(parentBranchId, state.parentBranchId());
      assertEquals(orderId, state.orderId());
      assertEquals(stockId, state.stockId());
      assertTrue(state.parentBranchId().isPresent());
      assertEquals(stockName, state.stockName());
      assertEquals(0, price.compareTo(state.price()));
      assertEquals(quantity, state.quantity());
      assertEquals(0, BigDecimal.valueOf(quantity * price.doubleValue()).compareTo(state.totalPrice()));
    }
  }

  @Test
  public void testCreateOrderItemWithQuantityMinUnitsPerBranchMinusOne() {
    var testKit = EventSourcedTestKit.of(OrderItemBranchEntity::new);

    var branchId = randomUUID();
    var parentBranchId = Optional.of(randomUUID());
    var orderId = "123";
    var stockId = "234";
    var stockName = "567";
    var price = BigDecimal.valueOf(100);
    var quantity = OrderItemBranch.minUnitsPerBranch - 1;

    var command = new OrderItemBranch.Command.CreateBranch(branchId, parentBranchId, orderId, stockId, stockName, price, quantity);
    var result = testKit.method(OrderItemBranchEntity::createBranch).invoke(command);

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertEquals(2, result.getAllEvents().size());

    {
      var event = result.getNextEventOfType(OrderItemBranch.Event.BranchCreated.class);
      assertEquals(quantity, event.quantity());
    }

    {
      var event = result.getNextEventOfType(OrderItemBranch.Event.LeafToBeCreated.class);
      assertEquals(quantity, event.quantity());
      assertEquals(branchId, event.parentBranchId());
    }
  }

  @Test
  public void testCreateOrderItemWithQuantityMinUnitsPerBranchPlusOne() {
    var testKit = EventSourcedTestKit.of(OrderItemBranchEntity::new);

    var orderItemId = randomUUID();
    var parentOrderItemId = Optional.of(randomUUID());
    var orderId = "123";
    var stockId = "234";
    var stockName = "567";
    var price = BigDecimal.valueOf(100);
    var quantity = OrderItemBranch.minUnitsPerBranch + 1;

    var command = new OrderItemBranch.Command.CreateBranch(orderItemId, parentOrderItemId, orderId, stockId, stockName, price, quantity);
    var result = testKit.method(OrderItemBranchEntity::createBranch).invoke(command);
    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertEquals(3, result.getAllEvents().size());

    {
      var event = result.getNextEventOfType(OrderItemBranch.Event.BranchCreated.class);
      assertEquals(quantity, event.quantity());
    }

    {
      var event = result.getNextEventOfType(OrderItemBranch.Event.LeafToBeCreated.class);
      assertEquals(orderItemId, event.parentBranchId());
    }

    {
      var event = result.getNextEventOfType(OrderItemBranch.Event.LeafToBeCreated.class);
      assertEquals(orderItemId, event.parentBranchId());
    }
  }

  @Test
  public void testCreateOrderItemWithQuantity2BranchesPlusOne() {
    var testKit = EventSourcedTestKit.of(OrderItemBranchEntity::new);

    var branchId = randomUUID();
    var parentBranchId = Optional.of(randomUUID());
    var orderId = "123";
    var stockId = "234";
    var stockName = "567";
    var price = BigDecimal.valueOf(100);
    var quantity = 2 * OrderItemBranch.minUnitsPerBranch + 1;

    var command = new OrderItemBranch.Command.CreateBranch(branchId, parentBranchId, orderId, stockId, stockName, price, quantity);
    var result = testKit.method(OrderItemBranchEntity::createBranch).invoke(command);
    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertEquals(4, result.getAllEvents().size());

    {
      var event = result.getNextEventOfType(OrderItemBranch.Event.BranchCreated.class);
      assertEquals(quantity, event.quantity());
    }

    {
      var event = result.getNextEventOfType(OrderItemBranch.Event.LeafToBeCreated.class);
      assertEquals(branchId, event.parentBranchId());
    }

    {
      var event = result.getNextEventOfType(OrderItemBranch.Event.LeafToBeCreated.class);
      assertEquals(branchId, event.parentBranchId());
    }

    {
      var event = result.getNextEventOfType(OrderItemBranch.Event.LeafToBeCreated.class);
      assertEquals(branchId, event.parentBranchId());
    }

    {
      var state = testKit.getState();
      assertEquals(quantity, state.quantity());
    }
  }

  @Test
  public void testCreateOrderItemWithQuantity3BranchesMinusOne() {
    var testKit = EventSourcedTestKit.of(OrderItemBranchEntity::new);

    var branchId = randomUUID();
    var parentBranchId = Optional.of(randomUUID());
    var orderId = "123";
    var stockId = "234";
    var stockName = "567";
    var price = BigDecimal.valueOf(100);
    var quantity = 3 * OrderItemBranch.minUnitsPerBranch - 1;

    var command = new OrderItemBranch.Command.CreateBranch(branchId, parentBranchId, orderId, stockId, stockName, price, quantity);
    var result = testKit.method(OrderItemBranchEntity::createBranch).invoke(command);
    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertEquals(4, result.getAllEvents().size());

    {
      var event = result.getNextEventOfType(OrderItemBranch.Event.BranchCreated.class);
      assertEquals(quantity, event.quantity());
    }

    {
      var event = result.getNextEventOfType(OrderItemBranch.Event.LeafToBeCreated.class);
      assertEquals(OrderItemBranch.minUnitsPerBranch, event.quantity());
      assertEquals(branchId, event.parentBranchId());
    }

    {
      var event = result.getNextEventOfType(OrderItemBranch.Event.LeafToBeCreated.class);
      assertEquals(OrderItemBranch.minUnitsPerBranch, event.quantity());
      assertEquals(branchId, event.parentBranchId());
    }

    {
      var event = result.getNextEventOfType(OrderItemBranch.Event.LeafToBeCreated.class);
      assertEquals(quantity % OrderItemBranch.minUnitsPerBranch, event.quantity());
      assertEquals(branchId, event.parentBranchId());
    }

    {
      var events = result.getAllEvents().stream()
          .filter(event -> event instanceof OrderItemBranch.Event.LeafToBeCreated)
          .map(event -> (OrderItemBranch.Event.LeafToBeCreated) event)
          .toList();
      assertEquals(quantity / OrderItemBranch.minUnitsPerBranch + (quantity % OrderItemBranch.minUnitsPerBranch > 0 ? 1 : 0), events.size());

      var eventsQuantity = events.stream()
          .map(event -> event.quantity())
          .reduce(0, (a, b) -> a + b);
      assertEquals(quantity, eventsQuantity);
    }
  }

  @Test
  public void testCreateOrderItemWithQuantityOnePointFiveMaxBranches() {
    var testKit = EventSourcedTestKit.of(OrderItemBranchEntity::new);

    var branchId = randomUUID();
    var parentBranchId = Optional.of(randomUUID());
    var orderId = "123";
    var stockId = "234";
    var stockName = "567";
    var price = BigDecimal.valueOf(100);
    var quantity = (int) (1.5 * OrderItemBranch.maxBranches * OrderItemBranch.minUnitsPerBranch) + OrderItemBranch.minUnitsPerBranch - 1;

    var command = new OrderItemBranch.Command.CreateBranch(branchId, parentBranchId, orderId, stockId, stockName, price, quantity);
    var result = testKit.method(OrderItemBranchEntity::createBranch).invoke(command);
    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertEquals(OrderItemBranch.maxBranches + 1, result.getAllEvents().size());

    {
      var event = result.getNextEventOfType(OrderItemBranch.Event.BranchCreated.class);
      assertEquals(quantity, event.quantity());
    }

    {
      var quantityOrderItemToBeCreated = result.getAllEvents().stream()
          .filter(event -> event instanceof OrderItemBranch.Event.BranchToBeCreated)
          .map(event -> (OrderItemBranch.Event.BranchToBeCreated) event)
          .map(event -> event.quantity())
          .reduce(0, (a, b) -> a + b);
      var quantityStockItemsToBeCreated = result.getAllEvents().stream()
          .filter(event -> event instanceof OrderItemBranch.Event.LeafToBeCreated)
          .map(event -> (OrderItemBranch.Event.LeafToBeCreated) event)
          .map(event -> event.quantity())
          .reduce(0, (a, b) -> a + b);
      assertEquals(quantity, quantityStockItemsToBeCreated + quantityOrderItemToBeCreated);
    }
  }
}