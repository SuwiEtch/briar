package org.briarproject.briar.android.settings;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.briar.api.identity.AuthorInfo;

public class LocalAuthorInfo {

	private final LocalAuthor localAuthor;
	private final AuthorInfo authorInfo;

	public LocalAuthorInfo(LocalAuthor localAuthor, AuthorInfo authorInfo) {
		this.localAuthor = localAuthor;
		this.authorInfo = authorInfo;
	}

	public LocalAuthor getLocalAuthor() {
		return localAuthor;
	}

	public AuthorInfo getAuthorInfo() {
		return authorInfo;
	}
}