package io.earthship3.domain.order;

import java.math.BigDecimal;

public interface OrderItemKv {

  public record State(
      String orderItemKvId,
      String customerId,
      String orderId,
      String stockId,
      String stockName,
      BigDecimal price,
      int quantity,
      BigDecimal totalPrice) {

    public static State empty() {
      return new State(null, null, null, null, null, BigDecimal.ZERO, 0, BigDecimal.ZERO);
    }

    public boolean isEmpty() {
      return orderItemKvId == null;
    }

    public State onCommand(Command.CreateOrderItemKv command) {
      if (!isEmpty()) {
        return this;
      }

      return new State(
          command.orderItemKvId,
          command.customerId,
          command.orderId,
          command.stockId,
          command.stockName,
          command.price,
          command.quantity,
          command.totalPrice);
    }

    public State onCommand(Command.ChangeQuantity command) {
      return new State(
          command.orderItemKvId,
          customerId,
          orderId,
          stockId,
          stockName,
          price,
          command.quantity,
          totalPrice);
    }

    public State onCommand(Command.ChangePrice command) {
      return new State(
          command.orderItemKvId,
          customerId,
          orderId,
          stockId,
          stockName,
          command.price,
          quantity,
          totalPrice);
    }

    public State onCommand(Command.CancelOrderItemKv command) {
      if (isEmpty()) {
        return this;
      }

      return new State(
          command.orderItemKvId,
          null,
          null,
          null,
          null,
          BigDecimal.ZERO,
          0,
          BigDecimal.ZERO);
    }
  }

  public sealed interface Command {

    record CreateOrderItemKv(
        String orderItemKvId,
        String customerId,
        String orderId,
        String stockId,
        String stockName,
        BigDecimal price,
        int quantity,
        BigDecimal totalPrice) implements Command {}

    record ChangeQuantity(
        String orderItemKvId,
        int quantity) implements Command {}

    record ChangePrice(
        String orderItemKvId,
        BigDecimal price) implements Command {}

    record CancelOrderItemKv(
        String orderItemKvId) implements Command {}
  }
}
