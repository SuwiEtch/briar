package org.briarproject.bramble.api.autodelete;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.lifecycle.LifecycleManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;

@NotNullByDefault
public interface AutoDeleteManager {

	/**
	 * Registers a hook to be called when messages are due for auto-deletion.
	 * This method should be called before
	 * {@link LifecycleManager#startServices(SecretKey)}.
	 */
	void registerAutoDeleteHook(ClientId c, int majorVersion,
			AutoDeleteHook hook);
}
