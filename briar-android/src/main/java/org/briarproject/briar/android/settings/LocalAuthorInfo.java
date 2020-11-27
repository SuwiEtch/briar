package org.briarproject.briar.android.settings;

import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.briar.api.identity.AuthorInfo;

class LocalAuthorInfo {

	private final LocalAuthor localAuthor;
	private final AuthorInfo authorInfo;

	LocalAuthorInfo(LocalAuthor localAuthor, AuthorInfo authorInfo) {
		this.localAuthor = localAuthor;
		this.authorInfo = authorInfo;
	}

	LocalAuthor getLocalAuthor() {
		return localAuthor;
	}

	AuthorInfo getAuthorInfo() {
		return authorInfo;
	}

}