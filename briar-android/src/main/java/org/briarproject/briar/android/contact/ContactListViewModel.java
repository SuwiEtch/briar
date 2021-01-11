package org.briarproject.briar.android.contact;

import android.app.Application;

import org.briarproject.bramble.api.connection.ConnectionRegistry;
import org.briarproject.bramble.api.contact.Contact;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.contact.event.ContactAddedEvent;
import org.briarproject.bramble.api.contact.event.ContactRemovedEvent;
import org.briarproject.bramble.api.contact.event.PendingContactAddedEvent;
import org.briarproject.bramble.api.contact.event.PendingContactRemovedEvent;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.NoSuchContactException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.db.TransactionManager;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.event.ContactConnectedEvent;
import org.briarproject.bramble.api.plugin.event.ContactDisconnectedEvent;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.briar.android.viewmodel.DbViewModel;
import org.briarproject.briar.android.viewmodel.LiveResult;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.conversation.ConversationManager;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;
import org.briarproject.briar.api.conversation.event.ConversationMessageReceivedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.lifecycle.MutableLiveData;

import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.now;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ContactListViewModel extends DbViewModel implements EventListener {

	private static final Logger LOG =
			getLogger(ContactListViewModel.class.getName());

	private final ContactManager contactManager;
	private final ConversationManager conversationManager;
	private final ConnectionRegistry connectionRegistry;
	private final AndroidExecutor androidExecutor;

	private final MutableLiveData<LiveResult<List<ContactListItem>>>
			contactListItems = new MutableLiveData<>();

	@Inject
	public ContactListViewModel(Application application,
			@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, TransactionManager db,
			AndroidExecutor androidExecutor, ContactManager contactManager,
			ConversationManager conversationManager,
			ConnectionRegistry connectionRegistry) {
		super(application, dbExecutor, lifecycleManager, db, androidExecutor);
		this.androidExecutor = androidExecutor;
		this.contactManager = contactManager;
		this.conversationManager = conversationManager;
		this.connectionRegistry = connectionRegistry;
	}

	public void loadContacts() {
		runOnDbThread(() -> {
			try {
				List<ContactListItem> contacts = loadContacts(null);
				androidExecutor.runOnUiThread(() -> {
					this.contactListItems.setValue(new LiveResult<>(contacts));
				});
			} catch (DbException e) {
				e.printStackTrace();
			}
		});
		// TODO: I would like to use this instead of the above, but it crashes the app :/
//		loadList(this::loadContacts, contactListItems::setValue);
	}

	private List<ContactListItem> loadContacts(@Nullable Transaction txn)
			throws DbException {
		long start = now();
		List<ContactListItem> contacts = new ArrayList<>();
		for (Contact c : contactManager.getContacts()) {
			try {
				ContactId id = c.getId();
				MessageTracker.GroupCount count =
						conversationManager.getGroupCount(id);
				boolean connected =
						connectionRegistry.isConnected(c.getId());
				contacts.add(new ContactListItem(c, connected, count));
			} catch (NoSuchContactException e) {
				// Continue
			}
		}
		logDuration(LOG, "Full load", start);
		return contacts;
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof ContactAddedEvent) {
			LOG.info("Contact added, reloading");
//			loadContacts();
		} else if (e instanceof ContactConnectedEvent) {
//			setConnected(((ContactConnectedEvent) e).getContactId(), true);
		} else if (e instanceof ContactDisconnectedEvent) {
//			setConnected(((ContactDisconnectedEvent) e).getContactId(), false);
		} else if (e instanceof ContactRemovedEvent) {
			LOG.info("Contact removed, removing item");
//			removeItem(((ContactRemovedEvent) e).getContactId());
		} else if (e instanceof ConversationMessageReceivedEvent) {
			LOG.info("Conversation message received, updating item");
			ConversationMessageReceivedEvent<?> p =
					(ConversationMessageReceivedEvent<?>) e;
			ConversationMessageHeader h = p.getMessageHeader();
//			updateItem(p.getContactId(), h);
		} else if (e instanceof PendingContactAddedEvent ||
				e instanceof PendingContactRemovedEvent) {
//			checkForPendingContacts();
		}
	}

	public MutableLiveData<LiveResult<List<ContactListItem>>> getContactListItems() {
		return contactListItems;
	}

}
