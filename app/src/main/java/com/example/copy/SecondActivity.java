package com.example.copy;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.widget.VideoView;

public class SecondActivity extends AppCompatActivity {

    Button button2, button6, button5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);


        button6 = findViewById(R.id.button6);
        button6.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(SecondActivity.this, predictimage.class);
                startActivity(intent);
            }
        });


        button2 = findViewById(R.id.button2);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(SecondActivity.this, MainActivity2.class);
                startActivity(intent);
            }
        });

        button5 = findViewById(R.id.button5);
        button5.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(getApplicationContext(),
                        "Exiting the app!", Toast.LENGTH_SHORT).show();
                finishAffinity();
                System.exit(0);
            }
        });
    }
}



/**

        findViewById(R.id.button2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                startActivityForResult(intent, 1);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == 1) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            VideoView videoView = new VideoView(this);
            videoView.setVideoURI(data.getData());
            videoView.start();
            builder.setView(videoView).show();
        }
    }
    }


/**
 *
 *
 *      * private static final int REQUEST_CAMERA_PERMISSION = 200;
 *      * private static final int IMAGE_WIDTH = 224;
 *      * private static final int IMAGE_HEIGHT = 224;
 *      * private static final int NUM_CHANNELS = 3;
 *      * private static final int NUM_CLASSES = 10;
 *      * <p>
 *      * private Camera camera;
 *      * private SurfaceHolder surfaceHolder;
 *      * private Interpreter interpreter;
 *      * private Executor executor = Executors.newSingleThreadExecutor();
 *      * <p>
 *      * private Button liveAnalysisButton;
 *      * private FrameLayout previewLayout;
 *      * <p>
 *      * private Camera.PreviewCallback previewCallback = new Camera.PreviewCallback() {
 *      *
 *      * @Override public void onPreviewFrame(byte[] data, Camera camera) {
 *      * // Preprocess and infer on the frame
 *      * Bitmap bitmap = preprocessFrame(data);
 *      * String activity = runInference(bitmap);
 *      * drawResult(activity);
 *      * }
 *      * };
 *
 *
 *
                // Request camera permission
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                }

                // Initialize the camera preview
                SurfaceView surfaceView = findViewById(R.id.camera_preview);
                surfaceHolder = surfaceView.getHolder();

                // Load the TensorFlow Lite model
                try {
                    ByteBuffer modelBuffer = FileUtil.loadMappedFile(this, "classi_model.tflite");
                    interpreter = new Interpreter(modelBuffer);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // Set up the button for live analysis
                liveAnalysisButton = findViewById(R.id.live_analysis_button);
                liveAnalysisButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startLiveAnalysis();
                    }
                });

                // Set up the preview layout
                previewLayout = findViewById(R.id.preview_layout);
            }



            private void startLiveAnalysis() {
                // Open the camera
                if (camera == null) {
                    try {
                        camera = Camera.open();
                        camera.setPreviewDisplay(surfaceHolder);
                        camera.setPreviewCallback(previewCallback);
                        camera.startPreview();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                // Capture frames every second
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        while (camera != null) {
                            try {
                                Thread.sleep(1000);
                                captureFrame();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
            }

            private void captureFrame() {
                // Capture a frame from the camera
                camera.setOneShotPreviewCallback(new Camera.PreviewCallback() {
                    @Override
                    public void onPreviewFrame(byte[] data, Camera camera) {
                        // Preprocess the frame
                        Bitmap bitmap = preprocessFrame(data);

                        // Run inference on the frame
                        String activity = runInference(bitmap);

                        // Display the result on the camera preview
                        drawResult(activity);
                    }
                });
            }

            private Bitmap preprocessFrame(byte[] data) {
                // Convert byte data to bitmap
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);

                // Resize the bitmap to match the input size of the model
                Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, IMAGE_WIDTH, IMAGE_HEIGHT, false);

                // Normalize the pixel values
                resizedBitmap = resizedBitmap.copy(Bitmap.Config.ARGB_8888, true);
                int[] pixels = new int[IMAGE_WIDTH * IMAGE_HEIGHT];
                resizedBitmap.getPixels(pixels, 0, resizedBitmap.getWidth(), 0, 0, resizedBitmap.getWidth(), resizedBitmap.getHeight());
                for (int i = 0; i < pixels.length; i++) {
                    int pixel = pixels[i];
                    int red = Color.red(pixel);
                    int green = Color.green(pixel);
                    int blue = Color.blue(pixel);
                    float normalizedRed = (red - 127.5f) / 127.5f;
                    float normalizedGreen = (green - 127.5f) / 127.5f;
                    float normalizedBlue = (blue - 127.5f) / 127.5f;
                    int normalizedPixel = Color.rgb((int) (normalizedRed * 255), (int) (normalizedGreen * 255), (int) (normalizedBlue * 255));
                    pixels[i] = normalizedPixel;
                }
                resizedBitmap.setPixels(pixels, 0, resizedBitmap.getWidth(), 0, 0, resizedBitmap.getWidth(), resizedBitmap.getHeight());

                return resizedBitmap;
            }

            private String runInference(Bitmap inputBitmap) {
                // Preallocate buffers for the input and output tensors
                ByteBuffer inputBuffer = ByteBuffer.allocateDirect(IMAGE_WIDTH * IMAGE_HEIGHT * NUM_CHANNELS * 4);
                inputBuffer.order(ByteOrder.nativeOrder());
                int[] outputLabels = new int[1];
                float[][] outputProbabilities = new float[1][NUM_CLASSES];

                // Preprocess the input frame and fill the input buffer
                inputBitmap.getPixels(new int[IMAGE_WIDTH * IMAGE_HEIGHT], 0, IMAGE_WIDTH, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
                for (int i = 0; i < IMAGE_WIDTH * IMAGE_HEIGHT; i++) {
                    int pixel = inputBitmap.getPixel(i % IMAGE_WIDTH, i / IMAGE_WIDTH);
                    inputBuffer.putFloat((Color.red(pixel) - 127.5f) / 127.5f);
                    inputBuffer.putFloat((Color.green(pixel) - 127.5f) / 127.5f);
                    inputBuffer.putFloat((Color.blue(pixel) - 127.5f) / 127.5f);
                }

                // Run inference
                interpreter.run(inputBuffer, outputProbabilities);

                // Postprocess the results and find the predicted activity
                float maxProbability = 0.0f;
                int predictedIndex = -1;
                for (int i = 0; i < NUM_CLASSES; i++) {
                    if (outputProbabilities[0][i] > maxProbability) {
                        maxProbability = outputProbabilities[0][i];
                        predictedIndex = i;
                    }
                }

                // Return the predicted activity label
                return getActivityLabel(predictedIndex);
            }

            private void drawResult(String activity) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Clear previous drawings
                        previewLayout.removeAllViews();

                        // Draw a red box on the camera preview
                        View boxView = new View(SecondActivity.this);
                        boxView.setLayoutParams(new FrameLayout.LayoutParams(300, 300));
                        boxView.setBackgroundColor(Color.RED);
                        previewLayout.addView(boxView);

                        // Draw the predicted activity label on top of the red box
                        Paint textPaint = new Paint();
                        textPaint.setColor(Color.WHITE);
                        textPaint.setTextSize(24);
                        textPaint.setStyle(Paint.Style.FILL);
                        textPaint.setTextAlign(Paint.Align.CENTER);

                        Canvas canvas = new Canvas();
                        canvas.drawText(activity, 150, 150, textPaint);
                        previewLayout.draw(canvas);
                    }
                });
            }

            private String getActivityLabel(int index) {
                // Map the index to the activity label based on your class labels
                switch (index) {
                    case 0:
                        return "Driving Safe";
                    case 1:
                        return "Testing Phone with Left Hand";
                    case 2:
                        return "Texting Phone with Right Hand";
                    case 3:
                        return "Listening to Call using Right Hand";
                    case 4:
                        return "Listening to Call using Left Hand";
                    case 5:
                        return "Operating Radio";
                    case 6:
                        return "Hairs and Makeup";
                    case 7:
                        return "Drinking";
                    case 8:
                        return "Talking with Passenger";
                    case 9:
                        return "Reaching Behind";
                    default:
                        return "Unknown";
                }
            }

            @Override
            protected void onDestroy() {
                super.onDestroy();

                // Release the camera resources
                if (camera != null) {
                    camera.setPreviewCallback(null);
                    camera.release();
                    camera = null;
                }

                // Close the interpreter
                if (interpreter != null) {
                    interpreter.close();
                    interpreter = null;
                }
            }

            @Override
            public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
                if (requestCode == REQUEST_CAMERA_PERMISSION) {
                    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        // Permission granted
                        Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show();
                    } else {
                        // Permission denied
                        Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }

**/


/**
        button2 = findViewById(R.id.button2);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Toast.makeText(getApplicationContext(),"Get ready",Toast.LENGTH_SHORT).show();

                Intent intent = new Intent(SecondActivity.this,MainActivity2.class);
                startActivity(intent);
            }
        });
**/


/**
        findViewById(R.id.button2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
                startActivityForResult(intent, 1);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == 1) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            VideoView videoView = new VideoView(this);
            videoView.setVideoURI(data.getData());
            videoView.start();
            builder.setView(videoView).show();
        }
    }
    **/

