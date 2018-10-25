// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.codelab.mlkit

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Environment
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.util.Pair
import android.view.View
import android.widget.*

import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.document.FirebaseVisionDocumentText
import com.google.firebase.ml.vision.text.FirebaseVisionText
import java.io.File

import java.io.IOException
import java.io.InputStream

class MainActivity : AppCompatActivity(), AdapterView.OnItemSelectedListener {
    private var mImageView: ImageView? = null
    private var mButton: Button? = null
    private var mCloudButton: Button? = null
    private var hideOverlayButton: Button? = null
    private var mSelectedImage: Bitmap? = null
    private var mGraphicOverlay: GraphicOverlay? = null
    private var dropdown: Spinner? = null
    private var result: TextView? = null
    // Max width (portrait mode)
    private var mImageMaxWidth: Int? = null
    // Max height (portrait mode)
    private var mImageMaxHeight: Int? = null

    private val PERMISSIONS_REQUEST_CODE = 555
    private var hasImagesInDirectory = false
    private var dropDownItem = ArrayList<String>()
    private var fileList: ArrayList<File> = ArrayList()


    // Functions for loading images from app assets.

    // Returns max image width, always for portrait mode. Caller needs to swap width / height for
    // landscape mode.
    private// Calculate the max width in portrait mode. This is done lazily since we need to
    // wait for
    // a UI layout pass to get the right values. So delay it to first time image
    // rendering time.
    val imageMaxWidth: Int?
        get() {
            if (mImageMaxWidth == null) {
                mImageMaxWidth = mImageView!!.width
            }

            return mImageMaxWidth
        }

    // Returns max image height, always for portrait mode. Caller needs to swap width / height for
    // landscape mode.
    private// Calculate the max width in portrait mode. This is done lazily since we need to
    // wait for
    // a UI layout pass to get the right values. So delay it to first time image
    // rendering time.
    val imageMaxHeight: Int?
        get() {
            if (mImageMaxHeight == null) {
                mImageMaxHeight = mImageView!!.height
            }

            return mImageMaxHeight
        }

    // Gets the targeted width / height.
    private val targetedWidthHeight: Pair<Int, Int>
        get() {
            val targetWidth: Int
            val targetHeight: Int
            val maxWidthForPortraitMode = imageMaxWidth!!
            val maxHeightForPortraitMode = imageMaxHeight!!
            targetWidth = maxWidthForPortraitMode
            targetHeight = maxHeightForPortraitMode
            return Pair(targetWidth, targetHeight)
        }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mImageView = findViewById(R.id.image_view)
        mButton = findViewById(R.id.button_text)
        mCloudButton = findViewById(R.id.button_cloud_text)
        hideOverlayButton = findViewById(R.id.button_hide_overlay)
        mGraphicOverlay = findViewById(R.id.graphic_overlay)
        dropdown = findViewById(R.id.spinner)
        result = findViewById(R.id.tv_result)

        mButton!!.setOnClickListener {
            hideOverlayButton!!.visibility = View.INVISIBLE
            hideOverlayButton!!.text = "Hide Overlay"
            runTextRecognition()
        }
        mCloudButton!!.setOnClickListener {
            hideOverlayButton!!.visibility = View.INVISIBLE
            hideOverlayButton!!.text = "Hide Overlay"
            runCloudTextRecognition()
        }
        hideOverlayButton!!.setOnClickListener {
            if (mGraphicOverlay!!.visibility == View.VISIBLE) {
                mGraphicOverlay!!.visibility = View.INVISIBLE
                hideOverlayButton!!.text = "Show Overlay"
            } else {
                mGraphicOverlay!!.visibility = View.VISIBLE
                hideOverlayButton!!.text = "Hide Overlay"
            }
        }

        hideOverlayButton!!.visibility = View.INVISIBLE

