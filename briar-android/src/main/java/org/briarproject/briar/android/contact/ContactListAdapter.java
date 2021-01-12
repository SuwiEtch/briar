package org.briarproject.briar.android.contact;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.R;

import javax.annotation.Nullable;

import androidx.recyclerview.widget.DiffUtil.ItemCallback;
import androidx.recyclerview.widget.ListAdapter;

import static androidx.recyclerview.widget.SortedList.INVALID_POSITION;

@NotNullByDefault
public class ContactListAdapter extends
		ListAdapter<ContactListItem, ContactListItemViewHolder> {

	@Nullable
	protected final BaseContactListAdapter.OnContactClickListener<ContactListItem>
			listener;

	public ContactListAdapter(
			BaseContactListAdapter.OnContactClickListener<ContactListItem> listener) {
		super(new ContactListCallback());
		this.listener = listener;
	}

	@NotNullByDefault
	private static class ContactListCallback
			extends ItemCallback<ContactListItem> {
		@Override
		public boolean areItemsTheSame(ContactListItem c1, ContactListItem c2) {
			return c1.getContact().equals(c2.getContact());
		}

		@Override
		public boolean areContentsTheSame(ContactListItem c1,
				ContactListItem c2) {
			// check for all properties that influence visual
			// representation of contact
			if (c1.isEmpty() != c2.isEmpty()) {
				return false;
			}
			if (c1.getUnreadCount() != c2.getUnreadCount()) {
				return false;
			}
			if (c1.getTimestamp() != c2.getTimestamp()) {
				return false;
			}
			return c1.isConnected() == c2.isConnected();
		}
	}

	@Override
	public ContactListItemViewHolder onCreateViewHolder(ViewGroup viewGroup,
			int i) {
		View v = LayoutInflater.from(viewGroup.getContext()).inflate(
				R.layout.list_item_contact, viewGroup, false);

		return new ContactListItemViewHolder(v);
	}

	@Override
	public void onBindViewHolder(ContactListItemViewHolder viewHolder,
			int position) {
		viewHolder.bind(getItem(position), listener);
	}

	int findItemPosition(ContactId c) {
		for (int i = 0; i < getItemCount(); i++) {
			ContactItem item = getItem(i);
			if (item != null && item.getContact().getId().equals(c))
				return i;
		}
		return INVALID_POSITION; // Not found
	}

}
