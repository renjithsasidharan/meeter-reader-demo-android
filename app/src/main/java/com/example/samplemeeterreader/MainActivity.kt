/*
 * Created by Renjith Sasidharan on 21/02/22, 11:57 PM
 * renjithks@gmail.com
 * Last modified 21/02/22, 11:57 PM
 * Copyright (c) 2022.
 * All rights reserved.
 */

package com.example.samplemeeterreader

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.Observer
import com.bumptech.glide.Glide
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Executors
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tensorflow.lite.meeterreader.ocr.ModelExecutionResult
import org.tensorflow.lite.meeterreader.ocr.OCRModelExecutor
import java.util.*


private const val TAG = "MainActivity"

class MainActivity : AppCompatActivity() {

    private val firstImageName = "1.jpg"
    private val secondImageName = "2.jpg"
    private val thirdImageName = "3.jpg"

    private lateinit var viewModel: MLExecutionViewModel
    private lateinit var resultImageView: ImageView
    private lateinit var tfImageView: ImageView
    private lateinit var androidImageView: ImageView
    private lateinit var chromeImageView: ImageView
    private lateinit var chipsGroup: ChipGroup
    private lateinit var runButton: Button
    private lateinit var selectButton: Button
    private lateinit var textPromptTextView: TextView

    private var selectedImageName = "1.jpg"
    private var selectedGalleryImageUri: Uri? = null
    private var ocrModel: OCRModelExecutor? = null
    private val inferenceThread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val mainScope = MainScope()
    private val mutex = Mutex()
    private val SELECT_IMAGE = 201

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tfe_is_activity_main)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        tfImageView = findViewById(R.id.tf_imageview)
        androidImageView = findViewById(R.id.android_imageview)
        chromeImageView = findViewById(R.id.chrome_imageview)

        val candidateImageViews = arrayOf<ImageView>(tfImageView, androidImageView, chromeImageView)

        val assetManager = assets
        try {
            val tfInputStream: InputStream = assetManager.open(firstImageName)
            val tfBitmap = BitmapFactory.decodeStream(tfInputStream)
            tfImageView.setImageBitmap(tfBitmap)
            val androidInputStream: InputStream = assetManager.open(secondImageName)
            val androidBitmap = BitmapFactory.decodeStream(androidInputStream)
            androidImageView.setImageBitmap(androidBitmap)
            val chromeInputStream: InputStream = assetManager.open(thirdImageName)
            val chromeBitmap = BitmapFactory.decodeStream(chromeInputStream)
            chromeImageView.setImageBitmap(chromeBitmap)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to open a test image")
        }

        for (iv in candidateImageViews) {
            setInputImageViewListener(iv)
        }

        resultImageView = findViewById(R.id.result_imageview)
        chipsGroup = findViewById(R.id.chips_group)
        textPromptTextView = findViewById(R.id.text_prompt)

        viewModel = AndroidViewModelFactory(application).create(MLExecutionViewModel::class.java)
        viewModel.resultingBitmap.observe(
            this,
            Observer { resultImage ->
                if (resultImage != null) {
                    updateUIWithResults(resultImage)
                }
                enableControls(true)
            }
        )

        mainScope.async(inferenceThread) { createModelExecutor() }

        selectButton = findViewById(R.id.select_button)
        selectButton.setOnClickListener {
            val i = Intent()
            i.type = "image/*"
            i.action = Intent.ACTION_GET_CONTENT

            startActivityForResult(Intent.createChooser(i, "Select Image"), SELECT_IMAGE)
        }

        runButton = findViewById(R.id.rerun_button)
        runButton.setOnClickListener {
            enableControls(false)

            mainScope.async(inferenceThread) {
                mutex.withLock {
                    if (ocrModel != null) {
                        var contentImage: Bitmap?
                        if (selectedGalleryImageUri != null) {
                            contentImage = MediaStore.Images.Media.getBitmap(baseContext.contentResolver, selectedGalleryImageUri)
                        } else {
                            val inputStream = baseContext.assets.open(selectedImageName)
                            contentImage = BitmapFactory.decodeStream(inputStream)
                        }

                        viewModel.onApplyModel(contentImage, ocrModel, inferenceThread)
                    } else {
                        Log.d(
                            TAG,
                            "Skipping running OCR since the ocrModel has not been properly initialized ..."
                        )
                    }
                }
            }
        }

        setChipsToLogView(null)
        enableControls(true)
    }

    //handle result of picked image
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == SELECT_IMAGE){
            selectedGalleryImageUri = data?.data
            val contentImage = MediaStore.Images.Media.getBitmap(this.contentResolver, data?.data)
            textPromptTextView.setText(getResources().getString(R.string.tfe_using_gallery_image))

            mainScope.async(inferenceThread) {
                mutex.withLock {
                    if (ocrModel != null) {
                        viewModel.onApplyModel(contentImage, ocrModel, inferenceThread)
                    } else {
                        Log.d(
                            TAG,
                            "Skipping running OCR since the ocrModel has not been properly initialized ..."
                        )
                    }
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setInputImageViewListener(iv: ImageView) {
        iv.setOnTouchListener(
            object : View.OnTouchListener {
                override fun onTouch(v: View, event: MotionEvent?): Boolean {
                    selectedGalleryImageUri = null
                    if (v.equals(tfImageView)) {
                        selectedImageName = firstImageName
                        textPromptTextView.setText(getResources().getString(R.string.tfe_using_first_image))
                    } else if (v.equals(androidImageView)) {
                        selectedImageName = secondImageName
                        textPromptTextView.setText(getResources().getString(R.string.tfe_using_second_image))
                    } else if (v.equals(chromeImageView)) {
                        selectedImageName = thirdImageName
                        textPromptTextView.setText(getResources().getString(R.string.tfe_using_third_image))
                    }
                    return false
                }
            }
        )
    }

    private suspend fun createModelExecutor() {
        mutex.withLock {
            if (ocrModel != null) {
                ocrModel!!.close()
                ocrModel = null
            }
            try {
                ocrModel = OCRModelExecutor(this)
            } catch (e: Exception) {
                Log.e(TAG, "Fail to create OCRModelExecutor: ${e.message}")
                val logText: TextView = findViewById(R.id.log_view)
                logText.text = e.message
            }
        }
    }

    private fun setChipsToLogView(reading: String?) {
        chipsGroup.removeAllViews()

        if (reading != null) {
            val chip = Chip(this)
            chip.text = reading
            chip.chipBackgroundColor = getColorStateListForChip(getRandomColor())
            chip.isClickable = false
            chipsGroup.addView(chip)

            val labelsFoundTextView: TextView = findViewById(R.id.tfe_is_labels_found)
            if (chipsGroup.childCount == 0) {
                labelsFoundTextView.text = getString(R.string.tfe_ocr_no_text_found)
            } else {
                labelsFoundTextView.text = getString(R.string.tfe_ocr_texts_found)
            }
            chipsGroup.parent.requestLayout()
        }
    }

    private fun getColorStateListForChip(color: Int): ColorStateList {
        val states =
            arrayOf(
                intArrayOf(android.R.attr.state_enabled), // enabled
                intArrayOf(android.R.attr.state_pressed) // pressed
            )

        val colors = intArrayOf(color, color)
        return ColorStateList(states, colors)
    }

    private fun setImageView(imageView: ImageView, image: Bitmap) {
        Glide.with(baseContext).load(image).override(250, 250).fitCenter().into(imageView)
    }

    private fun updateUIWithResults(modelExecutionResult: ModelExecutionResult) {
        setImageView(resultImageView, modelExecutionResult.bitmapResult)
        val logText: TextView = findViewById(R.id.log_view)
        logText.text = modelExecutionResult.executionLog

        setChipsToLogView(modelExecutionResult.reading)
        enableControls(true)
    }

    private fun enableControls(enable: Boolean) {
        runButton.isEnabled = enable
    }

    fun getRandomColor(): Int {
        val random = Random()
        return Color.argb(
            (128),
            (255 * random.nextFloat()).toInt(),
            (255 * random.nextFloat()).toInt(),
            (255 * random.nextFloat()).toInt()
        )
    }
}