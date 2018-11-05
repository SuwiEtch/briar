package org.briarproject.briar.media;

import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.api.media.Attachment;
import org.briarproject.briar.api.media.AttachmentHeader;

import java.io.BufferedInputStream;
import java.io.InputStream;

import static java.util.logging.Logger.getLogger;

public class AttachmentReader {

	public static Attachment getAttachment(ClientHelper clientHelper,
			AttachmentHeader h) throws DbException {
		// TODO: Support large messages
		MessageId m = h.getMessageId();
//		byte[] body = clientHelper.getMessage(m).getBody();
//		try {
//			BdfDictionary meta = clientHelper.getMessageMetadataAsDictionary(m);
//			String contentType = meta.getString(MSG_KEY_CONTENT_TYPE);
//			if (!contentType.equals(h.getContentType()))
//				throw new InvalidAttachmentException();
//			int offset;
//			try {
//				offset = meta.getLong(MSG_KEY_DESCRIPTOR_LENGTH).intValue();
//			} catch (FormatException e) {
//				throw new InvalidAttachmentException();
//			}
//			InputStream stream = new ByteArrayInputStream(body, offset,
//					body.length - offset);
//			return new Attachment(h, stream);
		String[] files = new String[] {
//				"error_animated.gif",
//				"error_high.jpg",
//				"error_wide.jpg",
//				"error_huge.gif",
//				"error_large.gif",
//				"error_malformed.jpg",
//				"wide.jpg",
//				"high.jpg",
//				"small.png",
				"kitten1.jpg",
				"kitten2.jpg",
//				"kitten3.gif",
				"kitten4.jpg",
				"kitten5.jpg",
				"kitten6.png",
		};
		int index = Math.abs(m.hashCode() % files.length);
		String file = files[index];
		getLogger(AttachmentReader.class.getName())
				.warning("Loading file: " + file);

		InputStream is = clientHelper.getClass().getClassLoader()
				.getResourceAsStream(file);
		return new Attachment(h, new BufferedInputStream(is));
//		} catch (FormatException e) {
//			throw new DbException(e);
//		}
	}

}
