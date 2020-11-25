package org.briarproject.briar.android.settings;

import android.app.Dialog;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;

import org.briarproject.briar.R;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import static java.util.Objects.requireNonNull;

public class ConfirmAvatarDialogFragment extends DialogFragment {

	final static String TAG = ConfirmAvatarDialogFragment.class.getName();

	private static final String ARG_URI = "uri";

	public static ConfirmAvatarDialogFragment newInstance(Uri uri) {
		ConfirmAvatarDialogFragment f = new ConfirmAvatarDialogFragment();

		Bundle args = new Bundle();
		args.putString(ARG_URI, uri.toString());
		f.setArguments(args);

		return f;
	}

	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

		LayoutInflater factory = LayoutInflater.from(getContext());
		final View view =
				factory.inflate(R.layout.fragment_confirm_avatar_dialog, null);
		builder.setView(view);

		builder.setTitle(R.string.dialog_confirm_profile_picture_title)
				.setMessage(R.string.dialog_confirm_profile_picture_question);
		builder.setNegativeButton(R.string.cancel, null);
		builder.setPositiveButton(R.string.dialog_confirm_profile_picture_set, (dialog, id) -> {
			//TODO: handle this
		});

		Bundle args = requireArguments();
		String argUri = requireNonNull(args.getString(ARG_URI));

		Uri uri = Uri.parse(argUri);
		ImageView imageView = view.findViewById(R.id.image);
		imageView.setImageResource(R.drawable.contact_connected);
		imageView.setImageURI(uri);

		return builder.create();
	}

}
