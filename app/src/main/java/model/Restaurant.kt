package model

import com.google.firebase.Timestamp

data class Restaurant(
    var id: String = "",
    var name: String = "",
    var imageUrl: String = "",
    var votes: Int = 0,
    var lastVoteReset: Timestamp? = null
) {
    // Note: All fields are var due to Firestore's requirement for setters during deserialization.
}