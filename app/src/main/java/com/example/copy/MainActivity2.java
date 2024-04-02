package com.example.copy;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.copy.ml.ClassiModel;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import android.graphics.SurfaceTexture;


//import org.tensorflow.lite.TensorBuffer;

public class MainActivity2 extends AppCompatActivity {
    private static final String TAG = "end";
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private CameraManager cameraManager;
    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private Handler handler;
    private SurfaceView surfaceView;
    private ViewGroup rootLayout;
    private FrameLayout overlayLayout;
    private TextView labelTextView;
    private Bitmap bitmap;
    private View boxView;
    private ClassiModel model; // Declare the model instance
    private ImageReader imageReader;
    private boolean isCapturingFrames = false;

    private MediaPlayer alarmPlayer;
    private boolean isAlarmPlaying = false;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        labelTextView = new TextView(this);
        boxView = new View(this);

        try {
            model = ClassiModel.newInstance(this);

        } catch (IOException e) {
            e.printStackTrace();
        }

        // Initialize the buttons
        Button btnResume = findViewById(R.id.btnResume);
        Button btnStop = findViewById(R.id.btnStop);
        Button btnBack = findViewById(R.id.btnBack);

        // Set click listeners for the buttons
        btnResume.setOnClickListener(v -> {
            isCapturingFrames = true;
            captureFrame();
        });


        //      btnStop.setOnClickListener(v -> {
        // Remove any existing views
        //          overlayLayout.removeView(labelTextView);
        //          overlayLayout.removeView(boxView);

        // Stop capturing frames
        //         isCapturingFrames = false;
        //     });

        btnStop.setOnClickListener(v -> {
            isCapturingFrames = false;
            // Clear any existing views
            overlayLayout.removeView(labelTextView);
            overlayLayout.removeView(boxView);
        });

        btnBack.setOnClickListener(v -> {

            // Create an Intent to start the SecondActivity
            Intent intent = new Intent(MainActivity2.this, SecondActivity.class);

            // Start the SecondActivity
            startActivity(intent);

            // Finish the current activity (MainActivity2)
            finish();
        });


        // Initialize the MediaPlayer
        alarmPlayer = MediaPlayer.create(this, R.raw.wake); // Replace with the actual resource ID of your alarm sound
        alarmPlayer.setOnCompletionListener(mediaPlayer -> {
            // Release the MediaPlayer resources when the sound is finished playing
            mediaPlayer.release();
            isAlarmPlaying = false;
        });


        surfaceView = findViewById(R.id.surfaceView);
        rootLayout = findViewById(android.R.id.content);
        overlayLayout = findViewById(R.id.overlayLayout);

