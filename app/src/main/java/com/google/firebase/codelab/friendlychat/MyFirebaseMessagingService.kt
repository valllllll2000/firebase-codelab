/**
 * Copyright Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.codelab.friendlychat

import android.util.Log

import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(s: String?) {
        // If you need to handle the generation of a token, initially or
        // after a refresh this is where you should do that.
        val token = FirebaseInstanceId.getInstance().token
        Log.i(TAG, "FCM Token: " + token!!)
        Log.i(TAG, "onNewToken string: " + s!!)

        // Once a token is generated, we subscribe to topic.
        FirebaseMessaging.getInstance()
                .subscribeToTopic(FRIENDLY_ENGAGE_TOPIC)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage?) {
        // Handle data payload of FCM messages.
        Log.d(TAG, "FCM Message Id: " + remoteMessage!!.messageId!!)
        Log.d(TAG, "FCM Notification Message: " + remoteMessage.notification!!)
        Log.d(TAG, "FCM Data Message: " + remoteMessage.data)
    }

    companion object {

        private const val TAG = "MyFMService"
        private const val FRIENDLY_ENGAGE_TOPIC = "friendly_engage"
    }

}
