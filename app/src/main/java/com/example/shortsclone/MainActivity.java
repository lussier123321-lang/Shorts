package com.example.shortsclone;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;
import com.google.firebase.messaging.FirebaseMessaging;
import com.squareup.picasso.Picasso;

import java.util.*;

public class MainActivity extends AppCompatActivity {
    final int WHITE=Color.WHITE, GRAY=0xffaaaaaa, BLACK=Color.BLACK, DARK=0xff101010;
    FirebaseAuth auth; FirebaseFirestore db; FirebaseStorage storage;
    LinearLayout root, content; TextView title; Uri selectedVideo;
    ActivityResultLauncher<String> videoPicker;

    @Override public void onCreate(Bundle b){ super.onCreate(b); getWindow().setStatusBarColor(BLACK); getWindow().setNavigationBarColor(BLACK);
        videoPicker=registerForActivityResult(new ActivityResultContracts.GetContent(), uri->{ if(uri!=null){selectedVideo=uri; uploadVideo(uri);} });
        if(!initFirebase()){ setupScreen(); return; }
        auth=FirebaseAuth.getInstance(); db=FirebaseFirestore.getInstance(); storage=FirebaseStorage.getInstance();
        if(auth.getCurrentUser()==null) authScreen(); else { registerPushToken(); mainShell("Home"); }
    }

    boolean initFirebase(){
        try{
            if(FirebaseApp.getApps(this).isEmpty()){
                String key=getString(R.string.firebase_api_key), app=getString(R.string.firebase_app_id), project=getString(R.string.firebase_project_id), bucket=getString(R.string.firebase_storage_bucket), sender=getString(R.string.firebase_gcm_sender_id);
                if(key.startsWith("REPLACE")||app.startsWith("REPLACE")||project.startsWith("REPLACE")) return false;
                FirebaseOptions o=new FirebaseOptions.Builder().setApiKey(key).setApplicationId(app).setProjectId(project).setStorageBucket(bucket).setGcmSenderId(sender).build();
                FirebaseApp.initializeApp(this,o);
            }
            return true;
        }catch(Exception e){ return false; }
    }

    void setupScreen(){
        base(); title.setText("Shorts backend setup");
        TextView t=txt("Connect Firebase to turn this into a real online social video app.\n\n1. Create a Firebase project.\n2. Add an Android app with package: com.example.shortsclone\n3. Enable Email/Password Authentication, Firestore Database, and Storage.\n4. Copy the values from your Firebase Android app into app/src/main/res/values/strings.xml.\n5. Build and install again.\n\nThe project already contains Firestore/Storage rules and the complete client data model.",16,WHITE); t.setPadding(28,30,28,20); content.addView(t,new LinearLayout.LayoutParams(-1,-2));
    }

