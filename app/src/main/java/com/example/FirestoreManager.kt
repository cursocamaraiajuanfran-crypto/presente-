package com.example

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions

object FirestoreManager {
    private const val TAG = "FirestoreManager"
    
    // Lazy initialization of FirebaseFirestore to prevent instant crashes if Google Services is unconfigured
    private val db: FirebaseFirestore? by lazy {
        try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Firestore failed to initialize. Google Services might be missing.", e)
            null
        }
    }

    // Flag to check if Firestore is available and ready
    val isAvailable: Boolean
        get() = db != null

    private var roomListener: ListenerRegistration? = null
    private var participantsListener: ListenerRegistration? = null

    /**
     * Creates a new room document in the Firestore "rooms" collection.
     */
    fun createRoom(
        roomCode: String,
        monitorName: String,
        latitude: Double,
        longitude: Double,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val firestore = db
        if (firestore == null) {
            onFailure(IllegalStateException("Firestore is not available (google-services.json might be missing)"))
            return
        }

        val roomData = hashMapOf(
            "roomCode" to roomCode,
            "monitorName" to monitorName,
            "centerLatitude" to latitude,
            "centerLongitude" to longitude,
            "isCallActive" to false,
            "callTimestamp" to 0L,
            "createdTimestamp" to System.currentTimeMillis()
        )

        firestore.collection("rooms")
            .document(roomCode)
            .set(roomData, SetOptions.merge())
            .addOnSuccessListener {
                Log.d(TAG, "Room $roomCode created successfully in Firestore.")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error creating room $roomCode", e)
                onFailure(e)
            }
    }

    /**
     * Adds or updates a participant in the "participants" subcollection of a room.
     */
    fun updateParticipantState(
        roomCode: String,
        nickname: String,
        distanceInMeters: Float,
        isPresent: Boolean,
        isSimulated: Boolean,
        angle: Float
    ) {
        val firestore = db ?: return

        val participantData = hashMapOf(
            "nickname" to nickname,
            "distanceInMeters" to distanceInMeters,
            "isPresent" to isPresent,
            "isSimulated" to isSimulated,
            "angle" to angle,
            "lastUpdated" to System.currentTimeMillis()
        )

        firestore.collection("rooms")
            .document(roomCode)
            .collection("participants")
            .document(nickname)
            .set(participantData, SetOptions.merge())
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating participant state for $nickname in room $roomCode", e)
            }
    }

    /**
     * Sets the isCallActive broadcast trigger in the room document.
     */
    fun triggerCall(roomCode: String, isActive: Boolean, onSuccess: () -> Unit = {}, onFailure: (Exception) -> Unit = {}) {
        val firestore = db ?: return

        val updateData = hashMapOf(
            "isCallActive" to isActive,
            "callTimestamp" to System.currentTimeMillis()
        )

        firestore.collection("rooms")
            .document(roomCode)
            .update(updateData as Map<String, Any>)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error triggering call in room $roomCode", e)
                onFailure(e)
            }
    }

    /**
     * Toggles a participant's attendance (Present / Absent) in the subcollection.
     */
    fun updateAttendance(roomCode: String, nickname: String, isPresent: Boolean) {
        val firestore = db ?: return

        firestore.collection("rooms")
            .document(roomCode)
            .collection("participants")
            .document(nickname)
            .update("isPresent", isPresent)
            .addOnFailureListener { e ->
                Log.e(TAG, "Error updating attendance for $nickname", e)
            }
    }

    /**
     * Listens in real-time to Room data and its subcollection of participants.
     */
    fun startListeningToRoom(
        roomCode: String,
        onRoomUpdate: (monitor: String, centerLat: Double, centerLon: Double, isCallActive: Boolean, callTime: Long) -> Unit,
        onParticipantsUpdate: (List<ParticipantState>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val firestore = db
        if (firestore == null) {
            onError(IllegalStateException("Firestore is not available"))
            return
        }

        // Clean up any existing listeners first
        stopListening()

        // 1. Listen to Room document
        roomListener = firestore.collection("rooms")
            .document(roomCode)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e(TAG, "Error listening to room $roomCode", e)
                    onError(e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val monitor = snapshot.getString("monitorName") ?: "Monitor"
                    val centerLat = snapshot.getDouble("centerLatitude") ?: 40.416775
                    val centerLon = snapshot.getDouble("centerLongitude") ?: -3.703790
                    val isCall = snapshot.getBoolean("isCallActive") ?: false
                    val callTime = snapshot.getLong("callTimestamp") ?: 0L

                    onRoomUpdate(monitor, centerLat, centerLon, isCall, callTime)
                }
            }

        // 2. Listen to Participants subcollection
        participantsListener = firestore.collection("rooms")
            .document(roomCode)
            .collection("participants")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.e(TAG, "Error listening to participants of room $roomCode", e)
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val participantList = mutableListOf<ParticipantState>()
                    for (doc in snapshots.documents) {
                        val nickname = doc.getString("nickname") ?: doc.id
                        val distance = doc.getDouble("distanceInMeters")?.toFloat() ?: 0f
                        val isPresent = doc.getBoolean("isPresent") ?: false
                        val isSimulated = doc.getBoolean("isSimulated") ?: false
                        val angle = doc.getDouble("angle")?.toFloat() ?: 0f
                        val lastUpdated = doc.getLong("lastUpdated") ?: System.currentTimeMillis()

                        participantList.add(
                            ParticipantState(
                                nickname = nickname,
                                distanceInMeters = distance,
                                isPresent = isPresent,
                                isSimulated = isSimulated,
                                angle = angle,
                                lastUpdated = lastUpdated
                            )
                        )
                    }
                    onParticipantsUpdate(participantList)
                }
            }
    }

    /**
     * Removes a participant from the "participants" subcollection.
     */
    fun removeParticipant(roomCode: String, nickname: String) {
        val firestore = db ?: return
        firestore.collection("rooms")
            .document(roomCode)
            .collection("participants")
            .document(nickname)
            .delete()
            .addOnSuccessListener {
                Log.d(TAG, "Participant $nickname cleanly removed from Firestore room $roomCode.")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error removing participant $nickname from room $roomCode", e)
            }
    }

    /**
     * Detaches all active Firestore real-time listeners.
     */
    fun stopListening() {
        roomListener?.remove()
        roomListener = null
        participantsListener?.remove()
        participantsListener = null
        Log.d(TAG, "Firestore listeners detached.")
    }
}
