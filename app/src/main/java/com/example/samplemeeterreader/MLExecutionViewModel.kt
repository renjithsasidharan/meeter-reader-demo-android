/*
 * Created by Renjith Sasidharan on 21/02/22, 11:57 PM
 * renjithks@gmail.com
 * Last modified 21/02/22, 11:57 PM
 * Copyright (c) 2022.
 * All rights reserved.
 */


package com.example.samplemeeterreader

import androidx.lifecycle.ViewModel
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.tensorflow.lite.meeterreader.ocr.ModelExecutionResult
import org.tensorflow.lite.meeterreader.ocr.OCRModelExecutor

private const val TAG = "MLExecutionViewModel"

class MLExecutionViewModel : ViewModel() {

  private val _resultingBitmap = MutableLiveData<ModelExecutionResult>()

  val resultingBitmap: LiveData<ModelExecutionResult>
    get() = _resultingBitmap

  private val viewModelJob = Job()
  private val viewModelScope = CoroutineScope(viewModelJob)

  // the execution of the model has to be on the same thread where the interpreter
  // was created
  fun onApplyModel(
    contentImage: Bitmap,
    ocrModel: OCRModelExecutor?,
    inferenceThread: ExecutorCoroutineDispatcher
  ) {
    viewModelScope.launch(inferenceThread) {
      try {
        val result = ocrModel?.execute(contentImage)
        _resultingBitmap.postValue(result)
      } catch (e: Exception) {
        Log.e(TAG, "Fail to execute OCRModelExecutor: ${e.message}")
        _resultingBitmap.postValue(null)
      }
    }
  }
}
