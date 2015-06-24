package com.forktech.cmerge.ui;

import java.util.List;
import java.util.Locale;
import java.util.Random;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.support.v4.widget.SimpleCursorAdapter;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.TextAppearanceSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AlphabetIndexer;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SectionIndexer;
import android.widget.TextView;

import com.forktech.cmerge.R;
import com.forktech.cmerge.ui.ContactsListFragment.ContactsQuery;

/**
 * This is a subclass of CursorAdapter that supports binding Cursor columns to a
 * view layout. If those items are part of search results, the search string is
 * marked by highlighting the query text. An {@link AlphabetIndexer} is used to
 * allow quicker navigation up and down the ListView.
 */
public class ContactsAdapter extends SimpleCursorAdapter implements
		SectionIndexer {
	private LayoutInflater mInflater; // Stores the layout inflater
	private AlphabetIndexer mAlphabetIndexer; // Stores the AlphabetIndexer
												// instance
	private TextAppearanceSpan highlightTextSpan; // Stores the highlight text
													// appearance style
	private Random mRandom;
	private int previous;
	private String mSearchTerm;
	
	private Context mContext;
	
	private ContactSelectListener mContactSelectListener;

	/**
	 * Instantiates a new Contacts Adapter.
	 * 
	 * @param context
	 *            A context that has access to the app's layout.
	 */
	public ContactsAdapter(Context context, ContactSelectListener listener) {
		super(context, 1231, null, new String[] {}, new int[] {}, 0);
		// super(context, null, 0);

		mContactSelectListener = listener;
		
		mContext = context;
		// Stores inflater for use later
		mInflater = LayoutInflater.from(context);

		// Loads a string containing the English alphabet. To fully localize the
		// app, provide a
		// strings.xml file in res/values-<x> directories, where <x> is a
		// locale. In the file,
		// define a string with android:name="alphabet" and contents set to all
		// of the
		// alphabetic characters in the language in their proper sort order, in
		// upper case if
		// applicable.
		final String alphabet = context.getString(R.string.alphabet);

		// Instantiates a new AlphabetIndexer bound to the column used to sort
		// contact names.
		// The cursor is left null, because it has not yet been retrieved.
		mAlphabetIndexer = new AlphabetIndexer(null, ContactsQuery.SORT_KEY,
				alphabet);

		// Defines a span for highlighting the part of a display name that
		// matches the search
		// string
		highlightTextSpan = new TextAppearanceSpan(context,
				R.style.searchTextHiglight);
		mRandom = new Random();
	}

	/**
	 * Identifies the start of the search string in the display name column of a
	 * Cursor row. E.g. If displayName was "Adam" and search query (mSearchTerm)
	 * was "da" this would return 1.
	 *
	 * @param displayName
	 *            The contact display name.
	 * @return The starting position of the search string in the display name,
	 *         0-based. The method returns -1 if the string is not found in the
	 *         display name, or if the search string is empty or null.
	 */
	private int indexOfSearchQuery(String displayName) {
		if (!TextUtils.isEmpty(mSearchTerm)) {
			return displayName.toLowerCase(Locale.getDefault()).indexOf(
					mSearchTerm.toLowerCase(Locale.getDefault()));
		}
		return -1;
	}
	
	
	@Override
	public View getView(int position, View convertView, ViewGroup container) {
		final Cursor cursor = getCursor();
		
		if (cursor.moveToPosition(position)) {
		
		final ViewHolder holder;
		if (convertView == null) {
			holder = new ViewHolder();
			convertView = mInflater.inflate(R.layout.contact_list_item,
					container, false);
			holder.text1 = (TextView) convertView.findViewById(R.id.contactname);
			holder.text2 = (TextView) convertView.findViewById(R.id.contactno);
			holder.checkContact = (CheckBox) convertView
					.findViewById(R.id.contactcheck);
			holder.contactImages = (ImageView) convertView
					.findViewById(R.id.contact_image);
			holder.contactFallback = (TextView) convertView
					.findViewById(R.id.tv_contact_image_fallback);
			holder.imageContainer = (RelativeLayout) convertView
					.findViewById(R.id.rl_contact_fallback);
			convertView.setTag(holder);
		} else {
			holder = (ViewHolder) convertView.getTag();
		}
		
		if(clearCheck) {
			holder.checkContact.setChecked(false);
		}
		
		holder.checkContact.setTag(cursor.getInt(ContactsQuery.NAME_RAW_CONTACT_ID));
		holder.checkContact
				.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

					@Override
					public void onCheckedChanged(CompoundButton buttonView,
							boolean isChecked) {
						int getPosition = (Integer) buttonView.getTag();
						// Cursor tempCursor = (Cursor) buttonView.getTag();
						if (isChecked) {
							Log.e("", DatabaseUtils.dumpCurrentRowToString(cursor));
							mContactSelectListener.addContact(getPosition);
						} else {
							mContactSelectListener.removeContact(getPosition);
						}

					}
				});

		final String photoUri = cursor
				.getString(ContactsQuery.PHOTO_THUMBNAIL_DATA);

		final String displayName = cursor.getString(ContactsQuery.DISPLAY_NAME);

		final int startIndex = indexOfSearchQuery(displayName);

		if (startIndex == -1) {
			// If the user didn't do a search, or the search string didn't match
			// a display
			// name, show the display name without highlighting
			holder.text1.setText(displayName);
			holder.text2.setText(displayName);

			if (TextUtils.isEmpty(mSearchTerm)) {
				// If the search search is empty, hide the second line of text
				// holder.text2.setVisibility(View.GONE);
			} else {
				// Shows a second line of text that indicates the search string
				// matched
				// something other than the display name
				holder.text2.setVisibility(View.VISIBLE);
			}
		} else {
			// If the search string matched the display name, applies a
			// SpannableString to
			// highlight the search string with the displayed display name

			// Wraps the display name in the SpannableString
			final SpannableString highlightedName = new SpannableString(
					displayName);

			// Sets the span to start at the starting point of the match and end
			// at "length"
			// characters beyond the starting point
			highlightedName.setSpan(highlightTextSpan, startIndex, startIndex
					+ mSearchTerm.length(), 0);

			// Binds the SpannableString to the display name View object
			holder.text1.setText(highlightedName);

			// Since the search string matched the name, this hides the
			// secondary message
			holder.text2.setVisibility(View.GONE);
		}

		// Processes the QuickContactBadge. A QuickContactBadge first appears as
		// a contact's
		// thumbnail image with styling that indicates it can be touched for
		// additional
		// information. When the user clicks the image, the badge expands into a
		// dialog box
		// containing the contact's details and icons for the built-in apps that
		// can handle
		// each detail type.

		// Generates the contact lookup Uri
		final Uri contactUri = Contacts.getLookupUri(
				cursor.getLong(ContactsQuery.ID),
				cursor.getString(ContactsQuery.LOOKUP_KEY));

		// Binds the contact's lookup Uri to the QuickContactBadge
		// holder.icon.assignContactUri(contactUri);

		// Loads the thumbnail image pointed to by photoUri into the
		// QuickContactBadge in a
		// background worker thread
		// mImageLoader.loadImage(photoUri, holder.icon);
		Bitmap contactImage = EPollUtil.getByteContactImage(mContext, photoUri,
				48);
		if (contactImage == null) {
			int[] color_previous = EPollUtil.getRandomColor(mContext, mRandom,
					previous);
			previous = color_previous[1];
			holder.contactImages.setVisibility(View.GONE);
			holder.contactFallback.setText(EPollUtil
					.getFallbackTextInitials(displayName));
			((GradientDrawable) holder.imageContainer.getBackground())
					.setColor(color_previous[0]);
		} else {
			holder.contactImages.setVisibility(View.VISIBLE);
			holder.contactFallback.setText("");
			((GradientDrawable) holder.imageContainer.getBackground())
					.setColor(Color.TRANSPARENT);
			holder.contactImages.setBackgroundResource(0);
			holder.contactImages.setImageBitmap(contactImage);
		}
		int tempCursor = cursor.getInt(ContactsQuery.ID);
	
		holder.checkContact.setChecked(mContactSelectListener.getContacts()
				.contains(tempCursor));

		}
		return convertView;
	}

	/**
	 * Overrides newView() to inflate the list item views.
	 */
