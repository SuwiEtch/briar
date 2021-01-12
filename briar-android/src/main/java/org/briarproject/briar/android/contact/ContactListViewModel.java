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
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.bramble.api.plugin.event.ContactConnectedEvent;
import org.briarproject.bramble.api.plugin.event.ContactDisconnectedEvent;
import org.briarproject.bramble.api.sync.event.MessageAddedEvent;
import org.briarproject.bramble.api.system.AndroidExecutor;
import org.briarproject.briar.android.viewmodel.DbViewModel;
import org.briarproject.briar.android.viewmodel.LiveResult;
import org.briarproject.briar.api.client.MessageTracker;
import org.briarproject.briar.api.conversation.ConversationManager;
import org.briarproject.briar.api.conversation.ConversationMessageHeader;
import org.briarproject.briar.api.conversation.event.ConversationMessageReceivedEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.arch.core.util.Function;
import androidx.lifecycle.MutableLiveData;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.LogUtils.logDuration;
import static org.briarproject.bramble.util.LogUtils.logException;
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
	private final EventBus eventBus;

	private final MutableLiveData<LiveResult<List<ContactListItem>>>
			contactListItems = new MutableLiveData<>();

	private final MutableLiveData<Boolean> hasPendingContacts =
			new MutableLiveData<>();

	@Inject
	public ContactListViewModel(Application application,
			@DatabaseExecutor Executor dbExecutor,
			LifecycleManager lifecycleManager, TransactionManager db,
			AndroidExecutor androidExecutor, ContactManager contactManager,
			ConversationManager conversationManager,
			ConnectionRegistry connectionRegistry, EventBus eventBus) {
		super(application, dbExecutor, lifecycleManager, db, androidExecutor);
		this.androidExecutor = androidExecutor;
		this.contactManager = contactManager;
		this.conversationManager = conversationManager;
		this.connectionRegistry = connectionRegistry;
		this.eventBus = eventBus;
		this.eventBus.addListener(this);
	}

	@Override
	protected void onCleared() {
		super.onCleared();
		eventBus.removeListener(this);
	}

	public void loadContacts() {
		runOnDbThread(() -> {
			try {
				List<ContactListItem> contacts = loadContacts(null);
				androidExecutor.runOnUiThread(() -> {
					contactListItems.setValue(new LiveResult<>(contacts));
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
		Collections.sort(contacts);
		logDuration(LOG, "Full load", start);
		return contacts;
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof ContactAddedEvent) {
			LOG.info("Contact added, reloading");
			loadContacts();
		} else if (e instanceof ContactConnectedEvent) {
			updateItem(((ContactConnectedEvent) e).getContactId(),
					item -> item.updatedItem(true), false);
		} else if (e instanceof ContactDisconnectedEvent) {
			updateItem(((ContactDisconnectedEvent) e).getContactId(),
					item -> item.updatedItem(false), false);
		} else if (e instanceof ContactRemovedEvent) {
			LOG.info("Contact removed, removing item");
			removeItem(((ContactRemovedEvent) e).getContactId());
		} else if (e instanceof ConversationMessageReceivedEvent) {
			LOG.info("Conversation message received, updating item");
			ConversationMessageReceivedEvent<?> p =
					(ConversationMessageReceivedEvent<?>) e;
			ConversationMessageHeader h = p.getMessageHeader();
			updateItem(p.getContactId(), item -> item.updatedItem(h), true);
		} else if (e instanceof MessageAddedEvent) {
			MessageAddedEvent a = (MessageAddedEvent) e;
			updateItem(a.getContactId(),
					item -> item.updatedItem(a.getMessage()), true);
		} else if (e instanceof PendingContactAddedEvent ||
				e instanceof PendingContactRemovedEvent) {
			checkForPendingContacts();
		}
	}

	public MutableLiveData<LiveResult<List<ContactListItem>>> getContactListItems() {
		return contactListItems;
	}

	public MutableLiveData<Boolean> getHasPendingContacts() {
		return hasPendingContacts;
	}

	private void updateItem(ContactId c,
			Function<ContactListItem, ContactListItem> replacer, boolean sort) {
		List<ContactListItem> list = updateListItems(contactListItems,
				itemToTest -> itemToTest.getContact().getId().equals(c),
				replacer);
		if (list == null) return;
		if (sort) Collections.sort(list);
		contactListItems.setValue(new LiveResult<>(list));
	}

	private void removeItem(ContactId c) {
		List<ContactListItem> list = removeListItems(contactListItems,
				itemToTest -> itemToTest.getContact().getId().equals(c));
		if (list == null) return;
		contactListItems.setValue(new LiveResult<>(list));
	}

	public void checkForPendingContacts() {
		runOnDbThread(() -> {
			try {
				boolean hasPending =
						!contactManager.getPendingContacts().isEmpty();
				androidExecutor.runOnUiThread(() -> {
					hasPendingContacts.setValue(hasPending);
				});
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

}
