package org.briarproject.briar.android.introduction;

import android.app.Application;
import android.widget.Toast;

import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.briar.R;
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

import static android.widget.Toast.LENGTH_SHORT;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logException;

@NotNullByDefault
class IntroductionViewModel extends DbViewModel {

	private static final Logger LOG =
			getLogger(IntroductionViewModel.class.getName());

	private final ContactManager contactManager;
	private final AuthorManager authorManager;
	private final IntroductionManager introductionManager;

	private final MutableLiveData<IntroductionInfo> introductionInfo =
			new MutableLiveData<>();

	LiveData<IntroductionInfo> getIntroductionInfo() {
		return introductionInfo;
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

	void setContactIds(int contactId1, int contactId2) {
		this.contactId1 = contactId1;
		this.contactId2 = contactId2;
		loadIntroductionInfo();
	}

	private void loadIntroductionInfo() {
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
				introductionInfo.postValue(
						new IntroductionInfo(c1, c2, possible));
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	void makeIntroduction(@Nullable String text) {
		runOnDbThread(() -> {
			// actually make the introduction
			try {
				long timestamp = System.currentTimeMillis();
				IntroductionInfo data = this.introductionInfo.getValue();
				introductionManager.makeIntroduction(
						data.getContact1().getContact(),
						data.getContact2().getContact(), text, timestamp);
			} catch (DbException e) {
				logException(LOG, WARNING, e);
				androidExecutor.runOnUiThread(() -> Toast.makeText(
						getApplication(), R.string.introduction_error,
						LENGTH_SHORT).show());
			}
		});
	}

}
