package com.example.android.sunshine.app.wearable;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

public class WatchFaceClient implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener{
    final static String LOG_TAG = "WatchFaceClient";
    final static String PATH_WEATHER_DATA = "/data/weather";

    final String[] UPDATE_WEARABLE_PROJECTION = new String[] {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP
    };

    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_MAX_TEMP = 1;
    private static final int INDEX_MIN_TEMP = 2;

    final static String KEY_MAX_TEMP = "max_temp";
    final static String KEY_MIN_TEMP = "min_temp";
    final static String KEY_WEATHER_ICON= "weather_icon";

    Context mContext;
    GoogleApiClient mGoogleApiClient;

    public WatchFaceClient(Context context) {
        mContext = context;
        connectClient();
    }

    public void sendData() {

        if(mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
            String location = Utility.getPreferredLocation(mContext);
            Uri uri = WeatherContract.WeatherEntry.buildWeatherLocationWithDate(location, System.currentTimeMillis());
            Cursor cursor = mContext.getContentResolver().query(uri, UPDATE_WEARABLE_PROJECTION, null, null, null);

            int weatherId;
            double maxTemperature;
            double minTemperature;

            if(cursor != null && cursor.moveToFirst()) {
                weatherId = cursor.getInt(INDEX_WEATHER_ID);
                maxTemperature = cursor.getDouble(INDEX_MAX_TEMP);
                minTemperature = cursor.getDouble(INDEX_MIN_TEMP);

                Bitmap icon = BitmapFactory.decodeResource(mContext.getResources(), Utility.getArtResourceForWeatherCondition(weatherId));
                Asset asset = Utility.createAssetFromBitmap(icon);

                PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(PATH_WEATHER_DATA).setUrgent();
                DataMap dataMap = putDataMapRequest.getDataMap();

                dataMap.putAsset(KEY_WEATHER_ICON, asset);
                dataMap.putString(KEY_MAX_TEMP, Utility.formatTemperature(mContext, maxTemperature));
                dataMap.putString(KEY_MIN_TEMP, Utility.formatTemperature(mContext, minTemperature));
                // Time is only used so the data always will be changed, data item is not sent if not updated.
                dataMap.putLong("time", System.currentTimeMillis());

                PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();
                PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi.putDataItem(mGoogleApiClient, putDataRequest);

                pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                    @Override
                    public void onResult(DataApi.DataItemResult dataItemResult) {
                        if (dataItemResult.getStatus().isSuccess()) {
                            Log.v(LOG_TAG, "Data Item Sent: " + dataItemResult.getDataItem().getUri());
                            Log.v(LOG_TAG, "Data Item count: " + dataItemResult.getDataItem().getAssets().size());
                            Log.v(LOG_TAG, "Data Item Sent: " + dataItemResult.getDataItem().toString());
                        }
                    }
                });
            }
        }
        else
            connectClient();
    }

    private void connectClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(mContext)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApiIfAvailable(Wearable.API)
                .build();

        mGoogleApiClient.connect();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(LOG_TAG,"onConnected");
        sendData();
    }

    @Override
    public void onConnectionSuspended(int i) {
        // Do nothing
        Log.d(LOG_TAG,"Connection suspended");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        // Do nothing
        Log.d(LOG_TAG,"Connection failed");
    }
}
