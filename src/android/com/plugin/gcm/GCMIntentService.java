package com.plugin.gcm;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import java.net.URL;
import java.net.HttpURLConnection;
import java.io.InputStream;
import java.io.IOException;
import android.net.Uri;

import android.graphics.BitmapShader;
import android.graphics.RectF;
import android.graphics.Shader;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;

import com.google.android.gcm.GCMBaseIntentService;

@SuppressLint("NewApi")
public class GCMIntentService extends GCMBaseIntentService {

	private static final String TAG = "GCMIntentService";

	private boolean isLolliOrAfter() {
		return (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP);
	}

	public static Bitmap getBitmapFromURL(String src) {
		try {
			URL url = new URL(src);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setDoInput(true);
			connection.connect();
			InputStream input = connection.getInputStream();
			Bitmap myBitmap = BitmapFactory.decodeStream(input);

			if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
				return Bitmap.createScaledBitmap(myBitmap, 128, 128, false);
			}

			// Ensure it is a circle
			Bitmap output = Bitmap.createBitmap(myBitmap.getWidth(),
					myBitmap.getHeight(), Bitmap.Config.ARGB_8888);
			Canvas canvas = new Canvas(output);

			BitmapShader shader;
			shader = new BitmapShader(myBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);

			Paint paint = new Paint();
			paint.setAntiAlias(true);
			paint.setShader(shader);

			RectF rect = new RectF(0.0f, 0.0f, myBitmap.getWidth(), myBitmap.getHeight());

			canvas.drawRoundRect(rect, myBitmap.getWidth() / 2, myBitmap.getWidth() / 2, paint);

			return output;
		} catch (IOException e) {
			// Log exception
			return null;
		}
	}

	public GCMIntentService() {
		super("GCMIntentService");
	}

	@Override
	public void onRegistered(Context context, String regId) {

		Log.v(TAG, "onRegistered: "+ regId);

		JSONObject json;

		try
		{
			json = new JSONObject().put("event", "registered");
			json.put("regid", regId);

			Log.v(TAG, "onRegistered: " + json.toString());

			// Send this JSON data to the JavaScript application above EVENT should be set to the msg type
			// In this case this is the registration ID
			PushPlugin.sendJavascript( json );

		}
		catch( JSONException e)
		{
			// No message to the user is sent, JSON failed
			Log.e(TAG, "onRegistered: JSON exception");
		}
	}

	@Override
	public void onUnregistered(Context context, String regId) {
		Log.d(TAG, "onUnregistered - regId: " + regId);
	}

	@Override
	protected void onMessage(Context context, Intent intent) {
		Log.d(TAG, "onMessage - context: " + context);

		// Extract the payload from the message
		Bundle extras = intent.getExtras();
		if (extras != null  && !PushPlugin.isIntercomPush(extras))
		{
			// ApplicationInfo app = context.getPackageManager().getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);

			ApplicationInfo app = getContext().getPackageManager().getApplicationInfo(getContext().getPackageName(), 0);
			Bundle bundle = app.metaData;

			String forceInForeground = bundle.getString("forceInForeground");
			// if we are in the foreground, just surface the payload, else post it to the statusbar
			if (PushPlugin.isInForeground() && forceInForeground == "N") {
				extras.putBoolean("foreground", true);
				PushPlugin.sendExtras(extras);
				// extras.putBoolean("foreground", false);
				// createNotification(context, extras);
			}
			else {
				extras.putBoolean("foreground", false);

				// Send a notification if there is a message
				if (extras.getString("message") != null && extras.getString("message").length() != 0) {
					createNotification(context, extras);
				}
			}
		}
	}

	public void createNotification(Context context, Bundle extras)
	{
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		String appName = getAppName(this);

		Intent notificationIntent = new Intent(this, PushHandlerActivity.class);
		notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
		notificationIntent.putExtra("pushBundle", extras);

		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

		int defaults = Notification.DEFAULT_ALL;


		if (extras.getString("defaults") != null) {
			try {
				defaults = Integer.parseInt(extras.getString("defaults"));
			} catch (NumberFormatException e) {}
		}

		NotificationCompat.Builder mBuilder =
				new NotificationCompat.Builder(context)
						.setDefaults(defaults)
						.setSmallIcon(context.getApplicationInfo().icon)
						.setWhen(System.currentTimeMillis())
						.setContentTitle(extras.getString("title"))
						.setTicker(extras.getString("title"))
						.setContentIntent(contentIntent)
						.setColor(0xffb972f1)
						.setPriority(2)
						.setAutoCancel(true);

		try {
			if (this.isLolliOrAfter()) {
				int smallIcoId = context.getResources().getIdentifier("shape", "drawable", context.getPackageName());
				mBuilder.setSmallIcon(smallIcoId);
			}
		}
		catch(Exception e) {
			Log.e(TAG, "Small Icon Set Error" + e.getMessage());
		}

		String imageUrl = extras.getString("imageUrl");
		if (imageUrl != null) {
			mBuilder.setLargeIcon(this.getBitmapFromURL(imageUrl));
		} else {
			int resId = context.getResources().getIdentifier("circle", "drawable", context.getPackageName());
			mBuilder.setLargeIcon(BitmapFactory.decodeResource(getResources(), resId));
		}

		String message = extras.getString("message");
		if (message != null) {
			mBuilder.setContentText(message);
		} else {
			mBuilder.setContentText("");
		}

		String msgcnt = extras.getString("msgcnt");
		if (msgcnt != null) {
			mBuilder.setNumber(Integer.parseInt(msgcnt));
		}

		int notId = 0;

		try {
			notId = Integer.parseInt(extras.getString("notId"));
		}
		catch(NumberFormatException e) {
			Log.e(TAG, "Number format exception - Error parsing Notification ID: " + e.getMessage());
		}
		catch(Exception e) {
			Log.e(TAG, "Number format exception - Error parsing Notification ID" + e.getMessage());
		}

		mNotificationManager.notify((String) appName, notId, mBuilder.build());
	}

	private static String getAppName(Context context)
	{
		CharSequence appName =
				context
						.getPackageManager()
						.getApplicationLabel(context.getApplicationInfo());

		return (String)appName;
	}

	@Override
	public void onError(Context context, String errorId) {
		Log.e(TAG, "onError - errorId: " + errorId);
	}

}
