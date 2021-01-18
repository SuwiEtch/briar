package org.briarproject.bramble.autodelete;

import org.briarproject.bramble.api.autodelete.AutoDeleteHook;
import org.briarproject.bramble.api.autodelete.AutoDeleteManager;
import org.briarproject.bramble.api.autodelete.event.AutoDeleteTimerStartedEvent;
import org.briarproject.bramble.api.autodelete.event.MessagesAutoDeletedEvent;
import org.briarproject.bramble.api.db.DatabaseComponent;
import org.briarproject.bramble.api.db.DatabaseExecutor;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.Transaction;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventExecutor;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.lifecycle.LifecycleManager.OpenDatabaseHook;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.ClientId;
import org.briarproject.bramble.api.sync.Group;
import org.briarproject.bramble.api.sync.GroupId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.api.system.TaskScheduler;
import org.briarproject.bramble.api.system.TaskScheduler.Cancellable;
import org.briarproject.bramble.api.versioning.ClientMajorVersion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

import static java.lang.Math.max;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.api.db.DatabaseComponent.NO_AUTO_DELETE_DEADLINE;
import static org.briarproject.bramble.util.LogUtils.logException;

@ThreadSafe
@NotNullByDefault
class AutoDeleteManagerImpl
		implements AutoDeleteManager, OpenDatabaseHook, EventListener {

	private static final Logger LOG =
			getLogger(AutoDeleteManagerImpl.class.getName());

	private final Executor dbExecutor;
	private final DatabaseComponent db;
	private final TaskScheduler taskScheduler;
	private final Clock clock;
	private final Map<ClientMajorVersion, AutoDeleteHook> hooks =
			new ConcurrentHashMap<>();
	private final Object lock = new Object();

	@GuardedBy("lock")
	private long nextDeadline = NO_AUTO_DELETE_DEADLINE;
	@GuardedBy("lock")
	@Nullable
	private Cancellable nextCheck = null;

	@Inject
	AutoDeleteManagerImpl(@DatabaseExecutor Executor dbExecutor,
			DatabaseComponent db, TaskScheduler taskScheduler, Clock clock) {
		this.dbExecutor = dbExecutor;
		this.db = db;
		this.taskScheduler = taskScheduler;
		this.clock = clock;
	}

	@Override
	public void registerAutoDeleteHook(ClientId c, int majorVersion,
			AutoDeleteHook hook) {
		hooks.put(new ClientMajorVersion(c, majorVersion), hook);
	}

	@Override
	public void onDatabaseOpened(Transaction txn) throws DbException {
		deleteMessages(txn);
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof AutoDeleteTimerStartedEvent) {
			AutoDeleteTimerStartedEvent a = (AutoDeleteTimerStartedEvent) e;
			onTimerStarted(a.getAutoDeleteDeadline());
		}
	}

	@EventExecutor
	private void onTimerStarted(long deadline) {
		synchronized (lock) {
			if (nextDeadline == NO_AUTO_DELETE_DEADLINE ||
					deadline < nextDeadline) {
				nextDeadline = deadline;
				if (nextCheck != null) nextCheck.cancel();
				nextCheck = scheduleDeletion(deadline);
			}
		}
	}

	private Cancellable scheduleDeletion(long deadline) {
		long now = clock.currentTimeMillis();
		long delay = max(0, deadline - now);
		if (LOG.isLoggable(INFO)) {
			LOG.info("Scheduling auto-delete task in " + delay + " ms");
		}
		return taskScheduler.schedule(this::deleteMessages, dbExecutor,
				delay, MILLISECONDS);
	}

	private void deleteMessages() {
		try {
			db.transaction(false, this::deleteMessages);
		} catch (DbException e) {
			logException(LOG, WARNING, e);
		}
	}

	private void deleteMessages(Transaction txn) throws DbException {
		Map<GroupId, ClientMajorVersion> clientCache = new HashMap<>();
		Map<GroupId, Collection<MessageId>> deleted = new HashMap<>();
		Map<MessageId, GroupId> ids = db.getMessagesToDelete(txn);
		if (LOG.isLoggable(INFO)) LOG.info(ids.size() + " messages to delete");
		for (Entry<MessageId, GroupId> e : ids.entrySet()) {
			MessageId m = e.getKey();
			GroupId g = e.getValue();
			ClientMajorVersion cv = clientCache.get(g);
			if (cv == null) {
				Group group = db.getGroup(txn, g);
				cv = new ClientMajorVersion(group.getClientId(),
						group.getMajorVersion());
				clientCache.put(g, cv);
			}
			AutoDeleteHook hook = hooks.get(cv);
			if (hook == null) {
				if (LOG.isLoggable(WARNING)) {
					LOG.warning("No auto-delete hook for " + cv);
				}
			} else if (hook.deleteMessage(txn, g, m)) {
				Collection<MessageId> messageIds = deleted.get(g);
				if (messageIds == null) {
					messageIds = new ArrayList<>();
					deleted.put(g, messageIds);
				}
				messageIds.add(m);
			} else {
				// TODO: Do something about this
				LOG.info("Message was not deleted");
			}
		}
		for (Entry<GroupId, Collection<MessageId>> e : deleted.entrySet()) {
			txn.attach(new MessagesAutoDeletedEvent(e.getKey(), e.getValue()));
		}
		long deadline = db.getNextAutoDeleteDeadline(txn);
		synchronized (lock) {
			nextDeadline = deadline;
			if (deadline == NO_AUTO_DELETE_DEADLINE) nextCheck = null;
			else nextCheck = scheduleDeletion(deadline);
		}
	}
}
