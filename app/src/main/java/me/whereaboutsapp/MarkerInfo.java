package me.whereaboutsapp;

import android.graphics.Bitmap;


import com.mapbox.mapboxsdk.geometry.LatLng;

import java.net.URL;

public class MarkerInfo {
    LatLng latLng;
    URL url;
    Bitmap bmp;

    public MarkerInfo(LatLng latLng, URL url) {
        this.latLng = latLng;
        this.url = url;
    }

    @Override
    public String toString() {
        return "MarkerInfo{" +
                "latLng=" + latLng +
                ", url=" + url +
                ", bmp=" + bmp +
                '}';
    }

    public Bitmap getBmp() {
        return bmp;
    }

    public void setBmp(Bitmap bmp) {
        this.bmp = bmp;
    }

    public LatLng getLatLng() {
        return latLng;
    }

    public void setLatLng(LatLng latLng) {
        this.latLng = latLng;
    }

    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

}
