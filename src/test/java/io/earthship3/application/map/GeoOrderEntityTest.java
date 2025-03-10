package io.earthship3.application.map;

import static akka.Done.done;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.EventSourcedTestKit;
import io.earthship3.domain.map.GeoOrder;
import io.earthship3.domain.map.LatLng;

public class GeoOrderEntityTest {

  @Test
  void testCreateGeoOrderSmall() {
    var testKit = EventSourcedTestKit.of(GeoOrderEntity::new);

    var orderId = "123";
    var generatorPosition = new LatLng(51.5074, -0.1278); // London UK
    var generatorRadiusKm = 10;
    var ordersToBeCreated = 2;
    var command = new GeoOrder.Command.CreateGeoOrders(orderId, generatorPosition, generatorRadiusKm, ordersToBeCreated);
    var result = testKit.call(entity -> entity.createGeoOrders(command));

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertEquals(2, result.getAllEvents().size());

    {
      var event = result.getNextEventOfType(GeoOrder.Event.GeoOrderCreated.class);
      assertEquals(orderId, event.order().orderId());
      assertTrue(event.order().lineItems().size() > 0);
    }

    {
      var event = result.getNextEventOfType(GeoOrder.Event.GeoOrdersToBeCreated.class);
      assertEquals(orderId, event.orderId());
      assertEquals(generatorPosition, event.generatorPosition());
      assertEquals(generatorRadiusKm, event.generatorRadiusKm());
      assertEquals(ordersToBeCreated - 1, event.geoOrdersToBeCreated());
    }
  }

  @Test
  void testCreateGeoOrderMedium() {
    var testKit = EventSourcedTestKit.of(GeoOrderEntity::new);

    var orderId = "123";
    var generatorPosition = new LatLng(51.5074, -0.1278); // London UK
    var generatorRadiusKm = 10;
    var ordersToBeCreated = 5;
    var command = new GeoOrder.Command.CreateGeoOrders(orderId, generatorPosition, generatorRadiusKm, ordersToBeCreated);
    var result = testKit.call(entity -> entity.createGeoOrders(command));

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertEquals(3, result.getAllEvents().size());

    {
      var event = result.getNextEventOfType(GeoOrder.Event.GeoOrderCreated.class);
      assertEquals(orderId, event.order().orderId());
      assertTrue(event.order().lineItems().size() > 0);
    }

    {
      var event = result.getNextEventOfType(GeoOrder.Event.GeoOrdersToBeCreated.class);
      assertEquals(orderId, event.orderId());
      assertEquals(generatorPosition, event.generatorPosition());
      assertEquals(generatorRadiusKm, event.generatorRadiusKm());
      assertEquals((ordersToBeCreated - 1) / 2, event.geoOrdersToBeCreated());
    }

    {
      var event = result.getNextEventOfType(GeoOrder.Event.GeoOrdersToBeCreated.class);
      assertEquals(orderId, event.orderId());
      assertEquals(generatorPosition, event.generatorPosition());
      assertEquals(generatorRadiusKm, event.generatorRadiusKm());
      assertEquals((ordersToBeCreated - 1) / 2 + (ordersToBeCreated - 1) % 2, event.geoOrdersToBeCreated());
    }
  }

  @Test
  void testCreateGeoOrderLarge() {
    var testKit = EventSourcedTestKit.of(GeoOrderEntity::new);

    var orderId = "123";
    var generatorPosition = new LatLng(51.5074, -0.1278); // London UK
    var generatorRadiusKm = 10;
    var ordersToBeCreated = 127; // Prime number between 100-200
    var command = new GeoOrder.Command.CreateGeoOrders(orderId, generatorPosition, generatorRadiusKm, ordersToBeCreated);
    var result = testKit.call(entity -> entity.createGeoOrders(command));

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertEquals(3, result.getAllEvents().size());

    {
      var event = result.getNextEventOfType(GeoOrder.Event.GeoOrderCreated.class);
      assertEquals(orderId, event.order().orderId());
      assertTrue(event.order().lineItems().size() > 0);
    }

    {
      var event = result.getNextEventOfType(GeoOrder.Event.GeoOrdersToBeCreated.class);
      assertEquals(orderId, event.orderId());
      assertEquals(generatorPosition, event.generatorPosition());
      assertEquals(generatorRadiusKm, event.generatorRadiusKm());
      assertEquals((ordersToBeCreated - 1) / 2, event.geoOrdersToBeCreated());
    }

    {
      var event = result.getNextEventOfType(GeoOrder.Event.GeoOrdersToBeCreated.class);
      assertEquals(orderId, event.orderId());
      assertEquals(generatorPosition, event.generatorPosition());
      assertEquals(generatorRadiusKm, event.generatorRadiusKm());
      assertEquals((ordersToBeCreated - 1) / 2 + (ordersToBeCreated - 1) % 2, event.geoOrdersToBeCreated());
    }
  }

  @Test
  void testCreateGeoOrder() {
    var testKit = EventSourcedTestKit.of(GeoOrderEntity::new);

    var orderId = "123";
    var generatorPosition = new LatLng(51.5074, -0.1278); // London UK
    var generatorRadiusKm = 10;
    var ordersToBeCreated = 1;
    var command1 = new GeoOrder.Command.CreateGeoOrders(orderId, generatorPosition, generatorRadiusKm, ordersToBeCreated);
    var result1 = testKit.call(entity -> entity.createGeoOrders(command1));

    assertTrue(result1.isReply());
    assertEquals(done(), result1.getReply());
    assertEquals(ordersToBeCreated, result1.getAllEvents().size());

    {
      var event = result1.getNextEventOfType(GeoOrder.Event.GeoOrderCreated.class);
      assertEquals(orderId, event.order().orderId());
      assertTrue(event.order().lineItems().size() > 0);
      assertTrue(distanceInKm(generatorPosition, event.position()) <= generatorRadiusKm);
    }
  }

  private double distanceInKm(LatLng pos1, LatLng pos2) {
    final double earthRadiusKm = 6371.0;

    double lat1 = Math.toRadians(pos1.lat());
    double lon1 = Math.toRadians(pos1.lng());
    double lat2 = Math.toRadians(pos2.lat());
    double lon2 = Math.toRadians(pos2.lng());

    double dLat = lat2 - lat1;
    double dLon = lon2 - lon1;

    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(lat1) * Math.cos(lat2) *
            Math.sin(dLon / 2) * Math.sin(dLon / 2);

    double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

    return earthRadiusKm * c;
  }
}
