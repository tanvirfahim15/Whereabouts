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
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.analytics.FirebaseAnalytics;

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
import static me.whereaboutsapp.StaticGlobal.MAPS_API_KEY;

public class MainActivity extends AppCompatActivity  implements OnMapReadyCallback {
    DBHelper db;
    SharedPreferences sharedPref;
    private FirebaseAnalytics mFirebaseAnalytics;
    private GoogleMap mMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowCustomEnabled(true);

        LayoutInflater inflator = (LayoutInflater) this .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = inflator.inflate(R.layout.abs_layout, null);

        actionBar.setCustomView(v);

        sharedPref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        Button show = (Button)findViewById(R.id.create_whereabouts);
        show.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
                    ActivityCompat.requestPermissions(MainActivity.this,
                            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                            223);
                    return;
                }
                buildUrl();

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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode==223) {

            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                buildUrl();

            } else {
                Toast.makeText(getApplicationContext(), "You must give storage access", Toast.LENGTH_LONG).show();
            }
        }
    }

    public Bitmap screenShot(View view) {
        view.setDrawingCacheEnabled(true);
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(),
                view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);
        return bitmap;
    }
    private void buildUrl() {
        MarkerLinks markerLinks = new MarkerLinks();
        ArrayList<LocationRecord> locationRecords = db.locationQuery(System.currentTimeMillis()-24*60*60*1000);
        String url = "http://maps.googleapis.com/maps/api/staticmap?&size=440x640";
        for(int i = 0;i<locationRecords.size();i++){
            Date date = new Date();
            date.setTime(locationRecords.get(i).time);
            String time = String.valueOf(new SimpleDateFormat("hh:mma").format(date));
            url += "&markers=icon:" + markerLinks.get_link(time+".png")+"%7C"+locationRecords.get(i).lat+","+locationRecords.get(i).lng;

        }
        url+= "&key="+MAPS_API_KEY;
        Log.d(DEBUG,url);
        try {
            new DownloadTask()
                    .execute(new URL(url));
        } catch (MalformedURLException e) {
            e.printStackTrace();
            Crashlytics.logException(e);
        }

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        db = new DBHelper(this);
        final LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        ArrayList<LocationRecord> locationRecords = db.locationQuery(System.currentTimeMillis()-24*60*60*1000);
        for (int i= 0;i<locationRecords.size();i++){
            LocationRecord locationRecord = locationRecords.get(i);
            LatLng latLng = new LatLng(locationRecord.lat, locationRecord.lng);
            Date date = new Date();
            date.setTime(locationRecord.time);
            String time = String.valueOf(new SimpleDateFormat("hh:mma").format(date));
            Marker marker = mMap.addMarker(new MarkerOptions().position(latLng).title(time));
            marker.showInfoWindow();
            bounds.include(latLng);
        }

        if(locationRecords.size()==1){
            LocationRecord locationRecord = locationRecords.get(0);
            LatLng marker = new LatLng(locationRecord.lat, locationRecord.lng);
            CameraUpdate center=
                    CameraUpdateFactory.newLatLng(marker);
            CameraUpdate zoom=CameraUpdateFactory.zoomTo((float) 15);
            mMap.moveCamera(center);
            mMap.animateCamera(zoom);
            return;
        }
        if(locationRecords.size()>0){
            mMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
                @Override
                public void onMapLoaded() {
                    mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 50));
                }
            });
        }
    }




    private class DownloadTask extends AsyncTask<URL,Void,Bitmap> {
        protected void onPreExecute(){

        }
        protected Bitmap doInBackground(URL...urls){
            URL url = urls[0];
            HttpURLConnection connection = null;
            try{
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();
                InputStream inputStream = connection.getInputStream();
                BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
                Bitmap bmp = BitmapFactory.decodeStream(bufferedInputStream);
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
                bundle.putString(FirebaseAnalytics.Param.CONTENT_TYPE, "link");
                bundle.putString(FirebaseAnalytics.Param.CONTENT, url.toString());
                mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT, bundle);

                return bmp;
            }catch(IOException e){
                e.printStackTrace();
                Crashlytics.logException(e);
            }finally{
                connection.disconnect();
            }
            return null;
        }
        public Bitmap combineImages(Bitmap c, Bitmap s) { // can add a 3rd parameter 'String loc' if you want to save the new image - left some code to do that at the bottom
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
            Toast.makeText(getApplicationContext(),"Whereabouts saved in Gallery",Toast.LENGTH_SHORT).show();
        }
    }
}
