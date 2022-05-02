package com.avion.multiimageupload.ui.dashboard

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class DashboardViewModel : ViewModel() {

    private val _selectedImages = MutableLiveData<MutableList<Uri>>().apply {
        value = mutableListOf()
    }
    val selectedImages: LiveData<MutableList<Uri>> = _selectedImages

    fun setSelectedImages(images: MutableList<Uri>) {
        _selectedImages.value = images
    }
}