package com.google.samples.mysample;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.media.Image;
import android.util.DisplayMetrics;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ImageUtils {

    private static final String TAG = "ImageUtils";

    public static Bitmap imageToBitmap(Image image) {
        Bitmap rawBitmap = null;
        BitmapFactory.Options options = new BitmapFactory.Options();

        switch (image.getFormat()) {
            case ImageFormat.JPEG:
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] imageBytes = new byte[buffer.remaining()];
                buffer.get(imageBytes);
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                rawBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                image.close();
                break;

            case ImageFormat.YUV_420_888:
                rawBitmap = yuvImageToBitmap(image);
                image.close();
                break;
            default:
                Log.d(TAG, "Unknown format " + image.getFormat() + ", can't save");
                image.close();
                break;
        }

        if (rawBitmap != null) {
            return rawBitmap;
        }
        return null;
    }

    public static void saveBitmap(Bitmap bmp, File extFilesDir, String fileName) {
        File outFile = new File(extFilesDir, fileName);
        try (FileOutputStream out = new FileOutputStream(outFile)) {
            // bmp is your Bitmap instance
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, out);
        } catch (FileNotFoundException fnfe) {
            Log.d(TAG, "File not found exception." + fnfe);
        } catch (IOException ioe) {
            Log.d(TAG, "IOException when opening output stream.", ioe);
        }
    }

    private static Bitmap yuvImageToBitmap(Image image) {
        Image.Plane yPlane = image.getPlanes()[0];
        Image.Plane uPlane = image.getPlanes()[1];
        Image.Plane vPlane = image.getPlanes()[2];
        Bitmap img = Bitmap.createBitmap(image.getWidth(), image.getHeight(),
                Bitmap.Config.RGB_565);
        ByteBuffer yBuff = yPlane.getBuffer();
        ByteBuffer cbBuff = uPlane.getBuffer();
        ByteBuffer crBuff = vPlane.getBuffer();
        for (int y = 0; y < image.getHeight(); ++y) {
            for (int x = 0; x < image.getWidth(); ++x) {
                int pixelsIn = x + y * yPlane.getRowStride();
                int yVal = yBuff.get(pixelsIn);
                int cPixelsIn = x / 2 * uPlane.getPixelStride() + y / 2 * uPlane.getRowStride();
                int cbVal = cbBuff.get(cPixelsIn);
                int crVal = crBuff.get(cPixelsIn);
                // Java doesn't do unsigned, so we have to decode what Java
                // thinks is two's complement back to unsigned.
                if (yVal < 0) {
                    yVal += 128;
                    yVal += 128;
                }
                if (cbVal < 0) {
                    cbVal += 128;
                    cbVal += 128;
                }
                if (crVal < 0) {
                    crVal += 128;
                    crVal += 128;
                }
                // Values used for YUV --> RGB conversion.
                crVal -= 128;
                cbVal -= 128;
                double yF = 1.164 * (yVal - 16);
                int r = (int) (yF + 1.596 * (crVal));
                int g = (int) (yF - 0.391 * (cbVal) - 0.813 * (crVal));
                int b = (int) (yF + 2.018 * (cbVal));
                // Clamp RGB to [0,255]
                if (r < 0) {
                    r = 0;
                } else if (r > 255) {
                    r = 255;
                }
                if (g < 0) {
                    g = 0;
                } else if (g > 255) {
                    g = 255;
                }
                if (b < 0) {
                    b = 0;
                } else if (b > 255) {
                    b = 255;
                }
                img.setPixel(x, y, Color.rgb(r, g, b));
            }
        }
        return img;
    }
}
