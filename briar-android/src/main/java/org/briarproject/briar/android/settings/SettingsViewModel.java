package org.briarproject.briar.android.settings;

import android.app.Application;
import android.content.ContentResolver;
import android.net.Uri;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.attachment.ImageCompressor;
import org.briarproject.briar.android.attachment.ImageSizeCalculator;
import org.briarproject.briar.android.viewmodel.LiveResult;
import org.briarproject.briar.api.avatar.AvatarManager;
import org.jsoup.UnsupportedMimeTypeException;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.lifecycle.AndroidViewModel;

import static java.util.Arrays.asList;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.AndroidUtils.getSupportedImageContentTypes;

@NotNullByDefault
public class SettingsViewModel extends AndroidViewModel {

	private static final int MAX_ATTACHMENT_DIMENSION = 1000;

	private final static Logger LOG =
			getLogger(SettingsViewModel.class.getName());

	private final IdentityManager identityManager;
	private final ImageSizeCalculator imageSizeCalculator;
	private final AvatarManager avatarManager;
	@DatabaseExecutor
	private final Executor dbExecutor;

	@Inject
	SettingsViewModel(Application application,
			IdentityManager identityManager,
			ImageSizeCalculator imageSizeCalculator,
			AvatarManager avatarManager,
			@DatabaseExecutor Executor dbExecutor) {
		super(application);
		this.identityManager = identityManager;
		this.imageSizeCalculator = imageSizeCalculator;
		this.avatarManager = avatarManager;
		this.dbExecutor = dbExecutor;
	}

	LiveResult<LocalAuthor> getOurselves() {
		try {
			return new LiveResult<>(identityManager.getLocalAuthor());
		} catch (DbException e) {
			return new LiveResult<>(e);
		}
	}

	void setAvatar(ContentResolver contentResolver, Uri uri)
			throws IOException, DbException {
		String contentType = contentResolver.getType(uri);
		if (contentType == null) throw new IOException("null content type");
		if (!asList(getSupportedImageContentTypes()).contains(contentType)) {
			String uriString = uri.toString();
			throw new UnsupportedMimeTypeException("", contentType, uriString);
		}
		InputStream is = contentResolver.openInputStream(uri);
		if (is == null) throw new IOException();
		ImageCompressor imageCompressor =
				new ImageCompressor(imageSizeCalculator);
		is = imageCompressor.compressImage(is, contentType, MAX_ATTACHMENT_DIMENSION);
		contentType = "image/jpeg";
		avatarManager.addAvatar(contentType, is);
	}

}
