package com.google.samples.mysample;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.hardware.pio.Gpio;
import android.hardware.pio.PeripheralManagerService;
import android.hardware.pio.PioInterruptCallback;
import android.system.ErrnoException;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import com.google.api.services.vision.v1.model.Image;

import java.util.Map;

/**
 * Doorbell activity that capture a picture from the Raspberry Pi 3
 * Camera on a button press and post it to Firebase and Google Cloud
 * Vision API.
 */
public class DoorbellActivity extends Activity {
    private static final String TAG = "DoorbellActivity";
    private static final int IMAGE_WIDTH = 640;
    private static final int IMAGE_HEIGHT = 480;

    private FirebaseDatabase mDatabase;
    private Gpio mButtonGpio;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * The GPIO pin to activate to listen for button presses.
     */
    private final String BUTTON_GPIO_PIN = "22";

    private PioInterruptCallback mButtonCallback;

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Headless Activity created.");

        mDatabase = FirebaseDatabase.getInstance();

        // Creates a new handler and associated thread for handling IO / networking tasks.
        // Since this is a "headless" app, we don't really need the overhead of AsyncTask, which
        // is primarily for calling back to the main thread and updating the UI when it's finished.p
        startBackgroundThread();

        // Initialize camera button.
        initializeDoorbellButton();
    }

    /**
     * Initializes Peripheral manager, which will listen to physical button presses.
     */
    private void initializeDoorbellButton() {
        PeripheralManagerService pioService = new PeripheralManagerService();
        try {
            // Names of all the GPIO pins on the device.
            Log.d(TAG, "gpio: " + pioService.getGpioList());

            mButtonGpio = pioService.openGpio(BUTTON_GPIO_PIN);
            mButtonGpio.setDirection(Gpio.DIRECTION_IN);
            mButtonGpio.setEdgeTriggerType(Gpio.EDGE_RISING);
            mButtonCallback = new PioInterruptCallback() {
                @Override
                public boolean onInterruptEvent(String name) {
                    Log.d(TAG, "button pressed");
                    // Doorbell rang!
                    mBackgroundHandler.post(() -> onPictureTaken(capturePlaceholderImage()));
                    return true;
                }

                @Override
                public void onError(String name, int errorCode) {
                    Log.e(TAG, "gpio interrupt error: " + name + ": " + errorCode);
                }
            };
            mButtonGpio.registerPioInterruptCallback(mButtonCallback);
        } catch (ErrnoException e) {
            Log.e(TAG, "gpio error", e);
        }
    }

    void onPictureTaken(Bitmap bitmap) {
        if (bitmap != null) {
            Image image = CloudVisionUtils.createEncodedImage(bitmap);
            final DatabaseReference log = mDatabase.getReference("logs").push();
            // upload image to firebase
            log.child("timestamp").setValue(ServerValue.TIMESTAMP);
            log.child("image").setValue(image.getContent());

            // annotate image by uploading to Cloud Vision API
            Map<String, Float> annotations = CloudVisionUtils.annotateImage(image);
            Log.d(TAG, "annotations:" + annotations);
            if (annotations != null) {
                log.child("annotations").setValue(annotations);
            }
        }
    }

    Bitmap capturePlaceholderImage() {
        Drawable placeholder = getDrawable(R.drawable.placeholder);
        Bitmap bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        placeholder.setBounds(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
        placeholder.draw(canvas);
        return bitmap;
    }

    @Override
    protected void onDestroy() {
        super.onStop();
        mButtonGpio.unregisterPioInterruptCallback(mButtonCallback);
        mButtonGpio.close();
    }
}
