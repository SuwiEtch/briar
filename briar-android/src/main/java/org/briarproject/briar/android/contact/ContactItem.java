package org.briarproject.briar.android.contact;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.api.identity.AuthorInfo;

import javax.annotation.concurrent.NotThreadSafe;

@NotThreadSafe
@NotNullByDefault
public class ContactItem {

	private final Contact contact;
	private final AuthorInfo authorInfo;
	private boolean connected;

	public ContactItem(Contact contact, AuthorInfo authorInfo) {
		this(contact, authorInfo, false);
	}

	public ContactItem(Contact contact, AuthorInfo authorInfo,
			boolean connected) {
		this.contact = contact;
		this.authorInfo = authorInfo;
		this.connected = connected;
	}

	public Contact getContact() {
		return contact;
	}

	public AuthorInfo getAuthorInfo() {
		return authorInfo;
	}

	boolean isConnected() {
		return connected;
	}

	void setConnected(boolean connected) {
		this.connected = connected;
	}

}
