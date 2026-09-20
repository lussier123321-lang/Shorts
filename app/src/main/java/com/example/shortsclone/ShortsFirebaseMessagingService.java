package com.example.shortsclone;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ShortsFirebaseMessagingService extends FirebaseMessagingService {
    static final String CHANNEL="shorts_social";
    @Override public void onNewToken(String token){
        if(FirebaseAuth.getInstance().getCurrentUser()!=null){
            FirebaseFirestore.getInstance().collection("users").document(FirebaseAuth.getInstance().getCurrentUser().getUid())
                .update("fcmTokens", FieldValue.arrayUnion(token));
        }
    }
    @Override public void onMessageReceived(RemoteMessage m){
        String title=m.getNotification()!=null?m.getNotification().getTitle():"Shorts";
        String body=m.getNotification()!=null?m.getNotification().getBody():"You have a new notification";
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26) nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Shorts notifications",NotificationManager.IMPORTANCE_DEFAULT));
        Intent i=new Intent(this,MainActivity.class); i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(this,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b=new NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body).setAutoCancel(true).setContentIntent(pi);
        nm.notify((int)System.currentTimeMillis(),b.build());
    }
}
