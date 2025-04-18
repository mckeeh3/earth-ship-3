package io.earthship3.api;

import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import io.earthship3.application.stock.InventoryOrderEntity;
import io.earthship3.domain.stock.InventoryOrder;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/inventory")
public class InventoryEndpoint {
  private final Logger log = LoggerFactory.getLogger(InventoryEndpoint.class);
  private final ComponentClient componentClient;

  public InventoryEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/create")
  public CompletionStage<Done> createInventoryOrder(InventoryOrder.Command.CreateInventoryOrder command) {
    log.info("POST /create {}", command);

    return componentClient.forEventSourcedEntity(command.inventoryOrderId())
        .method(InventoryOrderEntity::create)
        .invokeAsync(command);
  }

  @Get("/{inventoryOrderId}")
  public CompletionStage<InventoryOrder.State> getInventoryOrder(String inventoryOrderId) {
    log.info("GET /{}", inventoryOrderId);

    return componentClient.forEventSourcedEntity(inventoryOrderId)
        .method(InventoryOrderEntity::get)
        .invokeAsync();
  }
}
