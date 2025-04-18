package io.earthship3.application.stock;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import io.earthship3.domain.stock.InventoryOrder;
import io.earthship3.domain.stock.StockItemsBranch;
import io.earthship3.domain.stock.StockItemsBranch.Quantity;

@ComponentId("inventory-order-to-stock-items-branch-consumer")
@Consume.FromEventSourcedEntity(InventoryOrderEntity.class)
public class InventoryOrderToStockItemsBranchConsumer extends Consumer {
  private final Logger log = LoggerFactory.getLogger(InventoryOrderToStockItemsBranchConsumer.class);
  private final ComponentClient componentClient;

  public InventoryOrderToStockItemsBranchConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onEvent(InventoryOrder.Event event) {
    return switch (event) {
      case InventoryOrder.Event.InventoryOrderCreated e -> onEvent(e);
      default -> effects().ignore();
    };
  }

  private Effect onEvent(InventoryOrder.Event.InventoryOrderCreated event) {
    log.info("Event: {}", event);

    var parentStockItemId = Optional.<String>empty();
    var command = new StockItemsBranch.Command.AddQuantityToTree(
        event.stockId(), // this is the tree trunk branch ID
        event.stockId(),
        event.inventoryOrderId(), // this is the branch quantity ID
        Quantity.of(event.quantity()),
        parentStockItemId);

    componentClient.forEventSourcedEntity(event.stockId())
        .method(StockItemsBranchEntity::addQuantity)
        .invoke(command);

    return effects().done();
  }
}