        // Request camera permission
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        } else {
            startCamera();
        }
        handler = new Handler();
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void startCamera() {
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);

        try {
            cameraId = cameraManager.getCameraIdList()[0];
            System.out.println("Camera Id" + cameraId);

            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            imageDimension = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    .getOutputSizes(SurfaceHolder.class)[0];

            // Continue with opening the camera
            openCamera();

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        cameraDevice = camera;
                        createCameraPreview();
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        cameraDevice.close();
                    }

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        cameraDevice.close();
                    }
                }, null);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        if (cameraId == null) {
            Log.e(TAG, "No camera available");
        }
    }

    private void createCameraPreview() {
        try {
            int inputWidth = 224;
            int inputHeight = 224;
            // Create the image reader to capture the frame
            ImageReader imageReader1 = ImageReader.newInstance(
                    inputWidth,
                    inputHeight,
                    ImageFormat.JPEG,
                    1
            );

            SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.setFixedSize(inputWidth, inputHeight);

            List<Surface> outputSurfaces = new ArrayList<>(2);
            outputSurfaces.add(imageReader1.getSurface());
            outputSurfaces.add(surfaceHolder.getSurface());

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surfaceHolder.getSurface());

            cameraDevice.createCaptureSession(outputSurfaces,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (cameraDevice == null) {
                                return;
                            }
                            cameraCaptureSession = session;
                            updatePreview();
                            imageReader = imageReader1;
                            captureFrame();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Toast.makeText(getApplicationContext(), "Configuration change failed",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }, null);


        } catch (CameraAccessException e) {
            e.printStackTrace();
            System.out.println(e.getReason());
        }
    }

    private void updatePreview() {
        if (cameraDevice == null) {
            return;
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void captureFrame() {
        if (!isCapturingFrames) {
            return;
        }

        // Set the image available listener
        imageReader.setOnImageAvailableListener(reader -> {
            System.out.println("Image Read");
            try (Image image = reader.acquireLatestImage()) {
                if (image != null) {
                    // Convert the image to bitmap
                    bitmap = imageToBitmap(image);

                    // Process the bitmap with your model
                    backgroundHandler.post(() -> runInferenceOnBitmap(bitmap)); // Run on the background thread
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, backgroundHandler);

        try {
            // Create the capture request
            final CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(imageReader.getSurface());

            // Configure the capture request
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            // Capture the frame
            cameraCaptureSession.capture(captureRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            Log.e(TAG, "An error occurred: " + e.getMessage());
        }
    }

    private Bitmap imageToBitmap(@NonNull Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.capacity()];
        buffer.get(bytes);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
    }



    private Pair<String, Float> runInferenceOnBitmap(@NonNull Bitmap bitmap) {
        try {
            ClassiModel model = ClassiModel.newInstance(this);

            int inputWidth = 224;
            int inputHeight = 224;
            int inputChannels = 3;

            // Normalize and load the image into the input buffer
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, inputWidth, inputHeight, inputChannels}, DataType.FLOAT32);
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * inputWidth * inputHeight * inputChannels);
            byteBuffer.order(ByteOrder.nativeOrder());

            int[] intValues = new int[inputWidth * inputHeight];
            bitmap.getPixels(intValues, 0, inputWidth, 0, 0, inputWidth, inputHeight);
         //   bitmap.getPixels(intValues, 0, inputWidth, 0, 0, bitmap.getWidth(), bitmap.getHeight());
            int pixel = 0;

            // Iterate over each pixel and extract R, G, and B values. Normalize and add those values individually to the byte buffer.
            for (int i = 0; i < inputWidth; i++) {
                for (int j = 0; j < inputHeight; j++) {
                    int val = intValues[pixel++]; // RGB
                    byteBuffer.putFloat(((val >> 16) & 0xFF) / 255.0f);  // Red component
                    byteBuffer.putFloat(((val >> 8) & 0xFF) / 255.0f);   // Green component
                    byteBuffer.putFloat((val & 0xFF) / 255.0f);          // Blue component
                }
            }
            inputFeature0.loadBuffer(byteBuffer);

            // Run model inference and get the result
            ClassiModel.Outputs outputs = model.process(inputFeature0);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();


            float[] confidences = outputFeature0.getFloatArray();


            // Find the index of the class with the highest confidence.
            int maxPos = 0;
            float maxConfidence = 0;
            for (int i = 0; i < confidences.length; i++) {
                if (confidences[i] > maxConfidence) {
                    maxConfidence = confidences[i];
                    maxPos = i;
                }
            }

            String[] classes = {"Driving safe",
                    "Texting on phone / Using phone with right hand",
                    "Listening phone with right hand",
                    "Texting on phone / Using phone with left hand",
                    "Listening phone with left hand",
                    "Operating the radio",
                    "Drinking ",
                    "Reaching behind to grab something",
                    "Makeup / Setting hairs",
                    "Talking with passenger"};

            String label = classes[maxPos];

            float probability = maxConfidence;
            displayBoundingBox(label, probability);

        } catch (IOException e) {
            // TODO Handle the exception
            e.printStackTrace();
        }
        // Return a default value if an error occurs
        return new Pair<>("Unknown", 0.0f);

    }


    private void displayBoundingBox(String label, float probability) {
        runOnUiThread(() -> {
            // Remove any existing views
            rootLayout.removeView(labelTextView);
            overlayLayout.removeView(boxView);

            // Create and add the bounding box container view using overlayLayout
            int boxWidth = 1000;
            int boxHeight = 1600;
            int boxLeft = (rootLayout.getWidth() - boxWidth) / 2;
            int boxTop = (rootLayout.getHeight() - boxHeight) / 2;

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(boxWidth, boxHeight);
            params.leftMargin = boxLeft;
            params.topMargin = boxTop;

            // Create and add the red rectangle hollow box container
            FrameLayout boxContainer = new FrameLayout(this);
            boxContainer.setLayoutParams(params);
            overlayLayout.addView(boxContainer);

            // Create and add the red rectangle hollow box
            View boxView = new View(this);
            boxView.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            boxView.setBackgroundResource(R.drawable.rectangle_border);
            boxContainer.addView(boxView);

            // Create and add the label and probability text view inside the box container
            TextView textView = new TextView(this);
            String labelText = label + "\nProbability: " + (probability * 100) + "%";
            textView.setText(labelText);
            textView.setTextColor(Color.RED);
            textView.setGravity(Gravity.TOP | Gravity.START);
            textView.setPadding(10, 10, 10, 10);
            boxContainer.addView(textView);

            // After one second, remove the views from the layout
            handler.postDelayed(() -> {
                overlayLayout.removeView(boxContainer);
            }, 4000);
        });

        handler.postDelayed(this::captureFrame, 4000);
    }

}



/** This code works only for picture or video only
public class MainActivity2 extends AppCompatActivity {
    private static final String TAG = "end";
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private CameraManager cameraManager;
    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder captureRequestBuilder;
 //   private Size imageDimension;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private Handler handler;
    private SurfaceView surfaceView;
    private ViewGroup rootLayout;
    private FrameLayout overlayLayout;
    private TextView labelTextView;
    private Bitmap bitmap;
    private View boxView;
    private ClassiModel model; // Declare the model instance
    private ImageReader imageReader;
    private boolean isCapturingFrames = false;

    private MediaPlayer alarmPlayer;
    private boolean isAlarmPlaying = false;
    private Size imageDimension;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        labelTextView = new TextView(this);
        boxView = new View(this);

        try {
            model = ClassiModel.newInstance(this);

        } catch (IOException e) {
            e.printStackTrace();
        }

        // Initialize the buttons
        Button btnResume = findViewById(R.id.btnResume);
        Button btnStop = findViewById(R.id.btnStop);
        Button btnBack = findViewById(R.id.btnBack);

        // Set click listeners for the buttons
        btnResume.setOnClickListener(v -> {
            isCapturingFrames = true;
            captureFrame();
        });


        //      btnStop.setOnClickListener(v -> {
        // Remove any existing views
        //          overlayLayout.removeView(labelTextView);
        //          overlayLayout.removeView(boxView);

        // Stop capturing frames
        //         isCapturingFrames = false;
        //     });

        btnStop.setOnClickListener(v -> {
            isCapturingFrames = false;
            // Clear any existing views
            overlayLayout.removeView(labelTextView);
            overlayLayout.removeView(boxView);
        });

        btnBack.setOnClickListener(v -> {
            
            // Stop capturing frames
            isCapturingFrames = false;

            // Release the ImageReader
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }

            // Clear any existing views
            overlayLayout.removeView(labelTextView);
            overlayLayout.removeView(boxView);

            // Clear the current bitmap
            if (bitmap != null) {
                bitmap.recycle();
                bitmap = null;
            }

            // Create an Intent to start the SecondActivity
            Intent intent = new Intent(MainActivity2.this, SecondActivity.class);

            // Start the SecondActivity
            startActivity(intent);

            // Finish the current activity (MainActivity2)
            finish();
        });


        // Initialize the MediaPlayer
        alarmPlayer = MediaPlayer.create(this, R.raw.wake); // Replace with the actual resource ID of your alarm sound
        alarmPlayer.setOnCompletionListener(mediaPlayer -> {
            // Release the MediaPlayer resources when the sound is finished playing
            mediaPlayer.release();
            isAlarmPlaying = false;
        });


        surfaceView = findViewById(R.id.surfaceView);
        rootLayout = findViewById(android.R.id.content);
        overlayLayout = findViewById(R.id.overlayLayout);

        // Request camera permission
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        } else {
            startCamera();
        }
        handler = new Handler();
    }

    @Override
    protected void onResume() {
        super.onResume();

        startBackgroundThread();
    }

    @Override
    protected void onPause() {

        // Release the ImageReader
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }

        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }


    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }


    private void startCamera() {
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);

        try {
            cameraId = cameraManager.getCameraIdList()[0];
            System.out.println("Camera Id" + cameraId);

            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            imageDimension = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    .getOutputSizes(SurfaceHolder.class)[0];

            // Continue with opening the camera
            openCamera();

        } catch (CameraAccessException e) {
            e.printStackTrace();

            Log.e(TAG, "An error occurred: " + e.getMessage());
        }
    }

    private void openCamera() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        cameraDevice = camera;
                        createCameraPreview();
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        cameraDevice.close();
                    }

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        cameraDevice.close();
                    }
                }, null);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();

            Log.e(TAG, "An error occurred: " + e.getMessage());
        }
        if (cameraId == null) {
            Log.e(TAG, "No camera available");
        }
    }

    private void createCameraPreview() {
        try {
            int inputWidth = 224;
            int inputHeight = 224;
            // Create the image reader to capture the frame
            ImageReader imageReader1 = ImageReader.newInstance(
                    inputWidth,
                    inputHeight,
                    ImageFormat.JPEG,
                    1
            );

            SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.setFixedSize(inputWidth, inputHeight);

            List<Surface> outputSurfaces = new ArrayList<>(2);
            outputSurfaces.add(imageReader1.getSurface());
            outputSurfaces.add(surfaceHolder.getSurface());

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surfaceHolder.getSurface());

            cameraDevice.createCaptureSession(outputSurfaces,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (cameraDevice == null) {
                                return;
                            }
                            cameraCaptureSession = session;
                            updatePreview();
                            imageReader = imageReader1;
                            captureFrame();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Toast.makeText(getApplicationContext(), "Configuration change failed",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }, null);


        } catch (CameraAccessException e) {
            e.printStackTrace();
            System.out.println(e.getReason());

            Log.e(TAG, "An error occurred: " + e.getMessage());
        }
    }

    private void updatePreview() {
        if (cameraDevice == null) {
            return;
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();

            Log.e(TAG, "An error occurred: " + e.getMessage());
        }
    }

    private void closeCamera() {
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
            Log.e(TAG, "An error occurred: " + e.getMessage());
        }
    }

    private void captureFrame() {
        if (!isCapturingFrames) {
            return;
        }

        // Set the image available listener
        imageReader.setOnImageAvailableListener(reader -> {
            System.out.println("Image Read");
            try (Image image = reader.acquireLatestImage()) {
                if (image != null) {
                    // Convert the image to bitmap
                    bitmap = imageToBitmap(image);

                    // Process the bitmap with your model
                    runInferenceOnBitmap(bitmap);
                    image.close();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, backgroundHandler);



        try {
            // Create the capture request
            final CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(imageReader.getSurface());

            // Configure the capture request
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            // Capture the frame
            cameraCaptureSession.capture(captureRequestBuilder.build(), null, backgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
            Log.e(TAG, "An error occurred: " + e.getMessage());
        }
    }

    private Bitmap imageToBitmap(@NonNull Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.capacity()];
        buffer.get(bytes);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
    }


    private void runInferenceOnBitmap(@NonNull Bitmap bitmap) {

        //    ClassiModel model = ClassiModel.newInstance(this);

            int inputWidth = 224;
            int inputHeight = 224;
         //   int inputWidth = bitmap.getWidth();  // Use the actual Bitmap dimensions
         //   int inputHeight = bitmap.getHeight();

            int inputChannels = 3;

            // Normalize and load the image into the input buffer
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, inputWidth, inputHeight, inputChannels}, DataType.FLOAT32);
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * inputWidth * inputHeight * inputChannels);
            byteBuffer.order(ByteOrder.nativeOrder());

            int[] intValues = new int[inputWidth * inputHeight];
            bitmap.getPixels(intValues, 0, inputWidth, 0, 0, inputWidth, inputHeight);

            int pixel = 0;

            // Iterate over each pixel and extract R, G, and B values. Normalize and add those values individually to the byte buffer.
            for (int i = 0; i < inputWidth; i++) {
                for (int j = 0; j < inputHeight; j++) {
                    int val = intValues[pixel++]; // RGB
                    byteBuffer.putFloat(((val >> 16) & 0xFF) / 255.0f);  // Red component
                    byteBuffer.putFloat(((val >> 8) & 0xFF) / 255.0f);   // Green component
                    byteBuffer.putFloat((val & 0xFF) / 255.0f);          // Blue component
                }
            }
            inputFeature0.loadBuffer(byteBuffer);

            // Run model inference and get the result
            ClassiModel.Outputs outputs = model.process(inputFeature0);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();


            float[] confidences = outputFeature0.getFloatArray();


            // Find the index of the class with the highest confidence.
            int maxPos = 0;
            float maxConfidence = 0;
            for (int i = 0; i < confidences.length; i++) {
                if (confidences[i] > maxConfidence) {
                    maxConfidence = confidences[i];
                    maxPos = i;
                }
            }

            String[] classes = {"Driving safe",
                    "Texting on phone / Using phone with right hand",
                    "Listening phone with right hand",
                    "Texting on phone / Using phone with left hand",
                    "Listening phone with left hand",
                    "Operating the radio",
                    "Drinking ",
                    "Reaching behind to grab something",
                    "Makeup / Setting hairs",
                    "Talking with passenger"};

            // Return the predicted label and probability as a Pair
            //  return new Pair<>(predictedLabel, maxConfidence);

            String label = classes[maxPos];
            float probability = maxConfidence;
            displayBoundingBox(label, probability);

            // Release the Bitmap when done
            bitmap.recycle();

        // Return a default value if an error occurs
      //  return new Pair<>("Unknown", 0.0f);
    }


    private void displayBoundingBox(String label, float probability) {
        runOnUiThread(() -> {
            // Remove any existing views
            rootLayout.removeView(labelTextView);
            overlayLayout.removeView(boxView);

            // Create and add the bounding box container view using overlayLayout
            int boxWidth = 1000;
            int boxHeight = 1600;
            int boxLeft = (rootLayout.getWidth() - boxWidth) / 2;
            int boxTop = (rootLayout.getHeight() - boxHeight) / 2;

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(boxWidth, boxHeight);
            params.leftMargin = boxLeft;
            params.topMargin = boxTop;

            // Create and add the red rectangle hollow box container
            FrameLayout boxContainer = new FrameLayout(this);
            boxContainer.setLayoutParams(params);
            overlayLayout.addView(boxContainer);

            // Create and add the red rectangle hollow box
            View boxView = new View(this);
            boxView.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            boxView.setBackgroundResource(R.drawable.rectangle_border);
            boxContainer.addView(boxView);

            // Create and add the label and probability text view inside the box container
            TextView textView = new TextView(this);
            String labelText = label + "\nProbability: " + (probability * 100) + "%";
            textView.setText(labelText);
            textView.setTextColor(Color.RED);
            textView.setGravity(Gravity.TOP | Gravity.START);
            textView.setPadding(10, 10, 10, 10);
            boxContainer.addView(textView);

            // After one second, remove the views from the layout
            handler.postDelayed(() -> {
                overlayLayout.removeView(boxContainer);
            }, 4000);
        });

        handler.postDelayed(this::captureFrame, 4000);
    }

}
**/







/** textureview work only with buttons
public class MainActivity2 extends AppCompatActivity {
    private static final String TAG = "end";
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private CameraManager cameraManager;
    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private Handler handler;
    private TextureView textureView;
    private ViewGroup rootLayout;
    private FrameLayout overlayLayout;
    private TextView labelTextView;
    private Bitmap bitmap;
    private View boxView;
    private ClassiModel model; // Declare the model instance
    private ImageReader imageReader;
    private boolean isCapturingFrames = false;

    private MediaPlayer alarmPlayer;
    private boolean isAlarmPlaying = false;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        labelTextView = new TextView(this);
        boxView = new View(this);

        try {
            model = ClassiModel.newInstance(this);

        } catch (IOException e) {
            e.printStackTrace();
        }

        // Initialize the buttons
        Button btnResume = findViewById(R.id.btnResume);
        Button btnStop = findViewById(R.id.btnStop);
        Button btnBack = findViewById(R.id.btnBack);

        // Set click listeners for the buttons
        btnResume.setOnClickListener(v -> {
            isCapturingFrames = true;
            captureFrame();
        });


        //      btnStop.setOnClickListener(v -> {
        // Remove any existing views
        //          overlayLayout.removeView(labelTextView);
        //          overlayLayout.removeView(boxView);

        // Stop capturing frames
        //         isCapturingFrames = false;
        //     });

        btnStop.setOnClickListener(v -> {
            isCapturingFrames = false;
            // Clear any existing views
            overlayLayout.removeView(labelTextView);
            overlayLayout.removeView(boxView);
        });

        btnBack.setOnClickListener(v -> {
            // Create an Intent to start the SecondActivity
            Intent intent = new Intent(MainActivity2.this, SecondActivity.class);

            // Start the SecondActivity
            startActivity(intent);

            // Finish the current activity (MainActivity2)
            finish();
        });


        // Initialize the MediaPlayer
        alarmPlayer = MediaPlayer.create(this, R.raw.wake); // Replace with the actual resource ID of your alarm sound
        alarmPlayer.setOnCompletionListener(mediaPlayer -> {
            // Release the MediaPlayer resources when the sound is finished playing
            mediaPlayer.release();
            isAlarmPlaying = false;
        });


        textureView = findViewById(R.id.textureView);
        rootLayout = findViewById(android.R.id.content);
        overlayLayout = findViewById(R.id.overlayLayout);

        // Request camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        } else {
            startCamera();
        }
        handler = new Handler();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isCapturingFrames = true;
        //added this below 2 lines if we should start capturing frames
        if (isCapturingFrames){
            startCamera();
        }
        startBackgroundThread();
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }


    @Override
    protected void onDestroy()
   {
        super.onDestroy();
        releaseResources();
    }

    private void releaseResources() {
        // Set the model reference to null
        model = null;
    }

 //   private void releaseResources() {
        // Release the model instance
  //      if (model != null) {
   //         model.close();
    //    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void startCamera() {
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);

        try {
            cameraId = cameraManager.getCameraIdList()[0];
            System.out.println("Camera Id" + cameraId);

            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            imageDimension = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    .getOutputSizes(SurfaceHolder.class)[0];

            // Continue with opening the camera
            openCamera();

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        cameraDevice = camera;
                        createCameraPreview();
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        cameraDevice.close();
                    }

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        cameraDevice.close();
                        Log.e(TAG, "Error accessing camera :(");
                    }
                }, null);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        if (cameraId == null) {
            Log.e(TAG, "No camera available");
        }
    }

    private void createCameraPreview() {
        try {
            int inputWidth = 224;
            int inputHeight = 224;
            // Create the image reader to capture the frame
            ImageReader imageReader1 = ImageReader.newInstance(
                    inputWidth,
                    inputHeight,
                    ImageFormat.JPEG,
                    1
            );
    //        imageReader1.setOnImageAvailableListener(Reader2 -> {
     //           try(Image image =Reader2.acquireLatestImage()){
     //               if(image != null)
     //               {
     //                   if(isCapturingFrames)
     //                   {
    //                        bitmap = imageToBitmap(image);
     //
     //                       runInferenceOnBitmap(bitmap);
     //                   }
     //               }
     //           }
     //       }, backgroundHandler);


            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface surface = new Surface(texture);

            List<Surface> outputSurfaces = new ArrayList<>(2);
            outputSurfaces.add(imageReader1.getSurface());
            outputSurfaces.add(surface);

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);

            cameraDevice.createCaptureSession(outputSurfaces,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (cameraDevice == null) {
                                return;
                            }
                            cameraCaptureSession = session;
                            updatePreview();
                            imageReader = imageReader1;
                            captureFrame();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Toast.makeText(getApplicationContext(), "Configuration change failed",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }, null);


        } catch (CameraAccessException e) {
            e.printStackTrace();
            System.out.println(e.getReason());
        }
    }

    private void updatePreview() {
        if (cameraDevice == null) {
            return;
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void captureFrame() {
        if (!isCapturingFrames) {
            return;
        }

        // Set the image available listener
        imageReader.setOnImageAvailableListener(reader -> {
            System.out.println("Image Read");
            try (Image image = reader.acquireLatestImage()) {
                if (image != null) {
                    // Convert the image to bitmap
                    bitmap = imageToBitmap(image);

                    // Process the bitmap with your model
                    runInferenceOnBitmap(bitmap);
                }
            }
        }, backgroundHandler);


        try {
            // Create the capture request
            final CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(imageReader.getSurface());

            // Configure the capture request
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            // Capture the frame
            cameraCaptureSession.capture(captureRequestBuilder.build(), null, backgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private Bitmap imageToBitmap(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.capacity()];
        buffer.get(bytes);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
    }


    private Pair<String, Float> runInferenceOnBitmap(Bitmap bitmap) {
        try {
            ClassiModel model = ClassiModel.newInstance(this);

            int inputWidth = 224;
            int inputHeight = 224;
            int inputChannels = 3;

            // Normalize and load the image into the input buffer
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, inputWidth, inputHeight, inputChannels}, DataType.FLOAT32);
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * inputWidth * inputHeight * inputChannels);
            byteBuffer.order(ByteOrder.nativeOrder());

            int[] intValues = new int[inputWidth * inputHeight];
            bitmap.getPixels(intValues, 0, inputWidth, 0, 0, inputWidth, inputHeight);
            int pixel = 0;

            // Iterate over each pixel and extract R, G, and B values. Normalize and add those values individually to the byte buffer.
            for (int i = 0; i < inputWidth; i++) {
                for (int j = 0; j < inputHeight; j++) {
                    int val = intValues[pixel++]; // RGB
                    byteBuffer.putFloat(((val >> 16) & 0xFF) / 255.0f);  // Red component
                    byteBuffer.putFloat(((val >> 8) & 0xFF) / 255.0f);   // Green component
                    byteBuffer.putFloat((val & 0xFF) / 255.0f);          // Blue component
                }
            }
            inputFeature0.loadBuffer(byteBuffer);

            // Run model inference and get the result
            ClassiModel.Outputs outputs = model.process(inputFeature0);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();


            float[] confidences = outputFeature0.getFloatArray();


            // Find the index of the class with the highest confidence.
            int maxPos = 0;
            float maxConfidence = 0;
            for (int i = 0; i < confidences.length; i++) {
                if (confidences[i] > maxConfidence) {
                    maxConfidence = confidences[i];
                    maxPos = i;
                }
            }

            String[] classes = {"Driving safe",
                    "Texting on phone / Using phone with right hand",
                    "Listening phone with right hand",
                    "Texting on phone / Using phone with left hand",
                    "Listening phone with left hand",
                    "Operating the radio",
                    "Drinking ",
                    "Reaching behind to grab something",
                    "Makeup / Setting hairs",
                    "Talking with passenger"};

            String predictedLabel = classes[maxPos];

            // Return the predicted label and probability as a Pair
            //  return new Pair<>(predictedLabel, maxConfidence);

            String label = predictedLabel;
            float probability = maxConfidence;
            displayBoundingBox(label, probability);

        } catch (IOException e) {
            // TODO Handle the exception
        }

        // Return a default value if an error occurs
        return new Pair<>("Unknown", 0.0f);


    }

    private void displayBoundingBox(String label, float probability) {
        runOnUiThread(() -> {
            // Remove any existing views
            rootLayout.removeView(labelTextView);
            overlayLayout.removeView(boxView);

            // Create and add the bounding box container view using overlayLayout
            int boxWidth = 1000;
            int boxHeight = 1600;
            int boxLeft = (rootLayout.getWidth() - boxWidth) / 2;
            int boxTop = (rootLayout.getHeight() - boxHeight) / 2;

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(boxWidth, boxHeight);
            params.leftMargin = boxLeft;
            params.topMargin = boxTop;

            // Create and add the red rectangle hollow box container
            FrameLayout boxContainer = new FrameLayout(this);
            boxContainer.setLayoutParams(params);
            overlayLayout.addView(boxContainer);

            // Create and add the red rectangle hollow box
            View boxView = new View(this);
            boxView.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            boxView.setBackgroundResource(R.drawable.rectangle_border);
            boxContainer.addView(boxView);

            // Create and add the label and probability text view inside the box container
            TextView textView = new TextView(this);
            String labelText = label + "\nProbability: " + (probability * 100) + "%";
            textView.setText(labelText);
            textView.setTextColor(Color.RED);
            textView.setGravity(Gravity.TOP | Gravity.START);
            textView.setPadding(10, 10, 10, 10);
            boxContainer.addView(textView);

            // After one second, remove the views from the layout
            handler.postDelayed(() -> {
                overlayLayout.removeView(boxContainer);
            }, 4000);
        });

        handler.postDelayed(this::captureFrame, 4000);
    }

}














/**  MAin code that works with buttons nd probabiliy and box all is fine
public class MainActivity2 extends AppCompatActivity {
    private static final String TAG = "end";
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private CameraManager cameraManager;
    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private Handler handler;
    private SurfaceView surfaceView;
    private ViewGroup rootLayout;
    private FrameLayout overlayLayout;
    private TextView labelTextView;
    private Bitmap bitmap;
    private View boxView;
    private ClassiModel model; // Declare the model instance
    private ImageReader imageReader;
    private boolean isCapturingFrames = false;

    private MediaPlayer alarmPlayer;
    private boolean isAlarmPlaying = false;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        labelTextView = new TextView(this);
        boxView = new View(this);

        try {
            model = ClassiModel.newInstance(this);

        } catch (IOException e) {
            e.printStackTrace();
        }

        // Initialize the buttons
        Button btnResume = findViewById(R.id.btnResume);
        Button btnStop = findViewById(R.id.btnStop);
        Button btnBack = findViewById(R.id.btnBack);

        // Set click listeners for the buttons
        btnResume.setOnClickListener(v -> {
            isCapturingFrames = true;
            captureFrame();
        });


  //      btnStop.setOnClickListener(v -> {
            // Remove any existing views
  //          overlayLayout.removeView(labelTextView);
  //          overlayLayout.removeView(boxView);

            // Stop capturing frames
   //         isCapturingFrames = false;
   //     });

        btnStop.setOnClickListener(v -> {
            isCapturingFrames = false;
            // Clear any existing views
            overlayLayout.removeView(labelTextView);
            overlayLayout.removeView(boxView);
        });

        btnBack.setOnClickListener(v -> {
            // Create an Intent to start the SecondActivity
            Intent intent = new Intent(MainActivity2.this, SecondActivity.class);

            // Start the SecondActivity
            startActivity(intent);

            // Finish the current activity (MainActivity2)
            finish();
        });


        // Initialize the MediaPlayer
        alarmPlayer = MediaPlayer.create(this, R.raw.wake); // Replace with the actual resource ID of your alarm sound
        alarmPlayer.setOnCompletionListener(mediaPlayer -> {
            // Release the MediaPlayer resources when the sound is finished playing
            mediaPlayer.release();
            isAlarmPlaying = false;
        });


        surfaceView = findViewById(R.id.surfaceView);
        rootLayout = findViewById(android.R.id.content);
        overlayLayout = findViewById(R.id.overlayLayout);

        // Request camera permission
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        } else {
            startCamera();
        }
        handler = new Handler();
    }

    @Override
    protected void onResume() {
        super.onResume();

        startBackgroundThread();
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void startCamera() {
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);

        try {
            cameraId = cameraManager.getCameraIdList()[0];
            System.out.println("Camera Id" + cameraId);

            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            imageDimension = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    .getOutputSizes(SurfaceHolder.class)[0];

            // Continue with opening the camera
            openCamera();

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        cameraDevice = camera;
                        createCameraPreview();
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        cameraDevice.close();
                    }

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        cameraDevice.close();
                    }
                }, null);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        if (cameraId == null) {
            Log.e(TAG, "No camera available");
        }
    }

    private void createCameraPreview() {
        try {
            int inputWidth = 224;
            int inputHeight = 224;
            // Create the image reader to capture the frame
            ImageReader imageReader1 = ImageReader.newInstance(
                    inputWidth,
                    inputHeight,
                    ImageFormat.JPEG,
                    1
            );

            SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.setFixedSize(inputWidth, inputHeight);

            List<Surface> outputSurfaces = new ArrayList<>(2);
            outputSurfaces.add(imageReader1.getSurface());
            outputSurfaces.add(surfaceHolder.getSurface());

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surfaceHolder.getSurface());

            cameraDevice.createCaptureSession(outputSurfaces,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (cameraDevice == null) {
                                return;
                            }
                            cameraCaptureSession = session;
                            updatePreview();
                            imageReader = imageReader1;
                            captureFrame();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Toast.makeText(getApplicationContext(), "Configuration change failed",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }, null);


        } catch (CameraAccessException e) {
            e.printStackTrace();
            System.out.println(e.getReason());
        }
    }

    private void updatePreview() {
        if (cameraDevice == null) {
            return;
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void captureFrame() {
        if (!isCapturingFrames) {
            return;
        }

        // Set the image available listener
        imageReader.setOnImageAvailableListener(reader -> {
            System.out.println("Image Read");
            try (Image image = reader.acquireLatestImage()) {
                if (image != null) {
                    // Convert the image to bitmap
                    bitmap = imageToBitmap(image);

                    // Process the bitmap with your model
                    runInferenceOnBitmap(bitmap);
                }
            }
        }, backgroundHandler);


        try {
            // Create the capture request
            final CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(imageReader.getSurface());

            // Configure the capture request
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            // Capture the frame
            cameraCaptureSession.capture(captureRequestBuilder.build(), null, backgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private Bitmap imageToBitmap(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.capacity()];
        buffer.get(bytes);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
    }


    private Pair<String, Float> runInferenceOnBitmap(Bitmap bitmap) {
        try {
            ClassiModel model = ClassiModel.newInstance(this);

            int inputWidth = 224;
            int inputHeight = 224;
            int inputChannels = 3;

            // Normalize and load the image into the input buffer
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, inputWidth, inputHeight, inputChannels}, DataType.FLOAT32);
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * inputWidth * inputHeight * inputChannels);
            byteBuffer.order(ByteOrder.nativeOrder());

            int[] intValues = new int[inputWidth * inputHeight];
            bitmap.getPixels(intValues, 0, inputWidth, 0, 0, inputWidth, inputHeight);
            int pixel = 0;

            // Iterate over each pixel and extract R, G, and B values. Normalize and add those values individually to the byte buffer.
            for (int i = 0; i < inputWidth; i++) {
                for (int j = 0; j < inputHeight; j++) {
                    int val = intValues[pixel++]; // RGB
                    byteBuffer.putFloat(((val >> 16) & 0xFF) / 255.0f);  // Red component
                    byteBuffer.putFloat(((val >> 8) & 0xFF) / 255.0f);   // Green component
                    byteBuffer.putFloat((val & 0xFF) / 255.0f);          // Blue component
                }
            }
            inputFeature0.loadBuffer(byteBuffer);

            // Run model inference and get the result
            ClassiModel.Outputs outputs = model.process(inputFeature0);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();


            float[] confidences = outputFeature0.getFloatArray();


            // Find the index of the class with the highest confidence.
            int maxPos = 0;
            float maxConfidence = 0;
            for (int i = 0; i < confidences.length; i++) {
                if (confidences[i] > maxConfidence) {
                    maxConfidence = confidences[i];
                    maxPos = i;
                }
            }

            String[] classes = {"Driving safe",
                    "Texting on phone / Using phone with right hand",
                    "Listening phone with right hand",
                    "Texting on phone / Using phone with left hand",
                    "Listening phone with left hand",
                    "Operating the radio",
                    "Drinking ",
                    "Reaching behind to grab something",
                    "Makeup / Setting hairs",
                    "Talking with passenger"};

            String predictedLabel = classes[maxPos];

            // Return the predicted label and probability as a Pair
          //  return new Pair<>(predictedLabel, maxConfidence);

            String label = predictedLabel;
            float probability = maxConfidence;
            displayBoundingBox(label, probability);

        } catch (IOException e) {
            // TODO Handle the exception
        }

        // Return a default value if an error occurs
        return new Pair<>("Unknown", 0.0f);
    }


    private void displayBoundingBox(String label, float probability) {
        runOnUiThread(() -> {
            // Remove any existing views
            rootLayout.removeView(labelTextView);
            overlayLayout.removeView(boxView);

            // Create and add the bounding box container view using overlayLayout
            int boxWidth = 1000;
            int boxHeight = 1600;
            int boxLeft = (rootLayout.getWidth() - boxWidth) / 2;
            int boxTop = (rootLayout.getHeight() - boxHeight) / 2;

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(boxWidth, boxHeight);
            params.leftMargin = boxLeft;
            params.topMargin = boxTop;

            // Create and add the red rectangle hollow box container
            FrameLayout boxContainer = new FrameLayout(this);
            boxContainer.setLayoutParams(params);
            overlayLayout.addView(boxContainer);

            // Create and add the red rectangle hollow box
            View boxView = new View(this);
            boxView.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            boxView.setBackgroundResource(R.drawable.rectangle_border);
            boxContainer.addView(boxView);

            // Create and add the label and probability text view inside the box container
            TextView textView = new TextView(this);
            String labelText = label + "\nProbability: " + (probability * 100) + "%";
            textView.setText(labelText);
            textView.setTextColor(Color.RED);
            textView.setGravity(Gravity.TOP | Gravity.START);
            textView.setPadding(10, 10, 10, 10);
            boxContainer.addView(textView);

            // After one second, remove the views from the layout
            handler.postDelayed(() -> {
                overlayLayout.removeView(boxContainer);
            }, 4000);
        });

        handler.postDelayed(this::captureFrame, 4000);
    }

}
**/






/** Main code for backup where probability is shown but no sound after detection

public class MainActivity2 extends AppCompatActivity {
    private static final String TAG = "end";
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private CameraManager cameraManager;
    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private Handler handler;
    private SurfaceView surfaceView;
    private ViewGroup rootLayout;
    private FrameLayout overlayLayout;
    private TextView labelTextView;
    private Bitmap bitmap;
    private View boxView;
    private ClassiModel model; // Declare the model instance
    private ImageReader imageReader;


    private MediaPlayer alarmPlayer;
    private boolean isAlarmPlaying = false;

    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);

        labelTextView = new TextView(this);
        boxView = new View(this);

        try {
            model = ClassiModel.newInstance(this);

        } catch (IOException e) {
            e.printStackTrace();
        }


        // Initialize the MediaPlayer
        alarmPlayer = MediaPlayer.create(this, R.raw.wake); // Replace with the actual resource ID of your alarm sound
        alarmPlayer.setOnCompletionListener(mediaPlayer -> {
            // Release the MediaPlayer resources when the sound is finished playing
            mediaPlayer.release();
            isAlarmPlaying = false;
        });


        surfaceView = findViewById(R.id.surfaceView);
        rootLayout = findViewById(android.R.id.content);
        overlayLayout = findViewById(R.id.overlayLayout);

        // Request camera permission
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        } else {
            startCamera();
        }
        handler = new Handler();
    }

    @Override
    protected void onResume() {
        super.onResume();

        startBackgroundThread();
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void startCamera() {
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);

        try {
            cameraId = cameraManager.getCameraIdList()[0];
            System.out.println("Camera Id" + cameraId);

            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            imageDimension = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    .getOutputSizes(SurfaceHolder.class)[0];

            // Continue with opening the camera
            openCamera();

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(@NonNull CameraDevice camera) {
                        cameraDevice = camera;
                        createCameraPreview();
                    }

                    @Override
                    public void onDisconnected(@NonNull CameraDevice camera) {
                        cameraDevice.close();
                    }

                    @Override
                    public void onError(@NonNull CameraDevice camera, int error) {
                        cameraDevice.close();
                    }
                }, null);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        if (cameraId == null) {
            Log.e(TAG, "No camera available");
        }
    }

    private void createCameraPreview() {
        try {
            int inputWidth = 224;
            int inputHeight = 224;
            // Create the image reader to capture the frame
            ImageReader imageReader1 = ImageReader.newInstance(
                    inputWidth,
                    inputHeight,
                    ImageFormat.JPEG,
                    1
            );

            SurfaceHolder surfaceHolder = surfaceView.getHolder();
            surfaceHolder.setFixedSize(inputWidth, inputHeight);

            List<Surface> outputSurfaces = new ArrayList<>(2);
            outputSurfaces.add(imageReader1.getSurface());
            outputSurfaces.add(surfaceHolder.getSurface());

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surfaceHolder.getSurface());

            cameraDevice.createCaptureSession(outputSurfaces,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            if (cameraDevice == null) {
                                return;
                            }
                            cameraCaptureSession = session;
                            updatePreview();
                            imageReader = imageReader1;
                            captureFrame();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Toast.makeText(getApplicationContext(), "Configuration change failed",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }, null);


        } catch (CameraAccessException e) {
            e.printStackTrace();
            System.out.println(e.getReason());
        }
    }

    private void updatePreview() {
        if (cameraDevice == null) {
            return;
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (cameraCaptureSession != null) {
            cameraCaptureSession.close();
            cameraCaptureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void captureFrame() {
        // Check if the camera device is available
        if (cameraDevice == null) {
            Log.e(TAG, "Camera device is not available");
            return;
        }

        // Set the image available listener
        imageReader.setOnImageAvailableListener(reader -> {
            System.out.println("Image Read");
            try (Image image = reader.acquireLatestImage()) {
                if (image != null) {
                    // Convert the image to bitmap
                    bitmap = imageToBitmap(image);


                    // Save Image
                    // Create acquireLatestImagen image file name
//                    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
//                            .format(System.currentTimeMillis());
//
//                    String root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString()+ "/Camera/Your_Directory_Name";
//                    File myDir = new File(root);
//                    myDir.mkdirs();
//                    String fname = "Image-" + timeStamp + ".jpeg";
//                    File file = new File(myDir, fname);
//                    System.out.println(file.getAbsolutePath());
//
//                    if (file.exists()) file.delete();
//                    Log.i("LOAD", root + fname);
//                    try {
//                        FileOutputStream out = new FileOutputStream(file);
//                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
//                        out.flush();
//                        out.close();
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//
//                    MediaScannerConnection.scanFile(end.this, new String[]{file.getPath()}, new String[]{"image/jpeg"}, null);

                    // Process the bitmap with your model
                    runInferenceOnBitmap(bitmap);
                }
            }
        }, backgroundHandler);


        try {
            // Create the capture request
            final CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(imageReader.getSurface());

            // Configure the capture request
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            // Capture the frame
            cameraCaptureSession.capture(captureRequestBuilder.build(), null, backgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private Bitmap imageToBitmap(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.capacity()];
        buffer.get(bytes);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
    }


    private Pair<String, Float> runInferenceOnBitmap(Bitmap bitmap) {
        try {
            ClassiModel model = ClassiModel.newInstance(this);

            int inputWidth = 224;
            int inputHeight = 224;
            int inputChannels = 3;

            // Normalize and load the image into the input buffer
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, inputWidth, inputHeight, inputChannels}, DataType.FLOAT32);
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * inputWidth * inputHeight * inputChannels);
            byteBuffer.order(ByteOrder.nativeOrder());

            int[] intValues = new int[inputWidth * inputHeight];
            bitmap.getPixels(intValues, 0, inputWidth, 0, 0, inputWidth, inputHeight);
            int pixel = 0;

            // Iterate over each pixel and extract R, G, and B values. Normalize and add those values individually to the byte buffer.
            for (int i = 0; i < inputWidth; i++) {
                for (int j = 0; j < inputHeight; j++) {
                    int val = intValues[pixel++]; // RGB
                    byteBuffer.putFloat(((val >> 16) & 0xFF) / 255.0f);  // Red component
                    byteBuffer.putFloat(((val >> 8) & 0xFF) / 255.0f);   // Green component
                    byteBuffer.putFloat((val & 0xFF) / 255.0f);          // Blue component
                }
            }
            inputFeature0.loadBuffer(byteBuffer);

            // Run model inference and get the result
            ClassiModel.Outputs outputs = model.process(inputFeature0);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();


            float[] confidences = outputFeature0.getFloatArray();


            // Find the index of the class with the highest confidence.
            int maxPos = 0;
            float maxConfidence = 0;
            for (int i = 0; i < confidences.length; i++) {
                if (confidences[i] > maxConfidence) {
                    maxConfidence = confidences[i];
                    maxPos = i;
                }
            }

            String[] classes = {"Driving safe",
                    "Texting on phone / Using phone with right hand",
                    "Listening phone with right hand",
                    "Texting on phone / Using phone with left hand",
                    "Listening phone with left hand",
                    "Operating the radio",
                    "Drinking ",
                    "Reaching behind to grab something",
                    "Makeup / Setting hairs",
                    "Talking with passenger"};

            String predictedLabel = classes[maxPos];

            // Return the predicted label and probability as a Pair
            //  return new Pair<>(predictedLabel, maxConfidence);

            String label = predictedLabel;
            float probability = maxConfidence;
            displayBoundingBox(label, probability);

        } catch (IOException e) {
            // TODO Handle the exception
        }

        // Return a default value if an error occurs
        return new Pair<>("Unknown", 0.0f);
    }



    /**
     // Find the index of the class with the highest confidence.
     int maxPos = 0;
     float maxConfidence = 0;
     for (int i = 0; i < confidences.length; i++) {
     if (confidences[i] > maxConfidence) {
     maxConfidence = confidences[i];
     maxPos = i;
     }
     }

     String[] classes = {"Driving safe",
     "Texting on phone / Using phone with right hand",
     "Listening phone with right hand",
     "Texting on phone / Using phone with left hand",
     "Listening phone with left hand",
     "Operating the radio",
     "Drinking ",
     "Reaching behind to grab something",
     "Makeup / Setting hairs",
     "Talking with passenger"};

     // displayBoundingBox(classes[maxPos]);

     // Replace this line in your code where you call displayBoundingBox:
     String label = classes[maxPos];
     displayBoundingBox(label, probability);

     // Release model resources if no longer used.
     model.close();
     } catch (IOException e) {
     // TODO Handle the exception
     }
     }**/


/**
    private void displayBoundingBox(String label, float probability) {
        runOnUiThread(() -> {
            // Remove any existing views
            rootLayout.removeView(labelTextView);
            overlayLayout.removeView(boxView);

            // Create and add the bounding box container view using overlayLayout
            int boxWidth = 1000;
            int boxHeight = 1900;
            int boxLeft = (rootLayout.getWidth() - boxWidth) / 2;
            int boxTop = (rootLayout.getHeight() - boxHeight) / 2;

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(boxWidth, boxHeight);
            params.leftMargin = boxLeft;
            params.topMargin = boxTop;

            // Create and add the red rectangle hollow box container
            FrameLayout boxContainer = new FrameLayout(this);
            boxContainer.setLayoutParams(params);
            overlayLayout.addView(boxContainer);

            // Create and add the red rectangle hollow box
            View boxView = new View(this);
            boxView.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            boxView.setBackgroundResource(R.drawable.rectangle_border);
            boxContainer.addView(boxView);

            // Create and add the label and probability text view inside the box container
            TextView textView = new TextView(this);
            String labelText = label + "\nProbability: " + (probability * 100) + "%";
            textView.setText(labelText);
            textView.setTextColor(Color.RED);
            textView.setGravity(Gravity.TOP | Gravity.START);
            textView.setPadding(10, 10, 10, 10);
            boxContainer.addView(textView);

            // After one second, remove the views from the layout
            handler.postDelayed(() -> {
                overlayLayout.removeView(boxContainer);
            }, 4000);
        });

        handler.postDelayed(this::captureFrame, 4000);
    }

}

**/




















/**
 * alarm rang for 3 seconds



 // Remove any existing views
 rootLayout.removeView(labelTextView);
 overlayLayout.removeView(boxView);

 // Create and add the bounding box container view using overlayLayout
 int boxWidth = 1000;
 int boxHeight = 1900;
 int boxLeft = (rootLayout.getWidth() - boxWidth) / 2;
 int boxTop = (rootLayout.getHeight() - boxHeight) / 2;

 FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(boxWidth, boxHeight);
 params.leftMargin = boxLeft;
 params.topMargin = boxTop;

            // Create and add the red rectangle hollow box container
            FrameLayout boxContainer = new FrameLayout(this);
            boxContainer.setLayoutParams(params);
            overlayLayout.addView(boxContainer);

            // Create and add the red rectangle hollow box
            View boxView = new View(this);
            boxView.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            boxView.setBackgroundResource(R.drawable.rectangle_border);
            boxContainer.addView(boxView);

            // Create and add the label text view inside the box container
            TextView labelTextView = new TextView(this);
            labelTextView.setText(label);
            labelTextView.setTextColor(Color.RED);
            labelTextView.setGravity(Gravity.TOP | Gravity.START);
            labelTextView.setPadding(10, 10, 10, 10);
            boxContainer.addView(labelTextView);

            // If a non-safe driving activity is detected, play the alarm sound
            if (!label.equals("Driving safe") && !isAlarmPlaying) {
                alarmPlayer.start(); // Start playing the alarm sound
                isAlarmPlaying = true; // Set the flag to indicate that the alarm is playing
            }

            // After one second, remove the views from the layout
            handler.postDelayed(() -> {
                overlayLayout.removeView(boxContainer);

                // Stop the alarm sound after 3 seconds
                if (isAlarmPlaying) {
                    handler.postDelayed(() -> {
                        alarmPlayer.pause(); // Pause the alarm sound
                        isAlarmPlaying = false; // Reset the flag
                    }, 3000); // Stop the alarm after 3 seconds
                }
            }, 4000);
        });

        handler.postDelayed(this::captureFrame, 4000);
    }
    /**




 /**
          // Create and add the red rectangle hollow box container
            FrameLayout boxContainer = new FrameLayout(this);
            boxContainer.setLayoutParams(params);
            overlayLayout.addView(boxContainer);

            // Create and add the red rectangle hollow box
            View boxView = new View(this);
            boxView.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            boxView.setBackgroundResource(R.drawable.rectangle_border);
            boxContainer.addView(boxView);

            // Create and add the label text view inside the box container
            TextView labelTextView = new TextView(this);
            labelTextView.setText(label);
            labelTextView.setTextColor(Color.RED);
            labelTextView.setGravity(Gravity.TOP | Gravity.START);
            labelTextView.setPadding(10, 10, 10, 10);
            boxContainer.addView(labelTextView);

            // If a non-safe driving activity is detected, play the alarm sound
            if (!label.equals("Driving safe")) {
                if (!isAlarmPlaying) {
                    alarmPlayer.start(); // Start playing the alarm sound
                    isAlarmPlaying = true; // Set the flag to indicate that the alarm is playing
                }
            }

            // After one second, remove the views from the layout
            handler.postDelayed(() -> {
                overlayLayout.removeView(boxContainer);
            }, 4000);
        });

        handler.postDelayed(this::captureFrame, 4000);
    }

**/


    /**
     *
     * Real code of only nounding box
    private void displayBoundingBox(String label) {
        runOnUiThread(() -> {
            // Remove any existing views
            rootLayout.removeView(labelTextView);
            overlayLayout.removeView(boxView);

            // Create and add the bounding box container view using overlayLayout
            int boxWidth = 1000;
            int boxHeight = 1900;
            int boxLeft = (rootLayout.getWidth() - boxWidth) / 2;
            int boxTop = (rootLayout.getHeight() - boxHeight) / 2;

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(boxWidth, boxHeight);
            params.leftMargin = boxLeft;
            params.topMargin = boxTop;

            // Create and add the red rectangle hollow box container
            FrameLayout boxContainer = new FrameLayout(this);
            boxContainer.setLayoutParams(params);
            overlayLayout.addView(boxContainer);

            // Create and add the red rectangle hollow box
            View boxView = new View(this);
            boxView.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            boxView.setBackgroundResource(R.drawable.rectangle_border);
            boxContainer.addView(boxView);

            // Create and add the label text view inside the box container
            TextView labelTextView = new TextView(this);
            labelTextView.setText(label);
            labelTextView.setTextColor(Color.RED);
            labelTextView.setGravity(Gravity.TOP | Gravity.START);
            labelTextView.setPadding(10, 10, 10, 10);
            boxContainer.addView(labelTextView);

            // After one second, remove the views from the layout
            handler.postDelayed(() -> {
                overlayLayout.removeView(boxContainer);
            }, 4000);
        });

        handler.postDelayed(this::captureFrame, 4000);
    }
     **/
