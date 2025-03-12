package io.earthship3.domain.order;

import java.math.BigDecimal;
import java.util.List;

public interface ShoppingCartToOrderStream {

  public record LineItem(String skuId, String skuName, BigDecimal price, int quantity) {}

  public sealed interface Event {
    public record CheckedOut(String orderId, String customerId, List<LineItem> lineItems) implements Event {}
  }
}
