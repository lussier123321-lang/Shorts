# Firebase backend setup

1. Create a Firebase project.
2. Add Android app package `com.example.shortsclone`.
3. Enable Email/Password Authentication, Firestore, Storage, and Cloud Messaging.
4. Put the Android app credentials into `app/src/main/res/values/strings.xml`.
5. Install Firebase CLI and run `firebase use YOUR_PROJECT_ID` from the project root.
6. Deploy rules, indexes, storage rules and Functions:

```bash
cd functions
npm install
npm run build
cd ..
firebase deploy --only firestore:rules,firestore:indexes,storage,functions
```

## Cloud Transcoder

The upload trigger detects original MP4 uploads. By default it leaves the original playable and marks it ready. To enable real transcoding, enable the Google Cloud Transcoder API and set:

- `TRANSCODER_ENABLED=true`
- `TRANSCODER_LOCATION=us-central1` (or another supported region)

Then deploy Functions. Grant the Functions runtime service account permission to create Transcoder jobs and read/write the Storage bucket.

## Security

The included Firestore rules allow users to edit only their own profile and content, while permitting authenticated engagement actions such as likes/views/comments. Backend-triggered notification and recommendation writes use the Admin SDK and are not exposed to clients.
