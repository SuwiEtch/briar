package net.sf.briar.db;

import static java.util.concurrent.TimeUnit.SECONDS;
import static net.sf.briar.api.messaging.Rating.GOOD;
import static net.sf.briar.api.messaging.Rating.UNRATED;
import static org.junit.Assert.assertArrayEquals;

import java.io.File;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import net.sf.briar.BriarTestCase;
import net.sf.briar.TestDatabaseConfig;
import net.sf.briar.TestMessage;
import net.sf.briar.TestUtils;
import net.sf.briar.api.Author;
import net.sf.briar.api.AuthorId;
import net.sf.briar.api.ContactId;
import net.sf.briar.api.LocalAuthor;
import net.sf.briar.api.TransportConfig;
import net.sf.briar.api.TransportId;
import net.sf.briar.api.TransportProperties;
import net.sf.briar.api.clock.SystemClock;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.db.GroupMessageHeader;
import net.sf.briar.api.messaging.Group;
import net.sf.briar.api.messaging.GroupId;
import net.sf.briar.api.messaging.GroupStatus;
import net.sf.briar.api.messaging.Message;
import net.sf.briar.api.messaging.MessageId;
import net.sf.briar.api.transport.Endpoint;
import net.sf.briar.api.transport.TemporarySecret;

import org.apache.commons.io.FileSystemUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class H2DatabaseTest extends BriarTestCase {

	private static final int ONE_MEGABYTE = 1024 * 1024;
	private static final int MAX_SIZE = 5 * ONE_MEGABYTE;

	private final File testDir = TestUtils.getTestDirectory();
	private final Random random = new Random();
	private final GroupId groupId;
	private final Group group;
	private final AuthorId authorId;
	private final Author author;
	private final AuthorId localAuthorId;
	private final LocalAuthor localAuthor;
	private final MessageId messageId, messageId1;
	private final String contentType, subject;
	private final long timestamp;
	private final int size;
	private final byte[] raw;
	private final Message message, privateMessage;
	private final TransportId transportId;
	private final ContactId contactId;

	public H2DatabaseTest() throws Exception {
		super();
		groupId = new GroupId(TestUtils.getRandomId());
		group = new Group(groupId, "Group name", null);
		authorId = new AuthorId(TestUtils.getRandomId());
		author = new Author(authorId, "Alice", new byte[60]);
		localAuthorId = new AuthorId(TestUtils.getRandomId());
		localAuthor = new LocalAuthor(localAuthorId, "Bob", new byte[60],
				new byte[60]);
		messageId = new MessageId(TestUtils.getRandomId());
		messageId1 = new MessageId(TestUtils.getRandomId());
		contentType = "text/plain";
		subject = "Foo";
		timestamp = System.currentTimeMillis();
		size = 1234;
		raw = new byte[size];
		random.nextBytes(raw);
		message = new TestMessage(messageId, null, group, author, contentType,
				subject, timestamp, raw);
		privateMessage = new TestMessage(messageId1, null, null, null,
				contentType, subject, timestamp, raw);
		transportId = new TransportId(TestUtils.getRandomId());
		contactId = new ContactId(1);
	}

	@Before
	public void setUp() {
		testDir.mkdirs();
	}

	@Test
	public void testPersistence() throws Exception {
		// Store some records
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();
		assertFalse(db.containsContact(txn, contactId));
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		assertTrue(db.containsContact(txn, contactId));
		assertFalse(db.containsSubscription(txn, groupId));
		db.addSubscription(txn, group);
		assertTrue(db.containsSubscription(txn, groupId));
		assertFalse(db.containsMessage(txn, messageId));
		db.addGroupMessage(txn, message, true);
		assertTrue(db.containsMessage(txn, messageId));
		assertFalse(db.containsMessage(txn, messageId1));
		db.addPrivateMessage(txn, privateMessage, contactId, true);
		assertTrue(db.containsMessage(txn, messageId1));
		db.commitTransaction(txn);
		db.close();

		// Check that the records are still there
		db = open(true);
		txn = db.startTransaction();
		assertTrue(db.containsContact(txn, contactId));
		assertTrue(db.containsSubscription(txn, groupId));
		assertTrue(db.containsMessage(txn, messageId));
		byte[] raw1 = db.getRawMessage(txn, messageId);
		assertArrayEquals(raw, raw1);
		assertTrue(db.containsMessage(txn, messageId1));
		raw1 = db.getRawMessage(txn, messageId1);
		assertArrayEquals(raw, raw1);
		// Delete the records
		db.removeMessage(txn, messageId);
		db.removeMessage(txn, messageId1);
		db.removeContact(txn, contactId);
		db.removeSubscription(txn, groupId);
		db.commitTransaction(txn);
		db.close();

		// Check that the records are gone
		db = open(true);
		txn = db.startTransaction();
		assertFalse(db.containsContact(txn, contactId));
		assertEquals(Collections.emptyMap(),
				db.getRemoteProperties(txn, transportId));
		assertFalse(db.containsSubscription(txn, groupId));
		assertFalse(db.containsMessage(txn, messageId));
		assertFalse(db.containsMessage(txn, messageId1));
		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testRatings() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Unknown authors should be unrated
		assertEquals(UNRATED, db.getRating(txn, authorId));
		// Store a rating
		db.setRating(txn, authorId, GOOD);
		// Check that the rating was stored
		assertEquals(GOOD, db.getRating(txn, authorId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testUnsubscribingRemovesGroupMessage() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to a group and store a message
		db.addSubscription(txn, group);
		db.addGroupMessage(txn, message, false);

		// Unsubscribing from the group should remove the message
		assertTrue(db.containsMessage(txn, messageId));
		db.removeSubscription(txn, groupId);
		assertFalse(db.containsMessage(txn, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testRemovingContactRemovesPrivateMessage() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and store a private message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addPrivateMessage(txn, privateMessage, contactId, false);

		// Removing the contact should remove the message
		assertTrue(db.containsMessage(txn, messageId1));
		db.removeContact(txn, contactId);
		assertFalse(db.containsMessage(txn, messageId1));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendablePrivateMessagesMustHaveSeenFlagFalse()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and store a private message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addPrivateMessage(txn, privateMessage, contactId, false);

		// The message has no status yet, so it should not be sendable
		assertFalse(db.hasSendableMessages(txn, contactId));
		Iterator<MessageId> it =
				db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		// Adding a status with seen = false should make the message sendable
		db.addStatus(txn, contactId, messageId1, false);
		assertTrue(db.hasSendableMessages(txn, contactId));
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId1, it.next());
		assertFalse(it.hasNext());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendablePrivateMessagesMustFitCapacity()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and store a private message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addPrivateMessage(txn, privateMessage, contactId, false);
		db.addStatus(txn, contactId, messageId1, false);

		// The message is sendable, but too large to send
		assertTrue(db.hasSendableMessages(txn, contactId));
		Iterator<MessageId> it =
				db.getSendableMessages(txn, contactId, size - 1).iterator();
		assertFalse(it.hasNext());

		// The message is just the right size to send
		assertTrue(db.hasSendableMessages(txn, contactId));
		it = db.getSendableMessages(txn, contactId, size).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId1, it.next());
		assertFalse(it.hasNext());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendableGroupMessagesMustHavePositiveSendability()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addSubscription(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.setSubscriptions(txn, contactId, Arrays.asList(group), 1);
		db.addGroupMessage(txn, message, false);
		db.addStatus(txn, contactId, messageId, false);

		// The message should not be sendable
		assertFalse(db.hasSendableMessages(txn, contactId));
		Iterator<MessageId> it =
				db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		// Changing the sendability to > 0 should make the message sendable
		db.setSendability(txn, messageId, 1);
		assertTrue(db.hasSendableMessages(txn, contactId));
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());

		// Changing the sendability to 0 should make the message unsendable
		db.setSendability(txn, messageId, 0);
		assertFalse(db.hasSendableMessages(txn, contactId));
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendableGroupMessagesMustHaveSeenFlagFalse()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addSubscription(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.setSubscriptions(txn, contactId, Arrays.asList(group), 1);
		db.addGroupMessage(txn, message, false);
		db.setSendability(txn, messageId, 1);

		// The message has no status yet, so it should not be sendable
		assertFalse(db.hasSendableMessages(txn, contactId));
		Iterator<MessageId> it =
				db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		// Adding a status with seen = false should make the message sendable
		db.addStatus(txn, contactId, messageId, false);
		assertTrue(db.hasSendableMessages(txn, contactId));
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());

		// Changing the status to seen = true should make the message unsendable
		db.setStatusSeenIfVisible(txn, contactId, messageId);
		assertFalse(db.hasSendableMessages(txn, contactId));
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendableGroupMessagesMustBeSubscribed() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addSubscription(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.addGroupMessage(txn, message, false);
		db.setSendability(txn, messageId, 1);
		db.addStatus(txn, contactId, messageId, false);

		// The contact is not subscribed, so the message should not be sendable
		assertFalse(db.hasSendableMessages(txn, contactId));
		Iterator<MessageId> it =
				db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		// The contact subscribing should make the message sendable
		db.setSubscriptions(txn, contactId, Arrays.asList(group), 1);
		assertTrue(db.hasSendableMessages(txn, contactId));
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());

		// The contact unsubscribing should make the message unsendable
		db.setSubscriptions(txn, contactId, Collections.<Group>emptyList(), 2);
		assertFalse(db.hasSendableMessages(txn, contactId));
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendableGroupMessagesMustFitCapacity() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addSubscription(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.setSubscriptions(txn, contactId, Arrays.asList(group), 1);
		db.addGroupMessage(txn, message, false);
		db.setSendability(txn, messageId, 1);
		db.addStatus(txn, contactId, messageId, false);

		// The message is sendable, but too large to send
		assertTrue(db.hasSendableMessages(txn, contactId));
		Iterator<MessageId> it =
				db.getSendableMessages(txn, contactId, size - 1).iterator();
		assertFalse(it.hasNext());

		// The message is just the right size to send
		assertTrue(db.hasSendableMessages(txn, contactId));
		it = db.getSendableMessages(txn, contactId, size).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSendableGroupMessagesMustBeVisible() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addSubscription(txn, group);
		db.setSubscriptions(txn, contactId, Arrays.asList(group), 1);
		db.addGroupMessage(txn, message, false);
		db.setSendability(txn, messageId, 1);
		db.addStatus(txn, contactId, messageId, false);

		// The subscription is not visible to the contact, so the message
		// should not be sendable
		assertFalse(db.hasSendableMessages(txn, contactId));
		Iterator<MessageId> it =
				db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		// Making the subscription visible should make the message sendable
		db.addVisibility(txn, contactId, groupId);
		assertTrue(db.hasSendableMessages(txn, contactId));
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testMessagesToAck() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and some messages to ack
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addMessageToAck(txn, contactId, messageId);
		db.addMessageToAck(txn, contactId, messageId1);

		// Both message IDs should be returned
		Collection<MessageId> ids = Arrays.asList(messageId, messageId1);
		assertEquals(ids, db.getMessagesToAck(txn, contactId, 1234));

		// Remove both message IDs
		db.removeMessagesToAck(txn, contactId, ids);

		// Both message IDs should have been removed
		assertEquals(Collections.emptyList(), db.getMessagesToAck(txn,
				contactId, 1234));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testDuplicateMessageReceived() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and receive the same message twice
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addMessageToAck(txn, contactId, messageId);
		db.addMessageToAck(txn, contactId, messageId);

		// The message ID should only be returned once
		Collection<MessageId> ids = db.getMessagesToAck(txn, contactId, 1234);
		assertEquals(Arrays.asList(messageId), ids);

		// Remove the message ID
		db.removeMessagesToAck(txn, contactId, Arrays.asList(messageId));

		// The message ID should have been removed
		assertEquals(Collections.emptyList(), db.getMessagesToAck(txn,
				contactId, 1234));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testOutstandingMessageAcked() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addSubscription(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.setSubscriptions(txn, contactId, Arrays.asList(group), 1);
		db.addGroupMessage(txn, message, false);
		db.setSendability(txn, messageId, 1);
		db.addStatus(txn, contactId, messageId, false);

		// Retrieve the message from the database and mark it as sent
		Iterator<MessageId> it =
				db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());
		db.updateExpiryTimes(txn, contactId,
				Collections.singletonMap(messageId, 0), Long.MAX_VALUE);

		// The message should no longer be sendable
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		// Pretend that the message was acked
		db.removeOutstandingMessages(txn, contactId, Arrays.asList(messageId));

		// The message still should not be sendable
		it = db.getSendableMessages(txn, contactId, ONE_MEGABYTE).iterator();
		assertFalse(it.hasNext());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetUnrestrictedGroupMessages() throws Exception {
		AuthorId authorId1 = new AuthorId(TestUtils.getRandomId());
		Author author1 = new Author(authorId1, "Bob", new byte[60]);
		MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		Message message1 = new TestMessage(messageId1, null, group, author1,
				contentType, subject, timestamp, raw);
		GroupId groupId1 = new GroupId(TestUtils.getRandomId());
		Group group1 = new Group(groupId1, "Restricted group name",
				new byte[60]);
		MessageId messageId2 = new MessageId(TestUtils.getRandomId());
		Message message2 = new TestMessage(messageId2, null, group1, author,
				contentType, subject, timestamp, raw);
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to an unrestricted group and store two messages
		db.addSubscription(txn, group);
		db.addGroupMessage(txn, message, false);
		db.addGroupMessage(txn, message1, false);

		// Subscribe to a restricted group and store a message
		db.addSubscription(txn, group1);
		db.addGroupMessage(txn, message2, false);

		// Check that only the messages in the unrestricted group are retrieved
		Collection<MessageId> ids = db.getUnrestrictedGroupMessages(txn,
				authorId);
		Iterator<MessageId> it = ids.iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());
		ids = db.getUnrestrictedGroupMessages(txn, authorId1);
		it = ids.iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId1, it.next());
		assertFalse(it.hasNext());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetNumberOfSendableChildren() throws Exception {
		MessageId childId1 = new MessageId(TestUtils.getRandomId());
		MessageId childId2 = new MessageId(TestUtils.getRandomId());
		MessageId childId3 = new MessageId(TestUtils.getRandomId());
		GroupId groupId1 = new GroupId(TestUtils.getRandomId());
		Group group1 = new Group(groupId1, "Group name", null);
		Message child1 = new TestMessage(childId1, messageId, group, author,
				contentType, subject, timestamp, raw);
		Message child2 = new TestMessage(childId2, messageId, group, author,
				contentType, subject, timestamp, raw);
		// The third child is in a different group
		Message child3 = new TestMessage(childId3, messageId, group1, author,
				contentType, subject, timestamp, raw);
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to the groups and store the messages
		db.addSubscription(txn, group);
		db.addSubscription(txn, group1);
		db.addGroupMessage(txn, message, false);
		db.addGroupMessage(txn, child1, false);
		db.addGroupMessage(txn, child2, false);
		db.addGroupMessage(txn, child3, false);
		// Make all the children sendable
		db.setSendability(txn, childId1, 1);
		db.setSendability(txn, childId2, 5);
		db.setSendability(txn, childId3, 3);

		// There should be two sendable children
		assertEquals(2, db.getNumberOfSendableChildren(txn, messageId));
		// Make one of the children unsendable
		db.setSendability(txn, childId1, 0);
		// Now there should be one sendable child
		assertEquals(1, db.getNumberOfSendableChildren(txn, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetOldMessages() throws Exception {
		MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		Message message1 = new TestMessage(messageId1, null, group, author,
				contentType, subject, timestamp + 1000, raw);
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to a group and store two messages
		db.addSubscription(txn, group);
		db.addGroupMessage(txn, message, false);
		db.addGroupMessage(txn, message1, false);

		// Allowing enough capacity for one message should return the older one
		Iterator<MessageId> it = db.getOldMessages(txn, size).iterator();
		assertTrue(it.hasNext());
		assertEquals(messageId, it.next());
		assertFalse(it.hasNext());

		// Allowing enough capacity for both messages should return both
		Collection<MessageId> ids = new HashSet<MessageId>();
		for(MessageId id : db.getOldMessages(txn, size * 2)) ids.add(id);
		assertEquals(2, ids.size());
		assertTrue(ids.contains(messageId));
		assertTrue(ids.contains(messageId1));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetFreeSpace() throws Exception {
		byte[] largeBody = new byte[ONE_MEGABYTE];
		for(int i = 0; i < largeBody.length; i++) largeBody[i] = (byte) i;
		Message message1 = new TestMessage(messageId, null, group, author,
				contentType, subject, timestamp, largeBody);
		Database<Connection> db = open(false);

		// Sanity check: there should be enough space on disk for this test
		String path = testDir.getAbsolutePath();
		assertTrue(FileSystemUtils.freeSpaceKb(path) * 1024 > MAX_SIZE);

		// The free space should not be more than the allowed maximum size
		long free = db.getFreeSpace();
		assertTrue(free <= MAX_SIZE);
		assertTrue(free > 0);

		// Storing a message should reduce the free space
		Connection txn = db.startTransaction();
		db.addSubscription(txn, group);
		db.addGroupMessage(txn, message1, false);
		db.commitTransaction(txn);
		assertTrue(db.getFreeSpace() < free);

		db.close();
	}

	@Test
	public void testCloseWaitsForCommit() throws Exception {
		final CountDownLatch closing = new CountDownLatch(1);
		final CountDownLatch closed = new CountDownLatch(1);
		final AtomicBoolean transactionFinished = new AtomicBoolean(false);
		final AtomicBoolean error = new AtomicBoolean(false);
		final Database<Connection> db = open(false);

		// Start a transaction
		Connection txn = db.startTransaction();
		// In another thread, close the database
		Thread close = new Thread() {
			public void run() {
				try {
					closing.countDown();
					db.close();
					if(!transactionFinished.get()) error.set(true);
					closed.countDown();
				} catch(Exception e) {
					error.set(true);
				}
			}
		};
		close.start();
		closing.await();
		// Do whatever the transaction needs to do
		Thread.sleep(10);
		transactionFinished.set(true);
		// Commit the transaction
		db.commitTransaction(txn);
		// The other thread should now terminate
		assertTrue(closed.await(5, SECONDS));
		// Check that the other thread didn't encounter an error
		assertFalse(error.get());
	}

	@Test
	public void testCloseWaitsForAbort() throws Exception {
		final CountDownLatch closing = new CountDownLatch(1);
		final CountDownLatch closed = new CountDownLatch(1);
		final AtomicBoolean transactionFinished = new AtomicBoolean(false);
		final AtomicBoolean error = new AtomicBoolean(false);
		final Database<Connection> db = open(false);

		// Start a transaction
		Connection txn = db.startTransaction();
		// In another thread, close the database
		Thread close = new Thread() {
			public void run() {
				try {
					closing.countDown();
					db.close();
					if(!transactionFinished.get()) error.set(true);
					closed.countDown();
				} catch(Exception e) {
					error.set(true);
				}
			}
		};
		close.start();
		closing.await();
		// Do whatever the transaction needs to do
		Thread.sleep(10);
		transactionFinished.set(true);
		// Abort the transaction
		db.abortTransaction(txn);
		// The other thread should now terminate
		assertTrue(closed.await(5, SECONDS));
		// Check that the other thread didn't encounter an error
		assertFalse(error.get());
	}

	@Test
	public void testUpdateRemoteTransportProperties() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact with a transport
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		TransportProperties p = new TransportProperties(
				Collections.singletonMap("foo", "bar"));
		db.setRemoteProperties(txn, contactId, transportId, p, 1);
		assertEquals(Collections.singletonMap(contactId, p),
				db.getRemoteProperties(txn, transportId));

		// Replace the transport properties
		TransportProperties p1 = new TransportProperties(
				Collections.singletonMap("baz", "bam"));
		db.setRemoteProperties(txn, contactId, transportId, p1, 2);
		assertEquals(Collections.singletonMap(contactId, p1),
				db.getRemoteProperties(txn, transportId));

		// Remove the transport properties
		TransportProperties p2 = new TransportProperties();
		db.setRemoteProperties(txn, contactId, transportId, p2, 3);
		assertEquals(Collections.emptyMap(),
				db.getRemoteProperties(txn, transportId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testUpdateLocalTransportProperties() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a transport to the database
		db.addTransport(txn, transportId, 123);

		// Set the transport properties
		TransportProperties p = new TransportProperties();
		p.put("foo", "foo");
		p.put("bar", "bar");
		db.mergeLocalProperties(txn, transportId, p);
		assertEquals(p, db.getLocalProperties(txn, transportId));
		assertEquals(Collections.singletonMap(transportId, p),
				db.getLocalProperties(txn));

		// Update one of the properties and add another
		TransportProperties p1 = new TransportProperties();
		p1.put("bar", "baz");
		p1.put("bam", "bam");
		db.mergeLocalProperties(txn, transportId, p1);
		TransportProperties merged = new TransportProperties();
		merged.put("foo", "foo");
		merged.put("bar", "baz");
		merged.put("bam", "bam");
		assertEquals(merged, db.getLocalProperties(txn, transportId));
		assertEquals(Collections.singletonMap(transportId, merged),
				db.getLocalProperties(txn));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testUpdateTransportConfig() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a transport to the database
		db.addTransport(txn, transportId, 123);

		// Set the transport config
		TransportConfig c = new TransportConfig();
		c.put("foo", "foo");
		c.put("bar", "bar");
		db.mergeConfig(txn, transportId, c);
		assertEquals(c, db.getConfig(txn, transportId));

		// Update one of the properties and add another
		TransportConfig c1 = new TransportConfig();
		c1.put("bar", "baz");
		c1.put("bam", "bam");
		db.mergeConfig(txn, transportId, c1);
		TransportConfig merged = new TransportConfig();
		merged.put("foo", "foo");
		merged.put("bar", "baz");
		merged.put("bam", "bam");
		assertEquals(merged, db.getConfig(txn, transportId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testTransportsNotUpdatedIfVersionIsOld() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));

		// Initialise the transport properties with version 1
		TransportProperties p = new TransportProperties(
				Collections.singletonMap("foo", "bar"));
		assertTrue(db.setRemoteProperties(txn, contactId, transportId, p, 1));
		assertEquals(Collections.singletonMap(contactId, p),
				db.getRemoteProperties(txn, transportId));

		// Replace the transport properties with version 2
		TransportProperties p1 = new TransportProperties(
				Collections.singletonMap("baz", "bam"));
		assertTrue(db.setRemoteProperties(txn, contactId, transportId, p1, 2));
		assertEquals(Collections.singletonMap(contactId, p1),
				db.getRemoteProperties(txn, transportId));

		// Try to replace the transport properties with version 1
		TransportProperties p2 = new TransportProperties(
				Collections.singletonMap("quux", "etc"));
		assertFalse(db.setRemoteProperties(txn, contactId, transportId, p2, 1));

		// Version 2 of the properties should still be there
		assertEquals(Collections.singletonMap(contactId, p1),
				db.getRemoteProperties(txn, transportId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetMessageIfSendableReturnsNullIfNotInDatabase()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and subscribe to a group
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addSubscription(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.setSubscriptions(txn, contactId, Arrays.asList(group), 1);

		// The message is not in the database
		assertNull(db.getRawMessageIfSendable(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetMessageIfSendableReturnsNullIfSeen() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addSubscription(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.setSubscriptions(txn, contactId, Arrays.asList(group), 1);
		db.addGroupMessage(txn, message, false);

		// Set the sendability to > 0 and the status to seen = true
		db.setSendability(txn, messageId, 1);
		db.addStatus(txn, contactId, messageId, true);

		// The message is not sendable because its status is seen = true
		assertNull(db.getRawMessageIfSendable(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetMessageIfSendableReturnsNullIfNotSendable()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addSubscription(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.setSubscriptions(txn, contactId, Arrays.asList(group), 1);
		db.addGroupMessage(txn, message, false);

		// Set the sendability to 0 and the status to seen = false
		db.setSendability(txn, messageId, 0);
		db.addStatus(txn, contactId, messageId, false);

		// The message is not sendable because its sendability is 0
		assertNull(db.getRawMessageIfSendable(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetMessageIfSendableReturnsNullIfOld() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message -
		// the message is older than the contact's retention time
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addSubscription(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.setSubscriptions(txn, contactId, Arrays.asList(group), 1);
		db.setRetentionTime(txn, contactId, timestamp + 1, 1);
		db.addGroupMessage(txn, message, false);

		// Set the sendability to > 0 and the status to seen = false
		db.setSendability(txn, messageId, 1);
		db.addStatus(txn, contactId, messageId, false);

		// The message is not sendable because it's too old
		assertNull(db.getRawMessageIfSendable(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetMessageIfSendableReturnsMessage() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addSubscription(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.setSubscriptions(txn, contactId, Arrays.asList(group), 1);
		db.addGroupMessage(txn, message, false);

		// Set the sendability to > 0 and the status to seen = false
		db.setSendability(txn, messageId, 1);
		db.addStatus(txn, contactId, messageId, false);

		// The message is sendable so it should be returned
		byte[] b = db.getRawMessageIfSendable(txn, contactId, messageId);
		assertArrayEquals(raw, b);

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSetStatusSeenIfVisibleRequiresMessageInDatabase()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and subscribe to a group
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addSubscription(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.setSubscriptions(txn, contactId, Arrays.asList(group), 1);

		// The message is not in the database
		assertFalse(db.setStatusSeenIfVisible(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSetStatusSeenIfVisibleRequiresLocalSubscription()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact with a subscription
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.setSubscriptions(txn, contactId, Arrays.asList(group), 1);

		// There's no local subscription for the group
		assertFalse(db.setStatusSeenIfVisible(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSetStatusSeenIfVisibleRequiresContactSubscription()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addSubscription(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.addGroupMessage(txn, message, false);
		db.addStatus(txn, contactId, messageId, false);

		// There's no contact subscription for the group
		assertFalse(db.setStatusSeenIfVisible(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSetStatusSeenIfVisibleRequiresVisibility()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addSubscription(txn, group);
		db.setSubscriptions(txn, contactId, Arrays.asList(group), 1);
		db.addGroupMessage(txn, message, false);
		db.addStatus(txn, contactId, messageId, false);

		// The subscription is not visible
		assertFalse(db.setStatusSeenIfVisible(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSetStatusSeenIfVisibleReturnsTrueIfAlreadySeen()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addSubscription(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.setSubscriptions(txn, contactId, Arrays.asList(group), 1);
		db.addGroupMessage(txn, message, false);

		// The message has already been seen by the contact
		db.addStatus(txn, contactId, messageId, true);

		assertTrue(db.setStatusSeenIfVisible(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSetStatusSeenIfVisibleReturnsTrueIfNew()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact, subscribe to a group and store a message
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addSubscription(txn, group);
		db.addVisibility(txn, contactId, groupId);
		db.setSubscriptions(txn, contactId, Arrays.asList(group), 1);
		db.addGroupMessage(txn, message, false);

		// The message has not been seen by the contact
		db.addStatus(txn, contactId, messageId, false);

		assertTrue(db.setStatusSeenIfVisible(txn, contactId, messageId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testVisibility() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and subscribe to a group
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addSubscription(txn, group);

		// The group should not be visible to the contact
		assertEquals(Collections.emptyList(), db.getVisibility(txn, groupId));

		// Make the group visible to the contact
		db.addVisibility(txn, contactId, groupId);
		assertEquals(Arrays.asList(contactId), db.getVisibility(txn, groupId));

		// Make the group invisible again
		db.removeVisibility(txn, contactId, groupId);
		assertEquals(Collections.emptyList(), db.getVisibility(txn, groupId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetGroupMessageParentWithNoParent() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to a group
		db.addSubscription(txn, group);

		// A message with no parent should return null
		MessageId childId = new MessageId(TestUtils.getRandomId());
		Message child = new TestMessage(childId, null, group, null, contentType,
				subject, timestamp, raw);
		db.addGroupMessage(txn, child, false);
		assertTrue(db.containsMessage(txn, childId));
		assertNull(db.getGroupMessageParent(txn, childId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetGroupMessageParentWithAbsentParent() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to a group
		db.addSubscription(txn, group);

		// A message with an absent parent should return null
		MessageId childId = new MessageId(TestUtils.getRandomId());
		MessageId parentId = new MessageId(TestUtils.getRandomId());
		Message child = new TestMessage(childId, parentId, group, null,
				contentType, subject, timestamp, raw);
		db.addGroupMessage(txn, child, false);
		assertTrue(db.containsMessage(txn, childId));
		assertFalse(db.containsMessage(txn, parentId));
		assertNull(db.getGroupMessageParent(txn, childId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetGroupMessageParentWithParentInAnotherGroup()
			throws Exception {
		GroupId groupId1 = new GroupId(TestUtils.getRandomId());
		Group group1 = new Group(groupId1, "Group name", null);
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to two groups
		db.addSubscription(txn, group);
		db.addSubscription(txn, group1);

		// A message with a parent in another group should return null
		MessageId childId = new MessageId(TestUtils.getRandomId());
		MessageId parentId = new MessageId(TestUtils.getRandomId());
		Message child = new TestMessage(childId, parentId, group, null,
				contentType, subject, timestamp, raw);
		Message parent = new TestMessage(parentId, null, group1, null,
				contentType, subject, timestamp, raw);
		db.addGroupMessage(txn, child, false);
		db.addGroupMessage(txn, parent, false);
		assertTrue(db.containsMessage(txn, childId));
		assertTrue(db.containsMessage(txn, parentId));
		assertNull(db.getGroupMessageParent(txn, childId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetGroupMessageParentWithPrivateParent() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and subscribe to a group
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addSubscription(txn, group);

		// A message with a private parent should return null
		MessageId childId = new MessageId(TestUtils.getRandomId());
		Message child = new TestMessage(childId, messageId1, group, null,
				contentType, subject, timestamp, raw);
		db.addGroupMessage(txn, child, false);
		db.addPrivateMessage(txn, privateMessage, contactId, false);
		assertTrue(db.containsMessage(txn, childId));
		assertTrue(db.containsMessage(txn, messageId1));
		assertNull(db.getGroupMessageParent(txn, childId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetGroupMessageParentWithParentInSameGroup()
			throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to a group
		db.addSubscription(txn, group);

		// A message with a parent in the same group should return the parent
		MessageId childId = new MessageId(TestUtils.getRandomId());
		MessageId parentId = new MessageId(TestUtils.getRandomId());
		Message child = new TestMessage(childId, parentId, group, null,
				contentType, subject, timestamp, raw);
		Message parent = new TestMessage(parentId, null, group, null,
				contentType, subject, timestamp, raw);
		db.addGroupMessage(txn, child, false);
		db.addGroupMessage(txn, parent, false);
		assertTrue(db.containsMessage(txn, childId));
		assertTrue(db.containsMessage(txn, parentId));
		assertEquals(parentId, db.getGroupMessageParent(txn, childId));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetMessageBody() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and subscribe to a group
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addSubscription(txn, group);

		// Store a couple of messages
		int bodyLength = raw.length - 20;
		Message message1 = new TestMessage(messageId, null, group, null,
				contentType, subject, timestamp, raw, 5, bodyLength);
		Message privateMessage1 = new TestMessage(messageId1, null, null,
				null, contentType, subject, timestamp, raw, 10, bodyLength);
		db.addGroupMessage(txn, message1, false);
		db.addPrivateMessage(txn, privateMessage1, contactId, false);

		// Calculate the expected message bodies
		byte[] expectedBody = new byte[bodyLength];
		System.arraycopy(raw, 5, expectedBody, 0, bodyLength);
		assertFalse(Arrays.equals(expectedBody, new byte[bodyLength]));
		byte[] expectedBody1 = new byte[bodyLength];
		System.arraycopy(raw, 10, expectedBody1, 0, bodyLength);
		System.arraycopy(raw, 10, expectedBody1, 0, bodyLength);

		// Retrieve the raw messages
		assertArrayEquals(raw, db.getRawMessage(txn, messageId));
		assertArrayEquals(raw, db.getRawMessage(txn, messageId1));

		// Retrieve the message bodies
		byte[] body = db.getMessageBody(txn, messageId);
		assertArrayEquals(expectedBody, body);
		byte[] body1 = db.getMessageBody(txn, messageId1);
		assertArrayEquals(expectedBody1, body1);

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetMessageHeaders() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to a group
		db.addSubscription(txn, group);

		// Store a couple of messages
		db.addGroupMessage(txn, message, false);
		MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		MessageId parentId = new MessageId(TestUtils.getRandomId());
		long timestamp1 = System.currentTimeMillis();
		Message message1 = new TestMessage(messageId1, parentId, group, author,
				contentType, subject, timestamp1, raw);
		db.addGroupMessage(txn, message1, false);
		// Mark one of the messages read
		assertFalse(db.setReadFlag(txn, messageId, true));

		// Retrieve the message headers
		Collection<GroupMessageHeader> headers =
				db.getGroupMessageHeaders(txn, groupId);
		Iterator<GroupMessageHeader> it = headers.iterator();
		boolean messageFound = false, message1Found = false;
		// First header (order is undefined)
		assertTrue(it.hasNext());
		GroupMessageHeader header = it.next();
		if(messageId.equals(header.getId())) {
			assertHeadersMatch(message, header);
			assertTrue(header.isRead());
			assertFalse(header.isStarred());
			messageFound = true;
		} else if(messageId1.equals(header.getId())) {
			assertHeadersMatch(message1, header);
			assertFalse(header.isRead());
			assertFalse(header.isStarred());
			message1Found = true;
		} else {
			fail();
		}
		// Second header
		assertTrue(it.hasNext());
		header = it.next();
		if(messageId.equals(header.getId())) {
			assertHeadersMatch(message, header);
			assertTrue(header.isRead());
			assertFalse(header.isStarred());
			messageFound = true;
		} else if(messageId1.equals(header.getId())) {
			assertHeadersMatch(message1, header);
			assertFalse(header.isRead());
			assertFalse(header.isStarred());
			message1Found = true;
		} else {
			fail();
		}
		// No more headers
		assertFalse(it.hasNext());
		assertTrue(messageFound);
		assertTrue(message1Found);

		db.commitTransaction(txn);
		db.close();
	}

	private void assertHeadersMatch(Message m, GroupMessageHeader h) {
		assertEquals(m.getId(), h.getId());
		if(m.getParent() == null) assertNull(h.getParent());
		else assertEquals(m.getParent(), h.getParent());
		assertEquals(m.getGroup().getId(), h.getGroupId());
		if(m.getAuthor() == null) assertNull(h.getAuthor());
		else assertEquals(m.getAuthor(), h.getAuthor());
		assertEquals(m.getContentType(), h.getContentType());
		assertEquals(m.getSubject(), h.getSubject());
		assertEquals(m.getTimestamp(), h.getTimestamp());
	}

	@Test
	public void testReadFlag() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to a group and store a message
		db.addSubscription(txn, group);
		db.addGroupMessage(txn, message, false);

		// The message should be unread by default
		assertFalse(db.getReadFlag(txn, messageId));
		// Marking the message read should return the old value
		assertFalse(db.setReadFlag(txn, messageId, true));
		assertTrue(db.setReadFlag(txn, messageId, true));
		// The message should be read
		assertTrue(db.getReadFlag(txn, messageId));
		// Marking the message unread should return the old value
		assertTrue(db.setReadFlag(txn, messageId, false));
		assertFalse(db.setReadFlag(txn, messageId, false));
		// Unsubscribe from the group
		db.removeSubscription(txn, groupId);

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testStarredFlag() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to a group and store a message
		db.addSubscription(txn, group);
		db.addGroupMessage(txn, message, false);

		// The message should be unstarred by default
		assertFalse(db.getStarredFlag(txn, messageId));
		// Starring the message should return the old value
		assertFalse(db.setStarredFlag(txn, messageId, true));
		assertTrue(db.setStarredFlag(txn, messageId, true));
		// The message should be starred
		assertTrue(db.getStarredFlag(txn, messageId));
		// Unstarring the message should return the old value
		assertTrue(db.setStarredFlag(txn, messageId, false));
		assertFalse(db.setStarredFlag(txn, messageId, false));
		// Unsubscribe from the group
		db.removeSubscription(txn, groupId);

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetUnreadMessageCounts() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Subscribe to a couple of groups
		db.addSubscription(txn, group);
		GroupId groupId1 = new GroupId(TestUtils.getRandomId());
		Group group1 = new Group(groupId1, "Group name", null);
		db.addSubscription(txn, group1);

		// Store two messages in the first group
		db.addGroupMessage(txn, message, false);
		MessageId messageId1 = new MessageId(TestUtils.getRandomId());
		Message message1 = new TestMessage(messageId1, null, group, author,
				contentType, subject, timestamp, raw);
		db.addGroupMessage(txn, message1, false);

		// Store one message in the second group
		MessageId messageId2 = new MessageId(TestUtils.getRandomId());
		Message message2 = new TestMessage(messageId2, null, group1, author,
				contentType, subject, timestamp, raw);
		db.addGroupMessage(txn, message2, false);

		// Mark one of the messages in the first group read
		assertFalse(db.setReadFlag(txn, messageId, true));

		// There should be one unread message in each group
		Map<GroupId, Integer> counts = db.getUnreadMessageCounts(txn);
		assertEquals(2, counts.size());
		Integer count = counts.get(groupId);
		assertNotNull(count);
		assertEquals(1, count.intValue());
		count = counts.get(groupId1);
		assertNotNull(count);
		assertEquals(1, count.intValue());

		// Mark the read message unread (it will now be false rather than null)
		assertTrue(db.setReadFlag(txn, messageId, false));

		// Mark the message in the second group read
		assertFalse(db.setReadFlag(txn, messageId2, true));

		// There should be two unread messages in the first group, none in
		// the second group
		counts = db.getUnreadMessageCounts(txn);
		assertEquals(1, counts.size());
		count = counts.get(groupId);
		assertNotNull(count);
		assertEquals(2, count.intValue());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testMultipleSubscriptionsAndUnsubscriptions() throws Exception {
		// Create some groups
		List<Group> groups = new ArrayList<Group>();
		for(int i = 0; i < 100; i++) {
			GroupId id = new GroupId(TestUtils.getRandomId());
			groups.add(new Group(id, "Group name", null));
		}

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add a contact and subscribe to the groups
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		for(Group g : groups) db.addSubscription(txn, g);

		// Make the groups visible to the contact
		Collections.shuffle(groups);
		for(Group g : groups) db.addVisibility(txn, contactId, g.getId());

		// Make some of the groups invisible to the contact and remove them all
		Collections.shuffle(groups);
		for(Group g : groups) {
			if(Math.random() < 0.5)
				db.removeVisibility(txn, contactId, g.getId());
			db.removeSubscription(txn, g.getId());
		}

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testTemporarySecrets() throws Exception {
		// Create an endpoint and four consecutive temporary secrets
		long epoch = 123, latency = 234;
		boolean alice = false;
		long outgoing1 = 345, centre1 = 456;
		long outgoing2 = 567, centre2 = 678;
		long outgoing3 = 789, centre3 = 890;
		long outgoing4 = 901, centre4 = 123;
		Endpoint ep = new Endpoint(contactId, transportId, epoch, alice);
		Random random = new Random();
		byte[] secret1 = new byte[32], bitmap1 = new byte[4];
		random.nextBytes(secret1);
		random.nextBytes(bitmap1);
		TemporarySecret s1 = new TemporarySecret(contactId, transportId, epoch,
				alice, 0, secret1, outgoing1, centre1, bitmap1);
		byte[] secret2 = new byte[32], bitmap2 = new byte[4];
		random.nextBytes(secret2);
		random.nextBytes(bitmap2);
		TemporarySecret s2 = new TemporarySecret(contactId, transportId, epoch,
				alice, 1, secret2, outgoing2, centre2, bitmap2);
		byte[] secret3 = new byte[32], bitmap3 = new byte[4];
		random.nextBytes(secret3);
		random.nextBytes(bitmap3);
		TemporarySecret s3 = new TemporarySecret(contactId, transportId, epoch,
				alice, 2, secret3, outgoing3, centre3, bitmap3);
		byte[] secret4 = new byte[32], bitmap4 = new byte[4];
		random.nextBytes(secret4);
		random.nextBytes(bitmap4);
		TemporarySecret s4 = new TemporarySecret(contactId, transportId, epoch,
				alice, 3, secret4, outgoing4, centre4, bitmap4);

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Initially there should be no secrets in the database
		assertEquals(Collections.emptyList(), db.getSecrets(txn));

		// Add the contact, the transport, the endpoint and the first three
		// secrets (periods 0, 1 and 2)
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addTransport(txn, transportId, latency);
		db.addEndpoint(txn, ep);
		db.addSecrets(txn, Arrays.asList(s1, s2, s3));

		// Retrieve the first three secrets
		Collection<TemporarySecret> secrets = db.getSecrets(txn);
		assertEquals(3, secrets.size());
		boolean foundFirst = false, foundSecond = false, foundThird = false;
		for(TemporarySecret s : secrets) {
			assertEquals(contactId, s.getContactId());
			assertEquals(transportId, s.getTransportId());
			assertEquals(epoch, s.getEpoch());
			assertEquals(alice, s.getAlice());
			if(s.getPeriod() == 0) {
				assertArrayEquals(secret1, s.getSecret());
				assertEquals(outgoing1, s.getOutgoingConnectionCounter());
				assertEquals(centre1, s.getWindowCentre());
				assertArrayEquals(bitmap1, s.getWindowBitmap());
				foundFirst = true;
			} else if(s.getPeriod() == 1) {
				assertArrayEquals(secret2, s.getSecret());
				assertEquals(outgoing2, s.getOutgoingConnectionCounter());
				assertEquals(centre2, s.getWindowCentre());
				assertArrayEquals(bitmap2, s.getWindowBitmap());
				foundSecond = true;
			} else if(s.getPeriod() == 2) {
				assertArrayEquals(secret3, s.getSecret());
				assertEquals(outgoing3, s.getOutgoingConnectionCounter());
				assertEquals(centre3, s.getWindowCentre());
				assertArrayEquals(bitmap3, s.getWindowBitmap());
				foundThird = true;
			} else {
				fail();
			}
		}
		assertTrue(foundFirst);
		assertTrue(foundSecond);
		assertTrue(foundThird);

		// Adding the fourth secret (period 3) should delete the first
		db.addSecrets(txn, Arrays.asList(s4));
		secrets = db.getSecrets(txn);
		assertEquals(3, secrets.size());
		foundSecond = foundThird = false;
		boolean foundFourth = false;
		for(TemporarySecret s : secrets) {
			assertEquals(contactId, s.getContactId());
			assertEquals(transportId, s.getTransportId());
			assertEquals(epoch, s.getEpoch());
			assertEquals(alice, s.getAlice());
			if(s.getPeriod() == 1) {
				assertArrayEquals(secret2, s.getSecret());
				assertEquals(outgoing2, s.getOutgoingConnectionCounter());
				assertEquals(centre2, s.getWindowCentre());
				assertArrayEquals(bitmap2, s.getWindowBitmap());
				foundSecond = true;
			} else if(s.getPeriod() == 2) {
				assertArrayEquals(secret3, s.getSecret());
				assertEquals(outgoing3, s.getOutgoingConnectionCounter());
				assertEquals(centre3, s.getWindowCentre());
				assertArrayEquals(bitmap3, s.getWindowBitmap());
				foundThird = true;
			} else if(s.getPeriod() == 3) {
				assertArrayEquals(secret4, s.getSecret());
				assertEquals(outgoing4, s.getOutgoingConnectionCounter());
				assertEquals(centre4, s.getWindowCentre());
				assertArrayEquals(bitmap4, s.getWindowBitmap());
				foundFourth = true;
			} else {
				fail();
			}
		}
		assertTrue(foundSecond);
		assertTrue(foundThird);
		assertTrue(foundFourth);

		// Removing the contact should remove the secrets
		db.removeContact(txn, contactId);
		assertEquals(Collections.emptyList(), db.getSecrets(txn));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testIncrementConnectionCounter() throws Exception {
		// Create an endpoint and a temporary secret
		long epoch = 123, latency = 234;
		boolean alice = false;
		long period = 345, outgoing = 456, centre = 567;
		Endpoint ep = new Endpoint(contactId, transportId, epoch, alice);
		Random random = new Random();
		byte[] secret = new byte[32], bitmap = new byte[4];
		random.nextBytes(secret);
		TemporarySecret s = new TemporarySecret(contactId, transportId, epoch,
				alice, period, secret, outgoing, centre, bitmap);

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add the contact, the transport, the endpoint and the temporary secret
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addTransport(txn, transportId, latency);
		db.addEndpoint(txn, ep);
		db.addSecrets(txn, Arrays.asList(s));

		// Retrieve the secret
		Collection<TemporarySecret> secrets = db.getSecrets(txn);
		assertEquals(1, secrets.size());
		s = secrets.iterator().next();
		assertEquals(contactId, s.getContactId());
		assertEquals(transportId, s.getTransportId());
		assertEquals(period, s.getPeriod());
		assertArrayEquals(secret, s.getSecret());
		assertEquals(outgoing, s.getOutgoingConnectionCounter());
		assertEquals(centre, s.getWindowCentre());
		assertArrayEquals(bitmap, s.getWindowBitmap());

		// Increment the connection counter twice and retrieve the secret again
		assertEquals(outgoing, db.incrementConnectionCounter(txn,
				s.getContactId(), s.getTransportId(), s.getPeriod()));
		assertEquals(outgoing + 1, db.incrementConnectionCounter(txn,
				s.getContactId(), s.getTransportId(), s.getPeriod()));
		secrets = db.getSecrets(txn);
		assertEquals(1, secrets.size());
		s = secrets.iterator().next();
		assertEquals(contactId, s.getContactId());
		assertEquals(transportId, s.getTransportId());
		assertEquals(period, s.getPeriod());
		assertArrayEquals(secret, s.getSecret());
		assertEquals(outgoing + 2, s.getOutgoingConnectionCounter());
		assertEquals(centre, s.getWindowCentre());
		assertArrayEquals(bitmap, s.getWindowBitmap());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testSetConnectionWindow() throws Exception {
		// Create an endpoint and a temporary secret
		long epoch = 123, latency = 234;
		boolean alice = false;
		long period = 345, outgoing = 456, centre = 567;
		Endpoint ep = new Endpoint(contactId, transportId, epoch, alice);
		Random random = new Random();
		byte[] secret = new byte[32], bitmap = new byte[4];
		random.nextBytes(secret);
		TemporarySecret s = new TemporarySecret(contactId, transportId, epoch,
				alice, period, secret, outgoing, centre, bitmap);

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add the contact, the transport, the endpoint and the temporary secret
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addTransport(txn, transportId, latency);
		db.addEndpoint(txn, ep);
		db.addSecrets(txn, Arrays.asList(s));

		// Retrieve the secret
		Collection<TemporarySecret> secrets = db.getSecrets(txn);
		assertEquals(1, secrets.size());
		s = secrets.iterator().next();
		assertEquals(contactId, s.getContactId());
		assertEquals(transportId, s.getTransportId());
		assertEquals(period, s.getPeriod());
		assertArrayEquals(secret, s.getSecret());
		assertEquals(outgoing, s.getOutgoingConnectionCounter());
		assertEquals(centre, s.getWindowCentre());
		assertArrayEquals(bitmap, s.getWindowBitmap());

		// Update the connection window and retrieve the secret again
		random.nextBytes(bitmap);
		db.setConnectionWindow(txn, contactId, transportId, period, centre,
				bitmap);
		secrets = db.getSecrets(txn);
		assertEquals(1, secrets.size());
		s = secrets.iterator().next();
		assertEquals(contactId, s.getContactId());
		assertEquals(transportId, s.getTransportId());
		assertEquals(period, s.getPeriod());
		assertArrayEquals(secret, s.getSecret());
		assertEquals(outgoing, s.getOutgoingConnectionCounter());
		assertEquals(centre, s.getWindowCentre());
		assertArrayEquals(bitmap, s.getWindowBitmap());

		// Updating a nonexistent window should not throw an exception
		db.setConnectionWindow(txn, contactId, transportId, period + 1, 1,
				bitmap);
		// The nonexistent window should not have been created
		secrets = db.getSecrets(txn);
		assertEquals(1, secrets.size());
		s = secrets.iterator().next();
		assertEquals(contactId, s.getContactId());
		assertEquals(transportId, s.getTransportId());
		assertEquals(period, s.getPeriod());
		assertArrayEquals(secret, s.getSecret());
		assertEquals(outgoing, s.getOutgoingConnectionCounter());
		assertEquals(centre, s.getWindowCentre());
		assertArrayEquals(bitmap, s.getWindowBitmap());

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testContactTransports() throws Exception {
		// Create some endpoints
		long epoch1 = 123, latency1 = 234;
		long epoch2 = 345, latency2 = 456;
		boolean alice1 = true, alice2 = false;
		TransportId transportId1 = new TransportId(TestUtils.getRandomId());
		TransportId transportId2 = new TransportId(TestUtils.getRandomId());
		Endpoint ep1 = new Endpoint(contactId, transportId1, epoch1, alice1);
		Endpoint ep2 = new Endpoint(contactId, transportId2, epoch2, alice2);

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Initially there should be no endpoints in the database
		assertEquals(Collections.emptyList(), db.getEndpoints(txn));

		// Add the contact, the transports and the endpoints
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		db.addTransport(txn, transportId1, latency1);
		db.addTransport(txn, transportId2, latency2);
		db.addEndpoint(txn, ep1);
		db.addEndpoint(txn, ep2);

		// Retrieve the contact transports
		Collection<Endpoint> endpoints = db.getEndpoints(txn);
		assertEquals(2, endpoints.size());
		boolean foundFirst = false, foundSecond = false;
		for(Endpoint ep : endpoints) {
			assertEquals(contactId, ep.getContactId());
			if(ep.getTransportId().equals(transportId1)) {
				assertEquals(epoch1, ep.getEpoch());
				assertEquals(alice1, ep.getAlice());
				foundFirst = true;
			} else if(ep.getTransportId().equals(transportId2)) {
				assertEquals(epoch2, ep.getEpoch());
				assertEquals(alice2, ep.getAlice());
				foundSecond = true;
			} else {
				fail();
			}
		}
		assertTrue(foundFirst);
		assertTrue(foundSecond);

		// Removing the contact should remove the contact transports
		db.removeContact(txn, contactId);
		assertEquals(Collections.emptyList(), db.getEndpoints(txn));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testGetAvailableGroups() throws Exception {
		ContactId contactId1 = new ContactId(2);
		AuthorId authorId1 = new AuthorId(TestUtils.getRandomId());
		Author author1 = new Author(authorId1, "Carol", new byte[60]);

		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();

		// Add two contacts who subscribe to a group
		db.addLocalAuthor(txn, localAuthor);
		assertEquals(contactId, db.addContact(txn, author, localAuthorId));
		assertEquals(contactId1, db.addContact(txn, author1, localAuthorId));
		db.setSubscriptions(txn, contactId, Arrays.asList(group), 1);
		db.setSubscriptions(txn, contactId1, Arrays.asList(group), 1);

		// The group should be available, not subscribed, not visible to all
		assertEquals(Collections.emptyList(), db.getSubscriptions(txn));
		Iterator<GroupStatus> it = db.getAvailableGroups(txn).iterator();
		assertTrue(it.hasNext());
		GroupStatus status = it.next();
		assertEquals(group, status.getGroup());
		assertFalse(status.isSubscribed());
		assertFalse(status.isVisibleToAll());
		assertFalse(it.hasNext());

		// Subscribe to the group - it should be available, subscribed,
		// not visible to all
		db.addSubscription(txn, group);
		assertEquals(Arrays.asList(group), db.getSubscriptions(txn));
		it = db.getAvailableGroups(txn).iterator();
		assertTrue(it.hasNext());
		status = it.next();
		assertEquals(group, status.getGroup());
		assertTrue(status.isSubscribed());
		assertFalse(status.isVisibleToAll());
		assertFalse(it.hasNext());

		// The first contact unsubscribes - the group should be available,
		// subscribed, not visible to all
		db.setSubscriptions(txn, contactId, Collections.<Group>emptyList(), 2);
		assertEquals(Arrays.asList(group), db.getSubscriptions(txn));
		it = db.getAvailableGroups(txn).iterator();
		assertTrue(it.hasNext());
		status = it.next();
		assertEquals(group, status.getGroup());
		assertTrue(status.isSubscribed());
		assertFalse(status.isVisibleToAll());
		assertFalse(it.hasNext());

		// Make the group visible to all contacts - it should be available,
		// subscribed, visible to all
		db.setVisibleToAll(txn, groupId, true);
		assertEquals(Arrays.asList(group), db.getSubscriptions(txn));
		it = db.getAvailableGroups(txn).iterator();
		assertTrue(it.hasNext());
		status = it.next();
		assertEquals(group, status.getGroup());
		assertTrue(status.isSubscribed());
		assertTrue(status.isVisibleToAll());
		assertFalse(it.hasNext());

		// Unsubscribe from the group - it should be available, not subscribed,
		// not visible to all
		db.removeSubscription(txn, groupId);
		assertEquals(Collections.emptyList(), db.getSubscriptions(txn));
		it = db.getAvailableGroups(txn).iterator();
		assertTrue(it.hasNext());
		status = it.next();
		assertEquals(group, status.getGroup());
		assertFalse(status.isSubscribed());
		assertFalse(status.isVisibleToAll());
		assertFalse(it.hasNext());

		// The second contact unsubscribes - the group should no longer be
		// available
		db.setSubscriptions(txn, contactId1, Collections.<Group>emptyList(), 2);
		assertEquals(Collections.emptyList(), db.getSubscriptions(txn));
		assertEquals(Collections.emptyList(), db.getAvailableGroups(txn));

		db.commitTransaction(txn);
		db.close();
	}

	@Test
	public void testExceptionHandling() throws Exception {
		Database<Connection> db = open(false);
		Connection txn = db.startTransaction();
		try {
			// Ask for a nonexistent message - an exception should be thrown
			db.getRawMessage(txn, messageId);
			fail();
		} catch(DbException expected) {
			// It should be possible to abort the transaction without error
			db.abortTransaction(txn);
		}
		// It should be possible to close the database cleanly
		db.close();
	}

	private Database<Connection> open(boolean resume) throws Exception {
		Database<Connection> db = new H2Database(new TestDatabaseConfig(testDir,
				MAX_SIZE), new SystemClock());
		if(!resume) TestUtils.deleteTestDirectory(testDir);
		db.open();
		return db;
	}

	@After
	public void tearDown() {
		TestUtils.deleteTestDirectory(testDir);
	}
}
