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

		// TODO: move strings to strings.xml
		builder.setTitle("Please confirm")
				.setMessage("Do you want to use this image?");
		builder.setNegativeButton(android.R.string.no, (dialog, id) -> {
			// nothing to do here
		});
		builder.setPositiveButton(android.R.string.yes, (dialog, id) -> {
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
