package io.earthship3.application.order;

import static akka.Done.done;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.TestKitSupport;
import io.earthship3.domain.order.ShoppingCart;

public class OrderIntegrationTest extends TestKitSupport {

  @Test
  public void testCheckout() throws Exception {
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
      var result = await(
          componentClient.forView()
              .method(OrderByCustomerIdView::findByCustomerId)
              .invokeAsync(customerId));

      while (result.orders().isEmpty()) {
        Thread.sleep(1000);
        result = await(
            componentClient.forView()
                .method(OrderByCustomerIdView::findByCustomerId)
                .invokeAsync(customerId));
      }
      assertTrue(result.orders().size() > 0);
    }
  }
}
