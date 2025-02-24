package com.example.myapplication.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.RestaurantAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import model.Restaurant
import java.util.Calendar
import java.util.concurrent.TimeUnit

class RestaurantsFragment : Fragment() {

    private lateinit var daysTextView: TextView
    private lateinit var hoursTextView: TextView
    private lateinit var minutesTextView: TextView
    private lateinit var secondsTextView: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: RestaurantAdapter
    private lateinit var restaurantOfTheWeekName: TextView
    private lateinit var restaurantOfTheWeekImage: ImageView
    private val restaurantList = mutableListOf<Restaurant>()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var currentUserId: String? = null
    private var countDownTimer: CountDownTimer? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_restraunts, container, false)

        // Initialize views
        daysTextView = view.findViewById(R.id.daysTextView)
        hoursTextView = view.findViewById(R.id.hoursTextView)
        minutesTextView = view.findViewById(R.id.minutesTextView)
        secondsTextView = view.findViewById(R.id.secondsTextView)
        restaurantOfTheWeekName = view.findViewById(R.id.restaurantOfTheWeekName)
        restaurantOfTheWeekImage = view.findViewById(R.id.restaurantOfTheWeekImage)

        // Get current user ID
        currentUserId = auth.currentUser?.uid

        if (currentUserId == null) {
            showToast("Please log in to vote!")
            return view
        }

        // Start countdown timer with updated calculation
        startCountdownTimer(calculateTimeUntilNextSundayMidnight())

        // Initialize RecyclerView
        recyclerView = view.findViewById(R.id.restrauntRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = RestaurantAdapter(restaurantList) { restaurant ->
            showVoteConfirmationDialog(restaurant)
        }
        recyclerView.adapter = adapter

        // Load restaurants and fetch Restaurant of the Week
        loadRestaurants()
        fetchRestaurantOfTheWeek()

        return view
    }

    /**
     * Calculate the time until the next Sunday at midnight.
     *
     * This implementation sets the target time to Sunday 00:00:00.000.
     * If the current time is already past that moment (e.g. on Sunday),
     * it adds 7 days so that the reset always happens at the start of the next week.
     */
    private fun calculateTimeUntilNextSundayMidnight(): Long {
        val calendar = Calendar.getInstance()
        // Set calendar to Sunday 00:00:00.000 of this week
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        // If that time has already passed, add 7 days to set it for the next week
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 7)
        }
        return calendar.timeInMillis - System.currentTimeMillis()
    }

    private fun loadRestaurants() {
        db.collection("restaurants")
            .orderBy("votes", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("LoadRestaurants", "Failed to load restaurants: $error")
                    showToast("Failed to load restaurants.")
                    return@addSnapshotListener
                }
                if (snapshot != null && !snapshot.isEmpty) {
                    restaurantList.clear()
                    for (document in snapshot.documents) {
                        val restaurant = document.toObject(Restaurant::class.java)?.apply {
                            id = document.id
                        }
                        restaurant?.let { restaurantList.add(it) }
                    }
                    adapter.notifyDataSetChanged()
                }
            }
    }

    private fun fetchRestaurantOfTheWeek() {
        db.collection("restaurants")
            .orderBy("votes", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.documents.isNotEmpty()) {
                    val restaurant = snapshot.documents[0].toObject(Restaurant::class.java)
                    restaurantOfTheWeekName.text = restaurant?.name ?: "No Restaurant"
                    Glide.with(this)
                        .load(restaurant?.imageUrl)
//                        .placeholder(R.drawable.placeholder_image)
//                        .error(R.drawable.error_image)
                        .into(restaurantOfTheWeekImage)

                    // Display the number of votes
                    val votesCount = restaurant?.votes ?: 0
                    view?.findViewById<TextView>(R.id.votesOnRestOfWeek)?.text = "$votesCount Votes"
                }
            }
            .addOnFailureListener { e ->
                Log.e("FetchRestaurantOfTheWeek", "Error: $e")
            }
    }

    private fun showVoteConfirmationDialog(restaurant: Restaurant) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.restraunt_voting_dialog, null)
        val dialogBuilder = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)

        val restaurantNameTextView = dialogView.findViewById<TextView>(R.id.nameRest)
        val restaurantImageView = dialogView.findViewById<ImageView>(R.id.restaurantImage)
        val confirmButton = dialogView.findViewById<TextView>(R.id.confirmBtn)
        val cancelButton = dialogView.findViewById<TextView>(R.id.cancelBtn)

        restaurantNameTextView.text = "Vote for \"${restaurant.name}\", you sure?"
        Glide.with(this)
            .load(restaurant.imageUrl)
