package com.forktech.cmerge.ui;

import android.content.Context;
import android.database.Cursor;
import android.support.v4.content.AsyncTaskLoader;

public class DbCursorLoader extends AsyncTaskLoader<Cursor> {
	private Cursor mCursor;

	public DbCursorLoader(Context context, Cursor db) {
		super(context);
		// Log.d("dbc","constructor");
		mCursor = db;
	}

	@Override
	public Cursor loadInBackground() {
		// Log.d("dbc","loadInBackground");

		Cursor cursor = mCursor;
		;
		if (cursor != null) {
			// Ensure the cursor window is filled
			cursor.getCount();
		}
		return cursor;
	}

	/* Runs on the UI thread */
	@Override
	public void deliverResult(Cursor cursor) {
		// Log.d("dbc","deliverResult");
		if (isReset()) {
			// An async query came in while the loader is stopped
			if (cursor != null) {
				cursor.close();
			}
			return;
		}
		Cursor oldCursor = mCursor;
		mCursor = cursor;

		if (isStarted()) {
			super.deliverResult(cursor);
		}

		if (oldCursor != null && oldCursor != cursor && !oldCursor.isClosed()) {
			oldCursor.close();
		}
	}

	/**
	 * Starts an asynchronous load of the contacts list data. When the result is
	 * ready the callbacks will be called on the UI thread. If a previous load
	 * has been completed and is still valid the result may be passed to the
	 * callbacks immediately.
	 * 
	 * Must be called from the UI thread
	 */
	@Override
	protected void onStartLoading() {
		// Log.d("dbc","onStartLoading");
		if (mCursor != null) {
			deliverResult(mCursor);
		}
		if (takeContentChanged() || mCursor == null) {
			forceLoad();
		}
	}

	/**
	 * Must be called from the UI thread
	 */
	@Override
	protected void onStopLoading() {
		// Log.d("dbc","onStopLoading");
		// Attempt to cancel the current load task if possible.
		cancelLoad();
	}

	@Override
	public void onCanceled(Cursor cursor) {
		// Log.d("dbc","onCanceled");
		if (cursor != null && !cursor.isClosed()) {
			cursor.close();
		}
	}

	@Override
	protected void onReset() {
		super.onReset();
		// Log.d("dbc","onReset");

		// Ensure the loader is stopped
		onStopLoading();

		if (mCursor != null && !mCursor.isClosed()) {
			mCursor.close();
		}
		mCursor = null;
	}
}
