package io.earthship3.domain.map;

import static io.earthship3.ShortUUID.randomUUID;

import java.time.Instant;
import java.util.List;

public interface GeoOrderGenerator {
  public record State(
      String generatorId,
      LatLng position,
      double radiusKm,
      int ratePerSecond,
      Instant startTime,
      int geoOrdersToGenerate,
      int geoOrdersGenerated) {

    public static State empty() {
      return new State(null, null, 0, 0, Instant.EPOCH, 0, 0);
    }

    public boolean isEmpty() {
      return generatorId == null;
    }

    public List<Event> onCommand(Command.CreateGeoOrderGenerator command) {
      if (!isEmpty()) {
        return List.of();
      }

      var geoOrderGeneratorCreated = new Event.GeoOrderGeneratorCreated(
          command.generatorId(),
          command.position(),
          command.radiusKm(),
          Instant.now(),
          command.ratePerSecond(),
          command.geoOrderCountLimit(),
          0);

      var geoOrdersToBeGenerated = new Event.GeoOrdersToBeGenerated(
          command.generatorId(),
          command.position(),
          command.radiusKm(),
          randomUUID(),
          1,
          1);

      var generatorCycleCompleted = new Event.GeneratorCycleCompleted(command.generatorId());

      return List.of(geoOrderGeneratorCreated, geoOrdersToBeGenerated, generatorCycleCompleted);
    }

    public List<Event> onCommand(Command.GenerateGeoOrders command) {
      if (geoOrdersGenerated >= geoOrdersToGenerate) {
        return List.of();
      }

      var now = Instant.now();
      var elapsedMs = now.toEpochMilli() - startTime.toEpochMilli();
      var expectedGeoOrders = Math.max(geoOrdersGenerated, (int) ((elapsedMs / 1000.0) * ratePerSecond));
      var geoOrdersToBeGenerated = Math.min(expectedGeoOrders - geoOrdersGenerated, geoOrdersToGenerate - geoOrdersGenerated);
      var geoOrderId = randomUUID();

      var geoOrdersToBeGeneratedEvent = new Event.GeoOrdersToBeGenerated(
          command.generatorId(),
          position,
          radiusKm,
          geoOrderId,
          geoOrdersToBeGenerated,
          geoOrdersGenerated + geoOrdersToBeGenerated);

      var generatorCycleCompleted = new Event.GeneratorCycleCompleted(command.generatorId());

      return List.of(geoOrdersToBeGeneratedEvent, generatorCycleCompleted);
    }

    public State onEvent(Event.GeoOrderGeneratorCreated event) {
      return new State(
          event.generatorId(),
          event.position(),
          event.radiusKm(),
          event.ratePerSecond(),
          event.startTime(),
          event.geoOrdersToGenerate(),
          event.geoOrderCountCurrent());
    }

    public State onEvent(Event.GeoOrdersToBeGenerated event) {
      return new State(
          generatorId,
          position,
          radiusKm,
          ratePerSecond,
          startTime,
          geoOrdersToGenerate,
          event.geoOrdersGenerated());
    }
  }

  public sealed interface Command {
    record CreateGeoOrderGenerator(String generatorId, LatLng position, double radiusKm, int ratePerSecond, int geoOrderCountLimit) implements Command {}

    record GenerateGeoOrders(String generatorId) implements Command {}
  }

  public sealed interface Event {
    record GeoOrderGeneratorCreated(String generatorId, LatLng position, double radiusKm, Instant startTime, int ratePerSecond, int geoOrdersToGenerate, int geoOrderCountCurrent) implements Event {}

    record GeoOrdersToBeGenerated(String generatorId, LatLng position, double radiusKm, String geoOrderId, int geoOrdersToBeGenerated, int geoOrdersGenerated) implements Event {}

    record GeneratorCycleCompleted(String generatorId) implements Event {}
  }
}
