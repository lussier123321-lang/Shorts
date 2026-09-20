# Shorts — ad-free TikTok-style Android app

This version expands the original local demo into a real Firebase-backed social short-video app.

## Included

- Email/password accounts
- User profiles and bios
- Follow / unfollow graph
- Personalized-ready video data model
- Public video feed
- Video upload to Firebase Storage
- Captions and hashtags as text
- Likes
- Comments
- User search
- In-app notifications for follows, likes, and comments
- Share via Android share sheet
- No advertising SDKs
- Firestore security rules
- Storage security rules
- Firestore indexes
- GitHub Actions APK build

## Backend architecture

Firebase Authentication handles accounts. Firestore stores profiles, videos, follows, likes, comments, and notifications. Firebase Storage stores uploaded MP4 files.

### Firestore collections

`users/{uid}`

`users/{uid}/following/{targetUid}`

`users/{uid}/followers/{followerUid}`

`users/{uid}/notifications/{notificationId}`

`videos/{videoId}`

`videos/{videoId}/likes/{uid}`

`videos/{videoId}/comments/{commentId}`

### Storage

`videos/{uid}/{random}.mp4`

## Connect Firebase

Follow `firebase/README.md`. Firebase's current Android documentation recommends using the Firebase Android BoM; this project uses BoM 34.19.0. See the official Firebase setup documentation for the current console workflow. 

You must create your own Firebase project and put its non-secret Android configuration values in `app/src/main/res/values/strings.xml`. Do not commit service-account keys or Firebase Admin credentials.

## Build

The repository contains a GitHub Actions workflow that installs Gradle and builds `app-debug.apk`. Open the Actions tab after pushing the project to GitHub, or trigger the workflow manually.

## Production next steps

For a production-scale service, add Cloud Functions for server-side notification fan-out, reliable like/follower counters, video moderation, thumbnails/transcoding, rate limiting, abuse reporting, pagination, recommendation ranking, and push notifications through FCM. The Android client intentionally does not contain any server/admin credentials.

## Production-style v3 features

This version adds a real recommendation pipeline and notification/backend layer:

- **For You ranking:** Cloud Functions periodically calculate `recommendationScore` from likes, comments, views, and freshness.
- **Following feed:** following relationships are fanned out into `users/{uid}/followingFeed` by a backend trigger.
- **Hashtags:** captions are parsed for `#hashtags`; hashtag search is supported in the Android app.
- **Push notifications:** Firebase Cloud Messaging tokens are stored per account, and backend triggers send push notifications for follows, likes, and comments.
- **Video processing hook:** Storage uploads are detected by Cloud Functions. Set `TRANSCODER_ENABLED=true` after enabling the Google Cloud Transcoder API to process uploaded MP4 files into a streaming-friendly 720p MP4 output.
- **No ads:** there are no advertising SDKs in the Android client or backend.

### Firebase deployment

Install Firebase CLI, authenticate, select your Firebase project, then from the project root:

```bash
firebase use YOUR_PROJECT_ID
cd functions && npm install && npm run build && cd ..
firebase deploy --only firestore:rules,firestore:indexes,storage,functions
```

For Transcoder API processing, enable the Google Cloud Transcoder API and configure the functions environment with `TRANSCODER_ENABLED=true` and optionally `TRANSCODER_LOCATION=us-central1`. The Cloud Functions service account needs permission to create Transcoder jobs and access the Storage bucket.

### Android push notifications

The app requests Android 13+ notification permission and registers its FCM token under the signed-in user's Firestore document. Notification taps open the Shorts app.
