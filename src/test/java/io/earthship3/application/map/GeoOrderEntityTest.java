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
    var radiusKm = 10;
    var ordersToBeCreated = GeoOrder.State.maxOrdersPerCreateRequest / 2;
    var command = new GeoOrder.Command.CreateGeoOrders(orderId, generatorPosition, radiusKm, ordersToBeCreated);
    var result = testKit.call(entity -> entity.createGeoOrders(command));

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertEquals(ordersToBeCreated, result.getAllEvents().size());

    {
      var event = result.getNextEventOfType(GeoOrder.Event.GeoOrderToBeGenerated.class);
      assertEquals(orderId, event.orderId());
      assertTrue(event.order().lineItems().size() > 0);
      assertTrue(distanceInKm(generatorPosition, event.orderPosition()) <= radiusKm);
    }
  }

  @Test
  void testCreateGeoOrderLarge() {
    var testKit = EventSourcedTestKit.of(GeoOrderEntity::new);

    var orderId = "123";
    var generatorPosition = new LatLng(51.5074, -0.1278); // London UK
    var radiusKm = 10;
    var ordersToBeCreated = GeoOrder.State.maxOrdersPerCreateRequest * 5;
    var command = new GeoOrder.Command.CreateGeoOrders(orderId, generatorPosition, radiusKm, ordersToBeCreated);
    var result = testKit.call(entity -> entity.createGeoOrders(command));

    assertTrue(result.isReply());
    assertEquals(done(), result.getReply());
    assertEquals(GeoOrder.State.maxOrdersPerCreateRequest + 1, result.getAllEvents().size());

    {
      var event = result.getNextEventOfType(GeoOrder.Event.GeoOrdersCreated.class);
      assertEquals(orderId, event.orderId());
      assertEquals(generatorPosition, event.generatorPosition());
      assertEquals(radiusKm, event.radiusKm());
      assertEquals(ordersToBeCreated - GeoOrder.State.maxOrdersPerCreateRequest, event.geoOrdersToBeCreated());
    }

    {
      var event = result.getNextEventOfType(GeoOrder.Event.GeoOrderToBeGenerated.class);
      assertEquals(orderId, event.orderId());
      assertTrue(event.order().lineItems().size() > 0);
      assertTrue(distanceInKm(generatorPosition, event.orderPosition()) <= radiusKm);
    }
  }

  @Test
  void testCreateGeoOrder() {
    var testKit = EventSourcedTestKit.of(GeoOrderEntity::new);

    var orderId = "123";
    var generatorPosition = new LatLng(51.5074, -0.1278); // London UK
    var radiusKm = 10;
    var ordersToBeCreated = 1;
    var command1 = new GeoOrder.Command.CreateGeoOrders(orderId, generatorPosition, radiusKm, ordersToBeCreated);
    var result1 = testKit.call(entity -> entity.createGeoOrders(command1));

    assertTrue(result1.isReply());
    assertEquals(done(), result1.getReply());
    assertEquals(ordersToBeCreated, result1.getAllEvents().size());

    {
      var event1 = result1.getNextEventOfType(GeoOrder.Event.GeoOrderToBeGenerated.class);
      assertEquals(orderId, event1.orderId());
      assertTrue(event1.order().lineItems().size() > 0);
      assertTrue(distanceInKm(generatorPosition, event1.orderPosition()) <= radiusKm);

      var command2 = new GeoOrder.Command.CreateGeoOrder(event1.orderId(), event1.orderPosition(), event1.order());
      var result2 = testKit.call(entity -> entity.createGeoOrder(command2));

      assertTrue(result2.isReply());
      assertEquals(done(), result2.getReply());
      assertEquals(1, result2.getAllEvents().size());

      var event2 = result2.getNextEventOfType(GeoOrder.Event.GeoOrderCreated.class);
      assertEquals(orderId, event2.orderId());
      assertEquals(event1.orderPosition(), event2.orderPosition());
      assertEquals(event1.order(), event2.order());
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