        checkPermission()
    }

    private fun runTextRecognition() {
        val image = FirebaseVisionImage.fromBitmap(mSelectedImage!!)
        val detector = FirebaseVision.getInstance()
                .onDeviceTextRecognizer
        mButton!!.isEnabled = false
        detector.processImage(image)
                .addOnSuccessListener { texts ->
                    mButton!!.isEnabled = true
                    hideOverlayButton!!.visibility = View.VISIBLE
                    processTextRecognitionResult(texts)
                }
                .addOnFailureListener { e ->
                    // Task failed with an exception
                    mButton!!.isEnabled = true
                    e.printStackTrace()
                }
    }

    @SuppressLint("SetTextI18n")
    private fun processTextRecognitionResult(texts: FirebaseVisionText) {
        val blocks = texts.textBlocks
        var concatText = ""
        if (blocks.size == 0) {
            showToast("No text found")
            result!!.text = "No Text Found !!!"
            return
        }
        mGraphicOverlay!!.clear()
        mGraphicOverlay!!.visibility = View.VISIBLE
        for (i in blocks.indices) {
            val lines = blocks[i].lines
            for (j in lines.indices) {
                val elements = lines[j].elements
                for (k in elements.indices) {
                    val textGraphic = TextGraphic(mGraphicOverlay, elements[k])
                    mGraphicOverlay!!.add(textGraphic)
                    concatText += elements[k].text
                }
            }
        }
        result!!.text = concatText
    }

    private fun runCloudTextRecognition() {
        mCloudButton!!.isEnabled = false
        val image = FirebaseVisionImage.fromBitmap(mSelectedImage!!)
        val detector = FirebaseVision.getInstance()
                .cloudDocumentTextRecognizer
        detector.processImage(image)
                .addOnSuccessListener { texts ->
                    mCloudButton!!.isEnabled = true
                    hideOverlayButton!!.visibility = View.VISIBLE
                    processCloudTextRecognitionResult(texts)
                }
                .addOnFailureListener { e ->
                    // Task failed with an exception
                    mCloudButton!!.isEnabled = true
                    e.printStackTrace()
                }
    }

    @SuppressLint("SetTextI18n")
    private fun processCloudTextRecognitionResult(text: FirebaseVisionDocumentText?) {
        // Task completed successfully
        var concatText = ""
        if (text == null) {
            showToast("No text found")
            result!!.text = "No Text Found !!!"
            return
        }
        mGraphicOverlay!!.clear()
        mGraphicOverlay!!.visibility = View.VISIBLE
        val blocks = text.blocks
        for (i in blocks.indices) {
            val paragraphs = blocks[i].paragraphs
            for (j in paragraphs.indices) {
                val words = paragraphs[j].words
                for (l in words.indices) {
                    val cloudDocumentTextGraphic = CloudTextGraphic(mGraphicOverlay, words[l])
                    mGraphicOverlay!!.add(cloudDocumentTextGraphic)
                    concatText += words[l].text
                }
            }
        }
        result!!.text = concatText
    }

    private fun showToast(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
    }

    override fun onItemSelected(parent: AdapterView<*>, v: View, position: Int, id: Long) {
        mGraphicOverlay!!.clear()
        if (hasImagesInDirectory) {
            mSelectedImage = BitmapFactory.decodeFile(fileList[position].absolutePath)
            Log.w("test", mSelectedImage.toString())
        } else {
            when (position) {
                0 -> mSelectedImage = getBitmapFromAsset(this, "Please_walk_on_the_grass.jpg")
                1 ->
                    // Whatever you want to happen when the second item gets selected
                    mSelectedImage = getBitmapFromAsset(this, "non-latin.jpg")
                2 ->
                    // Whatever you want to happen when the third item gets selected
                    mSelectedImage = getBitmapFromAsset(this, "nl2.jpg")
            }
        }
        if (mSelectedImage != null) {
            // Get the dimensions of the View
            val targetedSize = targetedWidthHeight

            val targetWidth = targetedSize.first
            val maxHeight = targetedSize.second

            // Determine how much to scale down the image
            val scaleFactor = Math.max(
                    mSelectedImage!!.width.toFloat() / targetWidth.toFloat(),
                    mSelectedImage!!.height.toFloat() / maxHeight.toFloat())

            val resizedBitmap = Bitmap.createScaledBitmap(
                    mSelectedImage!!,
                    (mSelectedImage!!.width / scaleFactor).toInt(),
                    (mSelectedImage!!.height / scaleFactor).toInt(),
                    true)

            mImageView!!.setImageBitmap(resizedBitmap)
            mSelectedImage = resizedBitmap
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>) {
        // Do nothing
    }

    companion object {
        private val TAG = "MainActivity"

        fun getBitmapFromAsset(context: Context, filePath: String): Bitmap? {
            val assetManager = context.assets

            val `is`: InputStream
            var bitmap: Bitmap? = null
            try {
                `is` = assetManager.open(filePath)
                bitmap = BitmapFactory.decodeStream(`is`)
            } catch (e: IOException) {
                e.printStackTrace()
            }

            return bitmap
        }
    }

    private fun checkPermission() {
        if (ActivityCompat.checkSelfPermission(this,
                        android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
                ||
                ActivityCompat.checkSelfPermission(this,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
                ||
                ActivityCompat.checkSelfPermission(this,
                        android.Manifest.permission.INTERNET)
                != PackageManager.PERMISSION_GRANTED
                ||
                ActivityCompat.checkSelfPermission(this,
                        android.Manifest.permission.ACCESS_NETWORK_STATE)
                != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this,
                    arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            android.Manifest.permission.INTERNET,
                            android.Manifest.permission.ACCESS_NETWORK_STATE),
                    PERMISSIONS_REQUEST_CODE)
        } else {
            Log.e("PERMISSION CHECK", "PERMISSION GRANTED")
            getImageData()
        }
    }

    private fun getImageData() {
        val gPath: String = Environment.getExternalStorageDirectory().absolutePath
        val sPath = "Pictures"
        val cardPath = "card"
//        val fullPath = File(gPath + File.separator + sPath)
        val fullPath = File("$gPath/$sPath/$cardPath")
        Log.w("test", "" + fullPath)
        imageReaderNew(fullPath)
    }

    private fun imageReaderNew(root: File) {
//        val fileList: ArrayList<File> = ArrayList()
        val listAllFiles = root.listFiles()

        if (listAllFiles != null && listAllFiles.isNotEmpty()) {
            for (currentFile in listAllFiles) {
                if (currentFile.name.endsWith(".jpeg")
                        || currentFile.name.endsWith(".jpg")
                        || currentFile.name.endsWith(".png")) {
                    // File absolute path
                    Log.w("downloadFilePath", currentFile.absolutePath)
                    // File Name
                    Log.w("downloadFileName", currentFile.name)

                    dropDownItem.add(currentFile.name)
                    fileList.add(currentFile.absoluteFile)

                    hasImagesInDirectory = true
                }
            }
            Log.w("fileList", "" + fileList.size)
        } else {
            Log.w("fileList Failed", "" + fileList.size)
        }

        setUpDropDownItem()
    }

    private fun setUpDropDownItem() {
        if (dropDownItem.isEmpty()) {
            dropDownItem = arrayListOf("Image 1", "Image 2", "Image 3")
        }
        val adapter = ArrayAdapter(this, android.R.layout
                .simple_spinner_dropdown_item, dropDownItem)
        dropdown!!.adapter = adapter
        dropdown!!.onItemSelectedListener = this
    }
}
