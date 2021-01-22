package org.briarproject.briar.android.introduction;

import android.app.Application;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.briar.android.contact.ContactItem;
import org.briarproject.briar.android.viewmodel.DbViewModel;
import org.briarproject.briar.api.identity.AuthorInfo;
import org.briarproject.briar.api.identity.AuthorManager;
import org.briarproject.briar.api.introduction.IntroductionManager;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;

@NotNullByDefault
public class IntroductionViewModel extends DbViewModel {

	private static final Logger LOG =
			getLogger(IntroductionViewModel.class.getName());

	private final ContactManager contactManager;
	private final AuthorManager authorManager;
	private final IntroductionManager introductionManager;

	private final MutableLiveData<Data> data = new MutableLiveData<>();
	private final MutableLiveData<Boolean> error = new MutableLiveData<>();

	LiveData<Data> getData() {
		return data;
	}

	LiveData<Boolean> getError() {
		return error;
	}

	static class Data {
		ContactItem c1;
		ContactItem c2;
		boolean possible;

		public Data(ContactItem c1, ContactItem c2, boolean possible) {
			this.c1 = c1;
			this.c2 = c2;
			this.possible = possible;
		}

		public ContactItem getContact1() {
			return c1;
		}

		public ContactItem getContact2() {
			return c2;
		}

		public boolean isPossible() {
			return possible;
		}
	}

	@Inject
	IntroductionViewModel(Application application,
			@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, TransactionManager db,
			AndroidExecutor androidExecutor, ContactManager contactManager,
			AuthorManager authorManager,
			IntroductionManager introductionManager) {
		super(application, dbExecutor, lifecycleManager, db, androidExecutor);
		this.contactManager = contactManager;
		this.authorManager = authorManager;
		this.introductionManager = introductionManager;
	}

	private int contactId1;
	private int contactId2;

	public void setContactId1(int contactId1) {
		this.contactId1 = contactId1;
	}

	public void setContactId2(int contactId2) {
		this.contactId2 = contactId2;
	}

	public void loadData() {
		runOnDbThread(() -> {
			try {
				Contact contact1 =
						contactManager.getContact(new ContactId(contactId1));
				Contact contact2 =
						contactManager.getContact(new ContactId(contactId2));
				AuthorInfo a1 = authorManager.getAuthorInfo(contact1);
				AuthorInfo a2 = authorManager.getAuthorInfo(contact2);
				boolean possible =
						introductionManager.canIntroduce(contact1, contact2);
				ContactItem c1 = new ContactItem(contact1, a1);
				ContactItem c2 = new ContactItem(contact2, a2);
				data.postValue(new Data(c1, c2, possible));
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}


	public void makeIntroduction(@Nullable String text) {
		runOnDbThread(() -> {
			// actually make the introduction
			try {
				long timestamp = System.currentTimeMillis();
				Data data = this.data.getValue();
				introductionManager.makeIntroduction(
						data.getContact1().getContact(),
						data.getContact2().getContact(), text, timestamp);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				error.postValue(true);
			}
		});
	}

}
