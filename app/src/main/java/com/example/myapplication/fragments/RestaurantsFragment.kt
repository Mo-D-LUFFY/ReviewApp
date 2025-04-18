package com.example.myapplication.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.example.myapplication.RestaurantAdapter
import com.example.myapplication.databinding.FragmentRestrauntsBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import model.Restaurant
import model.WeeklyWinnerRestaurant
import java.util.Calendar
import java.util.concurrent.TimeUnit

class RestaurantsFragment : Fragment() {
    private var _binding: FragmentRestrauntsBinding? = null
    private val binding get() = _binding!!


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
    private var weekStartTimestamp: Long = 0L
    private var restaurantsListener: ListenerRegistration? = null


    private lateinit var topRestaurant1Name: TextView
    private lateinit var topRestaurant2Name: TextView
    private lateinit var topRunnerImage: ImageView
    private lateinit var runnerUpImage: ImageView
    private lateinit var topRestaurant1Bar: View
    private lateinit var topRestaurant2Bar: View
    private lateinit var topRestaurant1Percent: TextView
    private lateinit var topRestaurant2Percent: TextView
    private lateinit var othersVotes: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRestrauntsBinding.inflate(inflater, container, false)
        val view = binding.root
        // Initialize views
        daysTextView = view.findViewById(R.id.daysTextView)
        hoursTextView = view.findViewById(R.id.hoursTextView)
        minutesTextView = view.findViewById(R.id.minutesTextView)
        secondsTextView = view.findViewById(R.id.secondsTextView)
        restaurantOfTheWeekName = view.findViewById(R.id.restaurantOfTheWeekName)
        restaurantOfTheWeekImage = view.findViewById(R.id.restaurantOfTheWeekImage)


        // Initialize UI components
        topRestaurant1Name = view.findViewById(R.id.topRestaurant1Name)
        topRestaurant2Name = view.findViewById(R.id.topRestaurant2Name)
        topRunnerImage = view.findViewById(R.id.topRunnerImage)
        runnerUpImage = view.findViewById(R.id.runnerUpImage)
        topRestaurant1Bar = view.findViewById(R.id.topRestaurant1Bar)
        topRestaurant2Bar = view.findViewById(R.id.topRestaurant2Bar)
        topRestaurant1Percent = view.findViewById(R.id.topRestaurant1Percent)
        topRestaurant2Percent = view.findViewById(R.id.topRestaurant2Percent)
        othersVotes = view.findViewById(R.id.othersVotes)

        // Initialize RecyclerView
        recyclerView = view.findViewById(R.id.restrauntRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)
        adapter = RestaurantAdapter(restaurantList) { restaurant ->
            showVoteConfirmationDialog(restaurant)
        }
        recyclerView.adapter = adapter



        // Get current user ID
        currentUserId = auth.currentUser?.uid

        if (currentUserId == null) {
            showToast("Please log in to vote!")
            return view
        }

        // Start countdown timer with updated calculation
        weekStartTimestamp = calculateStartOfWeek()
        startCountdownTimer(calculateTimeUntilNextSundayMidnight())
        loadRestaurants()
        fetchVotingData()
        fetchWeeklyWinner()

