package org.briarproject.briar.android.contact;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import org.briarproject.bramble.api.connection.ConnectionRegistry;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.contact.ContactManager;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.contact.BaseContactListAdapter.OnContactClickListener;
import org.briarproject.briar.android.contact.add.remote.AddContactActivity;
import org.briarproject.briar.android.contact.add.remote.PendingContactListActivity;
import org.briarproject.briar.android.conversation.ConversationActivity;
import org.briarproject.briar.android.fragment.BaseFragment;
import org.briarproject.briar.android.keyagreement.ContactExchangeActivity;
import org.briarproject.briar.android.util.BriarSnackbarBuilder;
import org.briarproject.briar.android.view.BriarRecyclerView;
import org.briarproject.briar.api.android.AndroidNotificationManager;
import org.briarproject.briar.api.conversation.ConversationManager;

import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.annotation.UiThread;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.util.Pair;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import io.github.kobakei.materialfabspeeddial.FabSpeedDial;
import io.github.kobakei.materialfabspeeddial.FabSpeedDial.OnMenuItemClickListener;

import static android.os.Build.VERSION.SDK_INT;
import static androidx.core.app.ActivityOptionsCompat.makeSceneTransitionAnimation;
import static androidx.core.view.ViewCompat.getTransitionName;
import static com.google.android.material.snackbar.BaseTransientBottomBar.LENGTH_INDEFINITE;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.briar.android.conversation.ConversationActivity.CONTACT_ID;
import static org.briarproject.briar.android.util.UiUtils.isSamsung7;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class ContactListFragment extends BaseFragment
		implements OnMenuItemClickListener {

	public static final String TAG = ContactListFragment.class.getName();
	private static final Logger LOG = Logger.getLogger(TAG);

	@Inject
	ConnectionRegistry connectionRegistry;
	@Inject
	EventBus eventBus;
	@Inject
	AndroidNotificationManager notificationManager;
	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private ContactListViewModel viewModel;
	private ContactListAdapter adapter;
	private BriarRecyclerView list;

	/**
	 * The Snackbar is non-null when shown and null otherwise.
	 * Use {@link #showSnackBar()} and {@link #dismissSnackBar()} to interact.
	 */
	@Nullable
	private Snackbar snackbar = null;

	// Fields that are accessed from background threads must be volatile
	@Inject
	volatile ContactManager contactManager;
	@Inject
	volatile ConversationManager conversationManager;

	public static ContactListFragment newInstance() {
		Bundle args = new Bundle();
		ContactListFragment fragment = new ContactListFragment();
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public String getUniqueTag() {
		return TAG;
	}

	@Override
	public void injectFragment(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(ContactListViewModel.class);
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater,
			@Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		requireActivity().setTitle(R.string.contact_list_button);

		View contentView = inflater.inflate(R.layout.fragment_contact_list,
				container, false);

		FabSpeedDial speedDial = contentView.findViewById(R.id.speedDial);
		speedDial.addOnMenuItemClickListener(this);

		OnContactClickListener<ContactListItem> onContactClickListener =
				(view, item) -> {
					Intent i = new Intent(getActivity(),
							ConversationActivity.class);
					ContactId contactId = item.getContact().getId();
					i.putExtra(CONTACT_ID, contactId.getInt());

					if (SDK_INT >= 23 && !isSamsung7()) {
						ContactListItemViewHolder holder =
								(ContactListItemViewHolder) list
										.getRecyclerView()
										.findViewHolderForAdapterPosition(
												adapter.findItemPosition(item));
						Pair<View, String> avatar =
								Pair.create(holder.avatar,
										getTransitionName(holder.avatar));
						Pair<View, String> bulb =
								Pair.create(holder.bulb,
										getTransitionName(holder.bulb));
						ActivityOptionsCompat options =
								makeSceneTransitionAnimation(getActivity(),
										avatar, bulb);
						ActivityCompat.startActivity(getActivity(), i,
								options.toBundle());
					} else {
						// work-around for android bug #224270
						startActivity(i);
					}
				};
		adapter = new ContactListAdapter(onContactClickListener);
		list = contentView.findViewById(R.id.list);
		list.setLayoutManager(new LinearLayoutManager(requireContext()));
		list.setAdapter(adapter);
		list.setEmptyImage(R.drawable.ic_empty_state_contact_list);
		list.setEmptyText(getString(R.string.no_contacts));
		list.setEmptyAction(getString(R.string.no_contacts_action));

		return contentView;
	}

	@Override
	public void onMenuItemClick(FloatingActionButton fab, @Nullable TextView v,
			int itemId) {
		switch (itemId) {
			case R.id.action_add_contact_nearby:
				Intent intent =
						new Intent(getContext(), ContactExchangeActivity.class);
				startActivity(intent);
				return;
			case R.id.action_add_contact_remotely:
				startActivity(
						new Intent(getContext(), AddContactActivity.class));
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		notificationManager.clearAllContactNotifications();
		notificationManager.clearAllContactAddedNotifications();
		loadContacts();
		checkForPendingContacts();
		list.startPeriodicUpdate();
	}

	private void checkForPendingContacts() {
		listener.runOnDbThread(() -> {
			try {
				if (contactManager.getPendingContacts().isEmpty()) {
					runOnUiThreadUnlessDestroyed(this::dismissSnackBar);
				} else {
					runOnUiThreadUnlessDestroyed(this::showSnackBar);
				}
			} catch (DbException e) {
				logException(LOG, WARNING, e);
			}
		});
	}

	@Override
	public void onStop() {
		super.onStop();
		eventBus.removeListener(this);
		adapter.clear();
		list.showProgressBar();
		list.stopPeriodicUpdate();
		dismissSnackBar();
	}

	@UiThread
	private void showSnackBar() {
		if (snackbar != null) return;
		View v = requireView();
		int stringRes = R.string.pending_contact_requests_snackbar;
		snackbar = new BriarSnackbarBuilder()
				.setAction(R.string.show, view -> showPendingContactList())
				.make(v, stringRes, LENGTH_INDEFINITE);
		snackbar.show();
	}

	@UiThread
	private void dismissSnackBar() {
		if (snackbar == null) return;
		snackbar.dismiss();
		snackbar = null;
	}

	private void showPendingContactList() {
		Intent i = new Intent(getContext(), PendingContactListActivity.class);
		startActivity(i);
	}

}
