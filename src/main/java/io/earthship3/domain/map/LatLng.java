package io.earthship3.domain.map;

public record LatLng(double lat, double lng) {
  // The Earth's radius at the equator is approximately 6371 km
  // One degree of latitude covers approximately 111.32 km (6371 * 2π / 360)
  public static final double kmPerDegreeLat = 111.32;

  public static LatLng of(double lat, double lng) {
    return new LatLng(lat, lng);
  }

  public LatLng topLeft(double radiusKm) {
    final double kmPerDegreeLat = 111.32;

    // Add radiusKm to latitude (moving north) and subtract from longitude (moving west)
    var latOffset = radiusKm / kmPerDegreeLat;

    // Longitude distance varies with latitude due to the Earth's spherical shape
    // Need to adjust by cosine of latitude to account for convergence at poles
    var lngOffset = radiusKm / (kmPerDegreeLat * Math.cos(Math.toRadians(lat)));

    return new LatLng(lat + latOffset, lng - lngOffset);
  }

  public LatLng bottomRight(double radiusKm) {
    // Add radiusKm to latitude (moving south) and subtract from longitude (moving east)
    var latOffset = -radiusKm / kmPerDegreeLat;

    // Longitude distance varies with latitude due to the Earth's spherical shape
    // Need to adjust by cosine of latitude to account for convergence at poles
    var lngOffset = radiusKm / (kmPerDegreeLat * Math.cos(Math.toRadians(lat)));

    return new LatLng(lat + latOffset, lng + lngOffset);
  }
}
