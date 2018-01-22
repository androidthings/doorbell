/*
 * Copyright 2016, The Android Open Source Project
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
package com.example.androidthings.doorbell;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Base64;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.button.ButtonInputDriver;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.lang.Thread;

/**
 * Doorbell activity that capture a picture from the Raspberry Pi 3
 * Camera on a button press and post it to Firebase and Google Cloud
 * Vision API.
 */
public class DoorbellActivity extends Activity {
    private static final String TAG = DoorbellActivity.class.getSimpleName();

    private FirebaseDatabase mDatabase;
    private DoorbellCamera mCamera;

    private ImageView mImage;
    private TextView[] mResultViews;
    private Map<String, Float> mAnnotations;

    private enum ActivityState { STARTING, IDLING, IMAGING, LABELLING, DISPLAYING };
    private ActivityState mState = ActivityState.STARTING;

    /*
     * Driver for the doorbell button;
     */
    private ButtonInputDriver mButtonInputDriver;

    /**
     * A {@link Handler} for running Camera tasks in the background.
     */
    private Handler mCameraHandler;

    /**
     * An additional thread for running Camera tasks that shouldn't block the UI.
     */
    private HandlerThread mCameraThread;

    /**
     * A {@link Handler} for running Cloud tasks in the background.
     */
    private Handler mCloudHandler;

    /**
     * An additional thread for running Cloud tasks that shouldn't block the UI.
     */
    private HandlerThread mCloudThread;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Doorbell Activity created.");

        // We need permission to access the camera
        if (checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // A problem occurred auto-granting the permission
            Log.d(TAG, "No permission");
            return;
        }

        setContentView(R.layout.activity_camera);
        mImage = (ImageView) findViewById(R.id.imageView);
        mResultViews = new TextView[3];
        mResultViews[0] = (TextView) findViewById(R.id.result1);
        mResultViews[1] = (TextView) findViewById(R.id.result2);
        mResultViews[2] = (TextView) findViewById(R.id.result3);

        mDatabase = FirebaseDatabase.getInstance();

        // Creates new handlers and associated threads for camera and networking operations.
        mCameraThread = new HandlerThread("CameraBackground");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());

        mCloudThread = new HandlerThread("CloudThread");
        mCloudThread.start();
        mCloudHandler = new Handler(mCloudThread.getLooper());

        // Initialize the doorbell button driver
        initPIO();

        // Camera code is complicated, so we've shoved it all in this closet class for you.
        mCamera = DoorbellCamera.getInstance();
        mCamera.initializeCamera(this, mCameraHandler, mOnImageAvailableListener);

        mState = ActivityState.IDLING;
        resetDisplayMsg("Welcome! Press the button to check-in for this event.");
    }

    private void initPIO() {
        try {
            mButtonInputDriver = new ButtonInputDriver(
                    BoardDefaults.getGPIOForButton(),
                    Button.LogicState.PRESSED_WHEN_LOW,
                    KeyEvent.KEYCODE_ENTER);
            mButtonInputDriver.register();
        } catch (IOException e) {
            mButtonInputDriver = null;
            Log.w(TAG, "Could not open GPIO pins", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mCamera.shutDown();

        mCameraThread.quitSafely();
        mCloudThread.quitSafely();
        try {
            mButtonInputDriver.close();
        } catch (IOException e) {
            Log.e(TAG, "button driver error", e);
        }
    }

    /*
     ** Reset the display with specified message.
     */
    private void resetDisplayMsg(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < mResultViews.length; i++) {
                    mResultViews[i].setText(null);
                }
                mResultViews[0].setText(msg);
            }
        });
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            // Doorbell rang!
            Log.d(TAG, "button pressed");

            if (mState != ActivityState.IDLING) return true;
            mState = ActivityState.IMAGING;
            resetDisplayMsg("Capturing image ...");
            mCamera.takePicture();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * Listener for new camera images.
     */
    private ImageReader.OnImageAvailableListener mOnImageAvailableListener =
            new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();

            // get image bytes
            ByteBuffer imageBuf = image.getPlanes()[0].getBuffer();
            final byte[] imageBytes = new byte[imageBuf.remaining()];
            imageBuf.get(imageBytes);
            image.close();

            renderOnDisplay(imageBytes);

            onPictureTaken(imageBytes);
        }
    };

    /**
     * Render image on display.
     */
    private void renderOnDisplay(byte[] imageBytes) {
        final Bitmap bitmap;
        bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mImage.setImageBitmap(bitmap);
            }
        });
    }

    /**
     * Handle image processing in Firebase and Cloud Vision.
     */
    private void onPictureTaken(final byte[] imageBytes) {
        if (imageBytes != null) {
            final DatabaseReference log = mDatabase.getReference("logs").push();
            String imageStr = Base64.encodeToString(imageBytes, Base64.NO_WRAP | Base64.URL_SAFE);
            // upload image to firebase
            log.child("timestamp").setValue(ServerValue.TIMESTAMP);
            log.child("image").setValue(imageStr);

            if (mState != ActivityState.IMAGING) return;
            mState = ActivityState.LABELLING;
            resetDisplayMsg("Sending image to Google Cloud Vision API for labelling.\nPLEASE WAIT ...");
            mCloudHandler.post(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "sending image to cloud vision");
                    // annotate image by uploading to Cloud Vision API
                    try {
                        mAnnotations = CloudVisionUtils.annotateImage(imageBytes);
                        mAnnotations = Utils.rsortByValue(mAnnotations);
                        Log.d(TAG, "cloud vision annotations:" + mAnnotations);
                        if (mAnnotations != null) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    for (int i = 0; i < mResultViews.length; i++) {
                                        mResultViews[i].setText(null);
                                    }
                                    List<String> keyList = new ArrayList<String>(mAnnotations.keySet());
                                    for(int i = 0; i < keyList.size() && i < mResultViews.length; i++) {
                                        String key = keyList.get(i);
                                        float value = mAnnotations.get(key);
                                        mResultViews[i].setText(key + ":\n" + value);
                                    }
                                }
                            });
                            log.child("annotations").setValue(mAnnotations);

                            if (mState != ActivityState.LABELLING) return;
                            mState = ActivityState.DISPLAYING;

                            try {
                                Thread.sleep(7500);
                                if (mState != ActivityState.DISPLAYING) return;
                                mState = ActivityState.IDLING;
                                resetDisplayMsg("Welcome! Press the button to check-in for this event.");
                            }
                            catch (InterruptedException e) {
                            }

                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Cloud Vison API error: ", e);
                    }
                }
            });
        }
    }
}
