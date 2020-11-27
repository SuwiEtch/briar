package org.briarproject.briar.android.settings;

import android.app.Application;
import android.content.ContentResolver;
import android.net.Uri;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.util.LogUtils;
import org.briarproject.briar.android.attachment.ImageCompressor;
import org.briarproject.briar.android.attachment.ImageSizeCalculator;
import org.briarproject.briar.api.avatar.AvatarManager;
import org.briarproject.briar.api.identity.AuthorInfo;
import org.briarproject.briar.api.identity.AuthorManager;
import org.jsoup.UnsupportedMimeTypeException;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

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
	private final AuthorManager authorManager;
	@DatabaseExecutor
	private final Executor dbExecutor;

	private final MutableLiveData<LocalAuthorInfo> ourAuthorInfo =
			new MutableLiveData<>();

	@Inject
	SettingsViewModel(Application application,
			IdentityManager identityManager,
			ImageSizeCalculator imageSizeCalculator,
			AvatarManager avatarManager,
			AuthorManager authorManager,
			@DatabaseExecutor Executor dbExecutor) {
		super(application);
		this.identityManager = identityManager;
		this.imageSizeCalculator = imageSizeCalculator;
		this.avatarManager = avatarManager;
		this.authorManager = authorManager;
		this.dbExecutor = dbExecutor;
	}

	void onCreate() {
		if (ourAuthorInfo.getValue() == null) loadAuthorInfo();
	}

	private void loadAuthorInfo() {
		dbExecutor.execute(() -> {
			try {
				LocalAuthor localAuthor = identityManager.getLocalAuthor();
				AuthorInfo authorInfo = authorManager.getMyAuthorInfo();
				ourAuthorInfo
						.postValue(
								new LocalAuthorInfo(localAuthor, authorInfo));
			} catch (DbException e) {
				LogUtils.logException(LOG, Level.WARNING, e);
			}
		});
	}

	LiveData<LocalAuthorInfo> getOurAuthorInfo() {
		return ourAuthorInfo;
	}

	boolean setAvatar(Uri uri) {
		try {
			trySetAvatar(uri);
			return true;
		} catch (IOException | DbException e) {
			return false;
		}
	}

	private void trySetAvatar(Uri uri) throws IOException, DbException {
		// TODO: move to IOExecutor
		// TODO: trigger update on ourAuthorInfo
		ContentResolver contentResolver =
				getApplication().getContentResolver();
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
		is = imageCompressor
				.compressImage(is, contentType, MAX_ATTACHMENT_DIMENSION);
		contentType = "image/jpeg";
		avatarManager.addAvatar(contentType, is);
	}

}
