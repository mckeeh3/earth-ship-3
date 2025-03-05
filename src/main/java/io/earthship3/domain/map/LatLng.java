package io.earthship3.domain.map;

public record LatLng(double lat, double lng) {
  public static LatLng of(double lat, double lng) {
    return new LatLng(lat, lng);
  }
}
