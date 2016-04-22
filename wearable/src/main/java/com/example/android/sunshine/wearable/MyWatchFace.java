/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.wearable;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyWatchFace.Engine> mWeakReference;

        public EngineHandler(MyWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyWatchFace.Engine engine = mWeakReference.get();

            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements
            GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener,
            DataApi.DataListener {

        final String LOG_TAG = Engine.class.getSimpleName();
        final String KEY_HIGH_TEMP = "high_temp";
        final String KEY_LOW_TEMP = "low_temp";
        final String KEY_WEATHER_ICON = "weather_icon";

        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        int mBackgroundColor;
        Paint mTimeTextPaint;
        Paint mDateTextPaint;
        Paint mWeatherTextPaint;
        boolean mAmbient;
        boolean mBurnInProtection;
        boolean mLowBitAmbient;
        Calendar mCalendar;

        float mYOffsetTime;
        float mYOffsetDate;
        float mYOffsetLine;
        float mYOffsetWeather;

        final SimpleDateFormat mSdfTime = new SimpleDateFormat("HH:mm");
        final SimpleDateFormat mSdfDate = new SimpleDateFormat("EEE, MMM dd yyyy");

        String mTemperature;
        int mWeatherIcon;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                //todo: need to handle intent?
                //mCalendar.clear(intent.getStringExtra("time-zone"));
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        GoogleApiClient mGoogleApiClient;
        boolean mHasDataApiListener;

        // Initialize once to save battery and improve performance.
        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setStatusBarGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP)
                    .setHotwordIndicatorGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP)
                    .build());

            Resources resources = MyWatchFace.this.getResources();
            mYOffsetTime = resources.getDimension(R.dimen.offset_time_text);
            mYOffsetDate = resources.getDimension(R.dimen.offset_date_text);
            mYOffsetLine = resources.getDimension(R.dimen.offset_line);
            mYOffsetWeather = resources.getDimension(R.dimen.offset_weather_text);

            mCalendar = Calendar.getInstance();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mBackgroundColor = getResources().getColor(R.color.primary, null);
                mTimeTextPaint = createTextPaint(resources.getColor(R.color.white, null), resources.getDimension(R.dimen.text_size_time));
                mDateTextPaint = createTextPaint(resources.getColor(R.color.primary_light, null), resources.getDimension(R.dimen.text_size_date));
                mWeatherTextPaint = createTextPaint(resources.getColor(R.color.primary_light, null), resources.getDimension(R.dimen.text_size_weather));
            }
            else {
                mBackgroundColor = getResources().getColor(R.color.primary);
                mTimeTextPaint = createTextPaint(resources.getColor(R.color.white), resources.getDimension(R.dimen.text_size_time));
                mDateTextPaint = createTextPaint(resources.getColor(R.color.primary_light), resources.getDimension(R.dimen.text_size_date));
                mWeatherTextPaint = createTextPaint(resources.getColor(R.color.primary_light), resources.getDimension(R.dimen.text_size_weather));
            }

            mGoogleApiClient = new GoogleApiClient.Builder(MyWatchFace.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();

            mGoogleApiClient.connect();
        }

        private Paint createTextPaint(int textColor, float textSize) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTextSize(textSize);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerTimeZoneReceiver();
                connectDataListener();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
            }
            else {
                unregisterTimeZoneReceiver();
                disconnectDataListener();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerTimeZoneReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterTimeZoneReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        private void connectDataListener() {
            if(!mGoogleApiClient.isConnected() || !mGoogleApiClient.isConnecting()) {
                mGoogleApiClient.connect();
            }
        }

        private void disconnectDataListener() {
            if(mGoogleApiClient.isConnected()) {
                mGoogleApiClient.disconnect();
            }
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();

            invalidate();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);

            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);

            //todo: disable anti-aliasing and bitmap filtering when the device switches to ambient mode.
            //todo: see http://developer.android.com/design/wear/watchfaces.html#SpecialScreens
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);

            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;

                if (mLowBitAmbient) {
                    mTimeTextPaint.setAntiAlias(!inAmbientMode);
                    mDateTextPaint.setAntiAlias(!inAmbientMode);
                }

                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            //todo: Setup offsets
            // Load resources that have alternate values for round watches.
//            Resources resources = MyWatchFace.this.getResources();
//            boolean isRound = insets.isRound();
//            mXOffset = resources.getDimension(isRound ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            }
            else {
                canvas.drawColor(mBackgroundColor);
            }

            mCalendar.setTimeInMillis(System.currentTimeMillis());
            String time = mSdfTime.format((mCalendar.getTime()));
            String date = mSdfDate.format((mCalendar.getTime())).toUpperCase();

            canvas.drawText(time, bounds.centerX() - (mTimeTextPaint.measureText(time)) / 2, mYOffsetTime, mTimeTextPaint);
            canvas.drawText(date, bounds.centerX() - (mDateTextPaint.measureText(date)) / 2, mYOffsetDate, mDateTextPaint);
            canvas.drawLine(bounds.centerX() - 30, mYOffsetLine, bounds.centerX() + 30, mYOffsetLine, mDateTextPaint);

            //todo: add weather data. image | temp
            if(!mTemperature.isEmpty())
                canvas.drawText(mTemperature, bounds.centerX() - (mWeatherTextPaint.measureText(time)) / 2, mYOffsetTime, mWeatherTextPaint);

        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();

            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS - (timeMs % INTERACTIVE_UPDATE_RATE_MS);

                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }

        /*GoogleApiClient*/
        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.d(LOG_TAG, "onConnected: " + bundle);

            mHasDataApiListener = true;
            Wearable.DataApi.addListener(mGoogleApiClient, this);
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d(LOG_TAG, "onConnectionSuspended: " + i);

            if(mHasDataApiListener)
                Wearable.DataApi.removeListener(mGoogleApiClient, this);
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.d(LOG_TAG, "onConnectionFailed: " + connectionResult);
            // Do nothing
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEventBuffer) {
            Log.d(LOG_TAG, "onDataChanged: " + dataEventBuffer.getCount());

            for (DataEvent dataEvent : dataEventBuffer) {
                if(dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                    DataItem dataitem = dataEvent.getDataItem();

                    if(dataitem.getUri().getPath().compareTo("") == 0) {
                        try {
                            DataMap dataMap = DataMapItem.fromDataItem(dataitem).getDataMap();
                            updateWeather(dataMap);
                        }
                        catch (IllegalStateException e) {
                            // Do nothing
                            Log.d(LOG_TAG, e.getMessage());
                        }
                    }
                }
            }
        }

        private void updateWeather(DataMap dataMap) {
            String high = dataMap.containsKey(KEY_HIGH_TEMP) ? Integer.toString(dataMap.getInt(KEY_HIGH_TEMP)) : "";
            String low = dataMap.containsKey(KEY_LOW_TEMP) ? Integer.toString(dataMap.getInt(KEY_LOW_TEMP)) : "";

            mTemperature = high + "° " + low + "°";
            //todo: get icon
        }
    }
}

































