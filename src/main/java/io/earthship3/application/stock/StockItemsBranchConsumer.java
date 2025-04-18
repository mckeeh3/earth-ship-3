package io.earthship3.application.stock;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import io.earthship3.domain.stock.StockItemsBranch;
import io.earthship3.domain.stock.StockItemsLeaf;
import io.earthship3.domain.stock.StockItemsLeaf.Quantity;

@ComponentId("stock-items-branch-consumer")
@Consume.FromEventSourcedEntity(StockItemsBranchEntity.class)
public class StockItemsBranchConsumer extends Consumer {
  private final Logger log = LoggerFactory.getLogger(StockItemsBranchConsumer.class);
  private final ComponentClient componentClient;

  public StockItemsBranchConsumer(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  public Effect onEvent(StockItemsBranch.Event event) {
    return switch (event) {
      case StockItemsBranch.Event.BranchToBeAdded e -> onEvent(e);
      case StockItemsBranch.Event.LeafToBeAdded e -> onEvent(e);
      case StockItemsBranch.Event.DelegateToSubBranch e -> onEvent(e);
      default -> effects().ignore();
    };
  }

  Effect onEvent(StockItemsBranch.Event.BranchToBeAdded event) {
    log.info("Event: {}", event);

    var command = new StockItemsBranch.Command.AddQuantityToTree(
        event.branchId(),
        event.stockId(),
        event.quantityId(),
        event.quantity(),
        Optional.of(event.parentBranchId()));

    componentClient.forEventSourcedEntity(event.branchId())
        .method(StockItemsBranchEntity::addQuantity)
        .invoke(command);

    return effects().done();
  }

  Effect onEvent(StockItemsBranch.Event.LeafToBeAdded event) {
    log.info("Event: {}", event);

    var command = new StockItemsLeaf.Command.CreateStockItems(
        event.leafId(),
        event.parentBranchId(),
        event.stockId(),
        event.quantityId(),
        Quantity.of(event.quantity().acquired(), event.quantity().available()));

    componentClient.forEventSourcedEntity(event.leafId())
        .method(StockItemsLeafEntity::createLeaf)
        .invoke(command);

    return effects().done();
  }

  Effect onEvent(StockItemsBranch.Event.DelegateToSubBranch event) {
    log.info("Event: {}", event);

    var command = new StockItemsBranch.Command.AddQuantityToTree(
        event.subBranchId(),
        event.stockId(),
        event.quantityId(),
        event.quantity(),
        Optional.of(event.branchId()));

    componentClient.forEventSourcedEntity(event.subBranchId())
        .method(StockItemsBranchEntity::addQuantity)
        .invoke(command);

    return effects().done();
  }
}
