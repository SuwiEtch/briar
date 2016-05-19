package org.briarproject;

import net.jodah.concurrentunit.Waiter;

import org.briarproject.api.clients.MessageQueueManager;
import org.briarproject.api.clients.PrivateGroupFactory;
import org.briarproject.api.clients.SessionId;
import org.briarproject.api.contact.Contact;
import org.briarproject.api.contact.ContactId;
import org.briarproject.api.contact.ContactManager;
import org.briarproject.api.crypto.SecretKey;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.db.DatabaseComponent;
import org.briarproject.api.db.DbException;
import org.briarproject.api.db.Metadata;
import org.briarproject.api.db.Transaction;
import org.briarproject.api.event.Event;
import org.briarproject.api.event.EventListener;
import org.briarproject.api.event.ForumInvitationReceivedEvent;
import org.briarproject.api.event.ForumInvitationResponseReceivedEvent;
import org.briarproject.api.event.MessageValidatedEvent;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.forum.ForumInvitationMessage;
import org.briarproject.api.forum.ForumManager;
import org.briarproject.api.forum.ForumSharingManager;
import org.briarproject.api.identity.AuthorFactory;
import org.briarproject.api.identity.IdentityManager;
import org.briarproject.api.identity.LocalAuthor;
import org.briarproject.api.lifecycle.LifecycleManager;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.SyncSession;
import org.briarproject.api.sync.SyncSessionFactory;
import org.briarproject.api.system.Clock;
import org.briarproject.contact.ContactModule;
import org.briarproject.crypto.CryptoModule;
import org.briarproject.forum.ForumModule;
import org.briarproject.lifecycle.LifecycleModule;
import org.briarproject.properties.PropertiesModule;
import org.briarproject.sync.SyncModule;
import org.briarproject.transport.TransportModule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import javax.inject.Inject;

