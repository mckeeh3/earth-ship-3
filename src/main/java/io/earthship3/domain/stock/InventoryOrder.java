package io.earthship3.domain.stock;

import java.util.Optional;

public interface InventoryOrder {
  public record State(
      String inventoryOrderId,
      String stockId,
      String stockName,
      int quantity) {

    public static State empty() {
      return new State(null, null, null, 0);
    }

    public boolean isEmpty() {
      return inventoryOrderId == null;
    }

    public Optional<Event> onCommand(Command.CreateInventoryOrder command) {
      if (!isEmpty()) {
        return Optional.empty();
      }

      return Optional.of(new Event.InventoryOrderCreated(
          command.inventoryOrderId,
          command.stockId,
          command.stockName,
          command.quantity));
    }

    public State onEvent(Event.InventoryOrderCreated event) {
      return new State(
          event.inventoryOrderId,
          event.stockId,
          event.stockName,
          event.quantity);
    }
  }

  public sealed interface Command {
    record CreateInventoryOrder(
        String inventoryOrderId,
        String stockId,
        String stockName,
        int quantity) implements Command {}
  }

  public sealed interface Event {
    record InventoryOrderCreated(
        String inventoryOrderId,
        String stockId,
        String stockName,
        int quantity) implements Event {}
  }
}
