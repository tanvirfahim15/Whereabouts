package me.whereaboutsapp;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

import static me.whereaboutsapp.StaticGlobal.DATABASE_NAME;

public class DBHelper extends SQLiteOpenHelper {


    public DBHelper(Context context) {
        super(context, DATABASE_NAME , null, 1);
    }
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS LOCATION(Time INTEGER,Lat REAL,Lng REAL);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    public void insert(LocationRecord locationRecord){
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("INSERT INTO LOCATION VALUES("+locationRecord.getTime()+","+locationRecord.getLat()+","+locationRecord.getLng()+");");
    }

    public ArrayList<LocationRecord> locationQuery(long time){
        ArrayList<LocationRecord> results = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor resultSet = db.rawQuery("Select * from LOCATION Where Time > "+time,null);
        resultSet.moveToFirst();

        while(resultSet.isAfterLast() == false){
            results.add(new LocationRecord(resultSet.getLong(0),resultSet.getFloat(1),resultSet.getFloat(2)));
            resultSet.moveToNext();
        }
        return results;
    }
}
