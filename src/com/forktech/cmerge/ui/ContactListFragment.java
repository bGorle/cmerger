package com.forktech.cmerge.ui;

import android.database.Cursor;
import android.database.DatabaseUtils;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.forktech.cmerge.ContactsListAdapter;
import com.forktech.cmerge.R;

public class ContactListFragment extends Fragment implements
		LoaderManager.LoaderCallbacks<Cursor> {

	private ContactsListAdapter mAdapter;
	private ListView mListView;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View v = inflater.inflate(R.layout.list_layout, container, false);
		mListView = (ListView) v.findViewById(android.R.id.list);

		return v;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		mAdapter = new ContactsListAdapter(getActivity(), null, false);

		mListView.setAdapter(mAdapter);
		getLoaderManager().initLoader(0, null, this);
	}

	private Cursor readContacts() {
		Cursor phones = getActivity().getContentResolver().query(
				ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
				null,
				null,
				null,
				"upper(" + ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
						+ ") ASC");
		String str = DatabaseUtils.dumpCursorToString(phones);
		Log.d("", DatabaseUtils.dumpCursorToString(phones));
		return phones; 
	}

	@Override
	public Loader<Cursor> onCreateLoader(int arg0, Bundle arg1) {
		return new DbCursorLoader(getActivity(), readContacts());
	}

	@Override
	public void onLoadFinished(Loader<Cursor> arg0, Cursor data) {
		mAdapter.swapCursor(data);
	}

	@Override
	public void onLoaderReset(Loader<Cursor> arg0) {
		mAdapter.swapCursor(null);
	}
}
