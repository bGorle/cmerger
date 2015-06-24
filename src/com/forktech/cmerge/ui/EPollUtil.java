/**
 * 
 */
package com.forktech.cmerge.ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.security.acl.Group;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;

import org.json.JSONArray;

import android.app.LauncherActivity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v4.app.NotificationCompat;
import android.telephony.TelephonyManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.View.MeasureSpec;
import android.webkit.MimeTypeMap;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.forktech.cmerge.R;

/**
 * Utility methods for our epoll application.
 * 
 * @author prakash
 *
 */
public class EPollUtil {
	/**
	 * Gives the first alphabet of the string if its not empty or null.
	 * 
	 * @param name
	 * @return
	 */
	public static String getFallbackTextInitials(String name) {
		if (name != null && !name.trim().isEmpty()) {
			return name.substring(0, 1);
		}
		return null;
	}

	/**
	 * This method gets the contact image from the phone book.
	 * 
	 * @param context
	 * @param imageUri
	 * @return Contact's image or null
	 */
	public static Bitmap getByteContactImage(Context context, String imageUri,
			int pixel) {
		if (imageUri == null) {
			return null;
		}
		if (imageUri != null) {
			try {
				Uri image_Uri = Uri.parse(imageUri);
				Bitmap bitmap = MediaStore.Images.Media.getBitmap(
						context.getContentResolver(), image_Uri);
				bitmap = Bitmap.createScaledBitmap(bitmap, pixel, pixel, false);
				return getRoundedCornerBitmap(bitmap, pixel);
			} catch (IOException e) {
				Log.e("EPollUtil", e.getLocalizedMessage());
			}
		}

		return null;
	}

	/**
	 * This method is used to create bitmap with rounded corners.
	 * 
	 * @param bitmap
	 *            which is to be rouded.
	 * @param pixels
	 *            dimension of the resultant image
	 * @return new bitmap with the specified dimension
	 */
	public static Bitmap getRoundedCornerBitmap(Bitmap bitmap, int pixels) {
		Bitmap output = Bitmap.createBitmap(bitmap.getWidth(),
				bitmap.getHeight(), Config.ARGB_8888);
		Canvas canvas = new Canvas(output);

		final int color = 0xff424242;
		final Paint paint = new Paint();
		final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
		final RectF rectF = new RectF(rect);
		final float roundPx = pixels;

		paint.setAntiAlias(true);
		canvas.drawARGB(0, 0, 0, 0);
		paint.setColor(color);
		canvas.drawRoundRect(rectF, roundPx, roundPx, paint);

		paint.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));
		canvas.drawBitmap(bitmap, rect, rect, paint);

		return output;
	}

	/**
	 * Returns a random color from list of colors.
	 * 
	 * @param context
	 * @return
	 */
	public static int[] getRandomColor(Context context, Random random,
			int previous) {
		String[] randomColorArray = context.getResources().getStringArray(
				R.array.randomColorBgArray);

		int index = random.nextInt(13 - 0);
		if (previous == index) {
			index = random.nextInt(13 - 0);
		}
		previous = index;
		String colorCode = randomColorArray[index];
		int color = Color.parseColor(colorCode);
		return new int[] { color, previous };
	}

}