//	@Override
//	public View newView(Context context, Cursor cursor, ViewGroup viewGroup) {
//		// Inflates the list item layout.
//		final View itemLayout = mInflater.inflate(R.layout.contact_list_item,
//				viewGroup, false);
//
//		// Creates a new ViewHolder in which to store handles to each view
//		// resource. This
//		// allows bindView() to retrieve stored references instead of calling
//		// findViewById for
//		// each instance of the layout.
//		final ViewHolder holder = new ViewHolder();
//		holder.text1 = (TextView) itemLayout.findViewById(R.id.contactname);
//		holder.text2 = (TextView) itemLayout.findViewById(R.id.contactno);
//		holder.checkContact = (CheckBox) itemLayout
//				.findViewById(R.id.contactcheck);
//		holder.contactImages = (ImageView) itemLayout
//				.findViewById(R.id.contact_image);
//		holder.contactFallback = (TextView) itemLayout
//				.findViewById(R.id.tv_contact_image_fallback);
//		holder.imageContainer = (RelativeLayout) itemLayout
//				.findViewById(R.id.rl_contact_fallback);
//		// holder.icon = (QuickContactBadge)
//		// itemLayout.findViewById(android.R.id.icon);
//
//		// Stores the resourceHolder instance in itemLayout. This makes
//		// resourceHolder
//		// available to bindView and other methods that receive a handle to the
//		// item view.
//		itemLayout.setTag(holder);
//		MatrixCursor matCursor = new MatrixCursor(ContactsQuery.PROJECTION);
//		matCursor.addRow(new String[] {
//				cursor.getString(cursor
//						.getColumnIndex(ContactsQuery.PROJECTION[0])),
//				cursor.getString(cursor
//						.getColumnIndex(ContactsQuery.PROJECTION[1])),
//				cursor.getString(cursor
//						.getColumnIndex(ContactsQuery.PROJECTION[2])),
//				cursor.getString(cursor
//						.getColumnIndex(ContactsQuery.PROJECTION[3])),
//				cursor.getString(cursor
//						.getColumnIndex(ContactsQuery.PROJECTION[4])) });
//
//		holder.checkContact.setTag(cursor.getInt(0));
//		holder.checkContact
//				.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
//
//					@Override
//					public void onCheckedChanged(CompoundButton buttonView,
//							boolean isChecked) {
//						int getPosition = (Integer) buttonView.getTag();
//						// Cursor tempCursor = (Cursor) buttonView.getTag();
//						if (isChecked) {
//							mContactSelectListener.addContact(getPosition);
//						} else {
//							mContactSelectListener.removeContact(getPosition);
//						}
//
//					}
//				});
//
//		// Returns the item layout view
//		return itemLayout;
//	}
//
	/**
	 * Binds data from the Cursor to the provided view.
	 */
