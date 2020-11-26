package org.briarproject.briar.android.settings;

import android.app.Application;

import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.IdentityManager;
import org.briarproject.bramble.api.identity.LocalAuthor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.briar.android.viewmodel.LiveResult;

import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.inject.Inject;

import androidx.lifecycle.AndroidViewModel;

import static java.util.logging.Logger.getLogger;

@NotNullByDefault
public class SettingsViewModel extends AndroidViewModel {

	private final static Logger LOG =
			getLogger(SettingsViewModel.class.getName());

	private final IdentityManager identityManager;
	@DatabaseExecutor
	private final Executor dbExecutor;

	@Inject
	SettingsViewModel(Application application,
			IdentityManager identityManager,
			@DatabaseExecutor Executor dbExecutor) {
		super(application);
		this.identityManager = identityManager;
		this.dbExecutor = dbExecutor;
	}

	public LiveResult<LocalAuthor> getOurselves() {
		try {
			return new LiveResult<>(identityManager.getLocalAuthor());
		} catch (DbException e) {
			return new LiveResult<>(e);
		}
	}

}
