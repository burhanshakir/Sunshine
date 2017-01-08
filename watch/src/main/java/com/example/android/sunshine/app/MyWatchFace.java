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

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;


import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class MyWatchFace extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    private static final String TAG = "MyWatchFace";

    public static double mHigh = 0.0d;
    public static double mLow = 0.0d;
    public static String mArtId;

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

    private class Engine extends CanvasWatchFaceService.Engine{


        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mAmbientBackgroundPaint;
        Paint mTextPaint;
        Paint mDatePaint;
        Paint mAmbientDatePaint;
        Paint mHighPaint;
        Paint mLowPaint;
        Paint mAmbientHighPaint;
        Paint mAmbientLowPaint;
        Paint mIconPaint;
        boolean mAmbient;


        Time mTime;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {

                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "updating time");
                }
                invalidate();
                if (shouldTimerBeRunning()) {
                    long timeMs = System.currentTimeMillis();
                    long delayMs = INTERACTIVE_UPDATE_RATE_MS
                            - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                    mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                }

            }
        };

        int mTapCount;

        float mXOffset;
        float mYOffset;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;
        boolean mBurnInProtection;


        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowUnreadCountIndicator(true)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            LocalBroadcastManager.getInstance(MyWatchFace.this)
                    .registerReceiver(new MyWatchFace.MessageReceiver(), new IntentFilter(Intent.ACTION_SEND));


            Resources resources = MyWatchFace.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            //interactive backround
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.background));

            //ambient background
            mAmbientBackgroundPaint = new Paint();
            mAmbientBackgroundPaint.setColor(resources.getColor(R.color.background));

            //watch
            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));
            //date
            mDatePaint = new Paint();
            mDatePaint = createTextPaint(resources.getColor(R.color.date_text));
            mDatePaint.setTextSize(25f);
            //date ambient
            mAmbientDatePaint = new Paint();
            mAmbientDatePaint = createTextPaint(resources.getColor(R.color.date_text_ambient));
            mAmbientDatePaint.setTextSize(25f);

            mHighPaint = new Paint();
            mHighPaint = createTextPaint(resources.getColor(R.color.high_text));
            mHighPaint.setTextSize(35f);

            mAmbientHighPaint = new Paint();
            mAmbientHighPaint = createTextPaint(resources.getColor(R.color.high_text_ambient));
            mAmbientHighPaint.setTextSize(35f);

            mLowPaint = new Paint();
            mLowPaint = createTextPaint(resources.getColor(R.color.low_text));
            mLowPaint.setTextSize(35f);

            mAmbientLowPaint=new Paint();
            mAmbientLowPaint=createTextPaint(resources.getColor(R.color.low_text_ambient));
            mAmbientLowPaint.setTextSize(35f);

            mIconPaint = new Paint();


            mTime = new Time();


        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);

            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {

                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {

                //invisible device, unregister broadcast receiver
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = MyWatchFace.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);
            mTextPaint.setTextSize(textSize);



        }

        /**
         * Detect if the device requires burn-in protection or is in low-bit ambient mode
         *
         * @param properties
         */
        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);

        }

        /**
         * once every minute, invalidate() to force the system to redraw the watch face
         */
        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                    mHighPaint.setAntiAlias(!inAmbientMode);
                    mLowPaint.setAntiAlias(!inAmbientMode);
                    mDatePaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = MyWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    mBackgroundPaint.setColor(resources.getColor(mTapCount % 2 == 0 ?
                            R.color.background : R.color.background2));
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            Resources resources = MyWatchFace.this.getResources();
            canvas.drawRect(0, 0, bounds.width(), bounds.height(),
                    isInAmbientMode() ? mAmbientBackgroundPaint : mBackgroundPaint);


            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            mTime.setToNow();
            String time = mAmbient ? String.format("%d:%02d", mTime.hour, mTime.minute)
                    : String.format("%d:%02d:%02d", mTime.hour, mTime.minute, mTime.second);


            canvas.drawText(time, mXOffset, mYOffset, mTextPaint);
            String date = formatDate(mTime.toMillis(false));
            canvas.drawText(date, mXOffset + resources.getDimension(R.dimen.extra_x_offset_date),
                    mYOffset + resources.getDimension(R.dimen.extra_y_offset_date),
                    isInAmbientMode() ? mAmbientDatePaint : mDatePaint);


            canvas.drawText(String.format(getString(R.string.format_temperature),mHigh),
                    mXOffset + resources.getDimension(R.dimen.extra_x_offset_high),
                    mYOffset + resources.getDimension(R.dimen.extra_y_offset_high), isInAmbientMode() ? mAmbientHighPaint:mHighPaint);


            canvas.drawText(String.format(getString(R.string.format_temperature),mLow),
                    mXOffset + resources.getDimension(R.dimen.extra_x_offset_low),
                    mYOffset + resources.getDimension(R.dimen.extra_y_offset_low), isInAmbientMode() ? mAmbientLowPaint: mLowPaint);


            if (mArtId!=null && !isInAmbientMode()) {

                try {
                    Bitmap artBitmap = null;
                    switch (mArtId) {
                        case "storm":
                            artBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.art_storm);
                            break;
                        case "light_rain":
                            artBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.art_light_rain);
                            break;
                        case "rain":
                            artBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.art_rain);
                            break;
                        case "snow":
                            artBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.art_snow);
                            break;
                        case "fog":
                            artBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.art_fog);
                            break;
                        case "clear":
                            artBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.art_clear);
                            break;
                        case "light_clouds":
                            artBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.art_light_clouds);
                            break;
                        case "clouds":
                            artBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.art_clouds);
                            break;
                    }
                    Log.d("Before creating bitmap", mArtId);
                    artBitmap = Bitmap.createScaledBitmap(artBitmap, 70, 55, true);
                    canvas.drawBitmap(artBitmap, mXOffset + resources.getDimension(R.dimen.extra_x_offset_icon),
                            mYOffset + resources.getDimension(R.dimen.extra_y_offset_icon), mIconPaint);
                    Log.d("Bitmap", "Should be created");
                } catch (NullPointerException npe) {
                    Log.e("Watchface ArtId", mArtId);
                }
            }

        }

        private String formatDate(long millis) {
            SimpleDateFormat formatter = new SimpleDateFormat("EEE,LLL dd yyyy");
            Date date = new Date(millis);
            return formatter.format(date);

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
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }


        private void updateTimer() {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "updateTimer");
            }
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }
    }

    public class MessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getBundleExtra("test");
            try {
                mHigh = Double.parseDouble((bundle.getString("high")));
                mLow = Double.parseDouble(bundle.getString("low"));
                mArtId = bundle.getString("art");
                Log.d("Received on Watchface", mHigh + "");

            }catch (Exception e){
                e.printStackTrace();
            }

        }
    }
}
