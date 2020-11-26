package org.briarproject.briar.android.settings;

import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.BaseActivity;

import java.io.IOException;

import javax.inject.Inject;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProviders;

import static android.widget.Toast.LENGTH_LONG;
import static java.util.Objects.requireNonNull;

public class ConfirmAvatarDialogFragment extends DialogFragment {

	final static String TAG = ConfirmAvatarDialogFragment.class.getName();

	private static final String ARG_URI = "uri";

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private SettingsViewModel settingsViewModel;

	public static ConfirmAvatarDialogFragment newInstance(Uri uri) {
		ConfirmAvatarDialogFragment f = new ConfirmAvatarDialogFragment();

		Bundle args = new Bundle();
		args.putString(ARG_URI, uri.toString());
		f.setArguments(args);

		return f;
	}

	@Override
	public void onAttach(Context ctx) {
		super.onAttach(ctx);
		((BaseActivity) requireActivity()).getActivityComponent().inject(this);
	}

	private Uri uri;

	public Dialog onCreateDialog(Bundle savedInstanceState) {
		ViewModelProvider provider =
				ViewModelProviders.of(this, viewModelFactory);
		settingsViewModel = provider.get(SettingsViewModel.class);

		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

		LayoutInflater factory = LayoutInflater.from(getContext());
		final View view =
				factory.inflate(R.layout.fragment_confirm_avatar_dialog, null);
		builder.setView(view);

		builder.setTitle(R.string.dialog_confirm_profile_picture_title)
				.setMessage(R.string.dialog_confirm_profile_picture_question);
		builder.setNegativeButton(R.string.cancel, null);
		builder.setPositiveButton(R.string.dialog_confirm_profile_picture_set,
				(dialog, id) -> {
					trySetAvatar();
				});

		Bundle args = requireArguments();
		String argUri = requireNonNull(args.getString(ARG_URI));
		uri = Uri.parse(argUri);

		MutableLiveData<LocalAuthor> ourselves =
				settingsViewModel.getOurAuthor();

		if (ourselves.getValue() == null) {
			Toast.makeText(getContext(),
					R.string.profile_picture_internal_error, LENGTH_LONG)
					.show();
			// It's not exactly documented that it works to dismiss a dialog at this stage,
			// but I also haven't been able to find a better way to do it.
			dismiss();
		} else {
			LocalAuthor us = ourselves.getValue();

			TextView textViewUserName = view.findViewById(R.id.username);
			textViewUserName.setText(us.getName());
		}

		ImageView imageView = view.findViewById(R.id.image);
		imageView.setImageResource(R.drawable.contact_connected);
		imageView.setImageURI(uri);

		return builder.create();
	}

	private void trySetAvatar() {
		try {
			ContentResolver contentResolver = getContext().getContentResolver();
			settingsViewModel.setAvatar(contentResolver, uri);
		} catch (IOException | DbException e) {
			Toast.makeText(getActivity(),
					"An error occurred while setting the avatar image",
					Toast.LENGTH_SHORT).show();
		}
	}

}
