package model
import com.google.firebase.firestore.IgnoreExtraProperties
import java.io.Serializable

@IgnoreExtraProperties
data class HomeCards(
    val dishName: String = "",
    val imageUrl: String = "",
    val restaurantName: String = "",
    val reviewText: String ="",
    val likes:Int =0,
    val rating:Int =0,
    val price:Int =0,

):Serializable