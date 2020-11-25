package org.briarproject.briar.android.settings;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BriarActivity;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProviders;
import de.hdodenhof.circleimageview.CircleImageView;
import im.delight.android.identicons.IdenticonDrawable;

import static android.content.Intent.ACTION_GET_CONTENT;
import static android.content.Intent.ACTION_OPEN_DOCUMENT;
import static android.content.Intent.CATEGORY_OPENABLE;
import static android.content.Intent.EXTRA_ALLOW_MULTIPLE;
import static android.content.Intent.EXTRA_MIME_TYPES;
import static android.os.Build.VERSION.SDK_INT;
import static org.briarproject.bramble.util.AndroidUtils.getSupportedImageContentTypes;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_AVATAR_IMAGE;

public class SettingsActivity extends BriarActivity {

	@Inject
	ViewModelProvider.Factory viewModelFactory;

	private SettingsViewModel settingsViewModel;

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setHomeButtonEnabled(true);
			actionBar.setDisplayHomeAsUpEnabled(true);
		}

		setContentView(R.layout.activity_settings);

		ViewModelProvider provider =
				ViewModelProviders.of(this, viewModelFactory);
		settingsViewModel = provider.get(SettingsViewModel.class);

		try {
			LocalAuthor ourselves =
					settingsViewModel.getOurselves();

			TextView textViewUserName = findViewById(R.id.avatarTitle);
			textViewUserName.setText(ourselves.getName());

			CircleImageView imageViewAvatar = findViewById(R.id.avatarImage);
			imageViewAvatar.setImageDrawable(
					new IdenticonDrawable(ourselves.getId().getBytes()));
		} catch (DbException e) {
			e.printStackTrace();
		}

		View avatarGroup = findViewById(R.id.avatarGroup);
		avatarGroup.setOnClickListener(e -> {
			selectAvatarImage();
		});
	}

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		}
		return false;
	}

	private void selectAvatarImage() {
		Intent intent = getAvatarFileIntent();
		startActivityForResult(intent, REQUEST_AVATAR_IMAGE);
	}

	private Intent getAvatarFileIntent() {
		// TODO: I copied this from TextAttachmentController#getAttachFileIntent,
		// maybe we should centralize it into a utility class.
		Intent intent = new Intent(SDK_INT >= 19 ?
				ACTION_OPEN_DOCUMENT : ACTION_GET_CONTENT);
		intent.setType("image/*");
		intent.addCategory(CATEGORY_OPENABLE);
		if (SDK_INT >= 19)
			intent.putExtra(EXTRA_MIME_TYPES, getSupportedImageContentTypes());
		return intent;
	}

	@Override
	protected void onActivityResult(int request, int result,
			@Nullable Intent data) {
		super.onActivityResult(request, result, data);

		if (request == REQUEST_AVATAR_IMAGE && result == RESULT_OK) {
			onAvatarImageReceived(data);
		}
	}

	private void onAvatarImageReceived(Intent resultData) {
		if (resultData == null) return;
		List<Uri> uris = new ArrayList<>();
		if (resultData.getData() != null) {
			uris.add(resultData.getData());
		} else if (SDK_INT >= 18 && resultData.getClipData() != null) {
			ClipData clipData = resultData.getClipData();
			for (int i = 0; i < clipData.getItemCount(); i++) {
				uris.add(clipData.getItemAt(i).getUri());
			}
		}
		if (uris.isEmpty()) {
			// TODO: show message that an image needs to be selected
			return;
		} else if (uris.size() != 1) {
			// TODO: show message that only one image should be selected
			return;
		}
		Uri uri = uris.get(0);

		ConfirmAvatarDialogFragment dialog =
				ConfirmAvatarDialogFragment.newInstance(uri);
		dialog.show(getSupportFragmentManager(),
				ConfirmAvatarDialogFragment.TAG);
	}

}
