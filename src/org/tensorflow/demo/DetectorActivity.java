/*
 * Copyright 2016 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.demo;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Environment;
import android.os.SystemClock;
import android.support.constraint.solver.widgets.Rectangle;
import android.text.TextUtils;
import android.util.Size;
import android.util.TypedValue;
import android.view.Display;
import android.widget.Toast;

import org.tensorflow.demo.OverlayView.DrawCallback;
import org.tensorflow.demo.api.ApiClient;
import org.tensorflow.demo.api.TensorflowApi;
import org.tensorflow.demo.env.BorderedText;
import org.tensorflow.demo.env.DetectedObject;
import org.tensorflow.demo.env.ImageUtils;
import org.tensorflow.demo.env.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DetectorActivity extends CameraActivity implements OnImageAvailableListener {
    private static final Logger LOGGER = new Logger();

    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final String TF_OD_API_MODEL_FILE =
            "file:///android_asset/ssd_mobilenet_v1_android_export.pb";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/coco_labels_list.txt";
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.1f;
    private static final boolean MAINTAIN_ASPECT = false;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;
    private static final int[] COLORS = {
            Color.BLUE, Color.RED, Color.GREEN, Color.YELLOW, Color.CYAN, Color.MAGENTA, Color.WHITE,
            Color.parseColor("#55FF55"), Color.parseColor("#FFA500"), Color.parseColor("#FF8888"),
            Color.parseColor("#AAAAFF"), Color.parseColor("#FFFFAA"), Color.parseColor("#55AAAA"),
            Color.parseColor("#AA33AA"), Color.parseColor("#0D0068")
    };
    private final Paint boxPaint = new Paint();
    private Integer sensorOrientation;
    private Classifier detector;
    private int previewWidth = 0;
    private int previewHeight = 0;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;
    private Bitmap cropCopyBitmap;
    private BorderedText borderedText;
    private long lastProcessingTimeMs;
    private OverlayView detectionOverlay;
    private List<Classifier.Recognition> mappedRecognitions =
            new LinkedList<>();


    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        try {
            detector = TensorFlowObjectDetectionAPIModel.create(
                    getAssets(), TF_OD_API_MODEL_FILE, TF_OD_API_LABELS_FILE, TF_OD_API_INPUT_SIZE);
        } catch (final IOException e) {
            LOGGER.e("Exception initializing classifier!", e);
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        final Display display = getWindowManager().getDefaultDisplay();
        final int screenOrientation = display.getRotation();

        LOGGER.i("Sensor orientation: %d, Screen orientation: %d", rotation, screenOrientation);

        sensorOrientation = rotation + screenOrientation;

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        renderDebug(canvas);
                    }
                });

        boxPaint.setColor(Color.RED);
        boxPaint.setStyle(Style.STROKE);
        boxPaint.setStrokeWidth(8.0f);
        detectionOverlay = findViewById(R.id.detection_overlay);
        detectionOverlay.addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        renderOverlay(canvas);
                    }
                });
    }

    protected void processImageRGBbytes(int[] rgbBytes) {
        tensorflowProcess(rgbBytes);
    }

    private void tensorflowProcess(int rgbBytes[]) {
        rgbFrameBitmap.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight);
        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
        runInServer();
    }

    private void runInServer() {
        System.out.println("runInServer");
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        croppedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        RequestBody requestBody = RequestBody.create(MediaType.parse("image/jpeg"), outputStream.toByteArray());
        final MultipartBody.Part part = MultipartBody.Part.createFormData("image", "name.jpg", requestBody);
        ApiClient.getInstance().create(TensorflowApi.class).getRecognition(part).enqueue(callback);
    }

    private Callback<List<Classifier.Recognition>> callback = new Callback<List<Classifier.Recognition>>() {
        @Override
        public void onResponse(Call<List<Classifier.Recognition>> call, final Response<List<Classifier.Recognition>> response) {
            runInBackground(new Runnable() {
                @Override
                public void run() {
                    System.out.println(response.message() + "");
                    List<Classifier.Recognition> results = response.body();
                    if (results != null && !results.isEmpty()) {
                        LOGGER.i("Detect: %s", results);
                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                        final Canvas canvas = new Canvas(cropCopyBitmap);

                        mappedRecognitions.clear();
                        for (final Classifier.Recognition result : results) {
                            final RectF location = result.getLocation();
                            if (location != null && result.getConfidence() >= MINIMUM_CONFIDENCE_TF_OD_API) {
                                canvas.drawRect(location, paint);
                                cropToFrameTransform.mapRect(location);
                                result.setLocation(location);
                                mappedRecognitions.add(result);
                                if (btnSwitch.isChecked()) {
                                    showImage(result);
                                }
                            }
                        }
                        requestRender();
                        detectionOverlay.postInvalidate();
                        computing = false;
                        if (postInferenceCallback != null) {
                            postInferenceCallback.run();
                        }
                    }
                }
            });

        }

        @Override
        public void onFailure(Call<List<Classifier.Recognition>> call, Throwable t) {
            System.out.println("onFailure");
            t.printStackTrace();
        }
    };

    public void showImage(final Classifier.Recognition result) {
        RectF location = result.getLocation();
        Rectangle r = new Rectangle();
        r.setBounds((int) location.left,
                (int) location.top,
                (int) (location.right - location.left),
                (int) (location.bottom - location.top));
        final Bitmap cutBitmap = cutBitmap(rgbFrameBitmap, r.x, r.y, r.width, r.height);
        final Bitmap rotatedBitmap = RotateBitmap(cutBitmap, 90);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                adapter.addItem(new DetectedObject(rotatedBitmap, result));
            }
        });
    }

    public static Bitmap RotateBitmap(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private Bitmap cutBitmap(Bitmap originalBitmap, int x, int y, int width, int height) {
        Bitmap cutBitmap = Bitmap.createBitmap(width,
                height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(cutBitmap);
        Rect srcRect = new Rect(x, y, x + width, y + height);
        Rect desRect = new Rect(0, 0, width, height);
        canvas.drawBitmap(originalBitmap, srcRect, desRect, null);
        return cutBitmap;
    }

    private void saveBitmap(Bitmap bitmap) throws Exception {
        File file = new File(Environment.getExternalStorageDirectory() + "/" + Environment.DIRECTORY_DCIM + "/image.jpg");
        if (!file.exists()) {
            file.createNewFile();
        }
        FileOutputStream outputStream = new FileOutputStream(file);
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);

    }

    @Override
    protected int getLayoutId() {
        return R.layout.camera_connection_fragment_detection;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    @Override
    public void onSetDebug(final boolean debug) {
        detector.enableStatLogging(debug);
    }

    private void renderOverlay(final Canvas canvas) {
        final float multiplier =
                Math.min(canvas.getWidth() / (float) previewHeight, canvas.getHeight() / (float) previewWidth);
        Matrix frameToCanvasMatrix =
                ImageUtils.getTransformationMatrix(
                        previewWidth,
                        previewHeight,
                        (int) (multiplier * previewHeight),
                        (int) (multiplier * previewWidth),
                        sensorOrientation,
                        false);
        int count = 0;
        for (final Classifier.Recognition recognition : mappedRecognitions) {
            final RectF location = new RectF(recognition.getLocation());

            frameToCanvasMatrix.mapRect(location);
            boxPaint.setColor(COLORS[count]);
            count = (count + 1) % COLORS.length;

            final float cornerSize = Math.min(location.width(), location.height()) / 8.0f;
            canvas.drawRoundRect(location, cornerSize, cornerSize, boxPaint);

            final String labelString =
                    !TextUtils.isEmpty(recognition.getTitle())
                            ? String.format("%s %.2f", recognition.getTitle(), recognition.getConfidence())
                            : String.format("%.2f", recognition.getConfidence());
            borderedText.drawText(canvas, location.left + cornerSize, location.bottom, labelString);
        }
    }

    private void renderDebug(final Canvas canvas) {
        if (!isDebug()) {
            return;
        }
        final Bitmap copy = cropCopyBitmap;
        if (copy == null) {
            return;
        }

        final int backgroundColor = Color.argb(100, 0, 0, 0);
        canvas.drawColor(backgroundColor);

        final Matrix matrix = new Matrix();
        final float scaleFactor = 2;
        matrix.postScale(scaleFactor, scaleFactor);
        matrix.postTranslate(
                canvas.getWidth() - copy.getWidth() * scaleFactor,
                canvas.getHeight() - copy.getHeight() * scaleFactor);
        canvas.drawBitmap(copy, matrix, new Paint());

        final Vector<String> lines = new Vector<>();
        if (detector != null) {
            final String statString = detector.getStatString();
            final String[] statLines = statString.split("\n");
            Collections.addAll(lines, statLines);
        }
        lines.add("");

        lines.add("Frame: " + previewWidth + "x" + previewHeight);
        lines.add("Crop: " + copy.getWidth() + "x" + copy.getHeight());
        lines.add("View: " + canvas.getWidth() + "x" + canvas.getHeight());
        lines.add("Rotation: " + sensorOrientation);
        lines.add("Inference time: " + lastProcessingTimeMs + "ms");

        borderedText.drawLines(canvas, 10, canvas.getHeight() - 10, lines);
    }
}
