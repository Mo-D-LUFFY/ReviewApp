import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.myapplication.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import model.DishReview

class ReviewAdapter(
    private var reviewList: List<DishReview>,
    private val context: Context
) : RecyclerView.Adapter<ReviewAdapter.ReviewViewHolder>() {

    private val firestore = FirebaseFirestore.getInstance()
    private val userId: String? = FirebaseAuth.getInstance().currentUser?.uid

    class ReviewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dishName: TextView = itemView.findViewById(R.id.Dish_name)
        val restaurantName: TextView = itemView.findViewById(R.id.Restraunt_Name)
        val userName: TextView = itemView.findViewById(R.id.User_name)
        val reviewText: TextView = itemView.findViewById(R.id.Review_text)
        val foodImage: ImageView = itemView.findViewById(R.id.Food_image)
        val priceOnCard: TextView = itemView.findViewById(R.id.priceOnCard)
        val rating: RatingBar = itemView.findViewById(R.id.rating)
        val userProfilePic: ImageView = itemView.findViewById(R.id.User_profile_pic)
        val likeBtn: ImageView = itemView.findViewById(R.id.like_btn)
        val likeCount: TextView = itemView.findViewById(R.id.like_count)
        val deleteBtn: ImageView = itemView.findViewById(R.id.deleteBtn) // Delete button
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.card_feed, parent, false)
        return ReviewViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        val review = reviewList[position]

        holder.dishName.text = review.dishName
        holder.restaurantName.text = review.restaurantName
        holder.userName.text = review.userName
        holder.reviewText.text = review.reviewText
        holder.priceOnCard.text = "Rs.${review.price}"
        holder.rating.rating = review.rating.toFloat()
        holder.likeCount.text = review.likes.toString()

        Glide.with(holder.itemView.context)
            .load(review.imageUrl)
            .into(holder.foodImage)

        Glide.with(holder.itemView.context)
            .load(review.userProfilePic)
            .into(holder.userProfilePic)

        // Set initial like button state based on isLikedByUser
        holder.likeBtn.setImageResource(if (review.isLikedByUser) R.drawable.liked_ic else R.drawable.like_ic)

        holder.likeBtn.setOnClickListener {
            userId ?: return@setOnClickListener // If userId is null, return early

            val mutableLikedBy = review.likedBy.toMutableList()

            if (review.isLikedByUser) {
                // User has already liked the review, so unlike it
                mutableLikedBy.remove(userId)
                review.likes -= 1
                holder.likeBtn.setImageResource(R.drawable.like_ic) // Change button state
            } else {
                // User has not liked the review, so like it
                mutableLikedBy.add(userId)
                review.likes += 1
                holder.likeBtn.setImageResource(R.drawable.liked_ic) // Change button state
            }

            // Update Firestore document with new likedBy list and likes count
            handleLikeClick(review.id, mutableLikedBy, review.likes)

            // Update UI
            holder.likeCount.text = review.likes.toString()
            review.isLikedByUser = !review.isLikedByUser // Toggle like status
        }

        // Handle delete button functionality
        holder.deleteBtn.setOnClickListener {
            if (userId == review.userId) {
                // Show confirmation dialog before deleting
                showDeleteConfirmationDialog(review.id, holder.adapterPosition)
            } else {
                // User is not the owner of the review
                Toast.makeText(context, "You can only delete your own reviews.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun getItemCount() = reviewList.size

    fun updateReviews(newReviews: List<DishReview>) {
        reviewList = newReviews
        notifyDataSetChanged()
    }

    private fun handleLikeClick(reviewId: String, likedBy: List<String>, likes: Int) {
        val reviewRef = firestore.collection("dishReview").document(reviewId)
        reviewRef.update("likedBy", likedBy, "likes", likes)
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to update like count: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun handleDeleteReview(reviewId: String) {
        val reviewRef = firestore.collection("dishReview").document(reviewId)
        reviewRef.delete()
            .addOnSuccessListener {
                // Remove the review from the list and update the adapter
                reviewList = reviewList.filter { review -> review.id != reviewId }
                notifyDataSetChanged()
                Toast.makeText(context, "Review deleted successfully.", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed to delete review: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDeleteConfirmationDialog(reviewId: String, position: Int) {
        AlertDialog.Builder(context)
            .setTitle("Delete Review")
            .setMessage("Do you really want to delete this review?")
            .setPositiveButton("Delete") { dialog, _ ->
                handleDeleteReview(reviewId)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }
}
