package com.example.image_incriptio_280623

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.InputStream

class MainActivity : AppCompatActivity() {

    private val PICK_IMAGE_REQUEST = 1
    private val STORAGE_PERMISSION_CODE = 2

    private lateinit var imageView: ImageView
    private lateinit var encryptedImageView: ImageView
    private lateinit var encryptButton: Button
    private lateinit var decryptButton: Button
    private lateinit var saveButton: Button
    private lateinit var securityKeyEditText: EditText

    private var selectedImage: Bitmap? = null
    private var encryptedImage: Bitmap? = null
    private var securityKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        encryptedImageView = findViewById(R.id.encryptedImageView)
        encryptButton = findViewById(R.id.encryptButton)
        decryptButton = findViewById(R.id.decryptButton)
        saveButton = findViewById(R.id.saveButton)
        securityKeyEditText = findViewById(R.id.securityKeyEditText)

        encryptButton.setOnClickListener {
            securityKey = securityKeyEditText.text.toString()
            pickImageFromGallery(PICK_IMAGE_REQUEST)
        }

        decryptButton.setOnClickListener {
            securityKey = securityKeyEditText.text.toString()
            pickImageFromGallery(PICK_IMAGE_REQUEST + 1)
        }

        saveButton.setOnClickListener {
            if (encryptedImage != null) {
                if (checkPermission()) {
                    saveImageToGallery(encryptedImage)
                } else {
                    requestPermission()
                }
            }
        }
    }

    private fun pickImageFromGallery(requestCode: Int) {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, requestCode)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            val imageUri = data?.data
            imageUri?.let {
                val inputStream: InputStream? = contentResolver.openInputStream(it)
                selectedImage = BitmapFactory.decodeStream(inputStream)
                imageView.setImageBitmap(selectedImage)

                if (requestCode == PICK_IMAGE_REQUEST) {
                    encryptedImage = encryptImage(selectedImage)
                    encryptedImageView.setImageBitmap(encryptedImage)
                } else {
                    val decryptedImage = decryptImage(selectedImage)
                    encryptedImageView.setImageBitmap(decryptedImage)
                }
            }
        }
    }

    private fun encryptImage(image: Bitmap?): Bitmap? {
        if (image == null || securityKey.isNullOrEmpty()) {
            return null
        }

        val key = securityKey!!
        val resizedImage = resizeImage(image, image.width, image.height)
        val encryptedBitmap = Bitmap.createBitmap(resizedImage.width, resizedImage.height, Bitmap.Config.ARGB_8888)

        val encryptedPixels = IntArray(resizedImage.width * resizedImage.height)
        val pixels = IntArray(resizedImage.width * resizedImage.height)
        resizedImage.getPixels(pixels, 0, resizedImage.width, 0, 0, resizedImage.width, resizedImage.height)

        for (i in pixels.indices) {
            encryptedPixels[i] = pixels[i] xor key.hashCode()
        }

        encryptedBitmap.setPixels(encryptedPixels, 0, resizedImage.width, 0, 0, resizedImage.width, resizedImage.height)
        return encryptedBitmap
    }

    private fun decryptImage(image: Bitmap?): Bitmap? {
        if (image == null || securityKey.isNullOrEmpty()) {
            return null
        }

        val key = securityKey!!
        val resizedImage = resizeImage(image, image.width, image.height)
        val decryptedBitmap = Bitmap.createBitmap(resizedImage.width, resizedImage.height, Bitmap.Config.ARGB_8888)

        val decryptedPixels = IntArray(resizedImage.width * resizedImage.height)
        val pixels = IntArray(resizedImage.width * resizedImage.height)
        resizedImage.getPixels(pixels, 0, resizedImage.width, 0, 0, resizedImage.width, resizedImage.height)

        for (i in pixels.indices) {
            decryptedPixels[i] = pixels[i] xor key.hashCode()
        }

        decryptedBitmap.setPixels(decryptedPixels, 0, resizedImage.width, 0, 0, resizedImage.width, resizedImage.height)
        return decryptedBitmap
    }

    private fun resizeImage(bitmap: Bitmap?, width: Int, height: Int): Bitmap {
        return bitmap?.let {
            Bitmap.createScaledBitmap(it, width, height, true)
        } ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    private fun checkPermission(): Boolean {
        val result = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
            STORAGE_PERMISSION_CODE
        )
    }

    private fun saveImageToGallery(bitmap: Bitmap?) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "EncryptedImage_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        if (uri != null) {
            val outputStream = resolver.openOutputStream(uri)
            outputStream?.use {
                bitmap?.compress(Bitmap.CompressFormat.JPEG, 100, it)
                it.flush()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            // Show a toast message indicating that the image is saved
            Toast.makeText(this, "Image saved to gallery", Toast.LENGTH_SHORT).show()
        } else {
            // Show a toast message if there's an error saving the image
            Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveImageToGallery(encryptedImage)
            } else {
                Toast.makeText(
                    this,
                    "Permission denied. Cannot save image to gallery.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}

