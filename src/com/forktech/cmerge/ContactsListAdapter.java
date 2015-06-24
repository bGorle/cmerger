package com.forktech.cmerge;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.support.v4.widget.CursorAdapter;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * An adapter to hold the contact list. It perform some operations related to
 * the individual contact like adding the contact removing the contact.
 * 
 * @author prakash
 * 
 */
public class ContactsListAdapter extends SimpleCursorAdapter {

	Context mcontext;
	LayoutInflater inflater;

	String mCurFilter;
	private Cursor mCursor;

	public ContactsListAdapter(Context context, Cursor c, boolean hideCheckbox) {
		super(context, 1231, c, new String[] {}, new int[] {}, 0);
		mcontext = context;
		inflater = LayoutInflater.from(mcontext);
	}

	static class ViewHolder {
		public TextView contactName;
		public TextView contactNo;
		public ImageView contactImages;
		public CheckBox checkContact;
		protected TextView contactFallback;
		protected RelativeLayout imageContainer;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		mCursor = getCursor();

		if (mCursor == null) {
			return null;
		}
		mCursor.moveToPosition(position);
		final ViewHolder holder;
		if (convertView == null) {
			holder = new ViewHolder();
			convertView = inflater.inflate(R.layout.contact_list_item, null);

			holder.contactName = (TextView) convertView
					.findViewById(R.id.contactname);
			holder.contactNo = (TextView) convertView
					.findViewById(R.id.contactno);

			holder.checkContact = (CheckBox) convertView
					.findViewById(R.id.contactcheck);
			holder.contactImages = (ImageView) convertView
					.findViewById(R.id.contact_image);
			holder.contactFallback = (TextView) convertView
					.findViewById(R.id.tv_contact_image_fallback);
			holder.imageContainer = (RelativeLayout) convertView
					.findViewById(R.id.rl_contact_fallback);
			convertView.setTag(holder);
		}

		else {
			holder = (ViewHolder) convertView.getTag();
		}

		holder.checkContact.setTag(position);

		holder.contactName.setText(mCursor.getString(mCursor
				.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)));
		holder.contactNo
				.setText(mCursor.getString(mCursor
						.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)));
		return convertView;

	}
}
