package org.tensorflow.demo.env;

import android.graphics.Bitmap;

import org.tensorflow.demo.Classifier;

/**
 * Created by viet on 01/02/2018.
 */

public class DetectedObject {
    private Bitmap bitmap;
    private Classifier.Recognition recognition;

    public DetectedObject(Bitmap bitmap, Classifier.Recognition recognition) {
        this.bitmap = bitmap;
        this.recognition = recognition;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public Classifier.Recognition getRecognition() {
        return recognition;
    }

    public void setRecognition(Classifier.Recognition recognition) {
        this.recognition = recognition;
    }
}