        return view
    }

    private fun fetchVotingData() {
        db.collection("restaurants")
            .orderBy("votes", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val topRestaurants = snapshot.documents.mapNotNull { it.toObject(Restaurant::class.java) }
                    Log.d("FetchVotingData", "Top restaurants fetched: ${topRestaurants.size}")
                    if (topRestaurants.size >= 2) {
                        val top1 = topRestaurants[0]
                        val top2 = topRestaurants[1]
                        val totalVotes = topRestaurants.sumOf { it.votes }
                        if (totalVotes > 0) {
                            val top1Percent = (top1.votes.toFloat() / totalVotes) * 100
                            val top2Percent = (top2.votes.toFloat() / totalVotes) * 100
                            val othersPercent = 100 - (top1Percent + top2Percent)

                            binding.topRestaurant1Name.text = top1.name
                            binding.topRestaurant2Name.text = top2.name
                            binding.topRestaurant1Percent.text = String.format("%.1f%%", top1Percent)
                            binding.topRestaurant2Percent.text = String.format("%.1f%%", top2Percent)
                            binding.othersVotes.text = "Others: ${String.format("%.1f%%", othersPercent)}"

                            Glide.with(requireContext()).load(top1.imageUrl).into(binding.topRunnerImage)
                            Glide.with(requireContext()).load(top2.imageUrl).into(binding.runnerUpImage)

                            val totalBarWeight = top1Percent + top2Percent
                            val params1 = binding.topRestaurant1Bar.layoutParams as LinearLayout.LayoutParams
                            params1.weight = (top1Percent / totalBarWeight) * 100
                            binding.topRestaurant1Bar.layoutParams = params1

                            val params2 = binding.topRestaurant2Bar.layoutParams as LinearLayout.LayoutParams
                            params2.weight = (top2Percent / totalBarWeight) * 100
                            binding.topRestaurant2Bar.layoutParams = params2
                        } else {
                            resetTopRestaurantsUI()
                        }
                    }
                } else {
                    Log.d("FetchVotingData", "No restaurants found in snapshot")
                }
            }
            .addOnFailureListener { e ->
                Log.e("FetchVotingData", "Error fetching votes: $e")
                showToast("Failed to fetch voting data.")
            }
    }
    private fun resetTopRestaurantsUI() {
        binding.topRestaurant1Name.text = "N/A"
        binding.topRestaurant2Name.text = "N/A"
        binding.topRestaurant1Percent.text = "0%"
        binding.topRestaurant2Percent.text = "0%"
        binding.othersVotes.text = "Others: 0%"
        binding.topRunnerImage.setImageResource(android.R.color.transparent)
        binding.runnerUpImage.setImageResource(android.R.color.transparent)
        val params = binding.topRestaurant1Bar.layoutParams as LinearLayout.LayoutParams
        params.weight = 0f
        binding.topRestaurant1Bar.layoutParams = params
        binding.topRestaurant2Bar.layoutParams = params
    }


    private fun loadRestaurants() {
        restaurantsListener = db.collection("restaurants")
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

    private fun fetchWeeklyWinner() {
        db.collection("weekly_winner").document("latest").get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val winner = document.toObject(WeeklyWinnerRestaurant::class.java)
                    winner?.let {
                        binding.restaurantOfTheWeekName.text = it.name
                        binding.restaurantOfTheWeekVotes.text = "${it.votes} votes"
                        Glide.with(requireContext()).load(it.imageUrl).into(binding.restaurantOfTheWeekImage)
                        Log.d("FetchWeeklyWinner", "Winner fetched: ${it.name}, votes: ${it.votes}")
                    }
                } else {
                    binding.restaurantOfTheWeekName.text = "No winner yet"
                    binding.restaurantOfTheWeekVotes.text = "0 votes"
                    binding.restaurantOfTheWeekImage.setImageResource(android.R.color.transparent)
                    Log.d("FetchWeeklyWinner", "No winner document exists")
                }
            }
            .addOnFailureListener { e ->
                Log.e("FetchWeeklyWinner", "Error fetching winner: $e")
                showToast("Failed to fetch Restaurant of the Week: ${e.message}")
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
            .whereGreaterThanOrEqualTo("timestamp", weekStartTimestamp)
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
                                    fetchVotingData()
                                }
                                .addOnFailureListener { e ->
                                    Log.e("VoteUpdate", "Failed to increment vote: $e")
                                    showToast("Failed to submit vote.")
                                }
                        }
                        .addOnFailureListener { e ->
                            Log.e("VoteSubmit", "Failed to record vote: $e")
                            showToast("Failed to submit vote.")
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("VoteCheck", "Failed to check vote: $e")
                showToast("Error checking your vote.")
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
        if (calendar.timeInMillis > System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, -7)
        }
        return calendar.timeInMillis
    }

    private fun startCountdownTimer(timeInMillis: Long) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(timeInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val days = TimeUnit.MILLISECONDS.toDays(millisUntilFinished)
                val hours = TimeUnit.MILLISECONDS.toHours(millisUntilFinished) % 24
                val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60
                val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                binding.daysTextView.text = String.format("%02d", days)
                binding.hoursTextView.text = String.format("%02d", hours)
                binding.minutesTextView.text = String.format("%02d", minutes)
                binding.secondsTextView.text = String.format("%02d", seconds)
            }

            override fun onFinish() {
                resetVotesAndSetWinner()
                weekStartTimestamp = calculateStartOfWeek()
                startCountdownTimer(calculateTimeUntilNextSundayMidnight())
            }
        }.start()
    }

    private fun calculateTimeUntilNextSundayMidnight(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 7)
        }
        return calendar.timeInMillis - System.currentTimeMillis()
    }
    private fun resetVotesAndSetWinner() {
        val restaurantsRef = db.collection("restaurants")
        val winnerRef = db.collection("weekly_winner").document("latest")

        restaurantsRef.get()
            .addOnSuccessListener { snapshot ->
                val restaurants = snapshot.documents.mapNotNull { it.toObject(Restaurant::class.java)?.apply { id = it.id } }
                if (restaurants.isNotEmpty()) {
                    val highestVotedRestaurant = restaurants.maxByOrNull { it.votes }
                    highestVotedRestaurant?.let { winner ->
                        val winnerData = WeeklyWinnerRestaurant(
                            restaurantId = winner.id,
                            name = winner.name,
                            imageUrl = winner.imageUrl,
                            votes = winner.votes,
                            weekStartTimestamp = Timestamp.now()
                        )
                        winnerRef.set(winnerData)
                            .addOnSuccessListener {
                                Log.d("WinnerSet", "Winner set: ${winner.name}")
                                resetRestaurantVotes(restaurantsRef, snapshot)
                            }
                            .addOnFailureListener { e ->
                                Log.e("WinnerSet", "Failed to set winner: $e")
                                showToast("Failed to set Restaurant of the Week.")
                            }
                    } ?: run {
                        Log.d("WinnerSet", "No votes cast this week.")
                        winnerRef.set(mapOf("name" to "No winner", "votes" to 0, "imageUrl" to ""))
                            .addOnSuccessListener {
                                resetRestaurantVotes(restaurantsRef, snapshot)
                            }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("ResetVotes", "Failed to fetch restaurants: $e")
                showToast("Failed to reset votes.")
            }
    }

    private fun resetRestaurantVotes(restaurantsRef: Query, snapshot: com.google.firebase.firestore.QuerySnapshot) {
        val batch = db.batch()
        val resetTimestamp = Timestamp.now()
        snapshot.documents.forEach { doc ->
            batch.update(doc.reference, mapOf(
                "votes" to 0,
                "lastVoteReset" to resetTimestamp
            ))
        }
        batch.commit()
            .addOnSuccessListener {
                Log.d("ResetVotes", "Restaurant votes reset at $resetTimestamp.")
                clearUserVotes()
            }
            .addOnFailureListener { e ->
                Log.e("ResetVotes", "Failed to reset restaurant votes: $e")
                showToast("Failed to reset votes.")
            }
    }

    private fun clearUserVotes() {
        db.collection("votes").get()
            .addOnSuccessListener { querySnapshot ->
                val batch = db.batch()
                querySnapshot.documents.forEach { document ->
                    batch.delete(document.reference)
                }
                batch.commit()
                    .addOnSuccessListener {
                        Log.d("VotesReset", "All user vote records cleared.")
                        showToast("Votes reset! New week started.")
                        fetchWeeklyWinner()
                        fetchVotingData()
                    }
                    .addOnFailureListener { e ->
                        Log.e("VotesReset", "Failed to clear user votes: $e")
                        showToast("Failed to reset user votes.")
                    }
            }
            .addOnFailureListener { e ->
                Log.e("VotesReset", "Failed to fetch user votes: $e")
            }
    }

    private fun showToast(message: String) {
        context?.let {
            Toast.makeText(it, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
        restaurantsListener?.remove()
        _binding = null
    }
}