import static org.briarproject.TestPluginsModule.MAX_LATENCY;
import static org.briarproject.api.forum.ForumConstants.FORUM_SALT_LENGTH;
import static org.briarproject.api.forum.ForumConstants.SHARE_MSG_TYPE_INVITATION;
import static org.briarproject.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ForumSharingIntegrationTest extends BriarTestCase {

	LifecycleManager lifecycleManager0, lifecycleManager1, lifecycleManager2;
	SyncSessionFactory sync0, sync1, sync2;
	ForumManager forumManager0, forumManager1, forumManager2;
	ContactManager contactManager0, contactManager1, contactManager2;
	ContactId contactId0, contactId2, contactId1, contactId21;
	IdentityManager identityManager0, identityManager1, identityManager2;
	LocalAuthor author0, author1, author2;
	Forum forum0;
	SharerListener listener0, listener2;
	InviteeListener listener1;

	@Inject
	Clock clock;
	@Inject
	AuthorFactory authorFactory;

	// objects accessed from background threads need to be volatile
	private volatile ForumSharingManager forumSharingManager0;
	private volatile ForumSharingManager forumSharingManager1;
	private volatile ForumSharingManager forumSharingManager2;
	private volatile Waiter eventWaiter;
	private volatile Waiter msgWaiter;

	private final File testDir = TestUtils.getTestDirectory();
	private final SecretKey master = TestUtils.getSecretKey();
	private final int TIMEOUT = 15000;
	private final String SHARER = "Sharer";
	private final String INVITEE = "Invitee";
	private final String SHARER2 = "Sharer2";

	private static final Logger LOG =
			Logger.getLogger(ForumSharingIntegrationTest.class.getName());

	private ForumSharingIntegrationTestComponent t0, t1, t2;

	@Rule
	public ExpectedException thrown=ExpectedException.none();

	@Before
	public void setUp() {
		ForumSharingIntegrationTestComponent component =
				DaggerForumSharingIntegrationTestComponent.builder().build();
		component.inject(this);
		injectEagerSingletons(component);

		assertTrue(testDir.mkdirs());
		File t0Dir = new File(testDir, SHARER);
		t0 = DaggerForumSharingIntegrationTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(t0Dir)).build();
		injectEagerSingletons(t0);
		File t1Dir = new File(testDir, INVITEE);
		t1 = DaggerForumSharingIntegrationTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(t1Dir)).build();
		injectEagerSingletons(t1);
		File t2Dir = new File(testDir, SHARER2);
		t2 = DaggerForumSharingIntegrationTestComponent.builder()
				.testDatabaseModule(new TestDatabaseModule(t2Dir)).build();
		injectEagerSingletons(t2);

		identityManager0 = t0.getIdentityManager();
		identityManager1 = t1.getIdentityManager();
		identityManager2 = t2.getIdentityManager();
		contactManager0 = t0.getContactManager();
		contactManager1 = t1.getContactManager();
		contactManager2 = t2.getContactManager();
		forumManager0 = t0.getForumManager();
		forumManager1 = t1.getForumManager();
		forumManager2 = t2.getForumManager();
		forumSharingManager0 = t0.getForumSharingManager();
		forumSharingManager1 = t1.getForumSharingManager();
		forumSharingManager2 = t2.getForumSharingManager();
		sync0 = t0.getSyncSessionFactory();
		sync1 = t1.getSyncSessionFactory();
		sync2 = t2.getSyncSessionFactory();

		// initialize waiters fresh for each test
		eventWaiter = new Waiter();
		msgWaiter = new Waiter();
	}

	@Test
	public void testSuccessfulSharing() throws Exception {
		startLifecycles();
		try {
			// initialize and let invitee accept all requests
			defaultInit(true);

			// send invitation
			forumSharingManager0
					.sendForumInvitation(forum0.getId(), contactId1, "Hi!");

			// sync first request message
			syncToInvitee();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener1.requestReceived);

			// sync response back
			syncToSharer();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener0.responseReceived);

			// forum was added successfully
			assertEquals(0, forumSharingManager0.getAvailableForums().size());
			assertEquals(1, forumManager1.getForums().size());

			// invitee has one invitation message from sharer
			List<ForumInvitationMessage> list =
					new ArrayList<>(forumSharingManager1
							.getForumInvitationMessages(contactId0));
			assertEquals(1, list.size());
			// check other things are alright with the forum message
			ForumInvitationMessage invitation = list.get(0);
			assertFalse(invitation.isAvailable());
			assertEquals(forum0.getName(), invitation.getForumName());
			assertEquals(contactId1, invitation.getContactId());
			assertEquals("Hi!", invitation.getMessage());
			// sharer has own invitation message
			assertEquals(1,
					forumSharingManager0.getForumInvitationMessages(contactId1)
							.size());
			// forum can not be shared again
			Contact c1 = contactManager0.getContact(contactId1);
			assertFalse(forumSharingManager0.canBeShared(forum0.getId(), c1));
			Contact c0 = contactManager1.getContact(contactId0);
			assertFalse(forumSharingManager1.canBeShared(forum0.getId(), c0));
		} finally {
			stopLifecycles();
		}
	}

	@Test
	public void testDeclinedSharing() throws Exception {
		startLifecycles();
		try {
			// initialize and let invitee accept all requests
			defaultInit(false);

			// send invitation
			forumSharingManager0
					.sendForumInvitation(forum0.getId(), contactId1, null);

			// sync first request message
			syncToInvitee();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener1.requestReceived);

			// sync response back
			syncToSharer();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener0.responseReceived);

			// forum was not added
			assertEquals(0, forumSharingManager0.getAvailableForums().size());
			assertEquals(0, forumManager1.getForums().size());
			// forum is no longer available to invitee who declined
			assertEquals(0, forumSharingManager1.getAvailableForums().size());

			// invitee has one invitation message from sharer
			List<ForumInvitationMessage> list =
					new ArrayList<>(forumSharingManager1
							.getForumInvitationMessages(contactId0));
			assertEquals(1, list.size());
			// check other things are alright with the forum message
			ForumInvitationMessage invitation = list.get(0);
			assertFalse(invitation.isAvailable());
			assertEquals(forum0.getName(), invitation.getForumName());
			assertEquals(contactId1, invitation.getContactId());
			assertEquals(null, invitation.getMessage());
			// sharer has own invitation message
			assertEquals(1,
					forumSharingManager0.getForumInvitationMessages(contactId1)
							.size());
			// forum can be shared again
			Contact c1 = contactManager0.getContact(contactId1);
			assertTrue(forumSharingManager0.canBeShared(forum0.getId(), c1));
		} finally {
			stopLifecycles();
		}
	}

	@Test
	public void testInviteeLeavesAfterFinished() throws Exception {
		startLifecycles();
		try {
			// initialize and let invitee accept all requests
			defaultInit(true);

			// send invitation
			forumSharingManager0
					.sendForumInvitation(forum0.getId(), contactId1, "Hi!");

			// sync first request message
			syncToInvitee();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener1.requestReceived);

			// sync response back
			syncToSharer();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener0.responseReceived);

			// forum was added successfully
			assertEquals(0, forumSharingManager0.getAvailableForums().size());
			assertEquals(1, forumManager1.getForums().size());
			assertTrue(forumManager1.getForums().contains(forum0));

			// sharer shares forum with invitee
			assertTrue(forumSharingManager0.getSharedWith(forum0.getId())
					.contains(contactId1));
			// invitee gets forum shared by sharer
			Contact contact0 = contactManager1.getContact(contactId1);
			assertTrue(forumSharingManager1.getSharedBy(forum0.getId())
					.contains(contact0));

			// invitee un-subscribes from forum
			forumManager1.removeForum(forum0);

			// send leave message to sharer
			syncToSharer();

			// forum is gone
			assertEquals(0, forumSharingManager0.getAvailableForums().size());
			assertEquals(0, forumManager1.getForums().size());

			// sharer no longer shares forum with invitee
			assertFalse(forumSharingManager0.getSharedWith(forum0.getId())
					.contains(contactId1));
			// invitee no longer gets forum shared by sharer
			assertFalse(forumSharingManager1.getSharedBy(forum0.getId())
					.contains(contact0));
			// forum can be shared again
			Contact c1 = contactManager0.getContact(contactId1);
			assertTrue(forumSharingManager0.canBeShared(forum0.getId(), c1));
			Contact c0 = contactManager1.getContact(contactId0);
			assertTrue(forumSharingManager1.canBeShared(forum0.getId(), c0));
		} finally {
			stopLifecycles();
		}
	}

	@Test
	public void testSharerLeavesAfterFinished() throws Exception {
		startLifecycles();
		try {
			// initialize and let invitee accept all requests
			defaultInit(true);

			// send invitation
			forumSharingManager0
					.sendForumInvitation(forum0.getId(), contactId1, null);

			// sync first request message
			syncToInvitee();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener1.requestReceived);

			// sync response back
			syncToSharer();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener0.responseReceived);

			// forum was added successfully
			assertEquals(0, forumSharingManager0.getAvailableForums().size());
			assertEquals(1, forumManager1.getForums().size());
			assertTrue(forumManager1.getForums().contains(forum0));

			// sharer shares forum with invitee
			assertTrue(forumSharingManager0.getSharedWith(forum0.getId())
					.contains(contactId1));
			// invitee gets forum shared by sharer
			Contact contact0 = contactManager1.getContact(contactId1);
			assertTrue(forumSharingManager1.getSharedBy(forum0.getId())
					.contains(contact0));

			// sharer un-subscribes from forum
			forumManager0.removeForum(forum0);

			// send leave message to invitee
			syncToInvitee();

			// forum is gone for sharer, but not invitee
			assertEquals(0, forumManager0.getForums().size());
			assertEquals(1, forumManager1.getForums().size());

			// invitee no longer shares forum with sharer
			assertFalse(forumSharingManager1.getSharedWith(forum0.getId())
					.contains(contactId0));
			// sharer no longer gets forum shared by invitee
			assertFalse(forumSharingManager1.getSharedBy(forum0.getId())
					.contains(contact0));
			// forum can be shared again
			Contact c0 = contactManager1.getContact(contactId0);
			assertTrue(forumSharingManager1.canBeShared(forum0.getId(), c0));
		} finally {
			stopLifecycles();
		}
	}

	@Test
	public void testSharerLeavesBeforeResponse() throws Exception {
		startLifecycles();
		try {
			// initialize and let invitee accept all requests
			defaultInit(true);

			// send invitation
			forumSharingManager0
					.sendForumInvitation(forum0.getId(), contactId1, null);

			// sharer un-subscribes from forum
			forumManager0.removeForum(forum0);

			// from her on expect the response to fail with a DbException
			thrown.expect(DbException.class);

			// sync first request message and leave message
			syncToInvitee();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener1.requestReceived);

			// invitee has no forums available
			assertEquals(0, forumSharingManager1.getAvailableForums().size());
		} finally {
			stopLifecycles();
		}
	}

	@Test
	public void testSessionIdReuse() throws Exception {
		startLifecycles();
		try {
			// initialize and let invitee accept all requests
			defaultInit(true);

			// send invitation
			forumSharingManager0
					.sendForumInvitation(forum0.getId(), contactId1, "Hi!");

			// sync first request message
			syncToInvitee();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener1.requestReceived);

			// sync response back
			syncToSharer();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener0.responseReceived);

			// forum was added successfully
			assertEquals(1, forumManager1.getForums().size());

			// reset event received state
			listener1.requestReceived = false;

			// get SessionId from invitation
			List<ForumInvitationMessage> list = new ArrayList<>(
					forumSharingManager1
							.getForumInvitationMessages(contactId0));
			assertEquals(1, list.size());
			ForumInvitationMessage msg = list.get(0);
			SessionId sessionId = msg.getSessionId();

			// get all sorts of stuff needed to send a message
			DatabaseComponent db = t0.getDatabaseComponent();
			MessageQueueManager queue = t0.getMessageQueueManager();
			Contact c1 = contactManager0.getContact(contactId1);
			PrivateGroupFactory groupFactory = t0.getPrivateGroupFactory();
			Group group = groupFactory
					.createPrivateGroup(forumSharingManager0.getClientId(), c1);
			long time = clock.currentTimeMillis();
			BdfList bodyList = BdfList.of(SHARE_MSG_TYPE_INVITATION,
					sessionId.getBytes(),
					TestUtils.getRandomString(42),
					TestUtils.getRandomBytes(FORUM_SALT_LENGTH)
			);
			byte[] body = t0.getClientHelper().toByteArray(bodyList);

			// add the message to the queue
			Transaction txn = db.startTransaction(false);
			try {
				queue.sendMessage(txn, group, time, body, new Metadata());
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}

			// actually send the message
			syncToInvitee();
			// make sure there was no new request received
			assertFalse(listener1.requestReceived);
		} finally {
			stopLifecycles();
		}
	}

	@Test
	public void testSharingSameForumWithEachOther() throws Exception {
		startLifecycles();
		try {
			// initialize and let invitee accept all requests
			defaultInit(true);

			// send invitation
			forumSharingManager0
					.sendForumInvitation(forum0.getId(), contactId1, "Hi!");

			// sync first request message
			syncToInvitee();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener1.requestReceived);

			// sync response back
			syncToSharer();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener0.responseReceived);

			// forum was added successfully
			assertEquals(1, forumManager1.getForums().size());

			// invitee now shares same forum back
			forumSharingManager1.sendForumInvitation(forum0.getId(),
					contactId0,
					"I am re-sharing this forum with you.");

			// sync re-share invitation
			syncToSharer();

			// make sure that no new request was received
			assertFalse(listener0.requestReceived);
			assertEquals(1,
					forumSharingManager0.getForumInvitationMessages(contactId1)
							.size());
		} finally {
			stopLifecycles();
		}
	}

	@Test
	public void testContactRemoved() throws Exception {
		startLifecycles();
		try {
			// initialize and let invitee accept all requests
			defaultInit(true);

			// send invitation
			forumSharingManager0
					.sendForumInvitation(forum0.getId(), contactId1, "Hi!");

			// sync first request message
			syncToInvitee();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener1.requestReceived);

			// sync response back
			syncToSharer();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener0.responseReceived);

			// forum was added successfully
			assertEquals(1, forumManager1.getForums().size());
			assertEquals(1,
					forumSharingManager0.getSharedWith(forum0.getId()).size());

			// remember SessionId from invitation
			List<ForumInvitationMessage> list = new ArrayList<>(
					forumSharingManager1
							.getForumInvitationMessages(contactId0));
			assertEquals(1, list.size());
			ForumInvitationMessage msg = list.get(0);
			SessionId sessionId = msg.getSessionId();

			// contacts now remove each other
			contactManager0.removeContact(contactId1);
			contactManager2.removeContact(contactId21);
			contactManager1.removeContact(contactId0);
			contactManager1.removeContact(contactId2);

			// make sure sharer does share the forum with nobody now
			assertEquals(0,
					forumSharingManager0.getSharedWith(forum0.getId()).size());

			// contacts add each other again
			addDefaultContacts();

			// get all sorts of stuff needed to send a message
			DatabaseComponent db = t0.getDatabaseComponent();
			MessageQueueManager queue = t0.getMessageQueueManager();
			Contact c1 = contactManager0.getContact(contactId1);
			PrivateGroupFactory groupFactory = t0.getPrivateGroupFactory();
			Group group = groupFactory
					.createPrivateGroup(forumSharingManager0.getClientId(), c1);
			long time = clock.currentTimeMillis();

			// construct a new message re-using the old SessionId
			BdfList bodyList = BdfList.of(SHARE_MSG_TYPE_INVITATION,
					sessionId.getBytes(),
					TestUtils.getRandomString(42),
					TestUtils.getRandomBytes(FORUM_SALT_LENGTH)
			);
			byte[] body = t0.getClientHelper().toByteArray(bodyList);

			// add the message to the queue
			Transaction txn = db.startTransaction(false);
			try {
				queue.sendMessage(txn, group, time, body, new Metadata());
				txn.setComplete();
			} finally {
				db.endTransaction(txn);
			}

			// actually send the message
			syncToInvitee();
			eventWaiter.await(TIMEOUT, 1);
			// make sure the new request was received with the same sessionId
			// as proof that the state got deleted along with contacts
			assertTrue(listener1.requestReceived);
		} finally {
			stopLifecycles();
		}
	}

	@Test
	public void testTwoContactsShareSameForum() throws Exception {
		startLifecycles();
		try {
			// initialize
			addDefaultIdentities();
			addDefaultContacts();
			addForumForSharer();

			// second sharer adds the same forum
			DatabaseComponent db2 = t2.getDatabaseComponent();
			Transaction txn = db2.startTransaction(false);
			db2.addGroup(txn, forum0.getGroup());
			txn.setComplete();
			db2.endTransaction(txn);

			// add listeners
			listener0 = new SharerListener();
			t0.getEventBus().addListener(listener0);
			listener1 = new InviteeListener(true, false);
			t1.getEventBus().addListener(listener1);
			listener2 = new SharerListener();
			t2.getEventBus().addListener(listener2);

			// send invitation
			forumSharingManager0
					.sendForumInvitation(forum0.getId(), contactId1, "Hi!");
			// sync first request message
			syncToInvitee();

			// second sharer sends invitation for same forum
			forumSharingManager2
					.sendForumInvitation(forum0.getId(), contactId1, null);
			// sync second request message
			deliverMessage(sync2, contactId2, sync1, contactId1,
					"Sharer2 to Invitee");

			// make sure we have only one forum available
			Collection<Forum> forums =
					forumSharingManager1.getAvailableForums();
			assertEquals(1, forums.size());

			// make sure both sharers actually share the forum
			Collection<Contact> contacts =
					forumSharingManager1.getSharedBy(forum0.getId());
			assertEquals(2, contacts.size());

			// answer second request
			Contact c2 = contactManager1.getContact(contactId2);
			forumSharingManager1.respondToInvitation(forum0, c2, true);
			// sync response
			deliverMessage(sync1, contactId21, sync2, contactId2,
					"Invitee to Sharer2");
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener2.responseReceived);

			// answer first request
			Contact c0 =
					contactManager1.getContact(contactId0);
			forumSharingManager1.respondToInvitation(forum0, c0, true);
			// sync response
			syncToSharer();
			eventWaiter.await(TIMEOUT, 1);
			assertTrue(listener0.responseReceived);
		} finally {
			stopLifecycles();
		}
	}


	@After
	public void tearDown() throws InterruptedException {
		TestUtils.deleteTestDirectory(testDir);
	}

	private class SharerListener implements EventListener {

		public volatile boolean requestReceived = false;
		public volatile boolean responseReceived = false;

		public void eventOccurred(Event e) {
			if (e instanceof MessageValidatedEvent) {
				MessageValidatedEvent event = (MessageValidatedEvent) e;
				if (event.getClientId()
						.equals(forumSharingManager0.getClientId()) &&
						!event.isLocal()) {
					LOG.info("TEST: Sharer received message in group " +
							((MessageValidatedEvent) e).getMessage()
									.getGroupId().hashCode());
					msgWaiter.resume();
				}
			} else if (e instanceof ForumInvitationResponseReceivedEvent) {
				ForumInvitationResponseReceivedEvent event =
						(ForumInvitationResponseReceivedEvent) e;
				eventWaiter.assertEquals(contactId1, event.getContactId());
				responseReceived = true;
				eventWaiter.resume();
			}
			// this is only needed for tests where a forum is re-shared
			else if (e instanceof ForumInvitationReceivedEvent) {
				ForumInvitationReceivedEvent event =
						(ForumInvitationReceivedEvent) e;
				eventWaiter.assertEquals(contactId1, event.getContactId());
				requestReceived = true;
				Forum f = event.getForum();
				try {
					Contact c = contactManager0.getContact(contactId1);
					forumSharingManager0.respondToInvitation(f, c, true);
				} catch (DbException ex) {
					eventWaiter.rethrow(ex);
				} finally {
					eventWaiter.resume();
				}
			}
		}
	}

	private class InviteeListener implements EventListener {

		public volatile boolean requestReceived = false;
		public volatile boolean responseReceived = false;

		private final boolean accept, answer;

		InviteeListener(boolean accept, boolean answer) {
			this.accept = accept;
			this.answer = answer;
		}

		InviteeListener(boolean accept) {
			this(accept, true);
		}

		public void eventOccurred(Event e) {
			if (e instanceof MessageValidatedEvent) {
				MessageValidatedEvent event = (MessageValidatedEvent) e;
				if (event.getClientId()
						.equals(forumSharingManager1.getClientId()) &&
						!event.isLocal()) {
					LOG.info("TEST: Invitee received message in group " +
							((MessageValidatedEvent) e).getMessage()
									.getGroupId().hashCode());
					msgWaiter.resume();
				}
			} else if (e instanceof ForumInvitationReceivedEvent) {
				ForumInvitationReceivedEvent event =
						(ForumInvitationReceivedEvent) e;
				requestReceived = true;
				if (!answer) return;
				Forum f = event.getForum();
				try {
					Contact c =
							contactManager1.getContact(event.getContactId());
					forumSharingManager1.respondToInvitation(f, c, accept);
				} catch (DbException ex) {
					eventWaiter.rethrow(ex);
				} finally {
					eventWaiter.resume();
				}
			}
			// this is only needed for tests where a forum is re-shared
			else if (e instanceof ForumInvitationResponseReceivedEvent) {
				ForumInvitationResponseReceivedEvent event =
						(ForumInvitationResponseReceivedEvent) e;
				eventWaiter.assertEquals(contactId0, event.getContactId());
				responseReceived = true;
				eventWaiter.resume();
			}
		}
	}

	private void startLifecycles() throws InterruptedException {
		// Start the lifecycle manager and wait for it to finish
		lifecycleManager0 = t0.getLifecycleManager();
		lifecycleManager1 = t1.getLifecycleManager();
		lifecycleManager2 = t2.getLifecycleManager();
		lifecycleManager0.startServices();
		lifecycleManager1.startServices();
		lifecycleManager2.startServices();
		lifecycleManager0.waitForStartup();
		lifecycleManager1.waitForStartup();
		lifecycleManager2.waitForStartup();
	}

	private void stopLifecycles() throws InterruptedException {
		// Clean up
		lifecycleManager0.stopServices();
		lifecycleManager1.stopServices();
		lifecycleManager2.stopServices();
		lifecycleManager0.waitForShutdown();
		lifecycleManager1.waitForShutdown();
		lifecycleManager2.waitForShutdown();
	}

	private void defaultInit(boolean accept) throws DbException {
		addDefaultIdentities();
		addDefaultContacts();
		addForumForSharer();
		listenToEvents(accept);
	}

	private void addDefaultIdentities() throws DbException {
		author0 = authorFactory.createLocalAuthor(SHARER,
				TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH),
				TestUtils.getRandomBytes(123));
		identityManager0.addLocalAuthor(author0);
		author1 = authorFactory.createLocalAuthor(INVITEE,
				TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH),
				TestUtils.getRandomBytes(123));
		identityManager1.addLocalAuthor(author1);
		author2 = authorFactory.createLocalAuthor(SHARER2,
				TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH),
				TestUtils.getRandomBytes(123));
		identityManager2.addLocalAuthor(author2);
	}

	private void addDefaultContacts() throws DbException {
		// sharer adds invitee as contact
		contactId1 = contactManager0.addContact(author1,
				author0.getId(), master, clock.currentTimeMillis(), true,
				true
		);
		// second sharer does the same
		contactId21 = contactManager2.addContact(author1,
				author2.getId(), master, clock.currentTimeMillis(), true,
				true
		);
		// invitee adds sharers back
		contactId0 = contactManager1.addContact(author0,
				author1.getId(), master, clock.currentTimeMillis(), true,
				true
		);
		contactId2 = contactManager1.addContact(author2,
				author1.getId(), master, clock.currentTimeMillis(), true,
				true
		);
	}

	private void addForumForSharer() throws DbException {
		// sharer creates forum
		forum0 = forumManager0.addForum("Test Forum");
	}

	private void listenToEvents(boolean accept) {
		listener0 = new SharerListener();
		t0.getEventBus().addListener(listener0);
		listener1 = new InviteeListener(accept);
		t1.getEventBus().addListener(listener1);
		listener2 = new SharerListener();
		t2.getEventBus().addListener(listener2);
	}

	private void syncToInvitee() throws IOException, TimeoutException {
		deliverMessage(sync0, contactId0, sync1, contactId1,
				"Sharer to Invitee");
	}

	private void syncToSharer() throws IOException, TimeoutException {
		deliverMessage(sync1, contactId1, sync0, contactId0,
				"Invitee to Sharer");
	}

	private void deliverMessage(SyncSessionFactory fromSync, ContactId fromId,
			SyncSessionFactory toSync, ContactId toId, String debug)
			throws IOException, TimeoutException {

		if (debug != null) LOG.info("TEST: Sending message from " + debug);

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		// Create an outgoing sync session
		SyncSession sessionFrom =
				fromSync.createSimplexOutgoingSession(toId, MAX_LATENCY, out);
		// Write whatever needs to be written
		sessionFrom.run();
		out.close();

		ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
		// Create an incoming sync session
		SyncSession sessionTo = toSync.createIncomingSession(fromId, in);
		// Read whatever needs to be read
		sessionTo.run();
		in.close();

		// wait for message to actually arrive
		msgWaiter.await(TIMEOUT, 1);
	}

	private void injectEagerSingletons(
			ForumSharingIntegrationTestComponent component) {

		component.inject(new LifecycleModule.EagerSingletons());
		component.inject(new ForumModule.EagerSingletons());
		component.inject(new CryptoModule.EagerSingletons());
		component.inject(new ContactModule.EagerSingletons());
		component.inject(new TransportModule.EagerSingletons());
		component.inject(new SyncModule.EagerSingletons());
		component.inject(new PropertiesModule.EagerSingletons());
	}

}
