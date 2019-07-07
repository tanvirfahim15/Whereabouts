package me.whereaboutsapp;

import android.Manifest;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import com.crashlytics.android.Crashlytics;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.Icon;
import com.mapbox.mapboxsdk.annotations.IconFactory;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.snapshotter.MapSnapshot;
import com.mapbox.mapboxsdk.snapshotter.MapSnapshotter;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import static me.whereaboutsapp.StaticGlobal.DEBUG;
import static me.whereaboutsapp.StaticGlobal.DELAY;

public class MainActivity extends AppCompatActivity {
    DBHelper db;
    SharedPreferences sharedPref;
    private FirebaseAnalytics mFirebaseAnalytics;

    private MapView mapView;
    MapboxMap mapboxMapg;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Mapbox.getInstance(getApplicationContext(), "pk.eyJ1IjoidGFudmlyZmFoaW0iLCJhIjoiY2p4c3V1d214MG50azNubnR3anB0YXZ3dyJ9.Mg5SaC8TrDQ6DbfS_K1tgw");

        setContentView(R.layout.activity_main);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowCustomEnabled(true);

        LayoutInflater inflator = (LayoutInflater) this .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflator.inflate(R.layout.abs_layout, null);

        actionBar.setCustomView(v);

        sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);


        mapView = (MapView) findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(new com.mapbox.mapboxsdk.maps.OnMapReadyCallback() {
            @Override
            public void onMapReady(@NonNull final MapboxMap mapboxMap) {
                mapboxMap.setStyle(Style.MAPBOX_STREETS, new Style.OnStyleLoaded() {
                    @Override
                    public void onStyleLoaded(@NonNull Style style) {
                        mapboxMapg = mapboxMap;
                        setupMap(true);
                    }
                });
            }
        });




        Button createWhereabouts = (Button)findViewById(R.id.create_whereabouts);
        createWhereabouts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            223);
                    return;
                }
                createWhereAbouts();

            }
        });
        Button refresh = (Button)findViewById(R.id.refresh);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setupMap(true);
            }
        });



        int service = sharedPref.getInt("service", 0);
        if(service == 0){
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putInt("service", 1);
            editor.apply();
            AlarmManager alarmMgr = (AlarmManager)getApplicationContext().getSystemService(Context.ALARM_SERVICE);
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            Intent intent = new Intent(getApplicationContext(), LocationService.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(getApplicationContext(), 100, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            if (Build.VERSION.SDK_INT >= 23) {
                alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                        10, pendingIntent);
            } else {
                alarmMgr.setRepeating(AlarmManager.RTC_WAKEUP,
                        SystemClock.elapsedRealtime(),
                        DELAY, pendingIntent);
            }
        }

    }

    private void createWhereAbouts() {
        mapView.setDrawingCacheEnabled(true);
        MapboxMap.SnapshotReadyCallback cb = new MapboxMap.SnapshotReadyCallback() {
            @Override
            public void onSnapshotReady(Bitmap snapshot) {
                new CreateWhereAboutsTask().execute(snapshot);

            }
        };
        mapboxMapg.snapshot(cb);

    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode==223) {

            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                createWhereAbouts();

            } else {
                Toast.makeText(getApplicationContext(), "You must give storage access", Toast.LENGTH_LONG).show();
            }
        }
    }



    private void setupMap(boolean first) {
        MarkerLinks markerLinks = new MarkerLinks();
        db = new DBHelper(this);

        final LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        ArrayList<LocationRecord> locationRecords = db.locationQuery(System.currentTimeMillis()-24*60*60*1000);
        for (int i= 0;i<locationRecords.size();i++){
            LocationRecord locationRecord = locationRecords.get(i);
            LatLng latLng = new LatLng(locationRecord.lat, locationRecord.lng);
            Date date = new Date();
            date.setTime(locationRecord.time);
            String time = String.valueOf(new SimpleDateFormat("hh:mma").format(date));
            try {
                new LoadMarkerTask().execute(new MarkerInfo(latLng,new URL(markerLinks.get_link(time+".png"))));
            } catch (MalformedURLException e) {
                e.printStackTrace();
                Crashlytics.logException(e);
            }
            bounds.include(latLng);
        }
        if(locationRecords.size()==1){
            LocationRecord locationRecord = locationRecords.get(0);
            LatLng marker = new LatLng(locationRecord.lat, locationRecord.lng);
            CameraPosition position = new CameraPosition.Builder()
                    .target(marker)
                    .zoom(15)
                    .build();
            mapboxMapg.animateCamera(CameraUpdateFactory.newCameraPosition(position), 500);
            return;
        }
        if(locationRecords.size()>0){
            LatLngBounds latLngBounds = bounds.build();
            mapboxMapg.animateCamera(CameraUpdateFactory.newLatLngBounds(latLngBounds, 100));
        }

    }
    private class LoadMarkerTask extends AsyncTask<MarkerInfo,Void,MarkerInfo> {


        protected void onPreExecute(){

        }
        protected MarkerInfo doInBackground(MarkerInfo... markerInfos){
            MarkerInfo markerInfo = markerInfos[0];
            URL url = markerInfo.url;
            HttpURLConnection connection = null;
            try{
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();
                InputStream inputStream = connection.getInputStream();
                BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
                Bitmap bmp = BitmapFactory.decodeStream(bufferedInputStream);
                markerInfo.bmp= bmp;
                return markerInfo;
            }catch(IOException e){
                e.printStackTrace();
                Crashlytics.logException(e);
            }finally{
                connection.disconnect();
            }
            return null;
        }


        protected void onPostExecute(MarkerInfo result){
            IconFactory iconFactory = IconFactory.getInstance(MainActivity.this);
            Icon icon = iconFactory.fromBitmap(result.bmp);

            mapboxMapg.addMarker(new MarkerOptions().icon(icon).position(result.latLng));
            //mapboxMapg.addMarker(new MarkerOptions().position(new com.mapbox.mapboxsdk.geometry.LatLng(result.latLng.latitude,result.latLng.longitude)));
                    //.icon(icon));
        }
    }

    private class CreateWhereAboutsTask extends AsyncTask<Bitmap,Void,Bitmap> {


        protected void onPreExecute(){

        }
        protected Bitmap doInBackground(Bitmap... bitmaps) {

            try{
                Bitmap bmp = bitmaps[0];
                Bitmap header = BitmapFactory.decodeResource(getApplicationContext().getResources(),
                        R.drawable.whereabouts);
                Bitmap footer = BitmapFactory.decodeResource(getApplicationContext().getResources(),
                        R.drawable.footer);
                Bitmap resized = Bitmap.createScaledBitmap(bmp, header.getWidth(), header.getWidth()*640/440, true);
                bmp=combineImages(header,resized);
                bmp=combineImages(bmp,footer);
                MediaStore.Images.Media.insertImage(getApplicationContext().getContentResolver(), bmp , String.valueOf(System.currentTimeMillis()), String.valueOf(System.currentTimeMillis()));

                Bundle bundle = new Bundle();
                bundle.putString(FirebaseAnalytics.Param.ITEM_ID, "100");
                bundle.putString(FirebaseAnalytics.Param.ITEM_NAME, "Whereabouts Generated");
                mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);
                return bmp;
            }catch(Exception e){
                e.printStackTrace();
                Crashlytics.logException(e);
            }finally{
            }
            return null;
        }
        public Bitmap combineImages(Bitmap c, Bitmap s) {
            Bitmap cs = null;
            int width, height = 0;
            width = c.getWidth();
            height = c.getHeight()+s.getHeight();
            cs = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas comboImage = new Canvas(cs);
            comboImage.drawBitmap(c, 0f, 0f, null);
            comboImage.drawBitmap(s, 0, c.getHeight(), null);
            return cs;
        }

        protected void onPostExecute(Bitmap result){
            Toast.makeText(getApplicationContext(),"Whereabouts Saved in Gallery",Toast.LENGTH_SHORT).show();
        }
    }
}

