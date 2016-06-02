package org.briarproject.android.introduction;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.transition.Fade;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.briarproject.R;
import org.briarproject.android.ActivityComponent;
import org.briarproject.android.contact.ContactListAdapter;
import org.briarproject.android.contact.ContactListItem;
import org.briarproject.android.fragment.BaseFragment;
import org.briarproject.android.util.BriarRecyclerView;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.conversation.ConversationManager;
import org.briarproject.api.conversation.ConversationItem;
import org.briarproject.api.db.DbException;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.plugins.ConnectionRegistry;
import org.briarproject.api.sync.GroupId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

public class ContactChooserFragment extends BaseFragment {

	public final static String TAG = "ContactChooserFragment";

	private static final Logger LOG =
			Logger.getLogger(ContactChooserFragment.class.getName());

	private IntroductionActivity introductionActivity;
	private BriarRecyclerView list;
	private ContactChooserAdapter adapter;
	private int contactId;

	// Fields that are accessed from background threads must be volatile
	protected volatile Contact c1;
	@Inject
	protected volatile ContactManager contactManager;
	@Inject
	protected volatile IdentityManager identityManager;
	@Inject
	protected volatile ConversationManager conversationManager;
	@Inject
	protected volatile ConnectionRegistry connectionRegistry;

	public static ContactChooserFragment newInstance() {
		
		Bundle args = new Bundle();
		
		ContactChooserFragment fragment = new ContactChooserFragment();
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		try {
			introductionActivity = (IntroductionActivity) context;
		} catch (ClassCastException e) {
			throw new InstantiationError(
					"This fragment is only meant to be attached to the IntroductionActivity");
		}
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {

		View contentView =
				inflater.inflate(R.layout.introduction_contact_chooser,
						container, false);


		if (Build.VERSION.SDK_INT >= 21) {
			setExitTransition(new Fade());
		}

		ContactListAdapter.OnItemClickListener onItemClickListener =
				new ContactListAdapter.OnItemClickListener() {
					@Override
					public void onItemClick(View view, ContactListItem item) {
						if (c1 == null) {
							throw new RuntimeException("c1 not accountExists");
						}
						Contact c2 = item.getContact();
						if (!c1.getLocalAuthorId()
								.equals(c2.getLocalAuthorId())) {
							warnAboutDifferentIdentities(view, c1, c2);
						} else {
							introductionActivity.showMessageScreen(view, c1,
									c2);
						}
					}
				};
		adapter = new ContactChooserAdapter(getActivity(), onItemClickListener);

		list = (BriarRecyclerView) contentView.findViewById(R.id.contactList);
		list.setLayoutManager(new LinearLayoutManager(getActivity()));
		list.setAdapter(adapter);
		list.setEmptyText(getString(R.string.no_contacts));

		contactId = introductionActivity.getContactId();

		return contentView;
	}

	@Override
	public void onResume() {
		super.onResume();

		loadContacts();
	}

	@Override
	public void onPause() {
		super.onPause();
		adapter.clear();
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
	}

	private void loadContacts() {
		introductionActivity.runOnDbThread(new Runnable() {
			@Override
			public void run() {
				try {
					List<ContactListItem> contacts = new ArrayList<>();
					AuthorId localAuthorId = null;
					for (Contact c : contactManager.getActiveContacts()) {
						if (c.getId().getInt() == contactId) {
							c1 = c;
							localAuthorId = c1.getLocalAuthorId();
						} else {
							ContactId id = c.getId();
							GroupId groupId =
									conversationManager.getConversationId(id);
							Collection<ConversationItem> messages =
									getMessages(id);
							boolean connected =
									connectionRegistry.isConnected(c.getId());
							LocalAuthor localAuthor = identityManager
									.getLocalAuthor(c.getLocalAuthorId());
							contacts.add(new ContactListItem(c, localAuthor,
									connected, groupId, messages));
						}
					}
					displayContacts(localAuthorId, contacts);
				} catch (DbException e) {
					if (LOG.isLoggable(WARNING))
						LOG.log(WARNING, e.toString(), e);
				}
			}
		});
	}

	private void displayContacts(final AuthorId localAuthorId,
			final List<ContactListItem> contacts) {
		introductionActivity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				adapter.setLocalAuthor(localAuthorId);
				if (contacts.size() == 0) list.showData();
				else adapter.addAll(contacts);
			}
		});
	}

	private void warnAboutDifferentIdentities(final View view, final Contact c1,
			final Contact c2) {

		DialogInterface.OnClickListener okListener =
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						introductionActivity.showMessageScreen(view, c1, c2);
					}
				};
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity(),
				R.style.BriarDialogTheme);
		builder.setTitle(getString(
				R.string.introduction_warn_different_identities_title));
		builder.setMessage(getString(
				R.string.introduction_warn_different_identities_text));
		builder.setPositiveButton(R.string.dialog_button_introduce, okListener);
		builder.setNegativeButton(android.R.string.cancel, null);
		builder.show();
	}

	/**
	 * This needs to be called from the DbThread
	 */
	private Collection<ConversationItem> getMessages(ContactId id)
			throws DbException {

		long now = System.currentTimeMillis();

		Collection<ConversationItem> messages =
				conversationManager.getMessages(id, false);
		long duration = System.currentTimeMillis() - now;
		if (LOG.isLoggable(INFO))
			LOG.info("Loading message headers took " + duration + " ms");

		return messages;
	}
}
