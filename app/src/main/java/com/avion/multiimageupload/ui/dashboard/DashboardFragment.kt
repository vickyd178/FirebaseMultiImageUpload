package com.avion.multiimageupload.ui.dashboard

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_GET_CONTENT
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.avion.easypermissions.EasyPermissionManager
import com.avion.multiimageupload.R
import com.avion.multiimageupload.adapter.AdapterGallery
import com.avion.multiimageupload.databinding.FragmentDashboardBinding
import com.avion.multiimageupload.utils.Constants
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.storage.FirebaseStorage
import com.theartofdev.edmodo.cropper.CropImage
import com.theartofdev.edmodo.cropper.CropImageView
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.*


class DashboardFragment : Fragment(), AdapterGallery.OnItemClickListener {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by viewModels()

    private var adapterGallery: AdapterGallery = AdapterGallery(this)
    private var storage: FirebaseStorage = FirebaseStorage.getInstance()
    private var storageReference = storage.reference


    private lateinit var progressDialog: ProgressDialog;

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initialize()
    }


    override fun onAttach(context: Context) {
        super.onAttach(context)
//        EasyPermissionManager.createAndGetPermissionLauncher(requireActivity())
    }

    private fun initialize() {
        progressDialog = ProgressDialog(requireContext())
        binding.apply {
            recyclerView.apply {
                layoutManager = GridLayoutManager(requireContext(), 2)
                adapter = adapterGallery
                setHasFixedSize(true)
            }

            viewModel.selectedImages.observe(viewLifecycleOwner) { images ->
                adapterGallery.submitList(images)
                btnUpload.setOnClickListener {
                    if (images.isNotEmpty())
                        uploadMultiple(images).observe(viewLifecycleOwner) {
                            it?.let { listOfImageUrls ->
                                listOfImageUrls.forEachIndexed { _, url ->
                                    println(url)
                                }
                            }

                            if (it.isNotEmpty()) {
                                Snackbar.make(
                                    binding.root,
                                    "${getImageAnnotation(it.size)} uploaded successfully.",
                                    Snackbar.LENGTH_LONG
                                ).show()
                                viewModel.setSelectedImages(mutableListOf())
                            }
                        }
                }
            }

            btnSelect.setOnClickListener {
                try {

                    EasyPermissionManager.requestPermissions(
                        requireContext(),
                        Constants.IMAGE_PERMISSIONS
                    ) { pickFile("image/*") }

                }catch (exception:Exception){
                    exception.printStackTrace()
                }
            }
        }
    }

    private fun getImageAnnotation(size: Int): String {
        return if (size == 1) "Image" else "${size} Images"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }



//    =========================Pick Image=============================================================

    private fun pickFile(
        mimetype: String? = null,
    ) {
        if (mimetype != null) {
            if (mimetype == "image/*") {

                val intent = Intent(ACTION_GET_CONTENT)
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                intent.type = "image/*"
                pickMultipleImages.launch(intent)

            }
        }
    }

    private val pickMultipleImages =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                if (result.resultCode == Activity.RESULT_OK) {
                    val data: Intent? = result.data
                    //If multiple image selected
                    if (data?.clipData != null) {
                        val count = data.clipData?.itemCount ?: 0
                        val listOfImages: MutableList<Uri> = mutableListOf()
                        for (i in 0 until count) {
                            val imageUri: Uri? = data.clipData?.getItemAt(i)?.uri
                            imageUri?.let {
                                listOfImages.add(it)
                            }

                        }
//                        without compression
//                        viewModel.setSelectedImages(listOfImages)
//                        use this function to compress images
                        compressImages(listOfImages)
                    }
                    //If single image selected
                    else if (data?.data != null) {
                        val imageUri: Uri? = data.data
                        val intent = CropImage.activity(imageUri)
                            .setAspectRatio(1920, 1080)
                            .setMinCropResultSize(1920, 1080)
                            .setAllowFlipping(false)
                            .setCropMenuCropButtonIcon(R.drawable.ic_baseline_check_24)
                            .setGuidelines(CropImageView.Guidelines.OFF)
                            .setRequestedSize(
                                1920,
                                1080,
                                CropImageView.RequestSizeOptions.SAMPLING
                            )
                            .getIntent(requireActivity())
                        imageCropperLauncher.launch(intent)
                    }
                }
            }
        }

    private var imageCropperLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                CropImage.getActivityResult(result.data)?.let { cropResult ->

                    CoroutineScope(Dispatchers.IO).launch {
                        val task = async {
                            Glide.with(requireContext()).asBitmap().load(cropResult.uri).submit()
                                .get()
                        }

                        Log.e("Cropped Image Width", "${task.await().width}")
                        Log.e("Cropped Image Height", "${task.await().height}")
                    }

                    val images = mutableListOf(cropResult.uri)
                    viewModel.setSelectedImages(images)

                }
            }
        }


    private fun compressImages(listOfImages: MutableList<Uri>) {
        val compressedImages: MutableList<Uri> = arrayListOf()

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            for (imageUri in listOfImages) {
                val task = async {
                    println("Got image at: $imageUri")
                    val filePath =
                        Constants.createUriToTempFile(
                            requireContext(),
                            imageUri
                        )?.absolutePath

                    val fullSizeBitmap: Bitmap = BitmapFactory.decodeFile(filePath)
                    Constants.getCompressedImage(
                        requireContext(),
                        fullSizeBitmap
                    ).toUri()
                }
                compressedImages.add(task.await())
            }
            withContext(Dispatchers.Main) {
                viewModel.setSelectedImages(compressedImages)
            }

        }


    }

    private fun uploadMultiple(
        fileUriList: List<Uri>,
    ): LiveData<List<String>> {
        val uploadedFileUrl = MutableLiveData<List<String>>()
        val listOfUploadedImages = mutableListOf<String>()
        progressDialog.setTitle("Uploading...")
        progressDialog.show()
        lifecycleScope.launch(Dispatchers.IO) {
            fileUriList.forEachIndexed { index, imageUri ->
                try {
                    val reference = storageReference.child(UUID.randomUUID().toString())
                    progressDialog.setMessage("Uploading ${index + 1}/${fileUriList.size}")
                    val uploadTask =
                        reference.putFile(imageUri)
                            .await()
                            .storage
                            .downloadUrl
                            .await()
                    listOfUploadedImages.add(uploadTask.toString())

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            withContext(Dispatchers.Main) {
                uploadedFileUrl.value = listOfUploadedImages
                progressDialog.dismiss()
            }
        }
        return uploadedFileUrl
    }

    override fun onItemClick(task: Uri, position: Int) {

    }
}