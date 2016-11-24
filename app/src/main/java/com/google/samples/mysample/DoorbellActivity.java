package com.google.samples.mysample;

import android.app.Activity;
import android.hardware.pio.GpioCallback;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.hardware.pio.Gpio;
import android.hardware.pio.PeripheralManagerService;
import android.util.Base64;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Doorbell activity that capture a picture from the Raspberry Pi 3
 * Camera on a button press and post it to Firebase and Google Cloud
 * Vision API.
 */
public class DoorbellActivity extends Activity {
    private static final String TAG = DoorbellActivity.class.getSimpleName();

    private FirebaseDatabase mDatabase;
    private Gpio mButtonGpio;
    private DoorbellCamera mCamera;

    /**
     * A {@link Handler} for running Camera tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * An additional thread for running Camera tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running Cloud tasks in the background.
     */
    private Handler mCloudHandler;

    /**
     * An additional thread for running Cloud tasks that shouldn't block the UI.
     */
    private HandlerThread mCloudThread;

    /**
     * The GPIO pin to activate to listen for button presses.
     */
    private final String BUTTON_GPIO_PIN = "BCM22";

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());

        mCloudThread = new HandlerThread("CloudThread");
        mCloudThread.start();
        mCloudHandler = new Handler(mCloudThread.getLooper());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Headless Activity created.");

        mDatabase = FirebaseDatabase.getInstance();

        // Creates a new handler and associated thread for handling IO / networking tasks.
        // Since this is a "headless" app, we don't really need the overhead of AsyncTask, which
        // is primarily for calling back to the main thread and updating the UI when it's finished.
        startBackgroundThread();

        // Camera code is complicated, so we've shoved it all in this closet class for you.
        mCamera = DoorbellCamera.getInstance();
        mCamera.initializeCamera(mBackgroundHandler, mOnImageAvailableListener, this);

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
            mButtonGpio.registerGpioCallback(mButtonCallback);
        } catch (IOException e) {
            Log.e(TAG, "gpio error", e);
        }
    }

    /**
     * Callback for GPIO button events.
     */
    private GpioCallback mButtonCallback = new GpioCallback() {
        @Override
        public boolean onGpioEdge(Gpio gpio) {
            // Doorbell rang!
            Log.d(TAG, "button pressed");

            mCamera.takePicture();
            return true;
        }

        @Override
        public void onGpioError(Gpio gpio, int error) {
            Log.e(TAG, "gpio interrupt error: " + gpio + ": " + error);
        }
    };

    /**
     * Listener for new camera images.
     */
    private ImageReader.OnImageAvailableListener mOnImageAvailableListener =
            (ImageReader reader) -> {
                final DatabaseReference log = mDatabase.getReference("logs").push();
                Image image = reader.acquireLatestImage();
                // get image bytes
                ByteBuffer imageBuf = image.getPlanes()[0].getBuffer();
                final byte[] imageBytes = new byte[imageBuf.remaining()];
                imageBuf.get(imageBytes);
                image.close();
                String imageStr = Base64.encodeToString(imageBytes, Base64.NO_WRAP | Base64.URL_SAFE);
                // upload image to firebase
                log.child("timestamp").setValue(ServerValue.TIMESTAMP);
                log.child("image").setValue(imageStr);

                mCloudHandler.post(() -> {
                    Log.d(TAG, "sending image to cloud vision");
                    // annotate image by uploading to Cloud Vision API
                    try {
                        Map<String, Float> annotations = CloudVisionUtils.annotateImage(imageBytes);
                        Log.d(TAG, "cloud vision annotations:" + annotations);
                        if (annotations != null) {
                            log.child("annotations").setValue(annotations);
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Cloud Vison API error: ", e);
                    }
                });
            };


    @Override
    protected void onStop() {
        if (mButtonGpio != null) {
            if (mButtonCallback != null) {
                mButtonGpio.unregisterGpioCallback(mButtonCallback);
            }
            try {
                mButtonGpio.close();
            } catch (IOException e) {
                Log.e(TAG, "gpio error: ", e);
            } finally {
                 mButtonGpio = null;
            }
        }
        super.onStop();
    }
}
