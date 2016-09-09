package com.google.samples.mysample;


import android.os.AsyncTask;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.pio.Gpio;
import android.pio.PeripheralManagerService;
import android.pio.PioInterruptEventListener;
import android.service.headless.HomeService;

import android.system.ErrnoException;
import android.util.Base64;
import android.util.Base64OutputStream;
import android.util.Log;

import com.google.api.services.vision.v1.model.Image;
import com.google.common.io.ByteStreams;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

/**
 * Implement your device logic here.
 */
public class MyHomeService extends HomeService {
    private static final String TAG = "MyHomeService";
    private HandlerThread mThread;
    private Handler mHandler;
    private static final String PLACEHOLDER_IMAGE_URL = "http://thecatapi.com/api/images/get?type=jpg";

    @Override
    public void onCreate() {
        Log.d(TAG, "Service started");

        mThread = new HandlerThread("backgroundThread");
        mThread.start();
        mHandler = new Handler(mThread.getLooper());
        PeripheralManagerService pioService = new PeripheralManagerService();
        try {
            Log.d(TAG, "gpio: " + pioService.getGpioList());
            Gpio buttonPin = pioService.openGpio("22");
            buttonPin.setDirection(Gpio.DIRECTION_IN);
            buttonPin.setEdgeTriggerType(Gpio.EDGE_FALLING);;
            buttonPin.registerInterruptHandler(new PioInterruptEventListener() {
                @Override
                public boolean onInterruptEvent(String name) {
                    Log.d(TAG, "button pressed");
                    onDoorbellRang();
                    return true;
                }
                @Override
                public void onError(String name, int errorCode) {
                    Log.e(TAG, "gpio interrupt error: " + name + ": " + errorCode);
                }
            });
        } catch (RemoteException|ErrnoException e) {
            Log.e(TAG, "gpio error", e);
        }

    }

    void onDoorbellRang() {
        mHandler.post(() -> {
            // create new log entry
            DatabaseReference log = FirebaseDatabase.getInstance().getReference("logs").push();
            log.child("timestamp").setValue(ServerValue.TIMESTAMP);
            byte[] imageBytes = capturePlaceholderImage();
            if (imageBytes != null) {
                // upload image to firebase
                Image image = CloudVisionUtils.createImage(imageBytes);
                log.child("image").setValue(image.getContent());
                // annotate image
                Map<String, Float> annotations = CloudVisionUtils.annotateImage(image);
                Log.d(TAG, "annotations:" + annotations);
                if (annotations != null) {
                    log.child("annotations").setValue(annotations);
                }
            }
        });
    }

    byte[] capturePlaceholderImage() {
        byte[] bs = null;
        try {
            URL imageURL = new URL(PLACEHOLDER_IMAGE_URL);
            bs = ByteStreams.toByteArray(imageURL.openStream());
        } catch (IOException e) {
            Log.e(TAG, "error fetching image", e);
        }
        return bs;
    }
}