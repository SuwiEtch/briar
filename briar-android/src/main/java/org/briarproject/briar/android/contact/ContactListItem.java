package org.briarproject.briar.android.contact;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.client.MessageTracker.GroupCount;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;
import org.briarproject.briar.api.identity.AuthorInfo;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
public class ContactListItem extends ContactItem {

	private boolean empty;
	private long timestamp;
	private int unread;

	public ContactListItem(Contact contact, AuthorInfo authorInfo,
			boolean connected, GroupCount count) {
		super(contact, authorInfo, connected);
		this.empty = count.getMsgCount() == 0;
		this.unread = count.getUnreadCount();
		this.timestamp = count.getLatestMsgTime();
	}

	public ContactListItem(Contact contact, AuthorInfo authorInfo,
			boolean connected, boolean empty, int unread, long timestamp) {
		super(contact, authorInfo, connected);
		this.empty = empty;
		this.unread = unread;
		this.timestamp = timestamp;
	}

	void addMessage(ConversationMessageHeader h) {
		empty = false;
		if (h.getTimestamp() > timestamp) timestamp = h.getTimestamp();
		if (!h.isRead()) unread++;
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

}
