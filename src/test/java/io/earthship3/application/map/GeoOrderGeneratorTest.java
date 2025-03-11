package io.earthship3.application.map;

import static akka.Done.done;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.earthship3.domain.map.GeoOrderGenerator;
import io.earthship3.domain.map.LatLng;

public class GeoOrderGeneratorTest {
  @Test
  void testCreateGeoOrderGenerator() {
    var testKit = EventSourcedTestKit.of(GeoOrderGeneratorEntity::new);

    var generatorId = "123";
    var position = new LatLng(51.5074, -0.1278); // London UK
    var radiusKm = 10;
    var ratePerSecond = 1;
    var geoOrdersToGenerate = 10;

    var command = new GeoOrderGenerator.Command.CreateGeoOrderGenerator(generatorId, position, radiusKm, ratePerSecond, geoOrdersToGenerate);
    var result = testKit.call(entity -> entity.createGenerator(command));

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertEquals(3, result.getAllEvents().size());

    {
      var event = result.getNextEventOfType(GeoOrderGenerator.Event.GeoOrderGeneratorCreated.class);
      assertEquals(generatorId, event.generatorId());
      assertEquals(position, event.position());
      assertEquals(radiusKm, event.radiusKm());
      assertEquals(ratePerSecond, event.ratePerSecond());
      assertEquals(geoOrdersToGenerate, event.geoOrdersToGenerate());
    }

    {
      var event = result.getNextEventOfType(GeoOrderGenerator.Event.GeoOrdersToBeGenerated.class);
      assertEquals(generatorId, event.generatorId());
      assertEquals(position, event.position());
      assertEquals(radiusKm, event.radiusKm());
      assertEquals(1, event.geoOrdersToBeGenerated());
    }

    {
      var event = result.getNextEventOfType(GeoOrderGenerator.Event.GeneratorCycleCompleted.class);
      assertEquals(generatorId, event.generatorId());
    }

    {
      var state = testKit.getState();
      assertEquals(generatorId, state.generatorId());
      assertEquals(position, state.position());
      assertEquals(radiusKm, state.radiusKm());
      assertEquals(ratePerSecond, state.ratePerSecond());
      assertTrue(state.startTime().isAfter(Instant.EPOCH));
      assertEquals(geoOrdersToGenerate, state.geoOrdersToGenerate());
      assertEquals(1, state.geoOrdersGenerated());
    }
  }

  @Test
  void testGenerateGeoOrders() {
    var testKit = EventSourcedTestKit.of(GeoOrderGeneratorEntity::new);

    var generatorId = "123";
    var position = new LatLng(51.5074, -0.1278); // London UK
    var radiusKm = 10;
    var ratePerSecond = 1000;
    var geoOrdersToGenerate = 10;

    {
      var command = new GeoOrderGenerator.Command.CreateGeoOrderGenerator(generatorId, position, radiusKm, ratePerSecond, geoOrdersToGenerate);
      var result = testKit.call(entity -> entity.createGenerator(command));

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(3, result.getAllEvents().size());
    }

    {
      var command = new GeoOrderGenerator.Command.GenerateGeoOrders(generatorId);
      var result = testKit.call(entity -> entity.generateGeoOrders(command));

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(2, result.getAllEvents().size());

      {
        var event = result.getNextEventOfType(GeoOrderGenerator.Event.GeoOrdersToBeGenerated.class);
        assertEquals(generatorId, event.generatorId());
        assertNotNull(event.geoOrderId());
        assertTrue(event.geoOrdersToBeGenerated() >= 0);
      }

      {
        var event = result.getNextEventOfType(GeoOrderGenerator.Event.GeneratorCycleCompleted.class);
        assertEquals(generatorId, event.generatorId());
      }
    }
  }

  @Test
  void testGenerateGeoOrdersToLimit() {
    var testKit = EventSourcedTestKit.of(GeoOrderGeneratorEntity::new);

    var generatorId = "123";
    var position = new LatLng(51.5074, -0.1278); // London UK
    var radiusKm = 10;
    var ratePerSecond = 1_000_000;
    var geoOrdersToGenerate = 10;

    {
      var command = new GeoOrderGenerator.Command.CreateGeoOrderGenerator(generatorId, position, radiusKm, ratePerSecond, geoOrdersToGenerate);
      var result = testKit.call(entity -> entity.createGenerator(command));

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(3, result.getAllEvents().size());
    }

    { // this should generate 10 geo orders due to very high rate per second
      var command = new GeoOrderGenerator.Command.GenerateGeoOrders(generatorId);
      var result = testKit.call(entity -> entity.generateGeoOrders(command));

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(2, result.getAllEvents().size());
    }

    { // this should not generate any geo orders due to the limit
      var command = new GeoOrderGenerator.Command.GenerateGeoOrders(generatorId);
      var result = testKit.call(entity -> entity.generateGeoOrders(command));

      assertTrue(result.isReply());
      assertEquals(done(), result.getReply());
      assertEquals(0, result.getAllEvents().size());
    }

    {
      var state = testKit.getState();
      assertEquals(geoOrdersToGenerate, state.geoOrdersToGenerate());
      assertEquals(geoOrdersToGenerate, state.geoOrdersGenerated());
    }
  }
}