//            .placeholder(R.drawable.placeholder_image)
//            .error(R.drawable.error_image)
            .into(restaurantImageView)

        val dialog = dialogBuilder.create()

        confirmButton.setOnClickListener {
            handleVote(restaurant)
            dialog.dismiss()
        }

        cancelButton.setOnClickListener { dialog.dismiss() }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
    }

    private fun handleVote(restaurant: Restaurant) {
        if (restaurant.id.isEmpty() || currentUserId.isNullOrEmpty()) {
            showToast("Invalid restaurant ID or user session.")
            return
        }

        db.collection("votes")
            .whereEqualTo("userId", currentUserId)
            .whereGreaterThanOrEqualTo("timestamp", calculateStartOfWeek())
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.documents.isNotEmpty()) {
                    showToast("You have already voted this week!")
                } else {
                    val voteData = hashMapOf(
                        "userId" to currentUserId,
                        "restaurantId" to restaurant.id,
                        "timestamp" to System.currentTimeMillis()
                    )

                    db.collection("votes").add(voteData)
                        .addOnSuccessListener {
                            db.collection("restaurants").document(restaurant.id)
                                .update("votes", FieldValue.increment(1))
                                .addOnSuccessListener {
                                    showToast("Vote submitted!")
                                    fetchRestaurantOfTheWeek()
                                }
                        }
                }
            }
    }

    /**
     * Returns the start of the week (Sunday midnight).
     */
    private fun calculateStartOfWeek(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun startCountdownTimer(timeInMillis: Long) {
        // Cancel any existing timer
        countDownTimer?.cancel()

        countDownTimer = object : CountDownTimer(timeInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val days = TimeUnit.MILLISECONDS.toDays(millisUntilFinished)
                val hours = TimeUnit.MILLISECONDS.toHours(millisUntilFinished) % 24
                val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60
                val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60

                daysTextView.text = String.format("%02d", days)
                hoursTextView.text = String.format("%02d", hours)
                minutesTextView.text = String.format("%02d", minutes)
                secondsTextView.text = String.format("%02d", seconds)
            }

            override fun onFinish() {
                // Reset votes for all restaurants to 0
                resetRestaurantVotes()
                // Clear all vote records so users can vote again
                clearUserVotes()
                // Restart the timer for the next week
                startCountdownTimer(calculateTimeUntilNextSundayMidnight())
            }
        }.start()
    }

    private fun resetRestaurantVotes() {
        db.collection("restaurants").get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()
                snapshot.documents.forEach { document ->
                    val restaurantRef = db.collection("restaurants").document(document.id)
                    batch.update(restaurantRef, "votes", 0)
                }
                batch.commit().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("VotesReset", "All restaurant votes have been reset.")
                    } else {
                        Log.e("VotesReset", "Failed to reset restaurant votes: ${task.exception}")
                    }
                }
            }
    }

    private fun clearUserVotes() {
        db.collection("votes").get()
            .addOnSuccessListener { querySnapshot ->
                val batch = db.batch()
                querySnapshot.documents.forEach { document ->
                    batch.delete(document.reference)
                }
                batch.commit().addOnSuccessListener {
                    Log.d("VotesReset", "All user vote records cleared.")
                    showToast("Votes have been reset. You can vote again now!")
                }
            }
    }

    private fun showToast(message: String) {
        context?.let {
            Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Prevent memory leaks by cancelling the timer when the view is destroyed.
        countDownTimer?.cancel()
    }
}