//	@Override
//	public void bindView(View view, Context context, Cursor cursor) {
//		// Gets handles to individual view resources
//		final ViewHolder holder = (ViewHolder) view.getTag();
//
//		// For Android 3.0 and later, gets the thumbnail image Uri from the
//		// current Cursor row.
//		// For platforms earlier than 3.0, this isn't necessary, because the
//		// thumbnail is
//		// generated from the other fields in the row.
//		final String photoUri = cursor
//				.getString(ContactsQuery.PHOTO_THUMBNAIL_DATA);
//
//		final String displayName = cursor.getString(ContactsQuery.DISPLAY_NAME);
//
//		final int startIndex = indexOfSearchQuery(displayName);
//
//		if (startIndex == -1) {
//			// If the user didn't do a search, or the search string didn't match
//			// a display
//			// name, show the display name without highlighting
//			holder.text1.setText(displayName);
//			holder.text2.setText(displayName);
//
//			if (TextUtils.isEmpty(mSearchTerm)) {
//				// If the search search is empty, hide the second line of text
//				// holder.text2.setVisibility(View.GONE);
//			} else {
//				// Shows a second line of text that indicates the search string
//				// matched
//				// something other than the display name
//				holder.text2.setVisibility(View.VISIBLE);
//			}
//		} else {
//			// If the search string matched the display name, applies a
//			// SpannableString to
//			// highlight the search string with the displayed display name
//
//			// Wraps the display name in the SpannableString
//			final SpannableString highlightedName = new SpannableString(
//					displayName);
//
//			// Sets the span to start at the starting point of the match and end
//			// at "length"
//			// characters beyond the starting point
//			highlightedName.setSpan(highlightTextSpan, startIndex, startIndex
//					+ mSearchTerm.length(), 0);
//
//			// Binds the SpannableString to the display name View object
//			holder.text1.setText(highlightedName);
//
//			// Since the search string matched the name, this hides the
//			// secondary message
//			holder.text2.setVisibility(View.GONE);
//		}
//
//		// Processes the QuickContactBadge. A QuickContactBadge first appears as
//		// a contact's
//		// thumbnail image with styling that indicates it can be touched for
//		// additional
//		// information. When the user clicks the image, the badge expands into a
//		// dialog box
//		// containing the contact's details and icons for the built-in apps that
//		// can handle
//		// each detail type.
//
//		// Generates the contact lookup Uri
//		final Uri contactUri = Contacts.getLookupUri(
//				cursor.getLong(ContactsQuery.ID),
//				cursor.getString(ContactsQuery.LOOKUP_KEY));
//
//		// Binds the contact's lookup Uri to the QuickContactBadge
//		// holder.icon.assignContactUri(contactUri);
//
//		// Loads the thumbnail image pointed to by photoUri into the
//		// QuickContactBadge in a
//		// background worker thread
//		// mImageLoader.loadImage(photoUri, holder.icon);
//		Bitmap contactImage = EPollUtil.getByteContactImage(context, photoUri,
//				48);
//		if (contactImage == null) {
//			int[] color_previous = EPollUtil.getRandomColor(context, mRandom,
//					previous);
//			previous = color_previous[1];
//			holder.contactImages.setVisibility(View.GONE);
//			holder.contactFallback.setText(EPollUtil
//					.getFallbackTextInitials(displayName));
//			((GradientDrawable) holder.imageContainer.getBackground())
//					.setColor(color_previous[0]);
//		} else {
//			holder.contactImages.setVisibility(View.VISIBLE);
//			holder.contactFallback.setText("");
//			((GradientDrawable) holder.imageContainer.getBackground())
//					.setColor(Color.TRANSPARENT);
//			holder.contactImages.setBackgroundResource(0);
//			holder.contactImages.setImageBitmap(contactImage);
//		}
//		int tempCursor = cursor.getInt(0);
//		// Log.e("", DatabaseUtils.dumpCursorToString(cursor));
//		Log.e("", Arrays.deepToString(mContactSelectListener.getContacts()
//				.toArray()));
//		holder.checkContact.setChecked(mContactSelectListener.getContacts()
//				.contains(tempCursor));
//
//	}

	/**
	 * Overrides swapCursor to move the new Cursor into the AlphabetIndex as
	 * well as the CursorAdapter.
	 */
	@Override
	public Cursor swapCursor(Cursor newCursor) {
		// Update the AlphabetIndexer with new cursor as well
		mAlphabetIndexer.setCursor(newCursor);
		return super.swapCursor(newCursor);
	}

	/**
	 * An override of getCount that simplifies accessing the Cursor. If the
	 * Cursor is null, getCount returns zero. As a result, no test for Cursor ==
	 * null is needed.
	 */
	@Override
	public int getCount() {
		if (getCursor() == null) {
			return 0;
		}
		return super.getCount();
	}

	/**
	 * Defines the SectionIndexer.getSections() interface.
	 */
	@Override
	public Object[] getSections() {
		return mAlphabetIndexer.getSections();
	}

	/**
	 * Defines the SectionIndexer.getPositionForSection() interface.
	 */
	@Override
	public int getPositionForSection(int i) {
		if (getCursor() == null) {
			return 0;
		}
		return mAlphabetIndexer.getPositionForSection(i);
	}

	/**
	 * Defines the SectionIndexer.getSectionForPosition() interface.
	 */
	@Override
	public int getSectionForPosition(int i) {
		if (getCursor() == null) {
			return 0;
		}
		return mAlphabetIndexer.getSectionForPosition(i);
	}

	/**
	 * A class that defines fields for each resource ID in the list item layout.
	 * This allows ContactsAdapter.newView() to store the IDs once, when it
	 * inflates the layout, instead of calling findViewById in each iteration of
	 * bindView.
	 */
	private class ViewHolder {
		TextView text1;
		TextView text2;
		CheckBox checkContact;
		public ImageView contactImages;
		protected TextView contactFallback;
		protected RelativeLayout imageContainer;
	}

	public void setSearchTerm(String searchTerm) {
		this.mSearchTerm = searchTerm;
	}
	
	boolean clearCheck = false;
	public void clearChecks(boolean bool) {
		clearCheck = bool;
	}

	public interface ContactSelectListener {
		public void addContact(int cursor);

		public void removeContact(int cursor);

		public List<Integer> getContacts();
	}
}
