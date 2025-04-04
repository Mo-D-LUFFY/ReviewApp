package com.example.myapplication
import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.myapplication.databinding.ActivityUserProfileBinding
import com.foysaldev.cropper.CropImage
import com.foysaldev.cropper.CropImageView
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.squareup.picasso.Picasso
import jp.wasabeef.picasso.transformations.CropCircleTransformation
import model.UserModel

class UserProfile : AppCompatActivity() {

    companion object {
        const val REQUEST_IMAGE_GALLERY = 2
    }

    private lateinit var selectedImageUri: Uri
    private lateinit var binding: ActivityUserProfileBinding
    private val mAuth: FirebaseAuth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set white status bar background
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_light)

        // Set dark icons for visibility
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = true

        binding = ActivityUserProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Fetch and display user's profile picture and name
        val currentUser = mAuth.currentUser
        currentUser?.let { user ->
            // Set profile picture
            user.photoUrl?.let { url ->
                Picasso.get()
                    .load(url)
                    .transform(CropCircleTransformation())
                    .into(binding.profileOnUser)
            }

            // Set user name
            val userName = user.displayName ?: ""
            binding.userNameProfile.text = userName

            // Set user email
            binding.userEmailProfile.text = user.email

            // Fetch and display user's phone number
            val userId = user.uid
            val databaseRef = FirebaseDatabase.getInstance().reference.child("users").child(userId)
            databaseRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val userModel = snapshot.getValue(UserModel::class.java)
                    userModel?.let { user ->
                        // Set user phone number
                        binding.userPhoneProfile.text = user.phone
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@UserProfile, "Failed to load phone number", Toast.LENGTH_SHORT).show()
                }
            })
        }
        //Click listener to Open Privacy Policy
        binding.privacyPolicyText.setOnClickListener {
            Log.d("UserProfile", "Privacy policy text clicked!")
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://vishalthakur18.github.io/Privacy_Policy_Page/")
            )
            startActivity(intent)
        }



        // Click listener for profile picture
        binding.profileOnUser.setOnClickListener {
            showCustomDialog()
        }

        // Click listener for feature suggest
        binding.featuresSuggest.setOnClickListener {
            showFeatureBottomSheet()
        }

        // Click listener for back button
        binding.backtobase.setOnClickListener {
            supportFinishAfterTransition()
        }

        binding.contactBtn.setOnClickListener {
            showContactUsDialog()
        }

        // Click listener for log out button
        binding.logOutBtn.setOnClickListener {
            mAuth.signOut()

            // Sign out from Google also
            val googleSignInClient = GoogleSignIn.getClient(
                this@UserProfile,
                GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.web_client_id))
                    .requestEmail()
                    .build()
            )
            googleSignInClient.signOut().addOnCompleteListener {
                val intent = Intent(this@UserProfile, Login::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(intent)
                finish()
            }
        }
    }

    private fun showContactUsDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.contact_us_dialog)

        val whatsappLayout = dialog.findViewById<LinearLayout>(R.id.contactWhatsapp)
        whatsappLayout.setOnClickListener {
            val phoneNumber = "+917985419494" // your WhatsApp number with country code
            val message = "Hi! I will send 5000 right away!"

            val url = "https://wa.me/${phoneNumber.replace("+", "")}?text=${Uri.encode(message)}"
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)

            try {
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, "WhatsApp is not installed on your device.", Toast.LENGTH_SHORT).show()
            }
        }


        dialog.setCanceledOnTouchOutside(true)


        // Set width with 30dp margin on both sides
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val marginInPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 20f, resources.displayMetrics
        ).toInt()

        dialog.window?.setLayout(screenWidth - (2 * marginInPx), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setGravity(Gravity.CENTER)



        dialog.show()
    }


    private fun showFeatureBottomSheet() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.featurebottomsheet)

        val featureInput = dialog.findViewById<EditText>(R.id.featureName)
        val uiInput = dialog.findViewById<EditText>(R.id.uiSuggestName)
        val submitFeature = dialog.findViewById<Button>(R.id.confirmSendButton)
        val cancelButton = dialog.findViewById<Button>(R.id.cancelDeleteButton)

        submitFeature.setOnClickListener {
            val featureText = featureInput.text.toString().trim()
            val uiText = uiInput.text.toString().trim()
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"

            if (featureText.isNotEmpty()) {
                // Save to Firestore
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val suggestion = hashMapOf(
                    "feature" to featureText,
                    "ui_ux" to uiText,
                    "userId" to userId,
                    "timestamp" to com.google.firebase.Timestamp.now()
                )
                submitFeature.isEnabled = false // Disable button to prevent multiple clicks

                db.collection("feature_suggestions")
                    .add(suggestion)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Thanks for your suggestion!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to submit. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                    .addOnCompleteListener {
                        submitFeature.isEnabled = true // Re-enable button after completion
                    }
            } else {
                Toast.makeText(this, "Please enter a feature", Toast.LENGTH_SHORT).show()
            }

        }

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val marginInPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 20f, resources.displayMetrics
        ).toInt()

        dialog.window?.setLayout(screenWidth - (2 * marginInPx), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setGravity(Gravity.CENTER)
        cancelButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }


    private fun showCustomDialog() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_IMAGE_GALLERY)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_GALLERY -> {
                    data?.data?.let { imageUri ->
                        selectedImageUri = imageUri
                        startCropActivity(imageUri)
                    }
                }

                CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE -> {
                    val result = CropImage.getActivityResult(data)
                    if (result.error != null) {
                        val error = result.error
                        // Handle crop error
                        Toast.makeText(this, "Crop error: ${error.message}", Toast.LENGTH_SHORT).show()
                    } else {
                        result.uri?.let { uri ->
                            selectedImageUri = uri // Save the cropped image URI

                            // Upload the image to Firebase Storage
                            uploadImageToFirebase(selectedImageUri)
                        }
                    }
                }
            }
        }
    }

    private fun startCropActivity(uri: Uri) {
        CropImage.activity(uri)
            .setGuidelines(CropImageView.Guidelines.ON)
            .setAspectRatio(1, 1)
            .setCropShape(CropImageView.CropShape.OVAL)
            .start(this)
    }

    private fun uploadImageToFirebase(uri: Uri) {
        val storageRef = Firebase.storage.reference
        val imagesRef = storageRef.child("profile_pictures/${FirebaseAuth.getInstance().currentUser?.uid}.jpg")

        val uploadTask = imagesRef.putFile(uri)
        uploadTask.addOnSuccessListener {
            imagesRef.downloadUrl.addOnSuccessListener { downloadUri ->
                // Update the profile picture in Firebase Authentication
                val currentUser = FirebaseAuth.getInstance().currentUser
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setPhotoUri(downloadUri)
                    .build()

                currentUser?.updateProfile(profileUpdates)
                    ?.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            // Profile picture updated successfully
                            Toast.makeText(this, "Profile picture updated", Toast.LENGTH_SHORT).show()

                            // Update the profile picture in the UI
                            Picasso.get().load(downloadUri)
                                .transform(CropCircleTransformation())
                                .into(binding.profileOnUser)
                        } else {
                            // Profile picture update failed
                            Toast.makeText(this, "Failed to update profile picture", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }.addOnFailureListener {
            // Handle unsuccessful uploads
            Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show()
        }
    }
}