    void base(){ root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BLACK); setContentView(root); title=txt("Shorts",22,WHITE); title.setGravity(Gravity.CENTER_VERTICAL); title.setTypeface(null,1); root.addView(title,new LinearLayout.LayoutParams(-1,64)); content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); root.addView(content,new LinearLayout.LayoutParams(-1,0,1)); }
    void clear(String s){ content.removeAllViews(); title.setText(s); }
    TextView txt(String s,float size,int c){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(c);t.setPadding(14,8,14,8);return t;}
    Button btn(String s){Button b=new Button(this);b.setText(s);b.setTextColor(WHITE);b.setTextSize(13);b.setAllCaps(false);b.setBackgroundColor(0xff202020);return b;}
    EditText edit(String hint){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(GRAY);e.setTextColor(WHITE);e.setSingleLine();e.setPadding(18,8,18,8);return e;}
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setPadding(8,8,8,8);return l;}

    void authScreen(){
        base(); title.setText("Shorts"); content.setPadding(24,60,24,20);
        TextView h=txt("Your short-video community",28,WHITE); h.setTypeface(null,1); content.addView(h);
        EditText email=edit("Email"); content.addView(email,new LinearLayout.LayoutParams(-1,58));
        EditText pass=edit("Password"); pass.setInputType(0x81); content.addView(pass,new LinearLayout.LayoutParams(-1,58));
        EditText username=edit("Username (for sign up)"); content.addView(username,new LinearLayout.LayoutParams(-1,58));
        Button login=btn("Log in"); Button signup=btn("Create account"); content.addView(login,new LinearLayout.LayoutParams(-1,56)); content.addView(signup,new LinearLayout.LayoutParams(-1,56));
        TextView note=txt("No ads. Your videos live in your Firebase Storage bucket and metadata/comments/follows live in Firestore.",13,GRAY); content.addView(note);
        login.setOnClickListener(v->auth.signInWithEmailAndPassword(email.getText().toString().trim(),pass.getText().toString()).addOnSuccessListener(x->mainShell("Home")).addOnFailureListener(e->toast(e.getMessage())));
        signup.setOnClickListener(v->{String em=email.getText().toString().trim(), pw=pass.getText().toString(), un=username.getText().toString().trim(); if(un.isEmpty()){toast("Enter a username");return;} auth.createUserWithEmailAndPassword(em,pw).addOnSuccessListener(x->{String uid=x.getUser().getUid(); Map<String,Object> u=new HashMap<>();u.put("username",un);u.put("displayName",un);u.put("bio","");u.put("photoUrl","");u.put("followers",0L);u.put("following",0L);u.put("createdAt",FieldValue.serverTimestamp());db.collection("users").document(uid).set(u).addOnSuccessListener(y->{ registerPushToken(); mainShell("Home"); });}).addOnFailureListener(e->toast(e.getMessage()));});
    }

    void mainShell(String page){
        base();
        LinearLayout nav=new LinearLayout(this); nav.setOrientation(LinearLayout.HORIZONTAL); nav.setPadding(4,4,4,4);
        String[] names={"Home","Search","Upload","Notifications","Profile"}; for(String n:names){Button b=btn(n); nav.addView(b,new LinearLayout.LayoutParams(0,52,1)); b.setOnClickListener(v->{if(n.equals("Home")) feed(); else if(n.equals("Search")) search(); else if(n.equals("Upload")) videoPicker.launch("video/*"); else if(n.equals("Notifications")) notifications(); else profile(auth.getCurrentUser().getUid());});}
        root.addView(nav,new LinearLayout.LayoutParams(-1,60)); if(page.equals("Home")) feed();
    }

    void feed(){ clear("For You");
        LinearLayout tabs=row(); Button forYou=btn("For You"); Button following=btn("Following"); tabs.addView(forYou,new LinearLayout.LayoutParams(0,52,1)); tabs.addView(following,new LinearLayout.LayoutParams(0,52,1)); content.addView(tabs);
        ProgressBar p=new ProgressBar(this); content.addView(p);
        forYou.setOnClickListener(v->loadForYou()); following.setOnClickListener(v->loadFollowing());
        loadForYou();
    }
    void loadForYou(){
        removeFeedBelowTabs(); ProgressBar p=new ProgressBar(this); content.addView(p);
        db.collection("videos").orderBy("recommendationScore",Query.Direction.DESCENDING).limit(40).get().addOnSuccessListener(s->{content.removeView(p); if(s.isEmpty()){content.addView(txt("No videos yet. Upload the first one!",18,WHITE));return;} for(DocumentSnapshot d:s) videoCard(d);}).addOnFailureListener(e->{content.removeView(p); db.collection("videos").orderBy("createdAt",Query.Direction.DESCENDING).limit(30).get().addOnSuccessListener(s->{for(DocumentSnapshot d:s)videoCard(d);});});
    }
    void loadFollowing(){
        removeFeedBelowTabs(); String uid=auth.getCurrentUser().getUid();
        db.collection("users").document(uid).collection("followingFeed").orderBy("createdAt",Query.Direction.DESCENDING).limit(50).get().addOnSuccessListener(s->{if(s.isEmpty()){content.addView(txt("Follow creators to build your Following feed.",16,WHITE));return;} for(DocumentSnapshot f:s){db.collection("videos").document(f.getString("videoId")).get().addOnSuccessListener(v->{if(v.exists())videoCard(v);});}});
    }
    void removeFeedBelowTabs(){ while(content.getChildCount()>1) content.removeViewAt(content.getChildCount()-1); }
    void registerPushToken(){ FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token->db.collection("users").document(auth.getCurrentUser().getUid()).update("fcmTokens",FieldValue.arrayUnion(token))); if(Build.VERSION.SDK_INT>=33) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},42); }

    void videoCard(DocumentSnapshot d){
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(8,10,8,18); card.setBackgroundColor(DARK);
        TextView meta=txt("@"+d.getString("username"),16,WHITE); meta.setTypeface(null,1); card.addView(meta);
        VideoView vv=new VideoView(this); card.addView(vv,new LinearLayout.LayoutParams(-1,520)); String url=d.getString("videoUrl"); if(url!=null){vv.setVideoURI(Uri.parse(url));vv.setOnPreparedListener(mp->{mp.setLooping(true);mp.setVolume(1,1);vv.start();});}
        TextView cap=txt(d.getString("caption")!=null?d.getString("caption"):"",15,WHITE); card.addView(cap);
        LinearLayout actions=row(); Button like=btn("♡  "+safeLong(d.get("likeCount"))); Button comments=btn("💬  Comments"); Button share=btn("↗  Share"); Button follow=btn("Follow"); actions.addView(like,new LinearLayout.LayoutParams(0,54,1)); actions.addView(comments,new LinearLayout.LayoutParams(0,54,1)); actions.addView(share,new LinearLayout.LayoutParams(0,54,1)); actions.addView(follow,new LinearLayout.LayoutParams(0,54,1)); card.addView(actions);
        like.setOnClickListener(v->toggleLike(d,like)); comments.setOnClickListener(v->comments(d)); share.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,"Watch this on Shorts: "+url);startActivity(Intent.createChooser(i,"Share video"));}); follow.setOnClickListener(v->follow(d.getString("ownerId"),follow)); meta.setOnClickListener(v->profile(d.getString("ownerId")));
        content.addView(card,new LinearLayout.LayoutParams(-1,-2));
        d.getReference().update("viewCount",FieldValue.increment(1));
    }
    long safeLong(Object o){return o instanceof Number?((Number)o).longValue():0;}

    void toggleLike(DocumentSnapshot d,Button b){String uid=auth.getCurrentUser().getUid(), vid=d.getId(); DocumentReference ref=db.collection("videos").document(vid).collection("likes").document(uid); ref.get().addOnSuccessListener(x->{if(x.exists()){ref.delete(); db.collection("videos").document(vid).update("likeCount",FieldValue.increment(-1)); b.setText("♡  "+Math.max(0,safeLong(d.get("likeCount"))-1));}else{ref.set(Collections.singletonMap("createdAt",FieldValue.serverTimestamp()));db.collection("videos").document(vid).update("likeCount",FieldValue.increment(1));b.setText("♥  "+(safeLong(d.get("likeCount"))+1));notifyUser(d.getString("ownerId"),"liked your video",vid);}});}

    void comments(DocumentSnapshot d){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL); ScrollView sc=new ScrollView(this); LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sc.addView(list);box.addView(sc,new LinearLayout.LayoutParams(-1,0,1)); EditText e=edit("Write a comment...");box.addView(e,new LinearLayout.LayoutParams(-1,58)); Button post=btn("Post comment");box.addView(post,new LinearLayout.LayoutParams(-1,54));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Comments").setView(box).create();
        Runnable load=()->d.getReference().collection("comments").orderBy("createdAt",Query.Direction.ASCENDING).get().addOnSuccessListener(s->{list.removeAllViews();for(DocumentSnapshot c:s){list.addView(txt("@"+c.getString("username")+"\n"+c.getString("text"),14,WHITE));}}); load.run();
        post.setOnClickListener(v->{String text=e.getText().toString().trim();if(text.isEmpty())return;Map<String,Object> c=new HashMap<>();c.put("uid",auth.getCurrentUser().getUid());c.put("username",currentUsername());c.put("text",text);c.put("createdAt",FieldValue.serverTimestamp());d.getReference().collection("comments").add(c).addOnSuccessListener(x->{d.getReference().update("commentCount",FieldValue.increment(1));e.setText("");load.run();});});dialog.show();
    }

    void search(){clear("Search"); EditText q=edit("Search users by username");content.addView(q,new LinearLayout.LayoutParams(-1,60));Button go=btn("Search");content.addView(go,new LinearLayout.LayoutParams(-1,54));LinearLayout results=new LinearLayout(this);results.setOrientation(LinearLayout.VERTICAL);content.addView(results,new LinearLayout.LayoutParams(-1,-2));go.setOnClickListener(v->{String term=q.getText().toString().trim().toLowerCase();if(term.isEmpty())return;if(term.startsWith("#")){ String tag=term.substring(1); db.collection("videos").whereArrayContains("hashtags",tag).limit(30).get().addOnSuccessListener(s->{results.removeAllViews();for(DocumentSnapshot d:s)videoCard(d);}); return; } db.collection("users").orderBy("username").startAt(term).endAt(term+"\\uf8ff").limit(30).get().addOnSuccessListener(s->{results.removeAllViews();for(DocumentSnapshot d:s){Button b=btn("@"+d.getString("username")+"  "+d.getString("displayName"));results.addView(b,new LinearLayout.LayoutParams(-1,58));b.setOnClickListener(x->profile(d.getId()));}});});}

    void profile(String uid){clear("Profile"); db.collection("users").document(uid).get().addOnSuccessListener(u->{if(!u.exists())return;LinearLayout head=row();TextView name=txt("@"+u.getString("username"),22,WHITE);name.setTypeface(null,1);head.addView(name,new LinearLayout.LayoutParams(0,60,1));Button f=btn(uid.equals(auth.getCurrentUser().getUid())?"Edit":"Follow");head.addView(f,new LinearLayout.LayoutParams(110,54));content.addView(head);content.addView(txt(u.getString("displayName")+"\n"+(u.getString("bio")==null?"":u.getString("bio"))+"\n\nFollowers: "+safeLong(u.get("followers"))+"   Following: "+safeLong(u.get("following")),15,WHITE));f.setOnClickListener(v->{if(!uid.equals(auth.getCurrentUser().getUid()))follow(uid,f);else editProfile(u);});db.collection("videos").whereEqualTo("ownerId",uid).orderBy("createdAt",Query.Direction.DESCENDING).get().addOnSuccessListener(s->{for(DocumentSnapshot d:s)videoCard(d);});});}

    void editProfile(DocumentSnapshot u){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);EditText dn=edit("Display name");dn.setText(u.getString("displayName"));EditText bio=edit("Bio");bio.setText(u.getString("bio"));box.addView(dn);box.addView(bio);new AlertDialog.Builder(this).setTitle("Edit profile").setView(box).setPositiveButton("Save",(d,w)->db.collection("users").document(auth.getCurrentUser().getUid()).update("displayName",dn.getText().toString(),"bio",bio.getText().toString())).setNegativeButton("Cancel",null).show();}

    void follow(String target,Button b){if(target==null||target.equals(auth.getCurrentUser().getUid()))return;String me=auth.getCurrentUser().getUid();DocumentReference r=db.collection("users").document(me).collection("following").document(target);r.get().addOnSuccessListener(x->{if(x.exists()){r.delete();db.collection("users").document(target).update("followers",FieldValue.increment(-1));db.collection("users").document(me).update("following",FieldValue.increment(-1));b.setText("Follow");}else{r.set(Collections.singletonMap("createdAt",FieldValue.serverTimestamp()));db.collection("users").document(target).collection("followers").document(me).set(Collections.singletonMap("createdAt",FieldValue.serverTimestamp()));db.collection("users").document(target).update("followers",FieldValue.increment(1));db.collection("users").document(me).update("following",FieldValue.increment(1));b.setText("Following");notifyUser(target,"started following you",me);}});}

    void notifications(){clear("Notifications");String uid=auth.getCurrentUser().getUid();db.collection("users").document(uid).collection("notifications").orderBy("createdAt",Query.Direction.DESCENDING).limit(50).get().addOnSuccessListener(s->{if(s.isEmpty())content.addView(txt("No notifications yet.",16,WHITE));for(DocumentSnapshot d:s){content.addView(txt(d.getString("text"),16,WHITE));}});}

    void notifyUser(String uid,String action,String target){if(uid==null||uid.equals(auth.getCurrentUser().getUid()))return;Map<String,Object> n=new HashMap<>();n.put("text","@"+currentUsername()+" "+action);n.put("fromUid",auth.getCurrentUser().getUid());n.put("target",target);n.put("createdAt",FieldValue.serverTimestamp());db.collection("users").document(uid).collection("notifications").add(n);}
    String currentUsername(){FirebaseUser u=auth.getCurrentUser(); if(u==null)return "user"; final String[] out={u.getEmail()!=null?u.getEmail().split("@")[0]:"user"}; try{Task<DocumentSnapshot> t=db.collection("users").document(u.getUid()).get(); if(t.isSuccessful()&&t.getResult()!=null&&t.getResult().exists()&&t.getResult().getString("username")!=null)out[0]=t.getResult().getString("username");}catch(Exception ignored){} return out[0];}

    void uploadVideo(Uri uri){
        if(auth.getCurrentUser()==null)return; clear("Upload"); ProgressBar p=new ProgressBar(this);content.addView(p);EditText cap=edit("Caption / hashtags");content.addView(cap,new LinearLayout.LayoutParams(-1,60));Button cancel=btn("Cancel");content.addView(cancel,new LinearLayout.LayoutParams(-1,54));cancel.setOnClickListener(v->feed());
        StorageReference ref=storage.getReference().child("videos/"+auth.getCurrentUser().getUid()+"/"+UUID.randomUUID()+".mp4");UploadTask task=ref.putFile(uri);task.addOnProgressListener(x->{int pct=(int)(100.0*x.getBytesTransferred()/Math.max(1,x.getTotalByteCount()));p.setProgress(pct);}).addOnSuccessListener(x->ref.getDownloadUrl().addOnSuccessListener(url->{Map<String,Object> v=new HashMap<>();v.put("ownerId",auth.getCurrentUser().getUid());v.put("username",currentUsername());v.put("caption",cap.getText().toString());v.put("videoUrl",url.toString());v.put("sourcePath",ref.getPath());v.put("mediaStatus","ready");v.put("likeCount",0L);v.put("commentCount",0L);v.put("viewCount",0L);v.put("createdAt",FieldValue.serverTimestamp());db.collection("videos").add(v).addOnSuccessListener(z->{toast("Video uploaded");feed();});})).addOnFailureListener(e->{toast("Upload failed: "+e.getMessage());feed();});
    }

    void toast(String s){Toast.makeText(this,s==null?"Something went wrong":s,Toast.LENGTH_LONG).show();}
}
