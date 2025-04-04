package com.example.myapplication.fragments
import HomeAdapter
import android.Manifest
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import model.HomeCards
import com.example.myapplication.R
import com.example.myapplication.UserProfile
import com.example.myapplication.databinding.FragmentHomeBinding
import com.foysaldev.cropper.CropImage
import com.foysaldev.cropper.CropImageView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import android.location.Location
import android.location.Geocoder
import android.text.Editable
import android.widget.SeekBar
import com.google.android.material.textview.MaterialTextView
import java.util.Locale

import android.os.Handler
import android.os.Looper
import android.widget.Button

import android.animation.Animator
import android.animation.ObjectAnimator
import android.widget.ImageView
import com.example.myapplication.ReviewDetailActivity


class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var homeAdapter: HomeAdapter
    private lateinit var newRecyclerView: RecyclerView
    private var foodItem= mutableListOf<HomeCards>()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var userLocation: GeoPoint? = null

    private lateinit var bottomSheetDialog: BottomSheetDialog
    private lateinit var bottomSheetView: View
    private lateinit var photos: ImageView

    private lateinit var refreshButton: Button
    private val handler = Handler(Looper.getMainLooper())
    private val refreshInterval: Long = 5 * 60 * 1000 // 5 minutes in milliseconds

//    private val binding get() = _binding!!

    private val REQUEST_IMAGE_CAPTURE = 1
    private val REQUEST_IMAGE_GALLERY = 2
    private var imageUri: Uri? = null
    private var imageUrl: String = ""

    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var cropActivityResultLauncher: ActivityResultLauncher<Intent>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        val binding=_binding?:return null
        val root=binding.root
        val currentUser = auth.currentUser

        // Start periodic location updates
        startLocationRefresh()

        val refreshLocation = binding.refreshLocation
        refreshLocation.setOnClickListener {
            fetchCurrentLocation()
            val rotateAnimation = ObjectAnimator.ofFloat(refreshLocation, "rotation", 0f,180f)
            rotateAnimation.duration = 1000 // Duration of speeding

            rotateAnimation.addListener(object  : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {
                    binding.currentLocation.visibility = View.GONE
                }

                override fun onAnimationEnd(animation: Animator) {
                    binding.currentLocation.visibility = View.VISIBLE
                    fetchCurrentLocation()
                }

                override fun onAnimationCancel(animation: Animator) {

                }

                override fun onAnimationRepeat(animation: Animator) {

                }
            })
            rotateAnimation.start()
        }

        val displayName = currentUser?.displayName ?: ""
        val firstName = displayName.split(" ").firstOrNull() ?: ""

        binding.userName.text = "Hi $firstName"

        Glide.with(requireContext())
            .load(currentUser?.photoUrl)
            .circleCrop()
            .placeholder(R.drawable.circular_bg)
            .into(binding.profilePic)

        binding.profilePic.setOnClickListener {
            val intent1 = Intent(requireContext(), UserProfile::class.java)
            val options = ActivityOptions.makeSceneTransitionAnimation(
                requireActivity(),
                binding.profilePic,
                "picture"
            )
            startActivity(intent1, options.toBundle())
        }

        //val addReviewsImageView: ImageView = binding.root.findViewById(R.id.add_reviews)
        binding.addReviews.setOnClickListener {
            showBottomSheet(requireContext())
        }
        binding.viewReview.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_feedFragment)
        }
        binding.restaurantBtn.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_restrauntsFragment)
        }
        binding.offerZoneBtn.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_offersFragment)
        }

        // Request location permissions
        requestLocationPermissions()

        // Fetch user's location
        //fetchUserLocation()


        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                openCamera()
            } else {
                Toast.makeText(requireContext(), "Camera permission is required to take pictures", Toast.LENGTH_SHORT).show()
            }
        }

        cropActivityResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == AppCompatActivity.RESULT_OK) {
                val resultUri = result.data?.let { CropImage.getActivityResult(it).uri }
                resultUri?.let {
                    selectedImageUri = it // Store globally
                }
            } else if (result.resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                val error = result.data?.let { CropImage.getActivityResult(it).error }
                Toast.makeText(requireContext(), "Crop failed: ${error?.message}", Toast.LENGTH_SHORT).show()
            }
        }




        // Initialize RecyclerView and Adapter
        newRecyclerView = _binding!!.recyclerHome  // Adjust this line according to your binding
        newRecyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        homeAdapter=HomeAdapter(foodItem,requireContext()) { selectedItem ->
            val intent = Intent(requireContext(), ReviewDetailActivity::class.java).apply {
                putExtra("dishName", selectedItem.dishName)
                putExtra("restaurantName", selectedItem.restaurantName)
//                putExtra("userName", selectedItem.userName) // Add this if available in HomeCards
                putExtra("reviewText", selectedItem.reviewText) // Add if available
                putExtra("price", selectedItem.price) // Ensure HomeCards has this
                putExtra("likes", selectedItem.likes)
                putExtra("rating", selectedItem.rating)
                putExtra("imageUrl", selectedItem.imageUrl)
//                putExtra("userProfilePic", selectedItem.userProfilePic) // Add if available
            }
            startActivity(intent)

        }
        newRecyclerView.adapter = homeAdapter

        // Show ProgressBar and hide RecyclerView initially
        binding.progressBarHome.visibility = View.VISIBLE
        newRecyclerView.visibility = View.GONE

        fetchFoodItems()

        // Delay for 3-5 seconds if loading takes time
        GlobalScope.launch(Dispatchers.Main) {
            delay(3000)  // 3 seconds delay
            binding.progressBarHome.visibility = View.GONE  // Hide the ProgressBar
            newRecyclerView.visibility = View.VISIBLE  // Show the RecyclerView
        }



        return root
    }

    private fun fetchCurrentLocation() {
        requestLocationPermissions()
        fetchUserLocation()
    }

    private fun startLocationRefresh() {
        handler.post(object : Runnable {
            override fun run() {
                fetchCurrentLocation()
                handler.postDelayed(this, refreshInterval)
            }
        })
    }

    private fun requestLocationPermissions() {
        val permissionFineLocation = Manifest.permission.ACCESS_FINE_LOCATION
        val permissionCoarseLocation = Manifest.permission.ACCESS_COARSE_LOCATION

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                permissionFineLocation
            ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                requireContext(),
                permissionCoarseLocation
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(permissionFineLocation, permissionCoarseLocation),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }
    private fun fetchUserLocation() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnCompleteListener { task: Task<Location> ->
                if (task.isSuccessful && task.result != null) {
                    val location = task.result
                    userLocation = GeoPoint(location.latitude, location.longitude)

                    // Convert coordinates to a human-readable address
                    val geocoder = Geocoder(requireContext(), Locale.getDefault())
                    val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val address = addresses[0].getAddressLine(0)
                        // Display the address in the TextView
                        _binding?.currentLocation?.text = address
                    } else {
                        _binding?.currentLocation?.text = "Unable to fetch location"
                    }
                } else {
                    _binding?.currentLocation?.text = "Location not available"
                }
            }
        }
    }




    private fun fetchFoodItems() {
        firestore.collection("dishReview")
            .orderBy("likes", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                foodItem.clear() // Clear the list before adding new data
                for (document in documents) {
                    val review = document.toObject(HomeCards::class.java)
                    if (review.imageUrl.isNotEmpty()) { // Check for non-empty image URL
                        foodItem.add(review)
                    }
                }
                homeAdapter.updateReviews(foodItem)
            }
            .addOnFailureListener { exception ->
                Toast.makeText(requireContext(), "Error fetching reviews: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }



    private fun showBottomSheet(context: Context) {
        bottomSheetView = layoutInflater.inflate(R.layout.bottomsheethome, null)
        bottomSheetDialog = BottomSheetDialog(context, R.style.BottomSheetDialogTheme)
        bottomSheetDialog.setContentView(bottomSheetView)

        val slideIn = AnimationUtils.loadAnimation(context, R.anim.slide_in_bottom)
        bottomSheetView.startAnimation(slideIn)

        val bottomSheet = bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.setBackgroundColor(Color.TRANSPARENT)

        if (bottomSheet != null) {
            photos = bottomSheet.findViewById(R.id.imagePreview)
            // Load user profile into profileOnSheet ImageView

            val profileOnSheet = bottomSheet.findViewById<ImageView>(R.id.profileOnBottom)
            val currentUser = FirebaseAuth.getInstance().currentUser
            Glide.with(context)
                .load(currentUser?.photoUrl)
                .circleCrop()
                .placeholder(R.drawable.circular_bg) // Placeholder while loading
                .into(profileOnSheet)
        }

        bottomSheetView.background = ContextCompat.getDrawable(context, R.drawable.dialog_home_bg_newitem)

        val behavior = BottomSheetBehavior.from(bottomSheet!!)
        behavior.isDraggable = false
        behavior.state = BottomSheetBehavior.STATE_EXPANDED

        val slideOut = AnimationUtils.loadAnimation(context, R.anim.slide_out_bottom)
        slideOut.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation) {}

            override fun onAnimationEnd(animation: Animation) {
                bottomSheetDialog.dismiss()
            }

            override fun onAnimationRepeat(animation: Animation) {}
        })

        val dishNameEditText = bottomSheetView.findViewById<EditText>(R.id.dishName)
        val restaurantNameEditText = bottomSheetView.findViewById<EditText>(R.id.restaurantName)
        val dishPriceEditText = bottomSheetView.findViewById<EditText>(R.id.dishPrice)
        val dishReviewEditText = bottomSheetView.findViewById<EditText>(R.id.dishReview)
        val dishRatingsText = bottomSheetView.findViewById<MaterialTextView>(R.id.ratings)
        val toggleValue = bottomSheetView.findViewById<SeekBar>(R.id.ratingBar)


        dishRatingsText.text = Editable.Factory.getInstance().newEditable(toggleValue.progress.toString())

        toggleValue.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                dishRatingsText.text = Editable.Factory.getInstance().newEditable(toggleValue.progress.toString())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        bottomSheetView.findViewById<View>(R.id.postBtn).setOnClickListener {
            val dishName = dishNameEditText.text.toString().trim()
            val restaurantName = restaurantNameEditText.text.toString().trim()
            val dishPrice = dishPriceEditText.text.toString().toDoubleOrNull() ?: 0.0
            val dishReview = dishReviewEditText.text.toString().trim()
            val dishRating = toggleValue.progress

            if (validateInputs(dishName, restaurantName, dishPrice, dishReview)) {
                if (selectedImageUri != null) {
                    // Upload the image before posting the review
                    uploadImageToFirebase(selectedImageUri!!) { imageUrl ->
                        postReview(dishName, restaurantName, dishPrice, dishReview, dishRating, imageUrl)
                        bottomSheetDialog.dismiss() // Dismiss the dialog after posting
                    }
                } else {
                    // No image selected, post review with empty imageUrl
                    postReview(dishName, restaurantName, dishPrice, dishReview, dishRating, "")
                    bottomSheetDialog.dismiss() // Dismiss the dialog after posting
                }
            }
        }

        bottomSheetView.findViewById<View>(R.id.cancelBtn).setOnClickListener {
            bottomSheetView.startAnimation(slideOut)
        }


        bottomSheetView.findViewById<View>(R.id.uploadGallery).setOnClickListener {
            openGallery()
        }

        bottomSheetView.findViewById<View>(R.id.uploadCamera).setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        bottomSheetDialog.show()
    }
    private fun validateInputs(
        dishName: String,
        restaurantName: String,
        dishPrice: Double,
        dishReview: String
    ): Boolean {
        if (dishName.isEmpty()) {
            Toast.makeText(requireContext(), "Enter Dish Name", Toast.LENGTH_SHORT).show()
            return false
        }
        if (restaurantName.isEmpty()) {
            Toast.makeText(requireContext(), "Enter Restaurant Name", Toast.LENGTH_SHORT).show()
            return false
        }
        if (dishPrice <= 0.0) {
            Toast.makeText(requireContext(), "Enter Valid Price", Toast.LENGTH_SHORT).show()
            return false
        }
        if (dishReview.isEmpty() || dishReview.length !in 1..100) {
            Toast.makeText(requireContext(), "Enter Review (5 to 100 characters)", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, REQUEST_IMAGE_GALLERY)
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photoFile = createImageFile()
        if (photoFile != null) {
            imageUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                photoFile
            )
            intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)

            // Double-check if imageUri is properly initialized
            if (imageUri != null) {
                Log.d("CameraIntent", "Image URI set: $imageUri")
                startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
            } else {
                Log.e("CameraIntent", "Error: imageUri is null")
                Toast.makeText(requireContext(), "Error initializing image capture", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.e("CameraIntent", "Error creating image file")
            Toast.makeText(requireContext(), "Error creating image file", Toast.LENGTH_SHORT).show()
        }
    }


    @Throws(IOException::class)
    private fun createImageFile(): File? {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)

        return try {
            File.createTempFile(
                "JPEG_${timeStamp}_", /* prefix */
                ".jpg", /* suffix */
                storageDir /* directory */
            )
        } catch (ex: IOException) {
            Log.e("CreateFileError", "Error creating file: ${ex.message}")
            null
        }
    }
    private var selectedImageUri: Uri? = null  // Store selected image temporarily

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == AppCompatActivity.RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_GALLERY -> {
                    data?.data?.let { uri ->
                        startCropActivity(uri)
                    }
                }
                REQUEST_IMAGE_CAPTURE -> {
                    imageUri?.let {
                        Log.d("ActivityResult", "Starting crop activity with URI: $imageUri")
                        startCropActivity(it)
                    } ?: run {
                        Log.e("ActivityResult", "Error: Image URI is not initialized")
                        Toast.makeText(requireContext(), "Error: Image URI is not initialized", Toast.LENGTH_SHORT).show()
                    }
                }
                CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE -> {
                    val result = CropImage.getActivityResult(data)
                    if (result.isSuccessful) {
                        selectedImageUri = result.uri
                        Log.d("ActivityResult", "Image cropped successfully: $selectedImageUri")

                        // ✅ Ensure bottomSheetDialog is initialized before accessing it
                        if (::bottomSheetDialog.isInitialized) {
                            val photos: ImageView? = bottomSheetDialog.findViewById(R.id.imagePreview)
                            photos?.setImageURI(selectedImageUri)
                        } else {
                            Log.e("ActivityResult", "Error: bottomSheetDialog is not initialized")
                            Toast.makeText(requireContext(), "Error: Bottom Sheet is not opened", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val error = result.error
                        Log.e("ActivityResult", "Crop failed: ${error.message}")
                        Toast.makeText(requireContext(), "Crop failed: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else {
            Log.d("ActivityResult", "Result not OK, resultCode: $resultCode")
        }
    }




    private fun startCropActivity(uri: Uri) {
        CropImage.activity(uri)
            .setGuidelines(CropImageView.Guidelines.ON)
            .setAspectRatio(1, 1)
            .setCropShape(CropImageView.CropShape.RECTANGLE)
            .start(requireContext(), this)
    }

    private fun uploadImageToFirebase(imageUri: Uri, onSuccess: (String) -> Unit) {
        val storageRef = FirebaseStorage.getInstance().reference.child("images/${System.currentTimeMillis()}.jpg")

        storageRef.putFile(imageUri)
            .addOnSuccessListener { taskSnapshot ->
                taskSnapshot.storage.downloadUrl.addOnSuccessListener { uri ->
                    val imageUrl = uri.toString()
                    onSuccess(imageUrl) // Pass the image URL to the callback
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Image upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    private fun postReview(
        dishName: String,
        restaurantName: String,
        dishPrice: Double,
        dishReview: String,
        dishRating: Int,
        imageUrl: String
    ) {
        val currentUser = auth.currentUser
        val userProfilePic = currentUser?.photoUrl.toString()
        val userName = currentUser?.displayName ?: "Anonymous"
        val reviewRef = firestore.collection("dishReview").document()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

        val review = hashMapOf(
            "id" to reviewRef.id,
            "dishName" to dishName,
            "restaurantName" to restaurantName,
            "price" to dishPrice,
            "reviewText" to dishReview,
            "rating" to dishRating,
            "imageUrl" to imageUrl,
            "userProfilePic" to userProfilePic,
            "userName" to userName,
            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
            "userId" to userId
        )

        firestore.collection("dishReview")
            .add(review)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Review posted successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Failed to post review: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            fetchUserLocation()
        } else {
            _binding?.currentLocation?.text = "Outer Space, Maybe?"
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        _binding = null
    }
}