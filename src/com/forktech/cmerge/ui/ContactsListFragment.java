/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.forktech.cmerge.ui;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.SearchManager;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.Photo;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.SearchView;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.example.android.contactslist.util.ImageLoader;
import com.example.android.contactslist.util.Utils;
import com.forktech.cmerge.BuildConfig;
import com.forktech.cmerge.R;
import com.forktech.cmerge.ui.ContactsAdapter.ContactSelectListener;

/**
 * This fragment displays a list of contacts stored in the Contacts Provider.
 * Each item in the list shows the contact's thumbnail photo and display name.
 * On devices with large screens, this fragment's UI appears as part of a
 * two-pane layout, along with the UI of {@link ContactDetailFragment}. On
 * smaller screens, this fragment's UI appears as a single pane.
 *
 * This Fragment retrieves contacts based on a search string. If the user
 * doesn't enter a search string, then the list contains all the contacts in the
 * Contacts Provider. If the user enters a search string, then the list contains
 * only those contacts whose data matches the string. The Contacts Provider
 * itself controls the matching algorithm, which is a "substring" search: if the
 * search string is a substring of any of the contacts data, then there is a
 * match.
 *
 * On newer API platforms, the search is implemented in a SearchView in the
 * ActionBar; as the user types the search string, the list automatically
 * refreshes to display results ("type to filter"). On older platforms, the user
 * must enter the full string and trigger the search. In response, the trigger
 * starts a new Activity which loads a fresh instance of this fragment. The
 * resulting UI displays the filtered list and disables the search feature to
 * prevent furthering searching.
 */
