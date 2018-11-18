/**
 * Copyright Google Inc. All Rights Reserved.
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.codelab.friendlychat

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.firebase.ui.database.SnapshotParser
import com.google.android.gms.appinvite.AppInviteInvitation
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

class MainActivity : AppCompatActivity(), GoogleApiClient.OnConnectionFailedListener {

    private var mUsername: String? = null
    private var userPhotoUrl: String? = null
    private var mSharedPreferences: SharedPreferences? = null
    private var mGoogleApiClient: GoogleApiClient? = null

    private var mLinearLayoutManager: LinearLayoutManager? = null

    // Firebase instance variables
    private var mFirebaseAuth: FirebaseAuth? = null
    private var mFirebaseUser: FirebaseUser? = null
    private var mFirebaseDatabaseReference: DatabaseReference? = null
    private var mFirebaseAdapter: FirebaseRecyclerAdapter<FriendlyMessage, FriendlyMessageAdapter.MessageViewHolder>? =
        null
    private var mFirebaseRemoteConfig: FirebaseRemoteConfig? = null
    private var mFirebaseAnalytics: FirebaseAnalytics? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        // Set default username is anonymous.
        mUsername = ANONYMOUS

        mGoogleApiClient = GoogleApiClient.Builder(this)
            .enableAutoManage(this, this)
            .addApi(Auth.GOOGLE_SIGN_IN_API)
            .build()

        // Initialize Firebase Auth
        mFirebaseAuth = FirebaseAuth.getInstance()
        mFirebaseUser = mFirebaseAuth!!.currentUser
        if (mFirebaseUser == null) {
            // Not signed in, launch the Sign In activity
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
            return
        } else {
            mUsername = mFirebaseUser!!.displayName
            if (mFirebaseUser!!.photoUrl != null) {
                userPhotoUrl = mFirebaseUser!!.photoUrl!!.toString()
            }
        }

        mLinearLayoutManager = LinearLayoutManager(this)
        mLinearLayoutManager!!.stackFromEnd = true
        messageRecyclerView.layoutManager = mLinearLayoutManager

        // Initialize Firebase Remote Config.
        mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

        // Define Firebase Remote Config Settings.
        val firebaseRemoteConfigSettings = FirebaseRemoteConfigSettings.Builder()
            .setDeveloperModeEnabled(true)
            .build()

        // Define default config values. Defaults are used when fetched config values are not
        // available. Eg: if an error occurred fetching values from the server.
        val defaultConfigMap = HashMap<String, Any>()
        defaultConfigMap["friendly_msg_length"] = 10L

        // Apply config settings and default values.
        mFirebaseRemoteConfig!!.setConfigSettings(firebaseRemoteConfigSettings)
        mFirebaseRemoteConfig!!.setDefaults(defaultConfigMap)

        // Fetch remote config.
        fetchConfig()

        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this)

        // New child entries
        mFirebaseDatabaseReference = FirebaseDatabase.getInstance().reference
        val parser = SnapshotParser { dataSnapshot ->
            val friendlyMessage = dataSnapshot.getValue(FriendlyMessage::class.java)
            friendlyMessage?.id = dataSnapshot.key
            friendlyMessage!!
        }

        val messagesRef = mFirebaseDatabaseReference!!.child(MESSAGES_CHILD)
        val options = FirebaseRecyclerOptions.Builder<FriendlyMessage>()
            .setQuery(messagesRef, parser)
            .build()
        mFirebaseAdapter =
                FriendlyMessageAdapter(
                    options, mUsername, progressBar
                )

        mFirebaseAdapter!!.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                val friendlyMessageCount = mFirebaseAdapter!!.itemCount
                val lastVisiblePosition =
                    mLinearLayoutManager!!.findLastCompletelyVisibleItemPosition()
                // If the recycler view is initially being loaded or the
                // user is at the bottom of the list, scroll to the bottom
                // of the list to show the newly added message.
                if (lastVisiblePosition == -1 || positionStart >= friendlyMessageCount - 1 && lastVisiblePosition == positionStart - 1) {
                    messageRecyclerView.scrollToPosition(positionStart)
                }
            }
        })

        messageRecyclerView.adapter = mFirebaseAdapter

        messageEditText.filters = arrayOf<InputFilter>(
            InputFilter.LengthFilter(
                mSharedPreferences!!
                    .getInt(CodelabPreferences.FRIENDLY_MSG_LENGTH, DEFAULT_MSG_LENGTH_LIMIT)
            )
        )
        messageEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}

            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                sendButton.isEnabled = charSequence.toString().trim { it <= ' ' }.isNotEmpty()
            }

            override fun afterTextChanged(editable: Editable) {}
        })

        sendButton.setOnClickListener {
            // Send messages on click.
            val friendlyMessage =
                FriendlyMessage(text = messageEditText.text.toString(), name = mUsername, userPhotoUrl = userPhotoUrl)
            mFirebaseDatabaseReference!!.child(MESSAGES_CHILD).push().setValue(friendlyMessage)
            messageEditText!!.setText("")
        }

        addMessageImageView.setOnClickListener {
            // Select image for image message on click.
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "image/*"
            startActivityForResult(intent, REQUEST_IMAGE)
        }
    }

    // Fetch the config to determine the allowed length of messages.
    private fun fetchConfig() {
        var cacheExpiration: Long = 3600 // 1 hour in seconds
        // If developer mode is enabled reduce cacheExpiration to 0 so that
        // each fetch goes to the server. This should not be used in release
        // builds.
        if (mFirebaseRemoteConfig!!.info.configSettings
                .isDeveloperModeEnabled
        ) {
            cacheExpiration = 0
        }
        mFirebaseRemoteConfig!!.fetch(cacheExpiration)
            .addOnSuccessListener {
                // Make the fetched config available via
                // FirebaseRemoteConfig get<type> calls.
                mFirebaseRemoteConfig!!.activateFetched()
                applyRetrievedLengthLimit()
            }
            .addOnFailureListener { e ->
                // There has been an error fetching the config
                Log.w(TAG, "Error fetching config: ${e.message}")
                applyRetrievedLengthLimit()
            }
    }

    /**
     * Apply retrieved length limit to edit text field.
     * This result may be fresh from the server or it may be from cached
     * values.
     */
    private fun applyRetrievedLengthLimit() {
        val friendlyMsgLength = mFirebaseRemoteConfig!!.getLong("friendly_msg_length")
        messageEditText.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(friendlyMsgLength.toInt()))
        Log.d(TAG, "FML is: $friendlyMsgLength")
    }

    public override fun onStart() {
        super.onStart()
        // Check if user is signed in.
        // TODO: Add code to check if user is signed in.
    }

    public override fun onPause() {
        mFirebaseAdapter!!.stopListening()
        super.onPause()
    }

    public override fun onResume() {
        super.onResume()
        mFirebaseAdapter!!.startListening()
    }

    public override fun onDestroy() {
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.invite_menu -> {
                sendInvitation()
                return true
            }
            R.id.fresh_config_menu -> {
                fetchConfig()
                return true
            }
            R.id.sign_out_menu -> {
                signOutAndFinish()
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    private fun signOutAndFinish() {
        mFirebaseAuth!!.signOut()
        Auth.GoogleSignInApi.signOut(mGoogleApiClient)
        mUsername = ANONYMOUS
        startActivity(Intent(this, SignInActivity::class.java))
        finish()
    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        Log.d(TAG, "onConnectionFailed:$connectionResult")
        Toast.makeText(this, "Google Play Services error.", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "onActivityResult: requestCode=$requestCode, resultCode=$resultCode")

        when (requestCode) {
            REQUEST_IMAGE ->
                if (resultCode == Activity.RESULT_OK && data != null) {
                    onImageReceived(data)
            }
            REQUEST_INVITE ->
                if (resultCode == RESULT_OK) {
                    onInviteSuccessful(data, resultCode)
                } else {
                    // Sending failed or it was canceled, show failure message to the user
                    Log.d(TAG, "Failed to send invitation.")
                }
        }
    }

    private fun onInviteSuccessful(data: Intent?, resultCode: Int) {
        val payload = Bundle()
        payload.putString(FirebaseAnalytics.Param.VALUE, "sent")
        mFirebaseAnalytics?.logEvent(FirebaseAnalytics.Event.SHARE, payload)
        // Check how many invitations were sent and log.
        val ids = data?.let { AppInviteInvitation.getInvitationIds(resultCode, it) } ?: emptyArray()
        Log.d(TAG, "Invitations sent: ${ids.size}")
    }

    private fun onImageReceived(data: Intent) {
        val uri = data.data
        Log.d(TAG, "Uri: ${uri!!}")

        val tempMessage = FriendlyMessage(name = mUsername, userPhotoUrl = userPhotoUrl, imageUrl = LOADING_IMAGE_URL)
        mFirebaseDatabaseReference!!.child(MESSAGES_CHILD).push()
            .setValue(tempMessage) { databaseError, databaseReference ->
                if (databaseError == null) {
                    val key = databaseReference.key
                    val storageReference = FirebaseStorage.getInstance()
                        .getReference(mFirebaseUser!!.uid)
                        .child(key!!)
                        .child(uri.lastPathSegment!!)

                    putImageInStorage(storageReference, uri, key)
                } else {
                    Log.w(TAG, "Unable to write message to database.", databaseError.toException())
                }
            }
    }

    private fun putImageInStorage(storageReference: StorageReference, uri: Uri?, key: String?) {
        storageReference.putFile(uri!!).addOnCompleteListener(
            this@MainActivity
        ) { task ->
            if (task.isSuccessful) {
                val friendlyMessage = FriendlyMessage(
                    name = mUsername,
                    userPhotoUrl = userPhotoUrl,
                    imageUrl = task.result!!.metadata!!.reference!!
                        .downloadUrl.toString()
                )
                mFirebaseDatabaseReference!!.child(MESSAGES_CHILD).child(key!!)
                    .setValue(friendlyMessage)
            } else {
                Log.w(
                    TAG, "Image upload task was not successful.",
                    task.exception
                )
            }
        }
    }

    private fun sendInvitation() {
        val intent = AppInviteInvitation.IntentBuilder(getString(R.string.invitation_title))
            .setMessage(getString(R.string.invitation_message))
            .setCallToActionText(getString(R.string.invitation_cta))
            .build()
        startActivityForResult(intent, REQUEST_INVITE)
    }

    companion object {

        private const val TAG = "MainActivity"
        const val MESSAGES_CHILD = "messages"
        private const val REQUEST_INVITE = 1
        private const val REQUEST_IMAGE = 2
        private const val LOADING_IMAGE_URL = "https://www.google.com/images/spin-32.gif"
        const val DEFAULT_MSG_LENGTH_LIMIT = 10
        const val ANONYMOUS = "anonymous"
        private const val MESSAGE_SENT_EVENT = "message_sent"
        const val MESSAGE_URL = "http://friendlychat.firebase.google.com/message/"
    }
}
