package io.earthship3.api;

import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.Done;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Put;
import akka.javasdk.client.ComponentClient;
import io.earthship3.application.order.ShoppingCartEntity;
import io.earthship3.domain.order.ShoppingCart;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/shopping-cart")
public class ShoppingCartEndpoint {
  private final Logger log = LoggerFactory.getLogger(ShoppingCartEndpoint.class);
  private final ComponentClient componentClient;

  public ShoppingCartEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Put("/add-line-item")
  public CompletionStage<Done> addLineItem(ShoppingCart.Command.AddLineItem command) {
    log.info("PUT {}", command);

    return componentClient.forEventSourcedEntity(command.customerId())
        .method(ShoppingCartEntity::addLineItem)
        .invokeAsync(command);
  }

  @Put("/update-line-item")
  public CompletionStage<Done> updateLineItem(ShoppingCart.Command.UpdateLineItem command) {
    log.info("PUT {}", command);

    return componentClient.forEventSourcedEntity(command.customerId())
        .method(ShoppingCartEntity::updateLineItem)
        .invokeAsync(command);
  }

  @Put("/remove-line-item")
  public CompletionStage<Done> removeLineItem(ShoppingCart.Command.RemoveLineItem command) {
    log.info("PUT {}", command);

    return componentClient.forEventSourcedEntity(command.customerId())
        .method(ShoppingCartEntity::removeLineItem)
        .invokeAsync(command);
  }

  @Put("/checkout")
  public CompletionStage<Done> checkout(ShoppingCart.Command.Checkout command) {
    log.info("PUT {}", command);

    return componentClient.forEventSourcedEntity(command.customerId())
        .method(ShoppingCartEntity::checkout)
        .invokeAsync(command);
  }

  @Get("/{customerId}")
  public CompletionStage<ShoppingCart.State> shoppingCart(String customerId) {
    log.info("GET {}", customerId);

    return componentClient.forEventSourcedEntity(customerId)
        .method(ShoppingCartEntity::get)
        .invokeAsync();
  }
}