public class ContactsListFragment extends ListFragment implements
		AdapterView.OnItemClickListener, LoaderManager.LoaderCallbacks<Cursor>,
		ContactSelectListener {

	// Defines a tag for identifying log entries
	private static final String TAG = "ContactsListFragment";

	// Bundle key for saving previously selected search result item
	private static final String STATE_PREVIOUSLY_SELECTED_KEY = "com.example.android.contactslist.ui.SELECTED_ITEM";

	private ContactsAdapter mAdapter; // The main query adapter
	private ImageLoader mImageLoader; // Handles loading the contact image in a
										// background thread
	private String mSearchTerm; // Stores the current search query term

	// Contact selected listener that allows the activity holding this fragment
	// to be notified of
	// a contact being selected
	private OnContactsInteractionListener mOnContactSelectedListener;

	// Stores the previously selected search item so that on a configuration
	// change the same item
	// can be reselected again
	private int mPreviouslySelectedSearchItem = 0;

	// Whether or not the search query has changed since the last time the
	// loader was refreshed
	private boolean mSearchQueryChanged;

	// Whether or not this fragment is showing in a two-pane layout
	private boolean mIsTwoPaneLayout;

	// Whether or not this is a search result view of this fragment, only used
	// on pre-honeycomb
	// OS versions as search results are shown in-line via Action Bar search
	// from honeycomb onward
	private boolean mIsSearchResultView = false;

	private List<Integer> mContactList;

	/**
	 * Fragments require an empty constructor.
	 */
	public ContactsListFragment() {
	}

	/**
	 * In platform versions prior to Android 3.0, the ActionBar and SearchView
	 * are not supported, and the UI gets the search string from an EditText.
	 * However, the fragment doesn't allow another search when search results
	 * are already showing. This would confuse the user, because the resulting
	 * search would re-query the Contacts Provider instead of searching the
	 * listed results. This method sets the search query and also a boolean that
	 * tracks if this Fragment should be displayed as a search result view or
	 * not.
	 *
	 * @param query
	 *            The contacts search query.
	 */
	public void setSearchQuery(String query) {
		if (TextUtils.isEmpty(query)) {
			mIsSearchResultView = false;
		} else {
			mSearchTerm = query;
			mAdapter.setSearchTerm(query);
			mIsSearchResultView = true;
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mContactList = new ArrayList<Integer>();

		// Check if this fragment is part of a two-pane set up or a single pane
		// by reading a
		// boolean from the application resource directories. This lets allows
		// us to easily specify
		// which screen sizes should use a two-pane layout by setting this
		// boolean in the
		// corresponding resource size-qualified directory.
		mIsTwoPaneLayout = false;

		// Let this fragment contribute menu items
		setHasOptionsMenu(true);

		// Create the main contacts adapter
		mAdapter = new ContactsAdapter(getActivity(), this);

		if (savedInstanceState != null) {
			// If we're restoring state after this fragment was recreated then
			// retrieve previous search term and previously selected search
			// result.
			mSearchTerm = savedInstanceState.getString(SearchManager.QUERY);
			mAdapter.setSearchTerm(savedInstanceState
					.getString(SearchManager.QUERY));
			mPreviouslySelectedSearchItem = savedInstanceState.getInt(
					STATE_PREVIOUSLY_SELECTED_KEY, 0);
		}

		/*
		 * An ImageLoader object loads and resizes an image in the background
		 * and binds it to the QuickContactBadge in each item layout of the
		 * ListView. ImageLoader implements memory caching for each image, which
		 * substantially improves refreshes of the ListView as the user scrolls
		 * through it.
		 * 
		 * To learn more about downloading images asynchronously and caching the
		 * results, read the Android training class Displaying Bitmaps
		 * Efficiently.
		 * 
		 * http://developer.android.com/training/displaying-bitmaps/
		 */
		mImageLoader = new ImageLoader(getActivity(),
				getListPreferredItemHeight()) {
			@Override
			protected Bitmap processBitmap(Object data) {
				// This gets called in a background thread and passed the data
				// from
				// ImageLoader.loadImage().
				return loadContactPhotoThumbnail((String) data, getImageSize());
			}
		};

		// Set a placeholder loading image for the image loader
		mImageLoader.setLoadingImage(R.drawable.ic_contact_picture_holo_light);

		// Add a cache to the image loader
		mImageLoader.addImageCache(getActivity().getSupportFragmentManager(),
				0.1f);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		// Inflate the list fragment layout
		return inflater.inflate(R.layout.list_layout, container, false);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		// Set up ListView, assign adapter and set some listeners. The adapter
		// was previously
		// created in onCreate().
		setListAdapter(mAdapter);
		getListView().setOnItemClickListener(this);
		getListView().setOnScrollListener(new AbsListView.OnScrollListener() {
			@Override
			public void onScrollStateChanged(AbsListView absListView,
					int scrollState) {
				// Pause image loader to ensure smoother scrolling when flinging
				if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_FLING) {
					mImageLoader.setPauseWork(true);
				} else {
					mImageLoader.setPauseWork(false);
				}
			}

			@Override
			public void onScroll(AbsListView absListView, int i, int i1, int i2) {
			}
		});

		if (mIsTwoPaneLayout) {
			// In a two-pane layout, set choice mode to single as there will be
			// two panes
			// when an item in the ListView is selected it should remain
			// highlighted while
			// the content shows in the second pane.
			getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
		}

		// If there's a previously selected search item from a saved state then
		// don't bother
		// initializing the loader as it will be restarted later when the query
		// is populated into
		// the action bar search view (see onQueryTextChange() in
		// onCreateOptionsMenu()).
		if (mPreviouslySelectedSearchItem == 0) {
			// Initialize the loader, and create a loader identified by
			// ContactsQuery.QUERY_ID
			getLoaderManager().initLoader(ContactsQuery.QUERY_ID, null, this);
		}
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);

		try {
			// Assign callback listener which the holding activity must
			// implement. This is used
			// so that when a contact item is interacted with (selected by the
			// user) the holding
			// activity will be notified and can take further action such as
			// populating the contact
			// detail pane (if in multi-pane layout) or starting a new activity
			// with the contact
			// details (single pane layout).
			mOnContactSelectedListener = (OnContactsInteractionListener) activity;
		} catch (ClassCastException e) {
			throw new ClassCastException(activity.toString()
					+ " must implement OnContactsInteractionListener");
		}
	}

	@Override
	public void onPause() {
		super.onPause();

		// In the case onPause() is called during a fling the image loader is
		// un-paused to let any remaining background work complete.
		mImageLoader.setPauseWork(false);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
		// Gets the Cursor object currently bound to the ListView
		final Cursor cursor = mAdapter.getCursor();

		// Moves to the Cursor row corresponding to the ListView item that was
		// clicked
		cursor.moveToPosition(position);

		// Creates a contact lookup Uri from contact ID and lookup_key
		final Uri uri = Contacts.getLookupUri(cursor.getLong(ContactsQuery.ID),
				cursor.getString(ContactsQuery.LOOKUP_KEY));

		// Notifies the parent activity that the user selected a contact. In a
		// two-pane layout, the
		// parent activity loads a ContactDetailFragment that displays the
		// details for the selected
		// contact. In a single-pane layout, the parent activity starts a new
		// activity that
		// displays contact details in its own Fragment.
		mOnContactSelectedListener.onContactSelected(uri);

		// If two-pane layout sets the selected item to checked so it remains
		// highlighted. In a
		// single-pane layout a new activity is started so this is not needed.
		if (mIsTwoPaneLayout) {
			getListView().setItemChecked(position, true);
		}
	}

	/**
	 * Called when ListView selection is cleared, for example when search mode
	 * is finished and the currently selected contact should no longer be
	 * selected.
	 */
	private void onSelectionCleared() {
		// Uses callback to notify activity this contains this fragment
		mOnContactSelectedListener.onSelectionCleared();

		// Clears currently checked item
		getListView().clearChoices();
	}

	// This method uses APIs from newer OS version`s than the minimum that this
	// app supports. This
	// annotation tells Android lint that they are properly guarded so they
	// won't run on older OS
	// versions and can be ignored by lint.
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {

		// Inflate the menu items
		inflater.inflate(R.menu.contact_list_menu, menu);
		// Locate the search item
		MenuItem searchItem = menu.findItem(R.id.menu_search);

		// In versions prior to Android 3.0, hides the search item to prevent
		// additional
		// searches. In Android 3.0 and later, searching is done via a
		// SearchView in the ActionBar.
		// Since the search doesn't create a new Activity to do the searching,
		// the menu item
		// doesn't need to be turned off.
		if (mIsSearchResultView) {
			searchItem.setVisible(false);
		}

		// In version 3.0 and later, sets up and configures the ActionBar
		// SearchView
		if (Utils.hasHoneycomb()) {

			// Retrieves the system search manager service
			final SearchManager searchManager = (SearchManager) getActivity()
					.getSystemService(Context.SEARCH_SERVICE);

			// Retrieves the SearchView from the search menu item
			final SearchView searchView = (SearchView) MenuItemCompat
					.getActionView(searchItem);

			// Assign searchable info to SearchView
			searchView.setSearchableInfo(searchManager
					.getSearchableInfo(getActivity().getComponentName()));

			// Set listeners for SearchView
			searchView
					.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
						@Override
						public boolean onQueryTextSubmit(String queryText) {
							// Nothing needs to happen when the user submits the
							// search string
							return true;
						}

						@Override
						public boolean onQueryTextChange(String newText) {
							// Called when the action bar search text has
							// changed. Updates
							// the search filter, and restarts the loader to do
							// a new query
							// using the new search string.
							String newFilter = !TextUtils.isEmpty(newText) ? newText
									: null;

							// Don't do anything if the filter is empty
							if (mSearchTerm == null && newFilter == null) {
								return true;
							}

							// Don't do anything if the new filter is the same
							// as the current filter
							if (mSearchTerm != null
									&& mSearchTerm.equals(newFilter)) {
								return true;
							}

							// Updates current filter to new filter
							mSearchTerm = newFilter;
							mAdapter.setSearchTerm(newFilter);

							// Restarts the loader. This triggers
							// onCreateLoader(), which builds the
							// necessary content Uri from mSearchTerm.
							mSearchQueryChanged = true;
							getLoaderManager().restartLoader(
									ContactsQuery.QUERY_ID, null,
									ContactsListFragment.this);
							return true;
						}
					});

			// if (Utils.hasICS()) {
			// // This listener added in ICS
			// searchItem.setOnActionExpandListener(new
			// MenuItem.OnActionExpandListener() {
			// @Override
			// public boolean onMenuItemActionExpand(MenuItem menuItem) {
			// // Nothing to do when the action item is expanded
			// return true;
			// }
			//
			// @Override
			// public boolean onMenuItemActionCollapse(MenuItem menuItem) {
			// // When the user collapses the SearchView the current search
			// string is
			// // cleared and the loader restarted.
			// if (!TextUtils.isEmpty(mSearchTerm)) {
			// onSelectionCleared();
			// }
			// mSearchTerm = null;
			// mAdapter.setSearchTerm(null);
			// getLoaderManager().restartLoader(
			// ContactsQuery.QUERY_ID, null, ContactsListFragment.this);
			// return true;
			// }
			// });
			// }

			if (mSearchTerm != null) {
				// If search term is already set here then this fragment is
				// being restored from a saved state and the search menu item
				// needs to be expanded and populated again.

				// Stores the search term (as it will be wiped out by
				// onQueryTextChange() when the menu item is expanded).
				final String savedSearchTerm = mSearchTerm;

				// Expands the search menu item
				if (Utils.hasICS()) {
					searchItem.expandActionView();
				}

				// Sets the SearchView to the previous search string
				searchView.setQuery(savedSearchTerm, false);
			}
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (!TextUtils.isEmpty(mSearchTerm)) {
			// Saves the current search string
			outState.putString(SearchManager.QUERY, mSearchTerm);

			// Saves the currently selected contact
			outState.putInt(STATE_PREVIOUSLY_SELECTED_KEY, getListView()
					.getCheckedItemPosition());
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		// Sends a request to the People app to display the create contact
		// screen
		case R.id.menu_add_contact:
			final Intent intent = new Intent(Intent.ACTION_INSERT,
					Contacts.CONTENT_URI);
			startActivity(intent);
			break;
		// For platforms earlier than Android 3.0, triggers the search activity
		case R.id.menu_search:
			if (!Utils.hasHoneycomb()) {
				getActivity().onSearchRequested();
			}
			break;
		case R.id.submit:
			mergeContacts();
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	private void mergeContacts() {
		ArrayList<Integer> list = (ArrayList<Integer>) getContacts();
		int numOfContacts = list.size();

		if (numOfContacts < 2) {
			Toast.makeText(getActivity(),
					"Atleast select two contacts to merge", Toast.LENGTH_SHORT)
					.show();
			return;
		}
		mAdapter.clearChecks(true);
		for (int i = 0; i < numOfContacts - 1; i++) {
			ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();

			ops.add(ContentProviderOperation
					.newUpdate(
							ContactsContract.AggregationExceptions.CONTENT_URI)
					.withValue(
							ContactsContract.AggregationExceptions.TYPE,
							ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER)
					.withValue(
							ContactsContract.AggregationExceptions.RAW_CONTACT_ID1,
							getContacts().get(0))
					.withValue(
							ContactsContract.AggregationExceptions.RAW_CONTACT_ID2,
							getContacts().get(i + 1)).build());
			// Log.e("", Arrays.deepToString(getContacts().toArray()));

			try {
				ContentProviderResult[] result = getActivity()
						.getContentResolver().applyBatch(
								ContactsContract.AUTHORITY, ops);
				Log.e("", "result length : " + result.length + " :: "
						+ result[0].uri + result[0].describeContents());
			} catch (RemoteException e) {
				e.printStackTrace();
			} catch (OperationApplicationException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {

		// If this is the loader for finding contacts in the Contacts Provider
		// (the only one supported)
		if (id == ContactsQuery.QUERY_ID) {
			Uri contentUri;

			// There are two types of searches, one which displays all contacts
			// and
			// one which filters contacts by a search query. If mSearchTerm is
			// set
			// then a search query has been entered and the latter should be
			// used.

			if (mSearchTerm == null) {
				// Since there's no search string, use the content URI that
				// searches the entire
				// Contacts table
				contentUri = ContactsQuery.CONTENT_URI;
			} else {
				// Since there's a search string, use the special content Uri
				// that searches the
				// Contacts table. The URI consists of a base Uri and the search
				// string.
				contentUri = Uri.withAppendedPath(ContactsQuery.FILTER_URI,
						Uri.encode(mSearchTerm));
			}

			// Returns a new CursorLoader for querying the Contacts table. No
			// arguments are used
			// for the selection clause. The search string is either encoded
			// onto the content URI,
			// or no contacts search string is used. The other search criteria
			// are constants. See
			// the ContactsQuery interface.
			return new CursorLoader(getActivity(), contentUri,
					ContactsQuery.PROJECTION, ContactsQuery.SELECTION, null,
					ContactsQuery.SORT_ORDER);
		}

		Log.e(TAG, "onCreateLoader - incorrect ID provided (" + id + ")");
		return null;
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
		// This swaps the new cursor into the adapter.
		if (loader.getId() == ContactsQuery.QUERY_ID) {
			mAdapter.swapCursor(data);

			// If this is a two-pane layout and there is a search query then
			// there is some additional work to do around default selected
			// search item.
			if (mIsTwoPaneLayout && !TextUtils.isEmpty(mSearchTerm)
					&& mSearchQueryChanged) {
				// Selects the first item in results, unless this fragment has
				// been restored from a saved state (like orientation change)
				// in which case it selects the previously selected search item.
				if (data != null
						&& data.moveToPosition(mPreviouslySelectedSearchItem)) {
					// Creates the content Uri for the previously selected
					// contact by appending the
					// contact's ID to the Contacts table content Uri
					final Uri uri = Uri.withAppendedPath(Contacts.CONTENT_URI,
							String.valueOf(data.getLong(ContactsQuery.ID)));
					mOnContactSelectedListener.onContactSelected(uri);
					getListView().setItemChecked(mPreviouslySelectedSearchItem,
							true);
				} else {
					// No results, clear selection.
					onSelectionCleared();
				}
				// Only restore from saved state one time. Next time fall back
				// to selecting first item. If the fragment state is saved again
				// then the currently selected item will once again be saved.
				mPreviouslySelectedSearchItem = 0;
				mSearchQueryChanged = false;
			}
		}
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		if (loader.getId() == ContactsQuery.QUERY_ID) {
			// When the loader is being reset, clear the cursor from the
			// adapter. This allows the
			// cursor resources to be freed.
			mAdapter.swapCursor(null);
		}
	}

	/**
	 * Gets the preferred height for each item in the ListView, in pixels, after
	 * accounting for screen density. ImageLoader uses this value to resize
	 * thumbnail images to match the ListView item height.
	 *
	 * @return The preferred height in pixels, based on the current theme.
	 */
	private int getListPreferredItemHeight() {
		final TypedValue typedValue = new TypedValue();

		// Resolve list item preferred height theme attribute into typedValue
		getActivity().getTheme().resolveAttribute(
				android.R.attr.listPreferredItemHeight, typedValue, true);

		// Create a new DisplayMetrics object
		final DisplayMetrics metrics = new android.util.DisplayMetrics();

		// Populate the DisplayMetrics
		getActivity().getWindowManager().getDefaultDisplay()
				.getMetrics(metrics);

		// Return theme value based on DisplayMetrics
		return (int) typedValue.getDimension(metrics);
	}

	/**
	 * Decodes and scales a contact's image from a file pointed to by a Uri in
	 * the contact's data, and returns the result as a Bitmap. The column that
	 * contains the Uri varies according to the platform version.
	 *
	 * @param photoData
	 *            For platforms prior to Android 3.0, provide the Contact._ID
	 *            column value. For Android 3.0 and later, provide the
	 *            Contact.PHOTO_THUMBNAIL_URI value.
	 * @param imageSize
	 *            The desired target width and height of the output image in
	 *            pixels.
	 * @return A Bitmap containing the contact's image, resized to fit the
	 *         provided image size. If no thumbnail exists, returns null.
	 */
	private Bitmap loadContactPhotoThumbnail(String photoData, int imageSize) {

		// Ensures the Fragment is still added to an activity. As this method is
		// called in a
		// background thread, there's the possibility the Fragment is no longer
		// attached and
		// added to an activity. If so, no need to spend resources loading the
		// contact photo.
		if (!isAdded() || getActivity() == null) {
			return null;
		}

		// Instantiates an AssetFileDescriptor. Given a content Uri pointing to
		// an image file, the
		// ContentResolver can return an AssetFileDescriptor for the file.
		AssetFileDescriptor afd = null;

		// This "try" block catches an Exception if the file descriptor returned
		// from the Contacts
		// Provider doesn't point to an existing file.
		try {
			Uri thumbUri;
			// If Android 3.0 or later, converts the Uri passed as a string to a
			// Uri object.
			if (Utils.hasHoneycomb()) {
				thumbUri = Uri.parse(photoData);
			} else {
				// For versions prior to Android 3.0, appends the string
				// argument to the content
				// Uri for the Contacts table.
				final Uri contactUri = Uri.withAppendedPath(
						Contacts.CONTENT_URI, photoData);

				// Appends the content Uri for the Contacts.Photo table to the
				// previously
				// constructed contact Uri to yield a content URI for the
				// thumbnail image
				thumbUri = Uri.withAppendedPath(contactUri,
						Photo.CONTENT_DIRECTORY);
			}
			// Retrieves a file descriptor from the Contacts Provider. To learn
			// more about this
			// feature, read the reference documentation for
			// ContentResolver#openAssetFileDescriptor.
			afd = getActivity().getContentResolver().openAssetFileDescriptor(
					thumbUri, "r");

			// Gets a FileDescriptor from the AssetFileDescriptor. A
			// BitmapFactory object can
			// decode the contents of a file pointed to by a FileDescriptor into
			// a Bitmap.
			FileDescriptor fileDescriptor = afd.getFileDescriptor();

			if (fileDescriptor != null) {
				// Decodes a Bitmap from the image pointed to by the
				// FileDescriptor, and scales it
				// to the specified width and height
				return ImageLoader.decodeSampledBitmapFromDescriptor(
						fileDescriptor, imageSize, imageSize);
			}
		} catch (FileNotFoundException e) {
			// If the file pointed to by the thumbnail URI doesn't exist, or the
			// file can't be
			// opened in "read" mode, ContentResolver.openAssetFileDescriptor
			// throws a
			// FileNotFoundException.
			if (BuildConfig.DEBUG) {
				Log.d(TAG, "Contact photo thumbnail not found for contact "
						+ photoData + ": " + e.toString());
			}
		} finally {
			// If an AssetFileDescriptor was returned, try to close it
			if (afd != null) {
				try {
					afd.close();
				} catch (IOException e) {
					// Closing a file descriptor might cause an IOException if
					// the file is
					// already closed. Nothing extra is needed to handle this.
				}
			}
		}

		// If the decoding failed, returns null
		return null;
	}

	/**
	 * This interface must be implemented by any activity that loads this
	 * fragment. When an interaction occurs, such as touching an item from the
	 * ListView, these callbacks will be invoked to communicate the event back
	 * to the activity.
	 */
	public interface OnContactsInteractionListener {
		/**
		 * Called when a contact is selected from the ListView.
		 * 
		 * @param contactUri
		 *            The contact Uri.
		 */
		public void onContactSelected(Uri contactUri);

		/**
		 * Called when the ListView selection is cleared like when a contact
		 * search is taking place or is finishing.
		 */
		public void onSelectionCleared();
	}

	/**
	 * This interface defines constants for the Cursor and CursorLoader, based
	 * on constants defined in the
	 * {@link android.provider.ContactsContract.Contacts} class.
	 */
	public interface ContactsQuery {

		// An identifier for the loader
		final static int QUERY_ID = 1;

		// A content URI for the Contacts table
		final static Uri CONTENT_URI = Contacts.CONTENT_URI;

		// The search/filter query Uri
		final static Uri FILTER_URI = Contacts.CONTENT_FILTER_URI;

		// The selection clause for the CursorLoader query. The search criteria
		// defined here
		// restrict results to contacts that have a display name and are linked
		// to visible groups.
		// Notice that the search on the string provided by the user is
		// implemented by appending
		// the search string to CONTENT_FILTER_URI.
		@SuppressLint("InlinedApi")
		final static String SELECTION = (Utils.hasHoneycomb() ? Contacts.DISPLAY_NAME_PRIMARY
				: Contacts.DISPLAY_NAME)
				+ "<>''" + " AND " + Contacts.IN_VISIBLE_GROUP + "=1";

		// The desired sort order for the returned Cursor. In Android 3.0 and
		// later, the primary
		// sort key allows for localization. In earlier versions. use the
		// display name as the sort
		// key.
		@SuppressLint("InlinedApi")
		final static String SORT_ORDER = Utils.hasHoneycomb() ? Contacts.SORT_KEY_PRIMARY
				: Contacts.DISPLAY_NAME;

		// The projection for the CursorLoader query. This is a list of columns
		// that the Contacts
		// Provider should return in the Cursor.
		@SuppressLint("InlinedApi")
		final static String[] PROJECTION = {

				// The contact's row id
				Contacts._ID,

				// A pointer to the contact that is guaranteed to be more
				// permanent than _ID. Given
				// a contact's current _ID value and LOOKUP_KEY, the Contacts
				// Provider can generate
				// a "permanent" contact URI.
				Contacts.LOOKUP_KEY,

				// In platform version 3.0 and later, the Contacts table
				// contains
				// DISPLAY_NAME_PRIMARY, which either contains the contact's
				// displayable name or
				// some other useful identifier such as an email address. This
				// column isn't
				// available in earlier versions of Android, so you must use
				// Contacts.DISPLAY_NAME
				// instead.
				Utils.hasHoneycomb() ? Contacts.DISPLAY_NAME_PRIMARY
						: Contacts.DISPLAY_NAME,

				// In Android 3.0 and later, the thumbnail image is pointed to
				// by
				// PHOTO_THUMBNAIL_URI. In earlier versions, there is no direct
				// pointer; instead,
				// you generate the pointer from the contact's ID value and
				// constants defined in
				// android.provider.ContactsContract.Contacts.
				Utils.hasHoneycomb() ? Contacts.PHOTO_THUMBNAIL_URI
						: Contacts._ID,

				// The sort order column for the returned Cursor, used by the
				// AlphabetIndexer
				SORT_ORDER, Contacts.NAME_RAW_CONTACT_ID, };

		// The query column numbers which map to each value in the projection
		final static int ID = 0;
		final static int LOOKUP_KEY = 1;
		final static int DISPLAY_NAME = 2;
		final static int PHOTO_THUMBNAIL_DATA = 3;
		final static int SORT_KEY = 4;
		final static int NAME_RAW_CONTACT_ID = 5;
	}

	@Override
	public void addContact(int contactId) {
		if (!mContactList.contains(contactId)) {
			mContactList.add(contactId);
		}
	}

	@Override
	public void removeContact(int contactId) {
		Log.e("", "removed " + contactId);
		mContactList.remove(Integer.valueOf(contactId));
	}

	@Override
	public List<Integer> getContacts() {
		return mContactList;
	}
}
