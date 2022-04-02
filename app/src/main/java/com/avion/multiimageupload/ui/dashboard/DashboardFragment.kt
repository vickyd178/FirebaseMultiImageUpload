package com.avion.multiimageupload.ui.dashboard

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_GET_CONTENT
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.avion.multiimageupload.adapter.AdapterGallery
import com.avion.multiimageupload.databinding.FragmentDashboardBinding
import com.avion.multiimageupload.utils.Constants
import com.google.android.gms.tasks.Task
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.launch
import java.util.*

class DashboardFragment : Fragment(), AdapterGallery.OnItemClickListener {

    private val listOfImagesToUpload: MutableList<Uri> = arrayListOf()
    private var _binding: FragmentDashboardBinding? = null

    private val binding get() = _binding!!

    private lateinit var viewmodel: DashboardViewModel

    private var adapterGallery: AdapterGallery = AdapterGallery(this)

    private var storage: FirebaseStorage = FirebaseStorage.getInstance()

    private var storageReference = storage.reference
    private lateinit var progressDialog: ProgressDialog;

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewmodel =
            ViewModelProvider(this).get(DashboardViewModel::class.java)
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root: View = binding.root

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initialize()
    }

    private fun initialize() {
        progressDialog = ProgressDialog(requireContext())
        binding.apply {
            recyclerView.apply {
                layoutManager = GridLayoutManager(requireContext(), 2)
                adapter = adapterGallery
                setHasFixedSize(true)
            }

            btnUpload.setOnClickListener() {

                uploadMultiple(listOfImagesToUpload).observe(viewLifecycleOwner) {
                    Toast.makeText(
                        requireContext(),
                        "Uploading " + it.size + " Images to server",
                        Toast.LENGTH_LONG
                    ).show()
                }


            }
            btnSelect.setOnClickListener() {
                openFilePicker(pickFile("image/*"))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    //================================Check Storage Permissions=======================================

    lateinit var tempFunction: Unit // used to pass function to openFilePicker Function

    // util method
    private fun hasPermissions(context: Context, permissions: Array<String>): Boolean =
        permissions.all {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }


    val permReqLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.entries.all {
                it.value == true
            }
            if (granted) {
                tempFunction
            }
        }


    private fun openFilePicker(functionCall: Unit) {
        tempFunction = functionCall

        activity.let {
            if (hasPermissions(activity as Context, Constants.IMAGE_PERMISSIONS)) {
                val result = functionCall
            } else {
                permReqLauncher.launch(
                    Constants.IMAGE_PERMISSIONS
                )
            }
        }
    }


//    =========================Pick Image=============================================================

    private fun pickFile(
        mimetype: String? = null,
    ) {
        if (mimetype != null) {
            if (mimetype == "image/*") {
//                getProfileImageContent.launch(mimetype)
                val intent = Intent(ACTION_GET_CONTENT)
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                intent.type = "image/*"
                pickMultipleImages.launch(intent)
                //use for multiple images

            }
        }
    }

    val pickMultipleImages =
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
                        adapterGallery.submitList(listOfImages)

                        compressImages(listOfImages)
                    }
                    //If single image selected
                    else if (data?.data != null) {
                        val imageUri: Uri? = data.data
                    }
                }
            }
        }

    private fun compressImages(listOfImages: MutableList<Uri>) {
        var compressedImages: MutableList<Uri> = arrayListOf()

        viewLifecycleOwner.lifecycleScope.launch() {
            for (imageUri in listOfImages) {
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
                ).observe(viewLifecycleOwner) {
                    it?.apply {
                        println("ready to upload file at: " + toUri())
                        compressedImages.add(toUri())
                    }
                }
            }

            listOfImagesToUpload.clear()
            listOfImagesToUpload.addAll(compressedImages)
        }
    }

    private fun uploadMultiple(
        fileUriList: List<Uri>,
    ): LiveData<List<String>> {
        val uploadedFileUrl = MutableLiveData<List<String>>()
        val listOfUploadedImages = mutableListOf<String>()
        progressDialog.setTitle("Uploading...")
        var numberOfFilesUploaded = 0;
        fileUriList.forEachIndexed { index, imageUri ->
            println("index = $index, item = $imageUri ")

            val reference = storageReference.child(UUID.randomUUID().toString())
            val uploadTask = reference.putFile(imageUri)
            // Code for showing progressDialog while uploading
            progressDialog.setMessage("Uploading 1/${fileUriList.size}")
            progressDialog.show()

            uploadTask.addOnSuccessListener {
                val urlTask: Task<Uri> = it.storage.downloadUrl
                while (!urlTask.isSuccessful);
                val downloadUrl: Uri = urlTask.result!!
                listOfUploadedImages.add(downloadUrl.toString())
                numberOfFilesUploaded++
                progressDialog.setMessage("Uploading ${numberOfFilesUploaded}/${fileUriList.size}")
                println("Uploaded file ${numberOfFilesUploaded}")
                if (numberOfFilesUploaded == fileUriList.size)
                    uploadedFileUrl.value = listOfUploadedImages
            }
            val urlTask = uploadTask.continueWithTask { task ->
                if (!task.isSuccessful) {
                    throw task.exception!!
                }

                reference.downloadUrl
            }.addOnCompleteListener { _ ->
                if (numberOfFilesUploaded == fileUriList.size)
                    progressDialog.dismiss()
            }.addOnFailureListener {
                numberOfFilesUploaded++
                if (numberOfFilesUploaded == fileUriList.size) {
                    uploadedFileUrl.value = listOfUploadedImages
                    progressDialog.dismiss()
                }
            }
            uploadTask.addOnProgressListener { taskSnapshot ->
                // Progress Listener for loading
                // percentage on the dialog box
                val progress = (100.0
                        * taskSnapshot.bytesTransferred
                        / taskSnapshot.totalByteCount)
                /* progressDialog.setMessage(
                     "Uploaded "
                             + progress.toInt() + "%"
                 )*/
            }

        }
        return uploadedFileUrl
    }

    override fun onItemClick(task: Uri, position: Int) {

    }

}