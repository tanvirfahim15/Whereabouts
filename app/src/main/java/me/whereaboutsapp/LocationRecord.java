package me.whereaboutsapp;

public class LocationRecord {
    long time;
    double lat;
    double lng;

    public LocationRecord(long time, double lat, double lng) {
        this.time = time;
        this.lat = lat;
        this.lng = lng;
    }

    public long getTime() {
        return time;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public void setLng(double lng) {
        this.lng = lng;
    }

    @Override
    public String toString() {
        return "LocationRecord{" +
                "time=" + time +
                ", lat=" + lat +
                ", lng=" + lng +
                '}';
    }
}
