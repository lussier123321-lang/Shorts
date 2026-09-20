import { onDocumentCreated, onDocumentWritten } from 'firebase-functions/v2/firestore';
import { onObjectFinalized } from 'firebase-functions/v2/storage';
import { onSchedule } from 'firebase-functions/v2/scheduler';
import { initializeApp } from 'firebase-admin/app';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import { getMessaging } from 'firebase-admin/messaging';
import { getStorage } from 'firebase-admin/storage';
import { GoogleAuth } from 'google-auth-library';

initializeApp();
const db = getFirestore();

function hashtags(text: string): string[] {
  return [...new Set((text.match(/#[A-Za-z0-9_]+/g) || []).map(x => x.slice(1).toLowerCase()))];
}

async function sendPush(uid: string, title: string, body: string, data: Record<string,string> = {}) {
  const snap = await db.collection('users').doc(uid).get();
  const tokens = (snap.get('fcmTokens') || []) as string[];
  if (!tokens.length) return;
  await getMessaging().sendEachForMulticast({tokens, notification:{title,body}, data});
}

export const indexVideo = onDocumentCreated('videos/{videoId}', async event => {
  const d = event.data?.data(); if (!d) return;
  const tags = hashtags(String(d.caption || ''));
  const batch = db.batch();
  for (const tag of tags) batch.set(db.collection('hashtags').doc(tag), {name:tag, videoCount:FieldValue.increment(1)}, {merge:true});
  batch.update(event.data!.ref, {hashtags:tags, mediaStatus:d.mediaStatus || 'ready', recommendationScore:0, viewCount:0});
  await batch.commit();
});

export const notifyFollow = onDocumentCreated('users/{uid}/followers/{followerId}', async event => {
  const uid = event.params.uid, from = event.params.followerId;
  const u = await db.collection('users').doc(from).get();
  const username = u.get('username') || 'Someone';
  await db.collection('users').doc(uid).collection('notifications').add({type:'follow', fromUid:from, text:`@${username} started following you`, createdAt:FieldValue.serverTimestamp()});
  await sendPush(uid, 'New follower', `@${username} started following you`, {type:'follow', uid:from});
});

export const notifyComment = onDocumentCreated('videos/{videoId}/comments/{commentId}', async event => {
  const d = event.data?.data(); if (!d) return;
  const video = await db.collection('videos').doc(event.params.videoId).get();
  const owner = video.get('ownerId'); if (!owner || owner === d.uid) return;
  const username = d.username || 'Someone';
  await db.collection('users').doc(owner).collection('notifications').add({type:'comment',fromUid:d.uid,target:event.params.videoId,text:`@${username} commented on your video`,createdAt:FieldValue.serverTimestamp()});
  await sendPush(owner,'New comment',`@${username} commented on your video`,{type:'comment',videoId:event.params.videoId});
});

export const notifyLike = onDocumentCreated('videos/{videoId}/likes/{uid}', async event => {
  const video = await db.collection('videos').doc(event.params.videoId).get();
  const owner = video.get('ownerId'); if (!owner || owner === event.params.uid) return;
  const user = await db.collection('users').doc(event.params.uid).get();
  const username = user.get('username') || 'Someone';
  await db.collection('users').doc(owner).collection('notifications').add({type:'like',fromUid:event.params.uid,target:event.params.videoId,text:`@${username} liked your video`,createdAt:FieldValue.serverTimestamp()});
  await sendPush(owner,'New like',`@${username} liked your video`,{type:'like',videoId:event.params.videoId});
});

export const rebuildRecommendationScores = onSchedule('every 30 minutes', async () => {
  const snap = await db.collection('videos').orderBy('createdAt','desc').limit(500).get();
  const batch = db.batch();
  for (const doc of snap.docs) {
    const d = doc.data();
    const likes = Number(d.likeCount || 0), views = Number(d.viewCount || 0), comments = Number(d.commentCount || 0);
    const created = d.createdAt?.toMillis?.() || Date.now();
    const ageHours = Math.max(0.1,(Date.now()-created)/3600000);
    const score = (likes*4 + comments*6 + Math.log1p(views)*2) / Math.pow(ageHours+2,0.65);
    batch.update(doc.ref,{recommendationScore:score});
  }
  await batch.commit();
});

export const createTranscodeJob = onObjectFinalized(async event => {
  const name = event.data.name || '';
  if (!name.startsWith('videos/') || !name.endsWith('.mp4')) return;
  const bucket = getStorage().bucket(event.data.bucket);
  const [meta] = await bucket.file(name).getMetadata();
  const uid = name.split('/')[1];
  const encoded = encodeURIComponent(name);
  const videoQuery = await db.collection('videos').where('ownerId','==',uid).where('sourcePath','==',name).limit(1).get();
  if (videoQuery.empty) return;
  const videoRef = videoQuery.docs[0].ref;
  await videoRef.update({mediaStatus:'processing'});
  // Transcoder API job creation is intentionally kept in a separate helper path.
  // Set TRANSCODER_ENABLED=true in functions config after enabling the API.
  if (process.env.TRANSCODER_ENABLED !== 'true') {
    await videoRef.update({mediaStatus:'ready',videoUrl:`https://storage.googleapis.com/${event.data.bucket}/${encoded}`});
    return;
  }
  const auth = new GoogleAuth({scopes:['https://www.googleapis.com/auth/cloud-platform']});
  const client = await auth.getClient();
  const project = process.env.GCLOUD_PROJECT!;
  const location = process.env.TRANSCODER_LOCATION || 'us-central1';
  const inputUri = `gs://${event.data.bucket}/${name}`;
  const outputPrefix = `gs://${event.data.bucket}/processed/${uid}/${videoRef.id}/`;
  const endpoint = `https://transcoder.googleapis.com/v1/projects/${project}/locations/${location}/jobs`;
  const body = {inputUri, outputUri:outputPrefix, config:{elementaryStreams:[{key:'video',videoStream:{h264:{heightPixels:720,widthPixels:404,bitrateBps:1200000,frameRate:30}}},{key:'audio',audioStream:{codec:'aac',bitrateBps:128000}}],muxStreams:[{key:'sd',container:'mp4',elementaryStreams:['video','audio'],segmentSettings:{segmentDuration:'2s'}}]}};
  const res = await client.request({url:endpoint,method:'POST',data:body});
  await videoRef.update({transcoderJob:(res.data as any).name || '', transcoderOutputPrefix:outputPrefix});
});

export const refreshFollowerFeed = onDocumentCreated('users/{uid}/following/{targetUid}', async event => {
  const uid=event.params.uid, target=event.params.targetUid;
  const videos=await db.collection('videos').where('ownerId','==',target).orderBy('createdAt','desc').limit(20).get();
  const batch=db.batch();
  for(const v of videos.docs) batch.set(db.collection('users').doc(uid).collection('followingFeed').doc(v.id),{videoId:v.id,ownerId:target,createdAt:v.get('createdAt')},{merge:true});
  await batch.commit();
});
