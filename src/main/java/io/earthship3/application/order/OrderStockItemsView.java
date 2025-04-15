package io.earthship3.application.order;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;
import io.earthship3.domain.order.OrderItemsLeaf;

@ComponentId("order-stock-items-view")
public class OrderStockItemsView extends View {
  private final Logger log = LoggerFactory.getLogger(OrderStockItemsView.class);

  @Query("""
      SELECT * as orderStockItems
      FROM orderStockItems
      WHERE stockId = :stockId
      AND readyToShip = FALSE
      AND backOrdered = FALSE
      """)
  public QueryEffect<OrderStockItemsRows> findPending(String stockId) {
    log.info("{}", stockId);
    return queryResult();
  }

  @Query("""
      SELECT * as orderStockItems
      FROM orderStockItems
      WHERE stockId = :stockId
      AND readyToShip = TRUE
      AND backOrdered = FALSE
      """)
  public QueryEffect<OrderStockItemsRows> findReadyToShip(String stockId) {
    log.info("{}", stockId);
    return queryResult();
  }

  @Query("""
      SELECT * as orderStockItems
      FROM orderStockItems
      WHERE stockId = :stockId
      AND backOrdered = TRUE
      """)
  public QueryEffect<OrderStockItemsRows> findBackOrdered(String stockId) {
    log.info("{}", stockId);
    return queryResult();
  }

  @Consume.FromEventSourcedEntity(OrderItemsLeafEntity.class)
  public static class OrderStockItemsConsumer extends TableUpdater<OrderStockItemRow> {
    private final Logger log = LoggerFactory.getLogger(OrderStockItemsConsumer.class);

    @Override
    public OrderStockItemRow emptyRow() {
      return new OrderStockItemRow(null, null, false, false);
    }

    public Effect<OrderStockItemRow> onEvent(OrderItemsLeaf.Event event) {
      log.info("{}", event);
      return effects().updateRow(OrderStockItemRow.eventToRow(rowState(), event));
    }
  }

  public record OrderStockItemsRows(List<OrderStockItemRow> orderStockItems) {}

  public record OrderStockItemRow(
      String orderStockItemId,
      String stockId,
      boolean readyToShip,
      boolean backOrdered) {

    static OrderStockItemRow eventToRow(OrderStockItemRow row, OrderItemsLeaf.Event event) {

      return switch (event) {
        case OrderItemsLeaf.Event.OrderItemsCreated e -> new OrderStockItemRow(e.leafId(), e.stockId(), false, false);
        default -> row;
      };
    }
  }
}
