package io.earthship3.application.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import io.earthship3.domain.order.Order;

@ComponentId("order-view")
public class OrderView extends View {
  private final Logger log = LoggerFactory.getLogger(OrderView.class);

  @Query("""
      SELECT * as orders
        FROM orders
        WHERE customerId = :customerId
        LIMIT 1000
      """)
  public QueryEffect<Orders> findByCustomerId(String customerId) {
    log.info("{}", customerId);
    return queryResult();
  }

  @Consume.FromEventSourcedEntity(OrderEntity.class)
  public static class OrderConsumer extends TableUpdater<OrderRow> {
    private final Logger log = LoggerFactory.getLogger(OrderConsumer.class);

    @Override
    public OrderRow emptyRow() {
      return OrderRow.empty();
    }

    public Effect<OrderRow> onEvent(Order.Event event) {
      log.info("Row: {}\n_Event: {}", OrderRow.eventToRow(rowState(), event), event);
      return effects().updateRow(OrderRow.eventToRow(rowState(), event));
    }
  }

  public record Orders(List<OrderRow> orders) {}

  public record Moment(String moment) {
    static Moment empty() {
      return new Moment("");
    }

    static Moment of(Optional<Instant> moment) {
      return new Moment(moment.map(Instant::toString).orElse(""));
    }

    Optional<Instant> optionalInstant() {
      return moment.isEmpty() ? Optional.empty() : Optional.of(Instant.parse(moment));
    }
  }

  public record LineItem(
      String skuId,
      String skuName,
      double price,
      int quantity,
      Moment readyToShipAt,
      Moment backOrderedAt) {

    static Order.LineItem rowToOrder(LineItem item) {
      return new Order.LineItem(
          item.skuId(),
          item.skuName(),
          BigDecimal.valueOf(item.price()),
          item.quantity(),
          item.readyToShipAt().optionalInstant(),
          item.backOrderedAt().optionalInstant());
    }

    static List<Order.LineItem> rowToOrder(List<LineItem> items) {
      return items.stream().map(LineItem::rowToOrder).toList();
    }

    static LineItem orderToRow(Order.LineItem item) {
      return new LineItem(
          item.skuId(),
          item.skuName(),
          item.price().doubleValue(),
          item.quantity(),
          Moment.of(item.readyToShipAt()),
          Moment.of(item.backOrderedAt()));
    }

    static List<LineItem> orderToRow(List<Order.LineItem> items) {
      return items.stream().map(LineItem::orderToRow).toList();
    }
  }

  public record OrderRow(
      String orderId,
      String customerId,
      List<LineItem> lineItems,
      double totalPrice,
      Moment readyToShipAt,
      Moment backOrderedAt,
      Moment cancelledAt) {

    static OrderRow empty() {
      return new OrderRow(null, null, List.of(), 0.0, Moment.empty(), Moment.empty(), Moment.empty());
    }

    static OrderRow eventToRow(OrderRow row, Order.Event event) {
      var order = rowToOrder(row);

      return switch (event) {
        case Order.Event.OrderCreated e -> orderToRow(order.onEvent(e));
        case Order.Event.OrderItemCreated e -> orderToRow(order.onEvent(e));
        case Order.Event.OrderReadyToShip e -> orderToRow(order.onEvent(e));
        case Order.Event.OrderBackOrdered e -> orderToRow(order.onEvent(e));
        case Order.Event.OrderCancelled e -> orderToRow(order.onEvent(e));
        case Order.Event.OrderItemCancelled e -> orderToRow(order.onEvent(e));
        case Order.Event.OrderItemReadyToShip e -> orderToRow(order.onEvent(e));
        case Order.Event.OrderItemBackOrdered e -> orderToRow(order.onEvent(e));
      };
    }

    static Order.State rowToOrder(OrderRow row) {
      return new Order.State(
          row.orderId(),
          row.customerId(),
          LineItem.rowToOrder(row.lineItems()),
          BigDecimal.valueOf(row.totalPrice()),
          row.readyToShipAt().optionalInstant(),
          row.backOrderedAt().optionalInstant(),
          row.cancelledAt().optionalInstant());
    }

    static OrderRow orderToRow(Order.State state) {
      return new OrderRow(state.orderId(),
          state.customerId(),
          LineItem.orderToRow(state.lineItems()),
          state.totalPrice().doubleValue(),
          Moment.of(state.readyToShipAt()),
          Moment.of(state.backOrderedAt()),
          Moment.of(state.cancelledAt()));
    }
  }
}
