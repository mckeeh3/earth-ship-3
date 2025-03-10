package io.earthship3.application.map;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.View;
import akka.javasdk.view.TableUpdater;
import io.earthship3.domain.map.GeoOrder;
import io.earthship3.domain.map.LatLng;

@ComponentId("geo-order-view")
public class GeoOrderView extends View {
  private final Logger log = LoggerFactory.getLogger(GeoOrderView.class);

  @Query("""
      SELECT COUNT(*)
        FROM geoOrders
      WHERE position.lat <= :topLeftLat
        AND position.lng >= :topLeftLng
        AND position.lat >= :bottomRightLat
        AND position.lng <= :bottomRightLng
      """)
  public QueryEffect<Count> countByPosition(Area area) {
    log.info("{} {} {} {}", area.topLeftLat, area.topLeftLng, area.bottomRightLat, area.bottomRightLng);
    return queryResult();
  }

  @Query("""
      SELECT * as geoOrders, next_page_token() as nextPageToken, has_more() as hasMore
        FROM geoOrders
      WHERE position.lat <= :topLeftLat
        AND position.lng >= :topLeftLng
        AND position.lat >= :bottomRightLat
        AND position.lng <= :bottomRightLng
      OFFSET page_token_offset(:nextPageToken)
      LIMIT 1000
      """)
  public QueryEffect<GeoOrders> findByPosition(Area area) {
    log.info("{} {} {} {}", area.topLeftLat, area.topLeftLng, area.bottomRightLat, area.bottomRightLng);
    return queryResult();
  }

  @Consume.FromEventSourcedEntity(GeoOrderEntity.class)
  public static class GeoOrderConsumer extends TableUpdater<GeoOrderRow> {
    private final Logger log = LoggerFactory.getLogger(GeoOrderConsumer.class);

    @Override
    public GeoOrderRow emptyRow() {
      return new GeoOrderRow(null, null);
    }

    public Effect<GeoOrderRow> onEvent(GeoOrder.Event event) {
      return switch (event) {
        case GeoOrder.Event.GeoOrderCreated e -> effects().updateRow(onEvent(e));
        default -> effects().ignore();
      };
    }

    GeoOrderRow onEvent(GeoOrder.Event.GeoOrderCreated event) {
      log.info("Row: {}\n_Event: {}", rowState(), event);
      return new GeoOrderRow(event.order().orderId(), event.position());
    }
  }

  public record Area(double topLeftLat, double topLeftLng, double bottomRightLat, double bottomRightLng, String nextPageToken) {}

  public record Count(int count) {}

  public record GeoOrders(List<GeoOrderRow> geoOrders, String nextPageToken, boolean hasMore) {}

  public record GeoOrderRow(String orderId, LatLng position) {}
}