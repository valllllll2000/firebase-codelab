package com.google.firebase.codelab.friendlychat

import android.content.Context
import android.graphics.drawable.Drawable
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.bumptech.glide.Glide
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.google.firebase.appindexing.Action
import com.google.firebase.appindexing.FirebaseAppIndex
import com.google.firebase.appindexing.FirebaseUserActions
import com.google.firebase.appindexing.Indexable
import com.google.firebase.appindexing.builders.Indexables
import com.google.firebase.storage.FirebaseStorage
import de.hdodenhof.circleimageview.CircleImageView

class FriendlyMessageAdapter(
    options: FirebaseRecyclerOptions<FriendlyMessage>,
    private val mUsername: String?,
    private val mProgressBar: ProgressBar?) :
    FirebaseRecyclerAdapter<FriendlyMessage, FriendlyMessageAdapter.MessageViewHolder>(options) {
    private val defaultProfileDrawable: Drawable?

    init {
        defaultProfileDrawable = ContextCompat.getDrawable(
            mProgressBar!!.context,
            R.drawable.ic_account_circle_black_36dp
        )
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): MessageViewHolder {
        val inflater = LayoutInflater.from(viewGroup.context)
        return MessageViewHolder(
            inflater.inflate(
                R.layout.item_message,
                viewGroup,
                false
            )
        )
    }

    override fun onBindViewHolder(viewHolder: MessageViewHolder, position: Int, friendlyMessage: FriendlyMessage) {
        mProgressBar!!.visibility = ProgressBar.INVISIBLE
        if (friendlyMessage.text != null) {
            viewHolder.displayText(friendlyMessage)
        } else if (friendlyMessage.imageUrl != null) {
            viewHolder.displayImage(friendlyMessage)
        }

        viewHolder.displayUserName(friendlyMessage)
        viewHolder.displayUserPicture(friendlyMessage, defaultProfileDrawable)

        if (friendlyMessage.text != null) {
            // write this message to the on-device index
            FirebaseAppIndex.getInstance()
                .update(getMessageIndexable(friendlyMessage))
        }
        // log a view action on it
        FirebaseUserActions.getInstance().end(getMessageViewAction(friendlyMessage))
    }

    private fun getMessageIndexable(friendlyMessage: FriendlyMessage): Indexable {
        val sender = Indexables.personBuilder()
            .setIsSelf(mUsername == friendlyMessage.name)
            .setName(friendlyMessage.name!!)
            .setUrl(MainActivity.MESSAGE_URL + (friendlyMessage.id!! + "/sender"))

        val recipient = Indexables.personBuilder()
            .setName(mUsername!!)
            .setUrl(MainActivity.MESSAGE_URL + (friendlyMessage.id!! + "/recipient"))

        return Indexables.messageBuilder()
            .setName(friendlyMessage.text!!)
            .setUrl(MainActivity.MESSAGE_URL + friendlyMessage.id)
            .setSender(sender)
            .setRecipient(recipient)
            .build()
    }

    private fun getMessageViewAction(friendlyMessage: FriendlyMessage): Action {
        return Action.Builder(Action.Builder.VIEW_ACTION)
            .setObject(friendlyMessage.name!!, MainActivity.MESSAGE_URL + friendlyMessage.id)
            .setMetadata(Action.Metadata.Builder().setUpload(false))
            .build()
    }

    class MessageViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        private var messageTextView: TextView =
            itemView.findViewById<View>(R.id.messageTextView) as TextView
        private var messageImageView: ImageView =
            itemView.findViewById<View>(R.id.messageImageView) as ImageView
        private var messengerTextView: TextView =
            itemView.findViewById<View>(R.id.messengerTextView) as TextView
        private var messengerImageView: CircleImageView =
            itemView.findViewById<View>(R.id.messengerImageView) as CircleImageView

        internal fun displayText(friendlyMessage: FriendlyMessage) {
            messageTextView.text = friendlyMessage.text
            messageTextView.visibility = TextView.VISIBLE
            messageImageView.visibility = ImageView.GONE
        }

        internal fun displayImage(friendlyMessage: FriendlyMessage) {
            val imageUrl = friendlyMessage.imageUrl
            if (imageUrl!!.startsWith("gs://")) {
                val storageReference = FirebaseStorage.getInstance().getReferenceFromUrl(imageUrl)
                storageReference.downloadUrl.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val downloadUrl = task.result!!.toString()
                        loadImage(messageImageView.context, downloadUrl, messageImageView)
                    } else {
                        Log.w(TAG, "Getting download url was not successful.", task.exception)
                    }
                }
            } else {
                loadImage(messageImageView.context, friendlyMessage.imageUrl, messageImageView)
            }
            messageImageView.visibility = ImageView.VISIBLE
            messageTextView.visibility = TextView.GONE
        }

        internal fun displayUserName(friendlyMessage: FriendlyMessage) {
            messengerTextView.text = friendlyMessage.name
        }

        internal fun displayUserPicture(friendlyMessage: FriendlyMessage, defaultDrawable: Drawable?) {
            if (friendlyMessage.userPhotoUrl == null) {
                messengerImageView.setImageDrawable(defaultDrawable)
            } else {
                loadImage(messengerImageView.context, friendlyMessage.userPhotoUrl, messengerImageView)
            }
        }

        private fun loadImage(context: Context, url: String?, imageView: ImageView) {
            Glide.with(context).load(url).into(imageView)
        }
    }

    companion object {

        private const val TAG = "Adapter"
    }
}