package io.earthship3.application.map;

import static akka.Done.done;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.TestKitSupport;
import io.earthship3.domain.map.GeoOrderGenerator;
import io.earthship3.domain.map.LatLng;

public class GeoOrderGeneratorIntegrationTest extends TestKitSupport {

  @Test
  public void testCreateGeoOrderGenerator() {
    var generatorId = "123";
    var position = new LatLng(51.5074, -0.1278); // London UK
    var radiusKm = 10;
    var ratePerSecond = 1000;
    var geoOrdersToGenerate = 10;

    {
      var command = new GeoOrderGenerator.Command.CreateGeoOrderGenerator(generatorId, position, radiusKm, ratePerSecond, geoOrdersToGenerate);
      var result = await(
          componentClient.forEventSourcedEntity(generatorId)
              .method(GeoOrderGeneratorEntity::createGenerator)
              .invokeAsync(command));

      assertEquals(done(), result);
    }

    {
      var topLeft = position.topLeft(radiusKm);
      var bottomRight = position.bottomRight(radiusKm);
      var nextPageToken = "";
      var area = new GeoOrderView.Area(topLeft.lat(), topLeft.lng(), bottomRight.lat(), bottomRight.lng(), nextPageToken);

      var result = queryGeoOrders(area);

      var geoOrdersCreated = Stream.iterate(result, r -> {
        sleep(1);
        return queryGeoOrders(area);
      }).anyMatch(r -> r.count() >= geoOrdersToGenerate);
      assertTrue(geoOrdersCreated);
      assertEquals(geoOrdersToGenerate, queryGeoOrders(area).count());
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
