package com.example.copy;

import androidx.annotation.Nullable;
import org.tensorflow.lite.support.image.TensorImage;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.copy.ml.ClassiModel;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class predictimage extends AppCompatActivity {

    Button camera, gallery;
    ImageView imageView;
    TextView result;
    //   TextView confidence;
    int imageSize = 224;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_predictimage);

        getPermission();

        camera = findViewById(R.id.button);
        //   confidence = findViewById(R.id.confidencesText);
        gallery = findViewById(R.id.button2);
        result = findViewById(R.id.result);
        imageView = findViewById(R.id.imageView);


        camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(intent, 3);
            }
        });
        gallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent cameraIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(cameraIntent, 1);
            }
        });
    }

    public void classifyImage(Bitmap image) {

        try {
            ClassiModel model = ClassiModel.newInstance(this);

            int inputWidth = 224;
            int inputHeight = 224;
            int inputChannels = 3;

            // Resize the image to match the model's input shape
     //       Bitmap resizedImage = Bitmap.createScaledBitmap(image, inputWidth, inputHeight, true);

            // Normalize and load the image into the input buffer
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, inputWidth, inputHeight, inputChannels}, DataType.FLOAT32);
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * inputWidth * inputHeight * inputChannels);
            byteBuffer.order(ByteOrder.nativeOrder());

            int[] intValues = new int[inputWidth * inputHeight];
            image.getPixels(intValues, 0, inputWidth, 0, 0, inputWidth, inputHeight);
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
                    "Drinking or Eating",
                    "Reaching behind to grab something",
                    "Makeup / Setting hairs",
                    "Talking with passenger"};

            result.setText(classes[maxPos]);

            //   String s = "";
            //   for(int i=0; i < classes.length; i++) {
            //       s += String.format("%s: %.1f%%\n", classes[i], confidences[i] * 100);
            //   }
            //   confidence.setText(s);

            // Release model resources if no longer used.
            model.close();
        } catch (IOException e) {
            // TODO Handle the exception
        }
    }

    void getPermission ()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(predictimage.this, new String[]{android.Manifest.permission.CAMERA}, 11);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult ( int requestCode, @Nullable String[] permissions,
                                             @Nullable int[] grantResults)
    {
        if (requestCode == 11) {
            if (grantResults.length > 0) {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    this.getPermission();
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        if(resultCode == RESULT_OK){
            if(requestCode == 3){      // Check if the request code corresponds to camera capture
                Bitmap image = (Bitmap) data.getExtras().get("data");
                int dimension = Math.min(image.getWidth(), image.getHeight());
                image = ThumbnailUtils.extractThumbnail(image, dimension, dimension);
                imageView.setImageBitmap(image);    // Display the captured image on the ImageView

                image = Bitmap.createScaledBitmap(image, imageSize, imageSize, false);
                classifyImage(image);
            }else {    // if (requestCode == 1) {  // Check if the request code corresponds to gallery selection{
                Uri dat = data.getData();
                Bitmap image = null;
                try {
                    image = MediaStore.Images.Media.getBitmap(this.getContentResolver(), dat);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                imageView.setImageBitmap(image);  // Display the selected image on the ImageView

                image = Bitmap.createScaledBitmap(image, imageSize, imageSize, false);
                classifyImage(image);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
}


/**
 * import os
 * from keras.preprocessing.image import ImageDataGenerator
 *
 * # Set the path to your original dataset
 * original_dataset_path = '/path/to/original/dataset/'
 *
 * # Set the path to store the augmented images
 * augmented_dataset_path = '/path/to/augmented/dataset/'
 *
 * # Define the augmentation parameters
 * datagen = ImageDataGenerator(
 *     rotation_range=20,
 *     width_shift_range=0.2,
 *     height_shift_range=0.2,
 *     shear_range=0.2,
 *     zoom_range=0.2,
 *     horizontal_flip=True,
 *     fill_mode='nearest'
 * )
 *
 * # Iterate through the class folders
 * for class_folder in os.listdir(original_dataset_path):
 *     class_path = os.path.join(original_dataset_path, class_folder)
 *
 *     # Create a corresponding folder in the augmented dataset path
 *     augmented_class_path = os.path.join(augmented_dataset_path, class_folder)
 *     os.makedirs(augmented_class_path, exist_ok=True)
 *
 *     # Iterate through the images in the class folder
 *     for image_file in os.listdir(class_path):
 *         image_path = os.path.join(class_path, image_file)
 *
 *         # Load the image
 *         img = load_image(image_path)
 *         img = img.reshape((1,) + img.shape)  # Reshape to (1, height, width, channels)
 *
 *         # Generate augmented images
 *         i = 0
 *         for batch in datagen.flow(img, batch_size=1, save_to_dir=augmented_class_path, save_prefix='aug', save_format='jpg'):
 *             i += 1
 *             if i >= 5:  # Generate 5 augmented images per original image
 *                 break
 */


/**
 * import tensorflow as tf
 *
 * # Set the path to your original dataset
 * original_dataset_path = '/path/to/original/dataset/'
 *
 * # Set the path to store the augmented images
 * augmented_dataset_path = '/path/to/augmented/dataset/'
 *
 * # Define the augmentation parameters
 * rotation_range = 20
 * width_shift_range = 0.2
 * height_shift_range = 0.2
 * shear_range = 0.2
 * zoom_range = 0.2
 * horizontal_flip = True
 *
 * # Create an ImageDataGenerator instance for augmentation
 * data_generator = tf.keras.preprocessing.image.ImageDataGenerator(
 *     rotation_range=rotation_range,
 *     width_shift_range=width_shift_range,
 *     height_shift_range=height_shift_range,
 *     shear_range=shear_range,
 *     zoom_range=zoom_range,
 *     horizontal_flip=horizontal_flip,
 *     fill_mode='nearest'
 * )
 *
 * # Load the original dataset using tf.data.Dataset.from_directory()
 * original_dataset = tf.keras.preprocessing.image_dataset_from_directory(
 *     original_dataset_path,
 *     image_size=(224, 224),
 *     batch_size=1,
 *     shuffle=False
 * )
 *
 * # Create a new dataset for augmented images
 * augmented_dataset = tf.data.Dataset.from_generator(
 *     lambda: data_generator.flow(
 *         original_dataset,
 *         batch_size=1,
 *         save_to_dir=augmented_dataset_path,
 *         save_prefix='aug',
 *         save_format='jpg'
 *     ),
 *     output_signature=tf.TensorSpec(shape=(1, 224, 224, 3), dtype=tf.float32)
 * )
 *
 * # Iterate through the augmented dataset to generate and save the augmented images
 * for _ in augmented_dataset:
 *     pass
 */



/**
 * import tensorflow as tf
 *
 * # Set the path to your original dataset
 * original_dataset_path = '/path/to/original/dataset/'
 *
 * # Set the path to store the augmented images
 * augmented_dataset_path = '/path/to/augmented/dataset/'
 *
 * # Define the augmentation parameters
 * flip_left_right = True
 * greyscale = True
 * saturation_range = (0.5, 1.5)
 * brightness_range = (-0.2, 0.2)
 * contrast_range = (0.8, 1.2)
 * hue_range = (-0.1, 0.1)
 * crop_size = (150, 150)
 *
 * # Create a list of available augmentation functions
 * available_augmentations = [
 *     lambda image: tf.image.flip_left_right(image) if flip_left_right else image,
 *     lambda image: tf.image.rgb_to_grayscale(image) if greyscale else image,
 *     lambda image: tf.image.random_saturation(image, *saturation_range),
 *     lambda image: tf.image.random_brightness(image, *brightness_range),
 *     lambda image: tf.image.random_contrast(image, *contrast_range),
 *     lambda image: tf.image.random_hue(image, *hue_range),
 *     lambda image: tf.image.central_crop(image, central_fraction=crop_size[0] / crop_size[1])
 * ]
 *
 * # Load the original dataset using tf.data.Dataset.from_directory()
 * original_dataset = tf.keras.preprocessing.image_dataset_from_directory(
 *     original_dataset_path,
 *     image_size=(224, 224),
 *     batch_size=1,
 *     shuffle=False
 * )
 *
 * # Apply data augmentation and save the augmented images
 * for category_images, category_labels in original_dataset:
 *     for image, label in zip(category_images, category_labels):
 *         augmented_image = image
 *         for augmentation_func in available_augmentations:
 *             augmented_image = augmentation_func(augmented_image)
 *
 *         # Convert the augmented image to uint8 format
 *         augmented_image = tf.cast(augmented_image, tf.uint8)
 *
 *         # Save the augmented image to the specified directory
 *         filename = f"{augmented_dataset_path}/category{label}_{tf.random.uniform([], 0, 1000000, dtype=tf.int64)}.jpg"
 *         tf.keras.preprocessing.image.save_img(filename, augmented_image)
 */