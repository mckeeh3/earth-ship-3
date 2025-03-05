package io.earthship3.application.order;

import static akka.Done.done;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.TestKitSupport;
import io.earthship3.application.order.OrderByCustomerIdView.Orders;
import io.earthship3.domain.order.ShoppingCart;

public class OrderIntegrationTest extends TestKitSupport {

  @Test
  public void testCheckout() {
    var customerId = "customer-1";
    {
      var command = new ShoppingCart.Command.AddLineItem(customerId, "456", "789", BigDecimal.valueOf(123.45), 1);
      var result = await(
          componentClient.forEventSourcedEntity(customerId)
              .method(ShoppingCartEntity::addLineItem)
              .invokeAsync(command));

      assertEquals(done(), result);
    }

    {
      var command = new ShoppingCart.Command.Checkout(customerId);
      var result = await(
          componentClient.forEventSourcedEntity(customerId)
              .method(ShoppingCartEntity::checkout)
              .invokeAsync(command));

      assertEquals(done(), result);
    }

    {
      var result = queryOrdersByCustomerId(customerId);

      var orders = Stream.iterate(result, r -> {
        sleep(1);
        return queryOrdersByCustomerId(customerId);
      }).takeWhile(r -> r.orders().isEmpty()).toList();
      assertTrue(orders.size() > 0);
    }
  }

  private Orders queryOrdersByCustomerId(String customerId) {
    return await(
        componentClient.forView()
            .method(OrderByCustomerIdView::findByCustomerId)
            .invokeAsync(customerId));
  }

  private void sleep(int seconds) {
    try {
      TimeUnit.SECONDS.sleep(seconds);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
