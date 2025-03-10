package io.earthship3.application.map;

import static akka.Done.done;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.TestKitSupport;
import io.earthship3.domain.map.GeoOrder;
import io.earthship3.domain.map.LatLng;

public class GeoOrderIntegrationTest extends TestKitSupport {

  @Test
  public void testCreateGeoOrder() {
    var orderId = "456";
    var orderPosition = new LatLng(51.5074, -0.1278); // London UK
    var geoOrdersToBeCreated = 10;
    var radiusKm = 10;

    {
      var command = new GeoOrder.Command.CreateGeoOrders(orderId, orderPosition, radiusKm, geoOrdersToBeCreated);
      var result = await(
          componentClient.forEventSourcedEntity(orderId)
              .method(GeoOrderEntity::createGeoOrders)
              .invokeAsync(command));

      assertEquals(done(), result);
    }

    {
      var topLeft = orderPosition.topLeft(radiusKm);
      var bottomRight = orderPosition.bottomRight(radiusKm);
      var nextPageToken = "";
      var area = new GeoOrderView.Area(topLeft.lat(), topLeft.lng(), bottomRight.lat(), bottomRight.lng(), nextPageToken);

      var result = queryGeoOrders(area);

      var geoOrdersCreated = Stream.iterate(result, r -> {
        sleep(1);
        return queryGeoOrders(area);
      }).anyMatch(r -> r.count() >= geoOrdersToBeCreated);
      assertTrue(geoOrdersCreated);
      assertEquals(geoOrdersToBeCreated, queryGeoOrders(area).count());
    }
  }

  private GeoOrderView.Count queryGeoOrders(GeoOrderView.Area area) {
    return await(
        componentClient.forView()
            .method(GeoOrderView::countByPosition)
            .invokeAsync(area));
  }

  private void sleep(int seconds) {
    try {
      TimeUnit.SECONDS.sleep(seconds);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}