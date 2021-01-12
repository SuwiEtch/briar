package org.briarproject.briar.android.contact;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.Message;
import org.briarproject.briar.api.client.MessageTracker.GroupCount;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
public class ContactListItem extends ContactItem
		implements Comparable<ContactListItem> {

	private boolean empty;
	private long timestamp;
	private int unread;

	public ContactListItem(Contact contact, boolean connected,
			GroupCount count) {
		super(contact, connected);
		this.empty = count.getMsgCount() == 0;
		this.unread = count.getUnreadCount();
		this.timestamp = count.getLatestMsgTime();
	}

	// TODO: this constructor will become available once #214 is merged
	private ContactListItem(Contact contact, boolean connected, boolean empty,
			int unread, long timestamp) {
		super(contact, connected);
		this.empty = empty;
		this.timestamp = timestamp;
		this.unread = unread;
	}

	void addMessage(ConversationMessageHeader h) {
		empty = false;
		if (h.getTimestamp() > timestamp) timestamp = h.getTimestamp();
		if (!h.isRead()) unread++;
	}

	void addMessage(Message m) {
		empty = false;
		if (m.getTimestamp() > timestamp) timestamp = m.getTimestamp();
	}

	ContactListItem updatedItem(ConversationMessageHeader h) {
		ContactListItem item = new ContactListItem(getContact(), isConnected(),
				empty, unread, timestamp);
		item.addMessage(h);
		return item;
	}

	public ContactListItem updatedItem(Message m) {
		ContactListItem item = new ContactListItem(getContact(), isConnected(),
				empty, unread, timestamp);
		item.addMessage(m);
		return item;
	}

	boolean isEmpty() {
		return empty;
	}

	long getTimestamp() {
		return timestamp;
	}

	int getUnreadCount() {
		return unread;
	}

	@Override
	public int compareTo(ContactListItem o) {
		return Long.compare(o.getTimestamp(), timestamp);
	}
}
