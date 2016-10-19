package com.google.samples.mysample;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.hardware.pio.GpioCallback;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.hardware.pio.Gpio;
import android.hardware.pio.PeripheralManagerService;
import android.system.ErrnoException;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ServerValue;

import com.google.api.services.vision.v1.model.Image;

import java.io.IOException;
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

    /**
     * The GPIO pin to activate to listen for button presses.
     */
    private final String BUTTON_GPIO_PIN = "22";

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;
    /**
     * Input attached to the doorbell button.
     */
    private Gpio mButtonGpio;
    /**
     * Synchronized database for doorbell events
     */
    private FirebaseDatabase mDatabase;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Headless Activity created.");

        mDatabase = FirebaseDatabase.getInstance();

        startBackgroundThread();

        initializeDoorbellButton();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mBackgroundThread.quitSafely();

        mButtonGpio.unregisterGpioCallback(mButtonCallback);
        mButtonGpio.close();
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());

    }

    /**
     * Initializes Peripheral manager, which will listen to physical button presses.
     */
    private void initializeDoorbellButton() {
        PeripheralManagerService pioService = new PeripheralManagerService();
        try {
            mButtonGpio = pioService.openGpio(BUTTON_GPIO_PIN);
            mButtonGpio.setDirection(Gpio.DIRECTION_IN);
            mButtonGpio.setEdgeTriggerType(Gpio.EDGE_RISING);
            mButtonGpio.registerGpioCallback(mButtonCallback, mBackgroundHandler);
        } catch (ErrnoException e) {
            Log.e(TAG, "gpio error", e);
        }
    }

    /**
     * Callback for GPIO button events.
     * Invoked on the background {@link HandlerThread}.
     */
    private GpioCallback mButtonCallback = new GpioCallback() {
        @Override
        public boolean onGpioEdge(Gpio gpio) {
            // Doorbell rang!
            Log.d(TAG, "button pressed");
            // Synchronously capture image
            final Bitmap image = captureImage();
            Log.d(TAG, "image captured");
            // Post task to annotate and upload image
            mBackgroundHandler.post(() -> onPictureTaken(image));
            return true;
        }

        @Override
        public void onGpioError(Gpio gpio, int error) {
            Log.e(TAG, "gpio interrupt error: " + gpio + ": " + error);
        }
    };

    /**
     * Capture an image from the camera.
     * Should be called on the background {@link HandlerThread}.
     *
     * @return image frame from the camera.
     */
    private Bitmap captureImage() {
        //TODO: Capture from camera
        Drawable placeholder = getDrawable(R.drawable.placeholder);
        Bitmap bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        placeholder.setBounds(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
        placeholder.draw(canvas);
        return bitmap;
    }

    /**
     * Process a captured camera image using the Vision API.
     * Should be called on the background {@link HandlerThread}.
     *
     * @param bitmap captured image to process.
     */
    private void onPictureTaken(Bitmap bitmap) {
        if (bitmap != null) {
            try {
                // Process the image using Cloud Vision
                Image image = CloudVisionUtils.createEncodedImage(bitmap);
                Map<String, Float> annotations = CloudVisionUtils.annotateImage(image);
                Log.d(TAG, "annotations:" + annotations);

                // Write the contents to Firebase
                final DatabaseReference log = mDatabase.getReference("logs").push();
                log.child("timestamp").setValue(ServerValue.TIMESTAMP);
                log.child("image").setValue(image.getContent());
                if (annotations != null) {
                    log.child("annotations").setValue(annotations);
                }
            } catch (IOException e) {
                Log.w(TAG, "Unable to annotate image", e);
            }

        }
    }
}
