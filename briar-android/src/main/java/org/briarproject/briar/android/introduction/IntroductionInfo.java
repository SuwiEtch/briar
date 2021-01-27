package org.briarproject.briar.android.introduction;

import org.briarproject.briar.android.contact.ContactItem;

class IntroductionInfo {
	private ContactItem c1;
	private ContactItem c2;
	private boolean possible;

	IntroductionInfo(ContactItem c1, ContactItem c2,
			boolean possible) {
		this.c1 = c1;
		this.c2 = c2;
		this.possible = possible;
	}

	ContactItem getContact1() {
		return c1;
	}

	ContactItem getContact2() {
		return c2;
	}

	boolean isPossible() {
		return possible;
	}
}
